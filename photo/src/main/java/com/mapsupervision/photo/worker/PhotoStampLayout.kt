package com.mapsupervision.photo.worker

import com.mapsupervision.domain.model.CaptureStamp
import kotlin.math.max

internal data class PhotoStampRow(
    val icon: String,
    val lines: List<String>
)

internal data class PhotoStampContent(
    val rows: List<PhotoStampRow>,
    val latitude: Double?,
    val longitude: Double?,
    val coordinateText: String?
)

internal data class PhotoStampRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

internal data class PhotoStampRowLayout(
    val row: PhotoStampRow,
    val rect: PhotoStampRect
)

internal data class PhotoStampLayout(
    val scale: Float,
    val margin: Float,
    val pillHeight: Float,
    val pillGap: Float,
    val pillPaddingHorizontal: Float,
    val textSize: Float,
    val iconSize: Float,
    val lineGap: Float,
    val coordinateTextSize: Float,
    val mapCornerRadius: Float,
    val mapBorderWidth: Float,
    val mapDotOuterRadius: Float,
    val mapDotInnerRadius: Float,
    val mapDotCoreRadius: Float,
    val mapCoordinateOffsetY: Float,
    val rowLayouts: List<PhotoStampRowLayout>,
    val mapRect: PhotoStampRect?
)

internal object PhotoStampLayoutCalculator {
    const val timeIcon = "\u23F1"
    const val locationIcon = "\uD83D\uDCCD"
    const val noteIcon = "\uD83D\uDCDD"

    private const val addressMaxChars = 42

    fun formatTime(epochMs: Long): String =
        java.text.SimpleDateFormat("HH:mm  dd/MM/yyyy", java.util.Locale.US)
            .format(java.util.Date(epochMs))

    fun buildContent(
        timestampMs: Long,
        latitude: Double?,
        longitude: Double?,
        missingLocationText: String
    ): PhotoStampContent {
        return buildContent(timestampMs, latitude, longitude, missingLocationText, null, null)
    }

    fun buildContent(
        timestampMs: Long,
        latitude: Double?,
        longitude: Double?,
        missingLocationText: String,
        address: String?,
        note: String? = null
    ): PhotoStampContent {
        val resolvedLocationText = if (!address.isNullOrBlank()) address else {
            coordinateText(latitude, longitude) ?: missingLocationText
        }
        val rows = buildList {
            add(PhotoStampRow(timeIcon, listOf(formatTime(timestampMs))))
            add(PhotoStampRow(locationIcon, wrapText(resolvedLocationText, addressMaxChars)))
            if (!note.isNullOrBlank()) {
                add(PhotoStampRow(noteIcon, wrapText(note, addressMaxChars)))
            }
        }
        return PhotoStampContent(
            rows = rows,
            latitude = latitude,
            longitude = longitude,
            coordinateText = coordinateText(latitude, longitude)
        )
    }

    fun buildContent(
        stamp: CaptureStamp,
        missingLocationText: String
    ): PhotoStampContent {
        val displayLatitude = stamp.latitude ?: stamp.mapScene?.cameraLatitude ?: stamp.mapScene?.centerLatitude
        val displayLongitude = stamp.longitude ?: stamp.mapScene?.cameraLongitude ?: stamp.mapScene?.centerLongitude
        val rows = buildList {
            add(PhotoStampRow(timeIcon, listOf(stamp.formattedTime())))
            add(PhotoStampRow(locationIcon, wrapText(stamp.resolvedLocationText(missingLocationText = missingLocationText), addressMaxChars)))
            if (stamp.note.isNotBlank()) {
                add(PhotoStampRow(noteIcon, wrapText(stamp.note, addressMaxChars)))
            }
        }
        return PhotoStampContent(
            rows = rows,
            latitude = displayLatitude,
            longitude = displayLongitude,
            coordinateText = coordinateText(displayLatitude, displayLongitude)
        )
    }

    fun calculate(
        frameWidth: Float,
        frameHeight: Float,
        rows: List<PhotoStampRow>,
        textWidth: (String) -> Float,
        iconWidth: (String) -> Float,
        showMap: Boolean
    ): PhotoStampLayout {
        val scale = (frameWidth / 3000f) * 1.4f
        val textScale = 1.5f * 1.2f
        val mapScale = 2.0f * 3f
        val margin = 40f * scale
        val pillHeight = 68f * scale * 1.2f
        val pillGap = 12f * scale * 1.2f
        val pillPaddingHorizontal = 28f * scale * 1.2f
        val textSize = 30f * scale * textScale
        val iconSize = 32f * scale * textScale
        val lineGap = 6f * scale * 1.2f
        val coordinateTextSize = 19f * scale * textScale
        val mapBorderWidth = 3.5f * scale * mapScale
        val mapCoordinateOffsetY = 16f * scale * mapScale
        val mapDotOuterRadius = 26f * scale * 2.0f
        val mapDotInnerRadius = 16f * scale * 2.0f
        val mapDotCoreRadius = 7f * scale * 2.0f
        val bottomInset = 28f * scale

        fun rowHeight(row: PhotoStampRow): Float {
            val linesCount = max(1, row.lines.size)
            return pillHeight * linesCount + lineGap * (linesCount - 1)
        }

        val maxIconWidth = rows.maxOfOrNull { iconWidth(it.icon) } ?: 0f

        fun rowWidth(row: PhotoStampRow): Float {
            val maxLineWidth = row.lines.maxOfOrNull(textWidth) ?: 0f
            return maxLineWidth + maxIconWidth + pillPaddingHorizontal * 2 + 14f * scale
        }

        val totalRowsHeight = rows.sumOf { rowHeight(it).toDouble() }.toFloat() +
            pillGap * max(0, rows.size - 1)
        val pillsRight = frameWidth - margin
        val pillsBottom = frameHeight - margin - bottomInset
        var currentTop = pillsBottom - totalRowsHeight
        val rowLayouts = rows.map { row ->
            val rect = PhotoStampRect(
                left = pillsRight - rowWidth(row),
                top = currentTop,
                right = pillsRight,
                bottom = currentTop + rowHeight(row)
            )
            currentTop += rect.height + pillGap
            PhotoStampRowLayout(row, rect)
        }

        val mapRect = if (showMap) {
            val mapSize = ((minOf(frameWidth, frameHeight) / 6f) * 1.5f).coerceAtLeast(120f)
            PhotoStampRect(
                left = margin,
                top = frameHeight - margin - bottomInset - mapSize,
                right = margin + mapSize,
                bottom = frameHeight - margin - bottomInset
            )
        } else {
            null
        }
        val mapCornerRadius = (mapRect?.width ?: 0f) * 0.12f

        return PhotoStampLayout(
            scale = scale,
            margin = margin,
            pillHeight = pillHeight,
            pillGap = pillGap,
            pillPaddingHorizontal = pillPaddingHorizontal,
            textSize = textSize,
            iconSize = iconSize,
            lineGap = lineGap,
            coordinateTextSize = coordinateTextSize,
            mapCornerRadius = mapCornerRadius,
            mapBorderWidth = mapBorderWidth,
            mapDotOuterRadius = mapDotOuterRadius,
            mapDotInnerRadius = mapDotInnerRadius,
            mapDotCoreRadius = mapDotCoreRadius,
            mapCoordinateOffsetY = mapCoordinateOffsetY,
            rowLayouts = rowLayouts,
            mapRect = mapRect
        )
    }

    internal fun wrapText(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val breakAt = text.lastIndexOf(',', maxChars).takeIf { it > maxChars / 2 }
            ?: text.lastIndexOf(' ', maxChars).takeIf { it > maxChars / 2 }
            ?: maxChars
        val line1 = text.substring(0, breakAt).trim()
        val line2 = text.substring(breakAt).trimStart(',', ' ')
        return listOf(line1, line2)
    }

    private fun coordinateText(latitude: Double?, longitude: Double?): String? {
        return if (latitude != null && longitude != null) {
            "Vĩ độ: ${"%.4f".format(java.util.Locale.US, latitude)}\nKinh độ: ${"%.4f".format(java.util.Locale.US, longitude)}"
        } else {
            null
        }
    }
}

