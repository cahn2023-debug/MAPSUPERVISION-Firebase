package com.mapsupervision.ai.prompt

import com.mapsupervision.ai.core.*
import com.mapsupervision.ai.prompt.*

object ChatActionParser {

    fun parse(
        message: String,
        contextSummary: String = "",
        selectedNodeCode: String? = null,
        normalizationContext: String = "",
        selectedRouteCode: String? = null,
        explicitAction: ChatActionType? = null
    ): ChatAssistantResult {
        return parseInternal(message, contextSummary, selectedNodeCode, selectedRouteCode, normalizationContext, explicitAction)
    }

    fun parseLlmResponse(
        llmResponse: String,
        selectedNodeCode: String? = null,
        normalizationContext: String = "",
        selectedRouteCode: String? = null
    ): ChatAssistantResult {
        val actionRegex = Regex("""\[ACTION:\s*([A-Z_]+)\s*(.*?)\]""", RegexOption.IGNORE_CASE)
        val match = actionRegex.find(llmResponse) ?: return ChatAssistantResult(
            answer = llmResponse,
            writeDisposition = WriteDisposition.REJECT,
            confidence = ChatConfidenceScore(50, 0, 0, 40, false)
        )
        val actionName = match.groupValues[1].uppercase()
        val paramsContent = match.groupValues[2]
        val cleanAnswer = llmResponse.replace(match.value, "").trim()
        val params = parseParams(paramsContent)

        return processPipeline(
            actionName = actionName,
            params = params,
            message = llmResponse,
            cleanAnswer = cleanAnswer.ifBlank { "Đã xử lý yêu cầu." },
            selectedNodeCode = selectedNodeCode,
            selectedRouteCode = selectedRouteCode,
            normalizationContext = normalizationContext
        )
    }

    private fun parseInternal(
        message: String,
        contextSummary: String,
        selectedNodeCode: String?,
        selectedRouteCode: String?,
        normalizationContext: String,
        explicitAction: ChatActionType?
    ): ChatAssistantResult {
        val resolvedRefs = NormalizationRefsParser.parse(normalizationContext)
        val normalized = normalizeText(message)
        val actionName: String
        val params = mutableMapOf<String, String>()

        val (nodeCode, nodeConf) = ChatDictionaryResolver.resolveNode(
            message = message,
            selectedNodeCode = selectedNodeCode ?: resolvedRefs.nodeCode,
            refs = resolvedRefs
        )
        val (routeCode, routeConf) = ChatDictionaryResolver.resolveRoute(
            message = message,
            selectedRouteCode = selectedRouteCode ?: resolvedRefs.routeCode,
            refs = resolvedRefs
        )
        val locationConf = maxOf(nodeConf, routeConf)
        val categoryNameResolved = ChatDictionaryResolver.resolveCategory(
            message = message,
            rawCategoryName = null,
            refs = resolvedRefs
        )
        val categoryConf = categoryNameResolved.third

        if (explicitAction != null) {
            actionName = explicitAction.name
            when (explicitAction) {
                ChatActionType.ADD_DAILY_LOG -> {
                    params["nodeCode"] = nodeCode.orEmpty()
                    params["routeCode"] = routeCode.orEmpty()
                    params["workItem"] = extractAfter(message, listOf("work", "cong viec", "nhat ky", "thi cong", "trien khai"))
                    params["manpower"] = extractInt(message, listOf("manpower", "nhan luc", "so nguoi"))?.toString() ?: "1"
                    params["note"] = message.trim()
                    params["volume"] = extractNumber(message, listOf("volume", "khoi luong", "thuc te", "actual"))?.toString() ?: "0"
                    params["categoryName"] = extractAfter(message, listOf("category", "hang muc", "vat tu"))
                }
                ChatActionType.ADD_WORK_PLAN -> {
                    params["nodeCode"] = nodeCode.orEmpty()
                    params["routeCode"] = routeCode.orEmpty()
                    params["title"] = "Kế hoạch thi công " + (nodeCode ?: routeCode ?: "")
                    params["description"] = message.trim()
                }
                ChatActionType.GENERATE_SUMMARY -> {
                    val scope = when {
                        normalized.contains("nha thau") || normalized.contains("contractor") -> "contractor"
                        normalized.contains("node") || normalized.contains("tram") || normalized.contains("nut") -> "node"
                        normalized.contains("ngay") || normalized.contains("tuan") || normalized.contains("thang") || normalized.contains("time") -> "time_range"
                        else -> "project"
                    }
                    params["scope"] = scope
                    val groupBy = when {
                        normalized.contains("nha thau") || normalized.contains("contractor") -> "contractor"
                        normalized.contains("trang thai") || normalized.contains("status") -> "status"
                        normalized.contains("ngay") || normalized.contains("day") -> "day"
                        else -> null
                    }
                    groupBy?.let { params["groupBy"] = it }
                    val currentEpoch = java.time.LocalDate.now().toEpochDay()
                    if (normalized.contains("tuan nay")) {
                        params["dateFromEpochDay"] = (currentEpoch - 7).toString()
                        params["dateToEpochDay"] = currentEpoch.toString()
                    } else if (normalized.contains("hom qua")) {
                        params["dateFromEpochDay"] = (currentEpoch - 1).toString()
                        params["dateToEpochDay"] = (currentEpoch - 1).toString()
                    }
                }
                else -> {}
            }
        } else {
            val hasLocation = nodeCode != null || routeCode != null
            val hasDate = DailyLogDateResolver.parseDateText(message) != null ||
                    normalized.contains("hom nay") || normalized.contains("hom qua") ||
                    Regex("""\d{1,2}[/-]\d{1,2}""").containsMatchIn(message)
            val hasWork = normalized.contains("thi cong") || normalized.contains("trien khai") ||
                    normalized.contains("mong") || normalized.contains("cot") ||
                    normalized.contains("lap") || normalized.contains("keo cap")
            val hasExplicitIntent = normalized.contains("nhat ky") || normalized.contains("daily log") ||
                    normalized.contains("nhiem vu") || normalized.contains("task") ||
                    normalized.contains("ghi chu") || normalized.contains("note") ||
                    normalized.contains("tong hop") || normalized.contains("summary") ||
                    normalized.contains("bao cao") || normalized.contains("report")

            if (hasDate && hasWork && hasLocation && !hasExplicitIntent) {
                val resolvedEntities = buildMap {
                    if (nodeCode != null) put("node", nodeCode)
                    if (routeCode != null) put("route", routeCode)
                }
                val options = listOf(
                    ChatIntentOption(
                        type = ChatActionType.ADD_DAILY_LOG,
                        label = "Ghi nhật ký",
                        draftJson = ""
                    ),
                    ChatIntentOption(
                        type = ChatActionType.ADD_WORK_PLAN,
                        label = "Kế hoạch",
                        draftJson = ""
                    ),
                    ChatIntentOption(
                        type = ChatActionType.GENERATE_SUMMARY,
                        label = "Tổng hợp",
                        draftJson = ""
                    )
                )
                return ChatAssistantResult(
                    answer = "Mình nhận thấy tin nhắn liên quan đến cả kế hoạch và nhật ký thi công. Bạn muốn làm gì tiếp theo?",
                    clarificationPrompt = ChatClarificationPrompt(
                        message = "Vui lòng chọn một hành động:",
                        options = options
                    ),
                    writeDisposition = WriteDisposition.REQUIRE_CONFIRMATION,
                    confidence = ChatConfidenceScore(90, locationConf, categoryConf, 85, true),
                    resolvedEntities = resolvedEntities
                )
            }

            when {
                normalized.contains("nhiem vu") || normalized.contains("task") || normalized.contains("viec can lam") -> {
                    actionName = "ADD_TASK"
                    params["objectCode"] = extractObjectCode(message, selectedNodeCode).orEmpty()
                    params["title"] = extractAfter(message, listOf("nhiem vu", "task", "viec can lam"))
                }
                normalized.contains("ghi chu") || normalized.contains("note") -> {
                    actionName = "ADD_NOTE"
                    params["objectCode"] = extractObjectCode(message, selectedNodeCode).orEmpty()
                    params["content"] = extractAfter(message, listOf("ghi chu", "note"))
                }
                normalized.contains("tong hop") || normalized.contains("tom tat") || normalized.contains("thong ke") || normalized.contains("summary") -> {
                    actionName = "GENERATE_SUMMARY"
                    val scope = when {
                        normalized.contains("nha thau") || normalized.contains("contractor") -> "contractor"
                        normalized.contains("node") || normalized.contains("tram") || normalized.contains("nut") -> "node"
                        normalized.contains("ngay") || normalized.contains("tuan") || normalized.contains("thang") || normalized.contains("time") -> "time_range"
                        else -> "project"
                    }
                    params["scope"] = scope
                    val groupBy = when {
                        normalized.contains("nha thau") || normalized.contains("contractor") -> "contractor"
                        normalized.contains("trang thai") || normalized.contains("status") -> "status"
                        normalized.contains("ngay") || normalized.contains("day") -> "day"
                        else -> null
                    }
                    groupBy?.let { params["groupBy"] = it }
                    val currentEpoch = java.time.LocalDate.now().toEpochDay()
                    if (normalized.contains("tuan nay")) {
                        params["dateFromEpochDay"] = (currentEpoch - 7).toString()
                        params["dateToEpochDay"] = currentEpoch.toString()
                    } else if (normalized.contains("hom qua")) {
                        params["dateFromEpochDay"] = (currentEpoch - 1).toString()
                        params["dateToEpochDay"] = (currentEpoch - 1).toString()
                    }
                }
                normalized.contains("khoi luong") || normalized.contains("volume") || normalized.contains("vat tu") ||
                        normalized.contains(" met ") || normalized.contains(" md ") || normalized.contains(" cai ") || normalized.contains(" ong ") -> {
                    actionName = "UPDATE_MATERIAL_OR_VOLUME_PROGRESS"
                    params["nodeCode"] = extractNodeCode(message, selectedNodeCode).orEmpty()
                    val plannedVal = extractNumber(message, listOf("planned", "ke hoach"))
                    if (plannedVal != null) {
                        params["plannedQty"] = plannedVal.toString()
                    }
                    var cleanMsgForActual = message
                    val nodeCodeToStrip = params["nodeCode"]
                    if (!nodeCodeToStrip.isNullOrBlank()) {
                        val escapeNode = Regex.escape(nodeCodeToStrip)
                        val stripRegex = Regex("""(?:node|tram|trạm|nut|nút|ho\s*ga|hố\s*ga)\s*$escapeNode""", RegexOption.IGNORE_CASE)
                        cleanMsgForActual = cleanMsgForActual.replace(stripRegex, "")
                        val wordRegex = Regex("""\b$escapeNode\b""", RegexOption.IGNORE_CASE)
                        cleanMsgForActual = cleanMsgForActual.replace(wordRegex, "")
                    }
                    val plannedRegexes = listOf(
                        Regex("""planned\s*[:=]?\s*\d+(?:[.,]\d+)?""", RegexOption.IGNORE_CASE),
                        Regex("""ke\s*hoach\s*[:=]?\s*\d+(?:[.,]\d+)?""", RegexOption.IGNORE_CASE)
                    )
                    plannedRegexes.forEach { r ->
                        cleanMsgForActual = cleanMsgForActual.replace(r, "")
                    }
                    val qty = extractProgressOrQty(cleanMsgForActual)
                    if (qty != null) {
                        params["actualQty"] = qty.toString()
                    }
                    params["materialName"] = extractAfter(message, listOf("hang muc", "vat tu", "material", "khoi luong"))
                }
                normalized.contains("cap nhat") || normalized.contains("progress") || normalized.contains("tien do") ||
                        normalized.contains("xong") || normalized.contains("hoan thanh") || normalized.contains("%") -> {
                    actionName = "UPDATE_CONSTRUCTION_PROGRESS"
                    params["nodeCode"] = extractNodeCode(message, selectedNodeCode).orEmpty()
                    val plannedVal = extractNumber(message, listOf("planned", "ke hoach"))
                    if (plannedVal != null) {
                        params["planned"] = plannedVal.toString()
                    }
                    var cleanMsgForActual = message
                    val nodeCodeToStrip = params["nodeCode"]
                    if (!nodeCodeToStrip.isNullOrBlank()) {
                        val escapeNode = Regex.escape(nodeCodeToStrip)
                        val stripRegex = Regex("""(?:node|tram|trạm|nut|nút|ho\s*ga|hố\s*ga)\s*$escapeNode""", RegexOption.IGNORE_CASE)
                        cleanMsgForActual = cleanMsgForActual.replace(stripRegex, "")
                        val wordRegex = Regex("""\b$escapeNode\b""", RegexOption.IGNORE_CASE)
                        cleanMsgForActual = cleanMsgForActual.replace(wordRegex, "")
                    }
                    val plannedRegexes = listOf(
                        Regex("""planned\s*[:=]?\s*\d+(?:[.,]\d+)?""", RegexOption.IGNORE_CASE),
                        Regex("""ke\s*hoach\s*[:=]?\s*\d+(?:[.,]\d+)?""", RegexOption.IGNORE_CASE)
                    )
                    plannedRegexes.forEach { r ->
                        cleanMsgForActual = cleanMsgForActual.replace(r, "")
                    }
                    val pct = extractProgressOrQty(cleanMsgForActual)
                    if (pct != null) {
                        params["actual"] = pct.toString()
                    }
                }
                normalized.contains("nhat ky") || normalized.contains("daily log") -> {
                    actionName = "ADD_DAILY_LOG"
                    params["nodeCode"] = extractNodeCode(message, selectedNodeCode).orEmpty()
                    params["workItem"] = extractAfter(message, listOf("work", "cong viec", "nhat ky"))
                    params["manpower"] = extractInt(message, listOf("manpower", "nhan luc", "so nguoi"))?.toString() ?: "1"
                    params["note"] = message.trim()
                    params["volume"] = extractNumber(message, listOf("volume", "khoi luong", "thuc te", "actual"))?.toString() ?: "0"
                    params["categoryName"] = extractAfter(message, listOf("category", "hang muc", "vat tu"))
                }
                normalized.contains("bao cao") || normalized.contains("report") -> {
                    actionName = "SAVE_REPORT_DRAFT"
                    params["title"] = "Báo cáo giám sát tự động"
                }
                else -> {
                    return ChatAssistantResult(
                        answer = buildFallbackAnswer(message, contextSummary),
                        confidence = ChatConfidenceScore(30, 0, 0, 30, false),
                        writeDisposition = WriteDisposition.REJECT
                    )
                }
            }
        }

        return processPipeline(
            actionName = actionName,
            params = params,
            message = message,
            cleanAnswer = if (actionName == "GENERATE_SUMMARY") "Dưới đây là tổng hợp báo cáo." else "Mình đã chuẩn bị sẵn phiếu xử lý.",
            selectedNodeCode = selectedNodeCode,
            selectedRouteCode = selectedRouteCode,
            normalizationContext = normalizationContext
        )
    }

    private fun extractProgressOrQty(message: String): Float? {
        val normalized = normalizeText(message)
        val percentRegex = Regex("""(\d+(?:[.,]\d+)?)\s*%""")
        percentRegex.find(message)?.let { match ->
            return match.groupValues[1].replace(',', '.').toFloatOrNull()
        }
        if (normalized.contains("xong") || normalized.contains("hoan thanh")) {
            return 100f
        }
        val numRegex = Regex("""(?:\b|[^0-9])(\d+(?:[.,]\d+)?)(?:\b|[^0-9])""")
        numRegex.findAll(message).forEach { match ->
            val numStr = match.groupValues[1].replace(',', '.')
            numStr.toFloatOrNull()?.let { return it }
        }
        return null
    }

    private fun processPipeline(
        actionName: String,
        params: Map<String, String>,
        message: String,
        cleanAnswer: String,
        selectedNodeCode: String?,
        selectedRouteCode: String?,
        normalizationContext: String
    ): ChatAssistantResult {
        val resolvedRefs = NormalizationRefsParser.parse(normalizationContext)

        // 1. Intent Detection
        val intent = runCatching { ChatActionType.valueOf(actionName) }.getOrNull()
        val intentConfidence = if (intent != null) 95 else 0

        if (intent == null) {
            return ChatAssistantResult(
                answer = cleanAnswer.ifBlank { message },
                writeDisposition = WriteDisposition.REJECT,
                confidence = ChatConfidenceScore(intentConfidence, 0, 0, 0, false)
            )
        }

        // 2 & 3. Entity Extraction and Canonicalization
        val (nodeCode, nodeConf) = ChatDictionaryResolver.resolveNode(
            message = message,
            selectedNodeCode = params["nodeCode"] ?: params["objectCode"] ?: params["matchedNodeCode"] ?: selectedNodeCode,
            refs = resolvedRefs
        )

        val (routeCode, routeConf) = ChatDictionaryResolver.resolveRoute(
            message = message,
            selectedRouteCode = params["routeCode"] ?: selectedRouteCode ?: resolvedRefs.routeCode,
            refs = resolvedRefs
        )

        val (categoryName, categoryUnit, categoryConf) = ChatDictionaryResolver.resolveCategory(
            message = message,
            rawCategoryName = params["categoryName"] ?: params["materialName"] ?: params["workItem"],
            refs = resolvedRefs
        )

        // 4. Scoring & Write Policy
        var isDataComplete = false
        val missingFields = mutableListOf<String>()
        val locationConf = maxOf(nodeConf, routeConf)

        var draftJson = ""
        var pendingAction: ChatPendingAction? = null

        when (intent) {
            ChatActionType.UPDATE_CONSTRUCTION_PROGRESS -> {
                val planned = params["planned"]?.toFloatOrNull()
                val actual = params["actual"]?.toFloatOrNull()
                if (nodeCode != null && planned != null && actual != null) {
                    isDataComplete = true
                } else {
                    if (nodeCode == null) missingFields.add("nodeCode")
                    if (planned == null) missingFields.add("planned")
                    if (actual == null) missingFields.add("actual")
                }
                val draft = ConstructionProgressDraft(nodeCode = nodeCode.orEmpty(), planned = planned ?: 100f, actual = actual ?: 0f)
                draftJson = """{"nodeCode":"${escapeJson(draft.nodeCode)}","planned":${draft.planned},"actual":${draft.actual}}"""
                pendingAction = ChatPendingAction(
                    type = intent,
                    title = "Cập nhật thi công node ${draft.nodeCode}",
                    draftJson = draftJson,
                    constructionProgress = draft
                )
            }
            ChatActionType.UPDATE_MATERIAL_OR_VOLUME_PROGRESS -> {
                val planned = params["plannedQty"]?.toFloatOrNull() ?: params["planned"]?.toFloatOrNull()
                val actual = params["actualQty"]?.toFloatOrNull() ?: params["actual"]?.toFloatOrNull() ?: params["volume"]?.toFloatOrNull()
                val matName = categoryName?.takeIf { it.isNotBlank() } ?: params["materialName"] ?: ""
                
                if (nodeCode != null && matName.isNotBlank() && actual != null) {
                    isDataComplete = true
                } else {
                    if (nodeCode == null) missingFields.add("nodeCode")
                    if (matName.isBlank()) missingFields.add("materialName")
                    if (actual == null) missingFields.add("actualQty")
                }

                val draft = MaterialOrVolumeProgressDraft(
                    nodeCode = nodeCode.orEmpty(),
                    materialName = matName,
                    actualQty = actual ?: 0f,
                    plannedQty = planned,
                    unit = categoryUnit.orEmpty()
                )
                val plannedJson = if (planned == null) "null" else planned.toString()
                draftJson = """{"nodeCode":"${escapeJson(draft.nodeCode)}","materialName":"${escapeJson(draft.materialName)}","actualQty":${draft.actualQty},"plannedQty":$plannedJson,"unit":"${escapeJson(draft.unit)}"}"""
                pendingAction = ChatPendingAction(
                    type = intent,
                    title = "Cập nhật khối lượng ${draft.materialName} node ${draft.nodeCode}",
                    draftJson = draftJson,
                    materialOrVolumeProgress = draft
                )
            }
            ChatActionType.ADD_DAILY_LOG -> {
                val workItem = params["workItem"]?.takeIf { it.isNotBlank() } ?: categoryName ?: "Nhật ký thi công"
                val manpower = params["manpower"]?.toIntOrNull() ?: 1
                val note = params["note"] ?: cleanAnswer
                val weather = params["weather"]?.takeIf { it.isNotBlank() } ?: ChatDictionaryResolver.resolveWeather(message)
                val temp = params["temperature"]?.toDoubleOrNull() ?: 0.0
                val vol = params["volume"]?.toDoubleOrNull() ?: params["actualQty"]?.toDoubleOrNull()
                val explicitDate = params["date"] ?: params["logDate"]
                val dateEpochDay = DailyLogDateResolver.resolveEpochDay(message = message, explicitDate = explicitDate)

                if ((nodeCode != null || routeCode != null) && workItem.isNotBlank()) {
                    isDataComplete = true
                } else {
                    if (nodeCode == null && routeCode == null) missingFields.add("nodeCode")
                    if (workItem.isBlank()) missingFields.add("workItem")
                }

                val draft = DailyLogDraft(
                    workItem = workItem,
                    manpower = manpower,
                    note = note,
                    weather = weather,
                    temperature = temp,
                    nodeCode = nodeCode,
                    routeCode = routeCode,
                    dateEpochDay = dateEpochDay,
                    volume = vol ?: 0.0,
                    unit = categoryUnit.orEmpty(),
                    categoryName = categoryName.orEmpty()
                )
                draftJson = buildDailyLogJson(draft)
                pendingAction = ChatPendingAction(
                    type = intent,
                    title = "Thêm nhật ký thi công ${draft.nodeCode ?: draft.routeCode ?: ""}",
                    draftJson = draftJson,
                    dailyLog = draft
                )
            }
            ChatActionType.UPDATE_SITE_PHOTO -> {
                val photoId = params["photoId"] ?: ""
                if (photoId.isNotBlank()) {
                    isDataComplete = true
                } else {
                    missingFields.add("photoId")
                }
                val draft = SitePhotoUpdateDraft(
                    photoId = photoId,
                    tagCodesCsv = params["tagCodesCsv"] ?: "",
                    matchedNodeCode = nodeCode,
                    latitude = params["latitude"]?.toDoubleOrNull(),
                    longitude = params["longitude"]?.toDoubleOrNull()
                )
                draftJson = buildSitePhotoJson(draft)
                pendingAction = ChatPendingAction(
                    type = intent,
                    title = "Cập nhật ảnh chụp ${draft.photoId}",
                    draftJson = draftJson,
                    sitePhotoUpdate = draft
                )
            }
            ChatActionType.SAVE_REPORT_DRAFT -> {
                val recActions = params["recommendedActions"]?.split("|")?.map { it.trim() } ?: emptyList()
                val projId = params["projectId"] ?: resolvedRefs.nodeCode ?: ""
                if (projId.isNotBlank()) {
                    isDataComplete = true
                } else {
                    missingFields.add("projectId")
                }
                val draft = ReportDraftDbSaveDraft(
                    projectId = projId,
                    title = params["title"] ?: "Báo cáo giám sát tự động",
                    executiveSummary = params["executiveSummary"] ?: cleanAnswer,
                    riskSection = params["riskSection"] ?: "",
                    recommendedActions = recActions
                )
                val actionsJsonArray = recActions.joinToString(prefix = "[", postfix = "]") { "\"${escapeJson(it)}\"" }
                draftJson = """{"projectId":"${escapeJson(draft.projectId)}","title":"${escapeJson(draft.title)}","executiveSummary":"${escapeJson(draft.executiveSummary)}","riskSection":"${escapeJson(draft.riskSection)}","recommendedActions":$actionsJsonArray}"""
                pendingAction = ChatPendingAction(
                    type = intent,
                    title = "Lưu nháp báo cáo dự án",
                    draftJson = draftJson,
                    reportDraftSave = draft
                )
            }
            ChatActionType.ADD_NOTE -> {
                val content = params["content"] ?: cleanAnswer
                if (nodeCode != null && content.isNotBlank()) {
                    isDataComplete = true
                } else {
                    if (nodeCode == null) missingFields.add("objectCode")
                    if (content.isBlank()) missingFields.add("content")
                }
                val draft = NoteDraft(objectCode = nodeCode.orEmpty(), content = content)
                draftJson = """{"objectCode":"${escapeJson(draft.objectCode)}","content":"${escapeJson(draft.content)}"}"""
                pendingAction = ChatPendingAction(
                    type = intent,
                    title = "Thêm ghi chú cho ${draft.objectCode}",
                    draftJson = draftJson,
                    noteDraft = draft
                )
            }
            ChatActionType.ADD_TASK -> {
                val title = params["title"] ?: cleanAnswer.ifBlank { "Nhiệm vụ mới" }
                if (nodeCode != null && title.isNotBlank()) {
                    isDataComplete = true
                } else {
                    if (nodeCode == null) missingFields.add("objectCode")
                    if (title.isBlank()) missingFields.add("title")
                }
                val desc = params["description"] ?: ""
                val status = params["status"] ?: "TODO"
                val draft = TaskDraft(objectCode = nodeCode.orEmpty(), title = title, description = desc, status = status)
                draftJson = buildTaskJson(draft)
                pendingAction = ChatPendingAction(
                    type = intent,
                    title = "Thêm nhiệm vụ cho ${draft.objectCode}",
                    draftJson = draftJson,
                    taskDraft = draft
                )
            }
            ChatActionType.GENERATE_SUMMARY -> {
                val projId = params["projectId"] ?: resolvedRefs.projectId ?: resolvedRefs.nodeCode ?: "P1"
                val scope = params["scope"] ?: "project"
                val filterValue = params["filterValue"]
                val dateFrom = params["dateFromEpochDay"]?.toLongOrNull()
                val dateTo = params["dateToEpochDay"]?.toLongOrNull()
                val groupBy = params["groupBy"]
                val cols = params["columns"]?.split("|")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

                isDataComplete = true
                val draft = SummaryRequestDraft(
                    projectId = projId,
                    scope = scope,
                    filterValue = filterValue,
                    dateFromEpochDay = dateFrom,
                    dateToEpochDay = dateTo,
                    groupBy = groupBy,
                    columns = cols
                )
                draftJson = buildSummaryRequestJson(draft)
                pendingAction = ChatPendingAction(
                    type = intent,
                    title = "Tổng hợp báo cáo dự án",
                    draftJson = draftJson,
                    summaryRequest = draft
                )
            }
            ChatActionType.ADD_WORK_PLAN -> {
                val explicitDate = params["date"] ?: params["logDate"]
                val dateEpochDay = DailyLogDateResolver.resolveEpochDay(message = message, explicitDate = explicitDate)
                val title = params["title"]?.takeIf { it.isNotBlank() } ?: "Kế hoạch thi công ${nodeCode ?: routeCode ?: ""}"
                val desc = params["description"] ?: message
                val draft = WorkPlanDraft(
                    plannedDateEpochDay = dateEpochDay,
                    title = title,
                    description = desc,
                    nodeCode = nodeCode,
                    routeCode = routeCode,
                    taskId = null
                )
                val taskTitle = "Xử lý kế hoạch: $title"
                val taskDraft = TaskDraft(
                    objectCode = nodeCode ?: routeCode ?: "",
                    title = taskTitle,
                    description = desc,
                    status = "TODO"
                )
                draftJson = """{"plannedDateEpochDay":$dateEpochDay,"title":"${escapeJson(title)}","description":"${escapeJson(desc)}","nodeCode":${if (nodeCode == null) "null" else "\"${escapeJson(nodeCode)}\""},"routeCode":${if (routeCode == null) "null" else "\"${escapeJson(routeCode)}\""}}"""
                isDataComplete = (nodeCode != null || routeCode != null) && title.isNotBlank()
                pendingAction = ChatPendingAction(
                    type = intent,
                    title = "Thêm kế hoạch thi công ${nodeCode ?: routeCode ?: ""}",
                    draftJson = draftJson,
                    workPlan = draft,
                    taskDraft = taskDraft
                )
            }
        }

        // Calculate overall confidence with completeness penalty based on intent requirements
        var overallConfidence = when (intent) {
            ChatActionType.SAVE_REPORT_DRAFT -> intentConfidence
            ChatActionType.GENERATE_SUMMARY -> intentConfidence
            ChatActionType.UPDATE_SITE_PHOTO -> (intentConfidence * 2 + locationConf) / 3
            ChatActionType.UPDATE_CONSTRUCTION_PROGRESS,
            ChatActionType.UPDATE_MATERIAL_OR_VOLUME_PROGRESS,
            ChatActionType.ADD_DAILY_LOG,
            ChatActionType.ADD_NOTE,
            ChatActionType.ADD_TASK,
            ChatActionType.ADD_WORK_PLAN -> {
                if (locationConf > 0) {
                    (intentConfidence + locationConf) / 2
                } else {
                    intentConfidence - 15
                }
            }
        }

        if (!isDataComplete) {
            val isCriticalMissing = when (intent) {
                ChatActionType.UPDATE_CONSTRUCTION_PROGRESS -> {
                    nodeCode == null || params["actual"]?.toFloatOrNull() == null
                }
                ChatActionType.UPDATE_MATERIAL_OR_VOLUME_PROGRESS -> {
                    val matName = categoryName?.takeIf { it.isNotBlank() } ?: params["materialName"] ?: ""
                    nodeCode == null || matName.isBlank()
                }
                ChatActionType.ADD_DAILY_LOG -> {
                    false
                }
                ChatActionType.ADD_NOTE -> {
                    nodeCode == null || (params["content"] ?: cleanAnswer).isBlank()
                }
                ChatActionType.ADD_TASK -> {
                    nodeCode == null || (params["title"] ?: cleanAnswer).isBlank()
                }
                ChatActionType.ADD_WORK_PLAN -> {
                    nodeCode == null && routeCode == null
                }
                else -> {
                    missingFields.isNotEmpty()
                }
            }
            val penalty = if (isCriticalMissing) 50 else 20
            overallConfidence = (overallConfidence - penalty).coerceAtLeast(0)
        }

        // Determine WriteDisposition
        val writeDisposition = when {
            intent == ChatActionType.GENERATE_SUMMARY -> WriteDisposition.AUTO_SAVE
            overallConfidence >= 85 && isDataComplete -> WriteDisposition.REQUIRE_CONFIRMATION
            overallConfidence >= 50 -> WriteDisposition.REQUIRE_CONFIRMATION
            else -> WriteDisposition.REJECT
        }

        val friendlyMissing = missingFields.map {
            when (it) {
                "nodeCode", "objectCode" -> "trạm/node"
                "planned", "plannedQty" -> "kế hoạch"
                "actual", "actualQty", "volume" -> "thực tế/khối lượng"
                "materialName", "workItem" -> "hạng mục"
                "content" -> "nội dung"
                "title" -> "tiêu đề"
                else -> it
            }
        }
        val friendlyMissingStr = friendlyMissing.joinToString(", ")

        val finalAnswer = when (writeDisposition) {
            WriteDisposition.REJECT -> {
                if (friendlyMissing.isNotEmpty()) {
                    "Yêu cầu chưa đủ thông tin chi tiết. Vui lòng cung cấp thêm: $friendlyMissingStr."
                } else {
                    "Mình chưa hiểu rõ yêu cầu. Bạn có thể nói rõ hơn được không?"
                }
            }
            WriteDisposition.REQUIRE_CONFIRMATION -> {
                if (cleanAnswer != "Mình đã chuẩn bị sẵn phiếu xử lý." && cleanAnswer != "Dưới đây là tổng hợp báo cáo." && cleanAnswer.isNotBlank()) {
                    cleanAnswer
                } else if (friendlyMissing.isNotEmpty()) {
                    "Mình đã tạo phiếu nháp nhưng còn thiếu thông tin: $friendlyMissingStr. Bạn vui lòng bổ sung trong phiếu xác nhận."
                } else {
                    "Mình đã chuẩn bị phiếu nháp. Bạn vui lòng xác nhận."
                }
            }
            else -> cleanAnswer.ifBlank { "Mình đã chuẩn bị sẵn phiếu xử lý." }
        }

        val finalPendingAction = if (writeDisposition == WriteDisposition.REJECT) null else pendingAction

        val resolvedEntities = buildMap {
            if (nodeCode != null && nodeCode.isNotBlank()) put("node", nodeCode)
            if (routeCode != null && routeCode.isNotBlank()) put("route", routeCode)
            if (categoryName != null && categoryName.isNotBlank()) put("category", categoryName)
            if (categoryUnit != null && categoryUnit.isNotBlank()) put("unit", categoryUnit)
        }

        return ChatAssistantResult(
            answer = finalAnswer,
            suggestedAction = intent.name,
            draftJson = draftJson,
            pendingAction = finalPendingAction,
            confidence = ChatConfidenceScore(
                intentConfidence = intentConfidence,
                locationConfidence = locationConf,
                categoryConfidence = categoryConf,
                overallConfidence = overallConfidence,
                isDataComplete = isDataComplete
            ),
            missingFields = missingFields,
            resolvedEntities = resolvedEntities,
            writeDisposition = writeDisposition
        )
    }

    private fun buildSummaryRequestJson(draft: SummaryRequestDraft): String {
        val filterVal = draft.filterValue?.let { "\"${escapeJson(it)}\"" } ?: "null"
        val groupByVal = draft.groupBy?.let { "\"${escapeJson(it)}\"" } ?: "null"
        val columnsJson = draft.columns.joinToString(prefix = "[", postfix = "]") { "\"${escapeJson(it)}\"" }
        return """{"projectId":"${escapeJson(draft.projectId)}","scope":"${escapeJson(draft.scope)}","filterValue":$filterVal,"dateFromEpochDay":${draft.dateFromEpochDay ?: "null"},"dateToEpochDay":${draft.dateToEpochDay ?: "null"},"groupBy":$groupByVal,"columns":$columnsJson}"""
    }


    private fun extractNodeCode(message: String, selectedNodeCode: String?): String? {
        val regex = Regex("""(?:node|tram|trạm|nut|nút|ho\s*ga|hố\s*ga)\s*([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
        return regex.find(message)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: selectedNodeCode
    }

    private fun extractObjectCode(message: String, selectedNodeCode: String?): String? {
        val regex = Regex("""(?:object|objectcode|node|tram|trạm|doi tuong|đối tượng|nut|nút|ho\s*ga|hố\s*ga)\s*([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
        return regex.find(message)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: selectedNodeCode
    }

    private fun extractNumber(message: String, keys: List<String>): Float? {
        val lower = normalizeText(message)
        keys.forEach { key ->
            val regex = Regex("""$key\s*[:=]?\s*([0-9]+(?:[.,][0-9]+)?)""", RegexOption.IGNORE_CASE)
            regex.find(lower)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toFloatOrNull()?.let { return it }
        }
        return null
    }

    private fun extractInt(message: String, keys: List<String>): Int? {
        val lower = normalizeText(message)
        keys.forEach { key ->
            val regex = Regex("""$key\s*[:=]?\s*([0-9]+)""", RegexOption.IGNORE_CASE)
            regex.find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun extractAfter(message: String, keys: List<String>): String {
        val lower = normalizeText(message)
        keys.forEach { key ->
            val index = lower.indexOf(key)
            if (index >= 0) {
                return message.substring(index + key.length).trim().trim(':', '=', '-', ' ')
            }
        }
        return ""
    }

    private fun normalizeText(value: String): String {
        return ChatDictionaryResolver.normalize(value)
    }

    private fun parseParams(paramsContent: String): Map<String, String> {
        val paramRegex = Regex("""(\w+)\s*=\s*(?:"([^"]*)"|'([^']*)'|(\S+))""")
        val params = mutableMapOf<String, String>()
        paramRegex.findAll(paramsContent).forEach { m ->
            val key = m.groupValues[1]
            val value = m.groupValues[2].takeIf { it.isNotEmpty() }
                ?: m.groupValues[3].takeIf { it.isNotEmpty() }
                ?: m.groupValues[4]
            params[key] = value
        }
        return params
    }

    private fun buildDailyLogJson(draft: DailyLogDraft): String {
        return buildString {
            append("{")
            append("\"workItem\":\"").append(escapeJson(draft.workItem)).append("\",")
            append("\"manpower\":").append(draft.manpower).append(",")
            append("\"note\":\"").append(escapeJson(draft.note)).append("\",")
            append("\"weather\":\"").append(escapeJson(draft.weather)).append("\",")
            append("\"temperature\":").append(draft.temperature).append(",")
            append("\"nodeCode\":")
            val nc = draft.nodeCode
            if (nc == null) append("null") else append("\"").append(escapeJson(nc)).append("\"")
            append(",")
            append("\"routeCode\":")
            val rc = draft.routeCode
            if (rc == null) append("null") else append("\"").append(escapeJson(rc)).append("\"")
            append(",")
            append("\"dateEpochDay\":").append(draft.dateEpochDay).append(",")
            append("\"volume\":").append(draft.volume).append(",")
            append("\"unit\":\"").append(escapeJson(draft.unit)).append("\",")
            append("\"categoryName\":\"").append(escapeJson(draft.categoryName)).append("\"")
            append("}")
        }
    }

    private fun buildSitePhotoJson(draft: SitePhotoUpdateDraft): String {
        val mnc = draft.matchedNodeCode
        return """{"photoId":"${escapeJson(draft.photoId)}","tagCodesCsv":"${escapeJson(draft.tagCodesCsv)}","matchedNodeCode":${if (mnc == null) "null" else "\"${escapeJson(mnc)}\""},"latitude":${draft.latitude ?: "null"},"longitude":${draft.longitude ?: "null"}}"""
    }

    private fun buildTaskJson(draft: TaskDraft): String {
        return """{"objectCode":"${escapeJson(draft.objectCode)}","title":"${escapeJson(draft.title)}","description":"${escapeJson(draft.description)}","status":"${draft.status}"}"""
    }

    private fun buildFallbackAnswer(message: String, contextSummary: String): String {
        return when {
            message.contains("bao cao", ignoreCase = true) || message.contains("báo cáo", ignoreCase = true) ->
                "Mình có thể hỗ trợ tóm tắt báo cáo từ dữ liệu hiện có."
            contextSummary.isNotBlank() ->
                "Mình đã nhận nội dung. Nếu bạn muốn cập nhật thi công, hãy nêu rõ node, kế hoạch và thực tế."
            else -> "Mình đã nhận nội dung. Bạn có thể hỏi về tiến độ, nhật ký hoặc báo cáo."
        }
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }
}
