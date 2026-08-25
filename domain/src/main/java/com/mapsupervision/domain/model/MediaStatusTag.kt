package com.mapsupervision.domain.model

import java.util.Locale

data class MediaStatusTag(
    val id: String,
    val projectId: String,
    val name: String,
    val createdAtEpochMs: Long
)

object MediaStatusTags {
    val systemNames = listOf("Hiện trạng", "Thi công", "Hoàn trả", "Vướng mắc")

    fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT)

    fun isSystem(name: String): Boolean = systemNames.any { normalize(it) == normalize(name) }
}
