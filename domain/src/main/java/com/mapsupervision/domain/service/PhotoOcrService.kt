package com.mapsupervision.domain.service

data class PhotoMaterialDataResult(
    val success: Boolean,
    val materialName: String?,
    val quantity: Double?,
    val unit: String?,
    val error: String?
)

data class PhotoDailyLogDataResult(
    val success: Boolean,
    val workItem: String?,
    val manpower: Int?,
    val note: String?,
    val error: String?
)

interface PhotoOcrService {
    suspend fun extractMaterialData(imageUri: String): PhotoMaterialDataResult
    suspend fun extractDailyLogData(imageUri: String): PhotoDailyLogDataResult
}
