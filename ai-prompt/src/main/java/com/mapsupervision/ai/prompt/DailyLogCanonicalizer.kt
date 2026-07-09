package com.mapsupervision.ai.prompt

import com.mapsupervision.ai.core.*

object DailyLogCanonicalizer {
    fun canonicalize(
        params: Map<String, String>,
        message: String,
        normalizationContext: String,
        selectedNodeCode: String?,
        selectedRouteCode: String? = null
    ): DailyLogDraft {
        val refs = NormalizationRefsParser.parse(normalizationContext)
        
        // Use ChatDictionaryResolver for fuzzy node resolution
        val resolvedNode = ChatDictionaryResolver.resolveNode(
            message = message,
            selectedNodeCode = params["nodeCode"] ?: selectedNodeCode,
            refs = refs
        ).first

        val resolvedRoute = ChatDictionaryResolver.resolveRoute(
            message = message,
            selectedRouteCode = params["routeCode"] ?: selectedRouteCode,
            refs = refs
        ).first
        
        val workItem = params["workItem"]?.takeIf { it.isNotBlank() } ?: "Nhật ký thi công"
        val manpower = params["manpower"]?.toIntOrNull() ?: 1
        val note = params["note"] ?: message.trim()
        
        // Fuzzy match category
        val (categoryName, unit, _) = ChatDictionaryResolver.resolveCategory(
            message = message,
            rawCategoryName = params["categoryName"],
            refs = refs
        )
        
        val weather = params["weather"]?.takeIf { it.isNotBlank() } ?: ChatDictionaryResolver.resolveWeather(message)
        val temperature = params["temperature"]?.toDoubleOrNull() ?: 0.0
        val volume = params["volume"]?.toDoubleOrNull() ?: 0.0
        val dateEpochDay = DailyLogDateResolver.resolveEpochDay(
            message = message,
            explicitDate = params["date"] ?: params["logDate"]
        )

        return DailyLogDraft(
            workItem = workItem,
            manpower = manpower,
            note = note,
            weather = weather,
            temperature = temperature,
            nodeCode = resolvedNode,
            routeCode = resolvedRoute,
            dateEpochDay = dateEpochDay,
            volume = volume,
            unit = unit.orEmpty(),
            categoryName = categoryName.orEmpty()
        )
    }
}
