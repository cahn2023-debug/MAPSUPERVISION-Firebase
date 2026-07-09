package com.mapsupervision.domain.model

fun parseCsvList(csv: String): List<String> =
    csv.split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

fun joinCsvList(values: List<String>): String =
    values.map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(",")
