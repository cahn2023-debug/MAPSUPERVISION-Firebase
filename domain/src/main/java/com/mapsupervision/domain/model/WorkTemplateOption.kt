package com.mapsupervision.domain.model

const val WORK_TEMPLATE_SOURCE_WORK_VOLUME = "Cong viec / khoi luong cong viec"
const val WORK_TEMPLATE_SOURCE_GENERAL = "Hang muc cong viec chung"

/**
 * Lua chon hang muc mau cho dropdown.
 */
data class WorkTemplateOption(
    val name: String,
    val unit: String,
    val source: String // WORK_TEMPLATE_SOURCE_WORK_VOLUME or WORK_TEMPLATE_SOURCE_GENERAL
)
