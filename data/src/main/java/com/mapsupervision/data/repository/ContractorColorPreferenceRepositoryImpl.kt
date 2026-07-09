package com.mapsupervision.data.repository

import android.content.Context
import com.mapsupervision.domain.repository.ContractorColorPreferenceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ContractorColorPreferenceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ContractorColorPreferenceRepository {

    private val colorPrefs by lazy {
        context.getSharedPreferences("contractor_colors", Context.MODE_PRIVATE)
    }

    override fun saveColor(projectId: String, contractor: String, hexColor: String) {
        colorPrefs.edit().putString("${projectId}_$contractor", hexColor).apply()
    }

    override fun loadColors(projectId: String): Map<String, String> {
        val all = colorPrefs.all as? Map<*, *> ?: return emptyMap()
        val prefix = "${projectId}_"
        return all.entries.mapNotNull { entry ->
            val key = entry.key as? String ?: return@mapNotNull null
            val value = entry.value as? String ?: return@mapNotNull null
            if (key.startsWith(prefix)) {
                key.substring(prefix.length) to value
            } else {
                null
            }
        }.toMap()
    }
}
