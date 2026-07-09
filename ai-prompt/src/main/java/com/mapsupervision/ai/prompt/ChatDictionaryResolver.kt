package com.mapsupervision.ai.prompt

import com.mapsupervision.ai.core.*

import java.text.Normalizer
import java.util.Locale

object ChatDictionaryResolver {
    fun normalize(text: String): String {
        val temp = Normalizer.normalize(text, Normalizer.Form.NFD)
        return temp.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace('đ', 'd')
            .replace('Đ', 'd')
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun levenshtein(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    fun resolveNode(message: String, selectedNodeCode: String?, refs: NormalizationRefs): Pair<String?, Int> {
        val normMsg = normalize(message)
        val explicitCode = selectedNodeCode?.trim()?.takeIf { it.isNotBlank() }
        
        if (explicitCode != null) {
            val normExplicit = normalize(explicitCode)
            refs.nodeCodes.firstOrNull { 
                val normCode = normalize(it)
                normCode == normExplicit || normCode.endsWith(normExplicit) || normCode.contains(normExplicit) 
            }?.let {
                return Pair(it, 95)
            }
        }

        val finalRefsCodes = if (explicitCode != null && !refs.nodeCodes.contains(explicitCode)) {
            refs.nodeCodes + explicitCode
        } else {
            refs.nodeCodes
        }

        if (normMsg.isBlank()) return Pair(selectedNodeCode, if (selectedNodeCode != null) 90 else 0)

        // 1. Direct mention of specific aliases
        if (normMsg.contains("tru dau tuyen") || normMsg.contains("dau tuyen")) {
            val firstCode = refs.nodeCode ?: finalRefsCodes.firstOrNull()
            if (firstCode != null) {
                return Pair(firstCode, 90)
            }
        }

        // 2. Parse exact word matching from message for known nodeCodes
        val words = normMsg.split(" ")
        for (code in finalRefsCodes) {
            val normCode = normalize(code)
            if (normCode.isNotBlank() && (normMsg.contains(normCode) || words.contains(normCode))) {
                return Pair(code, 95)
            }
        }

        // 3. Match map number label or descriptions (e.g. "ho ga A01" -> matches A01)
        val hgaMatch = Regex("""ho\s*ga\s*([a-z0-9]+)""").find(normMsg)
        if (hgaMatch != null) {
            val num = hgaMatch.groupValues[1]
            finalRefsCodes.firstOrNull { normalize(it).contains(num) }?.let {
                return Pair(it, 90)
            }
        }

        // 4. Fallback to refs.nodeCode or selectedNodeCode
        if (refs.nodeCode != null && refs.nodeCode.isNotBlank()) return Pair(refs.nodeCode, 90)
        if (selectedNodeCode != null && selectedNodeCode.isNotBlank()) return Pair(selectedNodeCode, 80)

        // 5. Fuzzy match against all node codes
        var bestNode: String? = null
        var bestScore = 0
        for (code in finalRefsCodes) {
            val normCode = normalize(code)
            val wordsInCode = normCode.split(" ")
            var score = 0
            for (w in wordsInCode) {
                if (w.length > 2 && normMsg.contains(w)) {
                    score += 30
                }
            }
            val dist = levenshtein(normMsg, normCode)
            if (dist <= 3) {
                score += (100 - dist * 20)
            }
            if (score > bestScore) {
                bestScore = score
                bestNode = code
            }
        }
        if (bestNode != null && bestScore > 40) {
            return Pair(bestNode, minOf(bestScore, 90))
        }

        return Pair(null, 0)
    }

    fun resolveRoute(message: String, selectedRouteCode: String?, refs: NormalizationRefs): Pair<String?, Int> {
        val normMsg = normalize(message)
        if (normMsg.isBlank()) return Pair(selectedRouteCode, if (selectedRouteCode != null) 90 else 0)

        for (ref in refs.routeRefs) {
            val aliases = listOf(ref.code, ref.startNodeCode, ref.endNodeCode, ref.contractor)
                .filter { it.isNotBlank() }
                .map { normalize(it) }
            if (aliases.any { it.isNotBlank() && normMsg.contains(it) }) {
                return Pair(ref.code, 95)
            }
        }

        for (code in refs.routeCodes) {
            val normCode = normalize(code)
            if (normCode.isNotBlank() && normMsg.contains(normCode)) {
                return Pair(code, 95)
            }
        }

        if (refs.routeCode != null && refs.routeCode.isNotBlank()) return Pair(refs.routeCode, 90)
        if (selectedRouteCode != null && selectedRouteCode.isNotBlank()) return Pair(selectedRouteCode, 80)

        return Pair(null, 0)
    }

    fun resolveCategory(message: String, rawCategoryName: String?, refs: NormalizationRefs): Triple<String?, String?, Int> {
        val normMsg = normalize(message)
        val rawCatNorm = rawCategoryName?.let { normalize(it) } ?: ""

        val candidates = refs.categories

        // 1. Exact match with refs
        for (ref in candidates) {
            val normName = normalize(ref.name)
            if (rawCatNorm == normName) {
                return Triple(ref.name, ref.unit.ifBlank { refs.categoryUnit }, 95)
            }
        }

        // 2. Check if raw contains ref.name
        for (ref in candidates) {
            val normName = normalize(ref.name)
            if (normName.isNotBlank() && rawCatNorm.contains(normName)) {
                return Triple(ref.name, ref.unit.ifBlank { refs.categoryUnit }, 90)
            }
        }

        // 3. Check if message contains ref.name
        for (ref in candidates) {
            val normName = normalize(ref.name)
            if (normName.isNotBlank() && normMsg.contains(normName)) {
                return Triple(ref.name, ref.unit.ifBlank { refs.categoryUnit }, 85)
            }
        }

        // Synonyms mapping
        val synonyms = mapOf(
            "be tong mong" to listOf("be tong", "mong", "do be tong", "be tong cot thep"),
            "cap quang" to listOf("cap", "di day", "keo cap", "rai cap", "luon cap"),
            "san lap" to listOf("dao dat", "lap dat", "dao dat ho ga", "ho ga")
        )

        for (ref in candidates) {
            val normName = normalize(ref.name)
            synonyms[normName]?.forEach { syn ->
                if (normMsg.contains(syn) || rawCatNorm.contains(syn)) {
                    return Triple(ref.name, ref.unit.ifBlank { refs.categoryUnit }, 80)
                }
            }
        }

        // Return raw if nothing matched
        if (rawCategoryName != null && rawCategoryName.isNotBlank()) {
            return Triple(rawCategoryName, refs.categoryUnit, 70)
        }

        return Triple(null, refs.categoryUnit, 0)
    }

    fun resolveWeather(message: String): String {
        val normMsg = normalize(message)
        return when {
            normMsg.contains("nang") -> "Nắng"
            normMsg.contains("mua") -> "Mưa"
            normMsg.contains("mat") -> "Mát mẻ"
            normMsg.contains("giong") || normMsg.contains("bao") -> "Giông bão"
            normMsg.contains("am") -> "Ấm áp"
            normMsg.contains("lanh") -> "Lạnh"
            else -> "Bình thường"
        }
    }
}
