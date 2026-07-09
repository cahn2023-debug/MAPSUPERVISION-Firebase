package com.mapsupervision.domain.repository

interface ContractorColorPreferenceRepository {
    fun saveColor(projectId: String, contractor: String, hexColor: String)
    fun loadColors(projectId: String): Map<String, String>
}
