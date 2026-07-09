package com.mapsupervision.domain.model

data class PhotoLocationSnapshot(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyM: Float? = null,
    val isMock: Boolean = false,
    val status: PhotoLocationStatus = PhotoLocationStatus.MISSING
)

enum class PhotoLocationStatus {
    OK,
    MISSING,
    INACCURATE
}
