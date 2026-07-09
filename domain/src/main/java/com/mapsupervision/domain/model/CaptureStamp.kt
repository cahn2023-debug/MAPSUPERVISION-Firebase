package com.mapsupervision.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CaptureStamp(
    val timestampMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val address: String,
    val note: String,
    val bearingDeg: Float,
    val objectContext: CaptureStampObjectContext? = null,
    val mapScene: CaptureStampMapScene? = null
) {
    fun formattedTime(locale: Locale = Locale.US): String =
        SimpleDateFormat("HH:mm  dd/MM/yyyy", locale).format(Date(timestampMs))

    fun resolvedLocationText(
        locale: Locale = Locale.US,
        missingLocationText: String
    ): String {
        if (address.isNotBlank()) return address
        if (latitude != null && longitude != null) {
            return "${"%.5f".format(locale, latitude)}, ${"%.5f".format(locale, longitude)}"
        }
        return missingLocationText
    }

    fun coordinateText(locale: Locale = Locale.US): String? {
        if (latitude == null || longitude == null) return null
        return "${"%.4f".format(locale, latitude)}, ${"%.4f".format(locale, longitude)}"
    }
}
