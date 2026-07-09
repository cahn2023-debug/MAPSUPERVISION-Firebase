package com.mapsupervision.data.db

import androidx.room.TypeConverter
import com.mapsupervision.domain.model.NodeSignalStatus
import com.mapsupervision.domain.model.PhotoLocationStatus
import com.mapsupervision.domain.model.ProjectStorageMode

class DbTypeConverters {
    @TypeConverter
    fun fromProjectStorageMode(value: ProjectStorageMode?): String? = value?.name

    @TypeConverter
    fun toProjectStorageMode(value: String?): ProjectStorageMode =
        value?.let(ProjectStorageMode::valueOf) ?: ProjectStorageMode.LEGACY_SHARED

    @TypeConverter
    fun fromPhotoLocationStatus(value: PhotoLocationStatus?): String? = value?.name

    @TypeConverter
    fun toPhotoLocationStatus(value: String?): PhotoLocationStatus =
        value?.let(PhotoLocationStatus::valueOf) ?: PhotoLocationStatus.MISSING

    @TypeConverter
    fun fromMediaType(value: com.mapsupervision.domain.model.MediaType?): String? = value?.name

    @TypeConverter
    fun toMediaType(value: String?): com.mapsupervision.domain.model.MediaType =
        value?.let(com.mapsupervision.domain.model.MediaType::valueOf) ?: com.mapsupervision.domain.model.MediaType.IMAGE

    @TypeConverter
    fun fromNodeSignalStatus(value: NodeSignalStatus?): String? = value?.name

    @TypeConverter
    fun toNodeSignalStatus(value: String?): NodeSignalStatus =
        value?.let(NodeSignalStatus::valueOf) ?: NodeSignalStatus.UNKNOWN

    @TypeConverter
    fun fromCoordinatesList(value: List<Pair<Double, Double>>?): String? {
        if (value == null) return null
        return value.joinToString(";") { "${it.first},${it.second}" }
    }

    @TypeConverter
    fun toCoordinatesList(value: String?): List<Pair<Double, Double>> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(';').mapNotNull { segment ->
            val parts = segment.split(',')
            if (parts.size >= 2) {
                val lat = parts[0].toDoubleOrNull()
                val lon = parts[1].toDoubleOrNull()
                if (lat != null && lon != null) {
                    lat to lon
                } else null
            } else null
        }
    }
}
