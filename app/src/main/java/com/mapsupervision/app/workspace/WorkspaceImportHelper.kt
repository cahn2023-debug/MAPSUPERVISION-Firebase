package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.DuplicateImportPolicy
import com.mapsupervision.domain.model.DuplicateBusinessKey
import com.mapsupervision.domain.model.MergeResult
import com.mapsupervision.domain.model.DedupStats
import com.mapsupervision.domain.model.DedupQualitySnapshot
import java.text.Normalizer
import java.util.Locale
import java.util.LinkedHashSet
import kotlin.math.roundToLong

object WorkspaceImportHelper {
    fun isStructuralRouteNode(code: String, routeNodeCodesUpper: Set<String>): Boolean {
        val upper = code.trim().uppercase()
        if (!routeNodeCodesUpper.contains(upper)) return false
        return (upper.contains("#PM") && upper.contains("_P")) ||
                upper.contains("_P") ||
                upper.endsWith("_S") ||
                upper.endsWith("_E")
    }

    private val COMBINING_MARKS_REGEX = Regex("\\p{Mn}+")
    private val NON_ALNUM_REGEX = Regex("[^a-z0-9]+")

    fun normalizeCode(code: String): String {
        val length = code.length
        if (length == 0) return ""
        var start = 0
        while (start < length && code[start].isWhitespace()) start++
        if (start >= length) return ""
        var end = length
        while (end > start && code[end - 1].isWhitespace()) end--
        var hasUpper = false
        for (i in start until end) {
            if (code[i].isUpperCase()) {
                hasUpper = true
                break
            }
        }
        if (!hasUpper) {
            if (start == 0 && end == length) return code
            return code.substring(start, end)
        }
        return code.substring(start, end).lowercase()
    }

    fun normalizeName(name: String): String {
        val trimmedLower = normalizeCode(name)
        if (trimmedLower.isEmpty()) return ""
        var simpleAscii = true
        for (i in trimmedLower.indices) {
            val ch = trimmedLower[i]
            if (!(ch in 'a'..'z' || ch in '0'..'9')) {
                simpleAscii = false
                break
            }
        }
        if (simpleAscii) return trimmedLower
        val base = Normalizer.normalize(trimmedLower, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS_REGEX, "")
            .replace("đ", "d")
            .replace(NON_ALNUM_REGEX, "")
        return base.trim()
    }

    fun coordBucketKey(lat: Double, lon: Double): Long {
        val latR = (lat * 100_000.0).roundToLong()
        val lonR = (lon * 100_000.0).roundToLong()
        return (latR shl 32) xor (lonR and 0xFFFF_FFFFL)
    }

    fun routeKey(startCode: String, endCode: String): String {
        val a = normalizeCode(startCode)
        val b = normalizeCode(endCode)
        return routeKeyNormalized(a, b)
    }

    fun routeKeyNormalized(a: String, b: String): String {
        return "$a->$b"
    }

    fun buildRouteKeySet(routes: List<GisRoute>): MutableSet<String> {
        val set = HashSet<String>(routes.size * 2 + 1)
        val normalizedCodeCache = HashMap<String, String>(routes.size * 2 + 1)
        fun normalized(value: String): String =
            normalizedCodeCache.getOrPut(value) { normalizeCode(value) }
        for (route in routes) {
            val start = normalized(route.startNodeCode)
            val end = normalized(route.endNodeCode)
            set += routeKeyNormalized(start, end)
        }
        return set
    }

    fun deduplicateImportedGeometry(
        projectId: String,
        incomingNodes: List<GisNode>,
        incomingRoutes: List<GisRoute>,
        existingNodes: List<GisNode>,
        existingRoutes: List<GisRoute>,
        duplicatePolicy: DuplicateImportPolicy = DuplicateImportPolicy.SKIP,
        deduplicationKey: DuplicateBusinessKey = DuplicateBusinessKey.CODE
    ): MergeResult {
        val nodeByCode = HashMap<String, GisNode>(existingNodes.size * 2)
        val nodeByName = HashMap<String, GisNode>(existingNodes.size * 2)
        val nodeByCoord = HashMap<Long, GisNode>(existingNodes.size * 2)
        for (node in existingNodes) {
            nodeByCode[normalizeCode(node.code)] = node
            nodeByName[normalizeName(node.code)] = node
            nodeByCoord[coordBucketKey(node.latitude, node.longitude)] = node
        }
        return deduplicateWithIndexes(
            projectId = projectId,
            incomingNodes = incomingNodes,
            incomingRoutes = incomingRoutes,
            nodeByCode = nodeByCode,
            nodeByName = nodeByName,
            nodeByCoord = nodeByCoord,
            codeAlias = HashMap<String, String>(incomingNodes.size * 2 + 1),
            existingRouteKeys = buildRouteKeySet(existingRoutes),
            existingRoutesByKey = existingRoutes.associateBy { routeKey(it.startNodeCode, it.endNodeCode) }.toMutableMap(),
            duplicatePolicy = duplicatePolicy,
            deduplicationKey = deduplicationKey
        )
    }

    fun deduplicateWithIndexes(
        projectId: String,
        incomingNodes: List<GisNode>,
        incomingRoutes: List<GisRoute>,
        nodeByCode: MutableMap<String, GisNode>,
        nodeByName: MutableMap<String, GisNode>,
        nodeByCoord: MutableMap<Long, GisNode>,
        codeAlias: MutableMap<String, String>,
        existingRouteKeys: MutableSet<String>,
        existingRoutesByKey: MutableMap<String, GisRoute> = mutableMapOf(),
        duplicatePolicy: DuplicateImportPolicy = DuplicateImportPolicy.SKIP,
        deduplicationKey: DuplicateBusinessKey = DuplicateBusinessKey.CODE
    ): MergeResult {
        val nodesToInsert = ArrayList<GisNode>(incomingNodes.size)
        var duplicateNodes = 0
        var codeMatches = 0
        var nameMatches = 0
        var coordMatches = 0
        var multiSignalMatches = 0
        var strongMatches = 0
        var weakMatches = 0
        var coordOnlyRejected = 0
        val cacheCapacity = incomingNodes.size.coerceAtLeast(16)
        val normalizedCodeCache = HashMap<String, String>(cacheCapacity)
        val normalizedNameCache = HashMap<String, String>(cacheCapacity)
        val coordKeyCache = HashMap<Long, Long>(cacheCapacity)
        val contractorKeyCache = HashMap<String, String>(cacheCapacity)

        fun codeKeyOf(value: String): String =
            normalizedCodeCache.getOrPut(value) { normalizeCode(value) }

        fun nameKeyOf(value: String): String =
            normalizedNameCache.getOrPut(value) { normalizeName(value) }

        fun coordKeyOf(lat: Double, lon: Double): Long {
            val latBits = java.lang.Double.doubleToRawLongBits(lat)
            val lonBits = java.lang.Double.doubleToRawLongBits(lon)
            val raw = latBits xor (lonBits * 31L)
            return coordKeyCache.getOrPut(raw) { coordBucketKey(lat, lon) }
        }

        fun contractorKeyOf(value: String): String =
            contractorKeyCache.getOrPut(value) { normalizeCode(value) }

        fun registerAlias(rawCode: String, normalizedCode: String, canonicalCode: String) {
            if (rawCode.isNotBlank()) codeAlias[rawCode] = canonicalCode
            if (normalizedCode.isNotBlank()) codeAlias[normalizedCode] = canonicalCode
        }

        fun chooseCanonical(
            byName: GisNode?,
            byCoord: GisNode?,
            byCode: GisNode?
        ): GisNode? {
            return when (deduplicationKey) {
                DuplicateBusinessKey.CODE -> byCode ?: byName
                DuplicateBusinessKey.COORDINATES -> byCoord
                DuplicateBusinessKey.COMPOSITE_CODE_COORD -> {
                    val codeCandidate = byCode ?: byName
                    if (codeCandidate != null && byCoord != null && codeCandidate.id == byCoord.id) {
                        codeCandidate
                    } else null
                }
            }
        }

        for (node in incomingNodes) {
            val codeKey = codeKeyOf(node.code)
            val nameKey = DedupSignalPolicy.effectiveNameKey(codeKey, nameKeyOf(node.code))
            val coordKey = coordKeyOf(node.latitude, node.longitude)

            val matchByCode = nodeByCode[codeKey]
            val matchByName = if (nameKey.isNotBlank()) nodeByName[nameKey] else null
            val matchByCoord = nodeByCoord[coordKey]

            val canonical = chooseCanonical(
                byCode = matchByCode,
                byName = matchByName,
                byCoord = matchByCoord
            )

            if (canonical != null) {
                duplicateNodes++
                registerAlias(node.code, codeKey, canonical.code)

                if (duplicatePolicy == DuplicateImportPolicy.UPDATE) {
                    nodesToInsert.add(node.copy(id = canonical.id))
                }

                var matchedSignals = 0
                if (canonical == matchByCode) {
                    codeMatches++
                    matchedSignals++
                }
                if (canonical == matchByName) {
                    nameMatches++
                    matchedSignals++
                }
                if (canonical == matchByCoord) {
                    coordMatches++
                    matchedSignals++
                }
                if (matchedSignals > 1) {
                    multiSignalMatches++
                }

                val hasCodeMatch = (canonical == matchByCode)
                val hasNameMatch = (canonical == matchByName)
                val hasCoordMatch = (canonical == matchByCoord)
                val hasContractorMatch = contractorKeyOf(node.contractor) == contractorKeyOf(canonical.contractor)

                val isStrong = (hasCodeMatch && hasCoordMatch) || (hasNameMatch && hasCoordMatch) || (hasCodeMatch && hasContractorMatch)
                if (isStrong) {
                    strongMatches++
                } else {
                    weakMatches++
                }
            } else {
                if (matchByCoord != null) {
                    val reject = DedupCoordMatchPolicy.shouldRejectCoordOnlyMatch(
                        incomingCodeKey = codeKey,
                        canonicalCodeKey = codeKeyOf(matchByCoord.code),
                        incomingContractorKey = contractorKeyOf(node.contractor),
                        canonicalContractorKey = contractorKeyOf(matchByCoord.contractor)
                    )
                    if (reject) {
                        coordOnlyRejected++
                    }
                }

                nodesToInsert.add(node)
                nodeByCode[codeKey] = node
                if (nameKey.isNotBlank()) {
                    nodeByName[nameKey] = node
                }
                nodeByCoord[coordKey] = node
            }
        }

        val routesToInsert = ArrayList<GisRoute>(incomingRoutes.size)
        var skippedSelfRoutes = 0
        var skippedDuplicateRoutes = 0

        for (route in incomingRoutes) {
            val start = codeAlias[route.startNodeCode] ?: route.startNodeCode
            val end = codeAlias[route.endNodeCode] ?: route.endNodeCode
            val normStart = codeKeyOf(start)
            val normEnd = codeKeyOf(end)
            if (normStart.isBlank() || normEnd.isBlank()) {
                routesToInsert.add(route.copy(startNodeCode = start, endNodeCode = end))
                continue
            }
            if (normStart == normEnd) {
                skippedSelfRoutes++
                continue
            }
            val key = routeKeyNormalized(normStart, normEnd)
            if (existingRouteKeys.add(key)) {
                routesToInsert.add(route.copy(startNodeCode = start, endNodeCode = end))
            } else {
                skippedDuplicateRoutes++
                if (duplicatePolicy == DuplicateImportPolicy.UPDATE) {
                    existingRoutesByKey[key]?.let { existing ->
                        routesToInsert.add(route.copy(id = existing.id, startNodeCode = start, endNodeCode = end))
                    }
                }
            }
        }

        return MergeResult(
            nodesToInsert = nodesToInsert,
            routesToInsert = routesToInsert,
            duplicateNodes = duplicateNodes,
            stats = DedupStats(
                codeMatches = codeMatches,
                nameMatches = nameMatches,
                coordMatches = coordMatches,
                multiSignalMatches = multiSignalMatches,
                strongMatches = strongMatches,
                weakMatches = weakMatches,
                coordOnlyRejected = coordOnlyRejected,
                skippedSelfRoutes = skippedSelfRoutes,
                skippedDuplicateRoutes = skippedDuplicateRoutes
            )
        )
    }

    fun dedupQualityScore(
        incomingNodes: Int,
        strongMatches: Int,
        weakMatches: Int,
        coordOnlyRejected: Int,
        incomingRoutes: Int,
        skippedSelfRoutes: Int,
        skippedDuplicateRoutes: Int
    ): Int {
        return DedupQualityScorer.score(
            incomingNodes = incomingNodes,
            strongMatches = strongMatches,
            weakMatches = weakMatches,
            coordOnlyRejected = coordOnlyRejected,
            incomingRoutes = incomingRoutes,
            skippedSelfRoutes = skippedSelfRoutes,
            skippedDuplicateRoutes = skippedDuplicateRoutes
        )
    }

    fun dedupQualitySnapshot(
        incomingNodes: Int,
        strongMatches: Int,
        weakMatches: Int,
        coordOnlyRejected: Int,
        incomingRoutes: Int,
        skippedSelfRoutes: Int,
        skippedDuplicateRoutes: Int
    ): DedupQualitySnapshot {
        val score = dedupQualityScore(
            incomingNodes = incomingNodes,
            strongMatches = strongMatches,
            weakMatches = weakMatches,
            coordOnlyRejected = coordOnlyRejected,
            incomingRoutes = incomingRoutes,
            skippedSelfRoutes = skippedSelfRoutes,
            skippedDuplicateRoutes = skippedDuplicateRoutes
        )
        val risk = DedupQualityAdvisor.riskLevel(
            score = score,
            incomingNodes = incomingNodes,
            strongMatches = strongMatches,
            weakMatches = weakMatches,
            coordOnlyRejected = coordOnlyRejected,
            incomingRoutes = incomingRoutes,
            skippedSelfRoutes = skippedSelfRoutes,
            skippedDuplicateRoutes = skippedDuplicateRoutes
        )
        return DedupQualitySnapshot(
            score = score,
            label = DedupQualityAdvisor.label(score),
            risk = risk,
            action = DedupQualityAdvisor.actionByRisk(risk),
            actionNote = DedupQualityAdvisor.actionNote(DedupQualityAdvisor.actionByRisk(risk)),
            diagnostics = DedupQualityAdvisor.diagnostics(
                incomingNodes = incomingNodes,
                strongMatches = strongMatches,
                weakMatches = weakMatches,
                coordOnlyRejected = coordOnlyRejected,
                incomingRoutes = incomingRoutes,
                skippedSelfRoutes = skippedSelfRoutes,
                skippedDuplicateRoutes = skippedDuplicateRoutes
            ),
            hint = DedupQualityAdvisor.hint(
                score = score,
                incomingNodes = incomingNodes,
                strongMatches = strongMatches,
                weakMatches = weakMatches,
                coordOnlyRejected = coordOnlyRejected,
                incomingRoutes = incomingRoutes,
                skippedSelfRoutes = skippedSelfRoutes,
                skippedDuplicateRoutes = skippedDuplicateRoutes
            )
        )
    }
}
