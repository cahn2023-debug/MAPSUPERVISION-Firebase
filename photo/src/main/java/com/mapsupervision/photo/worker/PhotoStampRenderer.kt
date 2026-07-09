package com.mapsupervision.photo.worker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface
import com.mapsupervision.domain.model.CaptureStamp
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.tan
import android.content.Context
import com.mapsupervision.domain.model.CameraAspectRatio

data class AspectCropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

fun calculateAspectCropRect(
    width: Int,
    height: Int,
    ratio: CameraAspectRatio
): AspectCropRect {
    if (ratio == CameraAspectRatio.RATIO_FULL) {
        return AspectCropRect(0, 0, width, height)
    }

    val isLandscape = width > height
    val baseRatio = when (ratio) {
        CameraAspectRatio.RATIO_4_3 -> 4f / 3f
        CameraAspectRatio.RATIO_16_9 -> 16f / 9f
        CameraAspectRatio.RATIO_1_1 -> 1.0f
        CameraAspectRatio.RATIO_FULL -> 1.0f
    }
    val targetRatio = if (isLandscape) baseRatio else (1f / baseRatio)
    val currentRatio = width.toFloat() / height.toFloat()

    return when {
        currentRatio > targetRatio -> {
            val cropWidth = (height * targetRatio).toInt().coerceAtMost(width)
            val left = ((width - cropWidth) / 2).coerceAtLeast(0)
            AspectCropRect(left, 0, cropWidth, height)
        }
        currentRatio < targetRatio -> {
            val cropHeight = (width / targetRatio).toInt().coerceAtMost(height)
            val top = ((height - cropHeight) / 2).coerceAtLeast(0)
            AspectCropRect(0, top, width, cropHeight)
        }
        else -> AspectCropRect(0, 0, width, height)
    }
}

object PhotoStampRenderer {
    const val MINIMAP_MIN_ZOOM = 18
    const val MINIMAP_MAX_ZOOM = 18
    internal const val MINIMAP_TILE_ALPHA = 204

    fun cropBitmapToAspectRatio(bitmap: Bitmap, ratio: CameraAspectRatio): Bitmap {
        val crop = calculateAspectCropRect(bitmap.width, bitmap.height, ratio)
        if (crop.width == bitmap.width && crop.height == bitmap.height && crop.left == 0 && crop.top == 0) {
            return bitmap
        }
        val cropped = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width, crop.height)
        if (cropped != bitmap) bitmap.recycle()
        return cropped
    }

    fun loadMutableNormalizedBitmap(file: File): Bitmap? {
        val src = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().applyOrientationTransform(orientation)
        val normalized = if (matrix.isIdentity) {
            src
        } else {
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true).also {
                if (it != src) src.recycle()
            }
        }
        return normalized.copy(Bitmap.Config.ARGB_8888, true)?.also {
            if (it != normalized) normalized.recycle()
        }
    }

    fun loadMutableNormalizedBitmap(context: Context, file: File): Bitmap? {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryClassMb = am.memoryClass
        val maxPixels = (memoryClassMb * 1024 * 1024 / 40)
        
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val imageWidth = options.outWidth
        val imageHeight = options.outHeight
        val totalPixels = imageWidth * imageHeight
        
        val decodeOptions = BitmapFactory.Options().apply {
            if (totalPixels > maxPixels) {
                var sample = 1
                while ((imageWidth / sample) * (imageHeight / sample) > maxPixels) {
                    sample *= 2
                }
                inSampleSize = sample
            }
        }
        
        val src = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().applyOrientationTransform(orientation)
        val normalized = if (matrix.isIdentity) {
            src
        } else {
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true).also {
                if (it != src) src.recycle()
            }
        }
        return normalized.copy(Bitmap.Config.ARGB_8888, true)?.also {
            if (it != normalized) normalized.recycle()
        }
    }

    fun writeBitmap(file: File, bitmap: Bitmap, quality: Int) {
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
        }
        bitmap.recycle()
        runCatching {
            ExifInterface(file.absolutePath).apply {
                setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString()
                )
                saveAttributes()
            }
        }
    }

    private fun resolveStampViewport(stamp: CaptureStamp): MinimapViewport? {
        val latitude = stamp.latitude ?: stamp.mapScene?.cameraLatitude ?: stamp.mapScene?.centerLatitude ?: return null
        val longitude = stamp.longitude ?: stamp.mapScene?.cameraLongitude ?: stamp.mapScene?.centerLongitude ?: return null
        return resolveMinimapViewport(
            rect = RectF(0f, 0f, 512f, 512f),
            latitude = latitude,
            longitude = longitude,
            bearingDeg = stamp.bearingDeg,
            borderWidth = 0f,
            outerDotRadius = 0f,
            mapScene = stamp.mapScene
        )
    }

    fun applyStamp(
        file: File,
        stamp: CaptureStamp,
        ratio: CameraAspectRatio,
        tileBitmap: Bitmap? = null
    ) {
        var mutable = loadMutableNormalizedBitmap(file) ?: return
        mutable = cropBitmapToAspectRatio(mutable, ratio)
        val viewport = resolveStampViewport(stamp)
        val resolvedTile = resolveStampTileBitmap(stamp, tileBitmap, viewport)
        drawStamp(
            canvas = Canvas(mutable),
            frameWidth = mutable.width.toFloat(),
            frameHeight = mutable.height.toFloat(),
            stamp = stamp,
            tileBitmap = resolvedTile,
            missingLocationText = "Khong co vi tri"
        )
        if (resolvedTile != null && resolvedTile !== tileBitmap) {
            resolvedTile?.recycle()
        }
        writeBitmap(file, mutable, 92)
    }

    fun applyStamp(
        context: Context,
        file: File,
        stamp: CaptureStamp,
        ratio: CameraAspectRatio,
        tileBitmap: Bitmap? = null
    ) {
        var mutable = loadMutableNormalizedBitmap(context, file) ?: return
        mutable = cropBitmapToAspectRatio(mutable, ratio)
        val viewport = resolveStampViewport(stamp)
        val resolvedTile = resolveStampTileBitmap(stamp, tileBitmap, viewport)
        drawStamp(
            canvas = Canvas(mutable),
            frameWidth = mutable.width.toFloat(),
            frameHeight = mutable.height.toFloat(),
            stamp = stamp,
            tileBitmap = resolvedTile,
            missingLocationText = "Khong co vi tri"
        )
        if (resolvedTile != null && resolvedTile !== tileBitmap) {
            resolvedTile?.recycle()
        }
        writeBitmap(file, mutable, 92)
    }

    fun createStampOverlayBitmap(
        frameWidthPx: Int,
        frameHeightPx: Int,
        stamp: CaptureStamp,
        tileBitmap: Bitmap? = null
    ): Bitmap {
        val overlay = Bitmap.createBitmap(frameWidthPx, frameHeightPx, Bitmap.Config.ARGB_8888)
        val viewport = resolveStampViewport(stamp)
        val resolvedTile = resolveStampTileBitmap(stamp, tileBitmap, viewport)
        drawStamp(
            canvas = Canvas(overlay),
            frameWidth = frameWidthPx.toFloat(),
            frameHeight = frameHeightPx.toFloat(),
            stamp = stamp,
            tileBitmap = resolvedTile,
            missingLocationText = "Khong co vi tri"
        )
        if (resolvedTile != null && resolvedTile !== tileBitmap) {
            resolvedTile?.recycle()
        }
        return overlay
    }

    fun resolveVideoOverlaySize(file: File): Pair<Int, Int> {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?: 1280
            val height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?: 720
            val rotation = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
            if (rotation == 90 || rotation == 270) {
                height to width
            } else {
                width to height
            }
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun drawStamp(
        canvas: Canvas,
        frameWidth: Float,
        frameHeight: Float,
        stamp: CaptureStamp,
        tileBitmap: Bitmap? = null,
        missingLocationText: String = "Khong co vi tri"
    ) {
        drawStamp(
            canvas = canvas,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            content = PhotoStampLayoutCalculator.buildContent(
                stamp = stamp,
                missingLocationText = missingLocationText
            ),
            bearingDeg = stamp.bearingDeg,
            tileBitmap = tileBitmap,
            mapScene = stamp.mapScene
        )
    }

    fun drawStamp(
        canvas: Canvas,
        frameWidth: Float,
        frameHeight: Float,
        timestampMs: Long,
        address: String,
        latitude: Double?,
        longitude: Double?,
        note: String,
        bearingDeg: Float,
        tileBitmap: Bitmap? = null,
        missingLocationText: String = "Khong co vi tri"
    ) {
        val stamp = CaptureStamp(
            timestampMs = timestampMs,
            latitude = latitude,
            longitude = longitude,
            address = address,
            note = note,
            bearingDeg = bearingDeg
        )
        drawStamp(
            canvas = canvas,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            stamp = stamp,
            tileBitmap = tileBitmap,
            missingLocationText = missingLocationText
        )
    }

    internal fun drawStamp(
        canvas: Canvas,
        frameWidth: Float,
        frameHeight: Float,
        content: PhotoStampContent,
        bearingDeg: Float,
        tileBitmap: Bitmap? = null,
        mapScene: com.mapsupervision.domain.model.CaptureStampMapScene? = null
    ) {
        val layout = PhotoStampLayoutCalculator.calculate(
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            rows = content.rows,
            textWidth = { 0f },
            iconWidth = { 0f },
            showMap = content.coordinateText != null
        )
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = layout.textSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = layout.iconSize
        }
        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 25, 110, 190)
        }
        val contentLayout = PhotoStampLayoutCalculator.calculate(
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            rows = content.rows,
            textWidth = { textPaint.measureText(it) },
            iconWidth = { iconPaint.measureText(it) },
            showMap = content.coordinateText != null
        )

        val maxIconWidth = content.rows.maxOfOrNull { iconPaint.measureText(it.icon) } ?: 0f

        val fontMetrics = textPaint.fontMetrics
        val iconMetrics = iconPaint.fontMetrics

        contentLayout.rowLayouts.forEach { rowLayout ->
            val rect = rowLayout.rect.toRectF()
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, pillBgPaint)

            rowLayout.row.lines.forEachIndexed { index, line -> 
                val lineTop = rect.top + index * (contentLayout.pillHeight + contentLayout.lineGap)
                val baselineY = lineTop + (contentLayout.pillHeight - fontMetrics.bottom - fontMetrics.top) / 2f
                canvas.drawText(line, rect.left + contentLayout.pillPaddingHorizontal, baselineY, textPaint)
            }

            val iconX = rect.right - contentLayout.pillPaddingHorizontal - maxIconWidth
            val iconY = rect.top + (rect.height() - iconMetrics.bottom - iconMetrics.top) / 2f
            canvas.drawText(rowLayout.row.icon, iconX, iconY, iconPaint)
        }

        val mapRect = contentLayout.mapRect
        if (mapRect != null && content.latitude != null && content.longitude != null) {
            drawMinimap(
                canvas = canvas,
                rect = mapRect.toRectF(),
                cornerR = contentLayout.mapCornerRadius,
                lat = content.latitude,
                lng = content.longitude,
                bearingDeg = bearingDeg,
                scale = contentLayout.scale,
                tileBitmap = tileBitmap,
                borderWidth = contentLayout.mapBorderWidth,
                coordinateText = content.coordinateText,
                coordinateTextSize = contentLayout.coordinateTextSize,
                coordinateOffsetY = contentLayout.mapCoordinateOffsetY,
                outerDotRadius = contentLayout.mapDotOuterRadius,
                innerDotRadius = contentLayout.mapDotInnerRadius,
                coreDotRadius = contentLayout.mapDotCoreRadius,
                mapScene = mapScene
            )
        }
    }

    private fun Matrix.applyOrientationTransform(orientation: Int): Matrix = apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(-90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
        }
    }

    fun fetchOsmTile(lat: Double, lng: Double, zoom: Int): Bitmap? {
        return try {
            val n = 1 shl zoom
            val x = (lng + 180.0) / 360.0 * n
            val latRad = Math.toRadians(lat)
            val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n

            val xTile = x.toInt()
            val yTile = y.toInt()
            val xFrac = x - xTile
            val yFrac = y - yTile

            val xLeft = if (xFrac < 0.5) (xTile - 1).coerceAtLeast(0) else xTile
            val xRight = (xLeft + 1).coerceAtMost(n - 1)
            val yTop = if (yFrac < 0.5) (yTile - 1).coerceAtLeast(0) else yTile
            val yBottom = (yTop + 1).coerceAtMost(n - 1)

            val tileTL = fetchOsmTileRaw(xLeft, yTop, zoom)
            val tileTR = fetchOsmTileRaw(xRight, yTop, zoom)
            val tileBL = fetchOsmTileRaw(xLeft, yBottom, zoom)
            val tileBR = fetchOsmTileRaw(xRight, yBottom, zoom)

            val composite = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(composite)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            tileTL?.let { canvas.drawBitmap(it, 0f, 0f, paint); it.recycle() }
            tileTR?.let { canvas.drawBitmap(it, 256f, 0f, paint); it.recycle() }
            tileBL?.let { canvas.drawBitmap(it, 0f, 256f, paint); it.recycle() }
            tileBR?.let { canvas.drawBitmap(it, 256f, 256f, paint); it.recycle() }

            composite
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchOsmTileRaw(xTile: Int, yTile: Int, zoom: Int): Bitmap? {
        return try {
            val url = URL("https://tile.openstreetmap.org/$zoom/$xTile/$yTile.png")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MapSupervision/1.0 (Android)")
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (conn.responseCode == 200) BitmapFactory.decodeStream(conn.inputStream) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveStampTileBitmap(
        stamp: CaptureStamp,
        tileBitmap: Bitmap?,
        viewport: MinimapViewport?
    ): Bitmap? {
        if (viewport == null) return null
        val hasScopedMap = !stamp.mapScene?.nodes.isNullOrEmpty() || !stamp.mapScene?.routes.isNullOrEmpty()
        if (tileBitmap != null && !hasScopedMap && viewport.zoom == MINIMAP_MAX_ZOOM) {
            return tileBitmap
        }
        return fetchOsmTile(viewport.centerLat, viewport.centerLng, zoom = viewport.zoom)
    }

    internal data class MinimapTileFrame(
        val xLeftTile: Int,
        val yTopTile: Int,
        val centerSourceX: Float,
        val centerSourceY: Float
    )

    internal data class MinimapViewport(
        val centerLat: Double,
        val centerLng: Double,
        val zoom: Int,
        val frame: MinimapTileFrame
    )

    private fun worldPixelPosition(lat: Double, lng: Double, zoom: Int): Pair<Float, Float> {
        val n = 1 shl zoom
        val x = (lng + 180.0) / 360.0 * n * 256.0
        val latRad = Math.toRadians(lat)
        val y = (1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / Math.PI) / 2.0 * n * 256.0
        return Pair(x.toFloat(), y.toFloat())
    }

    private fun buildMinimapTileFrame(
        centerLat: Double,
        centerLng: Double,
        zoom: Int
    ): MinimapTileFrame {
        val n = 1 shl zoom
        val x = (centerLng + 180.0) / 360.0 * n
        val latRad = Math.toRadians(centerLat)
        val y = (1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / Math.PI) / 2.0 * n

        val xTile = x.toInt()
        val yTile = y.toInt()
        val xFrac = x - xTile
        val yFrac = y - yTile

        val xLeft = if (xFrac < 0.5) (xTile - 1).coerceAtLeast(0) else xTile
        val yTop = if (yFrac < 0.5) (yTile - 1).coerceAtLeast(0) else yTile

        val centerSourceX = ((x - xLeft) * 256.0).toFloat()
        val centerSourceY = ((y - yTop) * 256.0).toFloat()
        return MinimapTileFrame(
            xLeftTile = xLeft,
            yTopTile = yTop,
            centerSourceX = centerSourceX,
            centerSourceY = centerSourceY
        )
    }

    internal fun getCanvasCoords(
        nodeLat: Double,
        nodeLng: Double,
        frame: MinimapTileFrame,
        rect: RectF,
        tileBitmapWidth: Int,
        zoom: Int
    ): Pair<Float, Float> {
        val (nodeWorldX, nodeWorldY) = worldPixelPosition(nodeLat, nodeLng, zoom)
        val sourceX = nodeWorldX - frame.xLeftTile * 256f
        val sourceY = nodeWorldY - frame.yTopTile * 256f
        val scale = rect.width() / tileBitmapWidth.toFloat()
        val canvasX = rect.centerX() + (sourceX - frame.centerSourceX) * scale
        val canvasY = rect.centerY() + (sourceY - frame.centerSourceY) * scale
        return Pair(canvasX, canvasY)
    }

    private fun minimapFitPaddingPx(
        rect: RectF,
        borderWidth: Float,
        outerDotRadius: Float
    ): Float {
        val coneRadius = rect.width() * 0.42f * 0.8f
        val visualPadding = maxOf(rect.width() * 0.08f, borderWidth * 2f, outerDotRadius * 1.5f)
        return maxOf(visualPadding, coneRadius + borderWidth)
    }

    private fun offsetCoordinate(
        latitude: Double,
        longitude: Double,
        bearingDeg: Float,
        distanceMeters: Double
    ): Pair<Double, Double> {
        val earthRadiusM = 6_378_137.0
        val bearingRad = Math.toRadians(bearingDeg.toDouble())
        val latRad = Math.toRadians(latitude)
        val lngRad = Math.toRadians(longitude)
        val angularDistance = distanceMeters / earthRadiusM
        val sinLat = sin(latRad)
        val cosLat = cos(latRad)
        val sinAd = sin(angularDistance)
        val cosAd = cos(angularDistance)
        val destLat = kotlin.math.asin(sinLat * cosAd + cosLat * sinAd * cos(bearingRad))
        val destLng = lngRad + kotlin.math.atan2(
            sin(bearingRad) * sinAd * cosLat,
            cosAd - sinLat * sin(destLat)
        )
        return Math.toDegrees(destLat) to Math.toDegrees(destLng)
    }

    internal fun resolveMinimapViewport(
        rect: RectF,
        latitude: Double,
        longitude: Double,
        bearingDeg: Float,
        borderWidth: Float,
        outerDotRadius: Float,
        mapScene: com.mapsupervision.domain.model.CaptureStampMapScene?
    ): MinimapViewport {
        val cameraLat = mapScene?.cameraLatitude ?: latitude
        val cameraLng = mapScene?.cameraLongitude ?: longitude
        val scopedNodes = mapScene?.nodes.orEmpty()
        val scopedRoutes = mapScene?.routes.orEmpty()
        val hasScopedMap = scopedNodes.isNotEmpty() || scopedRoutes.isNotEmpty()
        if (!hasScopedMap) {
            return MinimapViewport(
                centerLat = cameraLat,
                centerLng = cameraLng,
                zoom = MINIMAP_MAX_ZOOM,
                frame = buildMinimapTileFrame(cameraLat, cameraLng, MINIMAP_MAX_ZOOM)
            )
        }

        val fitPaddingPx = minimapFitPaddingPx(rect, borderWidth, outerDotRadius)
        val availableWidthPx = (rect.width() - fitPaddingPx * 2f).coerceAtLeast(24f)
        val availableHeightPx = (rect.height() - fitPaddingPx * 2f).coerceAtLeast(24f)
        val coneMeters = 35.0
        val mapPoints = mutableListOf<Pair<Double, Double>>()
        mapPoints += cameraLat to cameraLng
        mapPoints += offsetCoordinate(cameraLat, cameraLng, bearingDeg - 22.5f, coneMeters)
        mapPoints += offsetCoordinate(cameraLat, cameraLng, bearingDeg + 22.5f, coneMeters)
        scopedNodes.forEach { mapPoints += it.latitude to it.longitude }
        scopedRoutes.forEach { route -> mapPoints += route.points }

        val latitudes = mapPoints.map { it.first }
        val longitudes = mapPoints.map { it.second }
        for (zoom in MINIMAP_MAX_ZOOM downTo MINIMAP_MIN_ZOOM) {
            val worldPoints = mapPoints.map { worldPixelPosition(it.first, it.second, zoom) }
            val cameraWorld = worldPixelPosition(cameraLat, cameraLng, zoom)
            val maxDeltaX = worldPoints.maxOf { kotlin.math.abs(it.first - cameraWorld.first) }
            val maxDeltaY = worldPoints.maxOf { kotlin.math.abs(it.second - cameraWorld.second) }
            if (maxDeltaX * 2f <= availableWidthPx && maxDeltaY * 2f <= availableHeightPx) {
                return MinimapViewport(
                    centerLat = cameraLat,
                    centerLng = cameraLng,
                    zoom = zoom,
                    frame = buildMinimapTileFrame(cameraLat, cameraLng, zoom)
                )
            }
        }

        return MinimapViewport(
            centerLat = cameraLat,
            centerLng = cameraLng,
            zoom = MINIMAP_MIN_ZOOM,
            frame = buildMinimapTileFrame(cameraLat, cameraLng, MINIMAP_MIN_ZOOM)
        )
    }

    private fun drawMinimap(
        canvas: Canvas,
        rect: RectF,
        cornerR: Float,
        lat: Double,
        lng: Double,
        bearingDeg: Float,
        scale: Float,
        tileBitmap: Bitmap? = null,
        borderWidth: Float,
        coordinateText: String?,
        coordinateTextSize: Float,
        coordinateOffsetY: Float,
        outerDotRadius: Float,
        innerDotRadius: Float,
        coreDotRadius: Float,
        mapScene: com.mapsupervision.domain.model.CaptureStampMapScene? = null
    ) {
        val clipPath = Path().apply {
            addRoundRect(rect, cornerR, cornerR, Path.Direction.CW)
        }
        val hasScopedMap = !mapScene?.nodes.isNullOrEmpty() || !mapScene?.routes.isNullOrEmpty()
        canvas.save()
        canvas.clipPath(clipPath)

        val viewport = resolveMinimapViewport(
            rect = rect,
            latitude = lat,
            longitude = lng,
            bearingDeg = bearingDeg,
            borderWidth = borderWidth,
            outerDotRadius = outerDotRadius,
            mapScene = mapScene
        )
        val frame = viewport.frame

        if (tileBitmap != null) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(248, 242, 239, 233) }
            canvas.drawRect(rect, bgPaint)
            canvas.drawBitmap(
                tileBitmap,
                null,
                rect,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = MINIMAP_TILE_ALPHA }
            )
        } else {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(248, 242, 239, 233) }
            canvas.drawRect(rect, bgPaint)
            if (!hasScopedMap) {
                val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 160, 210, 230) }
                val waterPath = Path().apply {
                    moveTo(rect.left, rect.top + rect.height() * 0.58f)
                    cubicTo(
                        rect.left + rect.width() * 0.25f, rect.top + rect.height() * 0.50f,
                        rect.left + rect.width() * 0.60f, rect.top + rect.height() * 0.65f,
                        rect.right, rect.top + rect.height() * 0.55f
                    )
                    lineTo(rect.right, rect.bottom)
                    lineTo(rect.left, rect.bottom)
                    close()
                }
                canvas.drawPath(waterPath, waterPaint)

                val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 195, 230, 170) }
                canvas.drawRoundRect(
                    RectF(
                        rect.left + rect.width() * 0.06f,
                        rect.top + rect.height() * 0.06f,
                        rect.left + rect.width() * 0.38f,
                        rect.top + rect.height() * 0.40f
                    ),
                    18f * scale,
                    18f * scale,
                    greenPaint
                )
            }
        }

        // Draw GIS routes
        mapScene?.routes?.forEach { route ->
            if (route.points.size > 1) {
                val path = Path()
                var first = true
                route.points.forEach { pt ->
                    val (rx, ry) = getCanvasCoords(pt.first, pt.second, frame, rect, tileBitmap?.width ?: 512, viewport.zoom)
                    if (first) {
                        path.moveTo(rx, ry)
                        first = false
                    } else {
                        path.lineTo(rx, ry)
                    }
                }
                val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (route.highlighted) Color.argb(220, 255, 179, 0) else Color.argb(180, 26, 115, 232)
                    style = Paint.Style.STROKE
                    strokeWidth = if (route.highlighted) 5f * scale else 3f * scale
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                canvas.drawPath(path, routePaint)
            }
        }

        // Draw GIS nodes
        mapScene?.nodes?.forEach { node ->
            val (cxNode, cyNode) = getCanvasCoords(node.latitude, node.longitude, frame, rect, tileBitmap?.width ?: 512, viewport.zoom)
            val nodeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * scale
            }
            if (!node.label.isNullOrBlank()) {
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 8f * scale
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                }
                val radius = 9f * scale
                val nodeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    val hex = node.colorHex
                    color = if (!hex.isNullOrBlank()) {
                        try { Color.parseColor(hex) } catch (_: Exception) { Color.parseColor("#f97316") }
                    } else {
                        if (node.highlighted) Color.parseColor("#ffb300") else Color.parseColor("#f97316")
                    }
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(cxNode, cyNode, radius, nodeLabelPaint)
                canvas.drawCircle(cxNode, cyNode, radius, nodeStroke)

                val textY = cyNode - (labelPaint.descent() + labelPaint.ascent()) / 2f
                canvas.drawText(node.label, cxNode, textY, labelPaint)
            } else {
                val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    val hex = node.colorHex
                    color = if (!hex.isNullOrBlank()) {
                        try { Color.parseColor(hex) } catch (_: Exception) { Color.parseColor("#f97316") }
                    } else {
                        if (node.highlighted) Color.parseColor("#ffb300") else Color.parseColor("#f97316")
                    }
                    style = Paint.Style.FILL
                }
                val radius = if (node.highlighted) 6f * scale else 4f * scale
                canvas.drawCircle(cxNode, cyNode, radius, nodePaint)
                canvas.drawCircle(cxNode, cyNode, radius, nodeStroke)
            }
        }

        val cameraLat = mapScene?.cameraLatitude ?: lat
        val cameraLng = mapScene?.cameraLongitude ?: lng
        val (cx, cy) = getCanvasCoords(cameraLat, cameraLng, frame, rect, tileBitmap?.width ?: 512, viewport.zoom)
        val bearingRad = Math.toRadians(bearingDeg.toDouble()).toFloat()
        val coneAngle = Math.toRadians(45.0).toFloat()
        val coneLen = rect.width() * 0.42f * 0.8f

        val coneFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 200, 0)
            style = Paint.Style.FILL
        }
        val coneStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 220, 160, 0)
            style = Paint.Style.STROKE
            strokeWidth = 6f * scale
        }
        val conePath = Path().apply {
            moveTo(cx, cy)
            val leftAngle = bearingRad - coneAngle / 2
            lineTo(cx + sin(leftAngle) * coneLen, cy - cos(leftAngle) * coneLen)
            val sweepDeg = Math.toDegrees(coneAngle.toDouble()).toFloat()
            val startDeg = Math.toDegrees((bearingRad - coneAngle / 2).toDouble()).toFloat() - 90f
            arcTo(RectF(cx - coneLen, cy - coneLen, cx + coneLen, cy + coneLen), startDeg, sweepDeg)
            close()
        }
        canvas.drawPath(conePath, coneFillPaint)
        canvas.drawPath(conePath, coneStrokePaint)

        canvas.drawCircle(cx, cy, outerDotRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 220, 50, 50) })
        canvas.drawCircle(cx, cy, innerDotRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 220, 50, 50) })
        canvas.drawCircle(cx, cy, coreDotRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

        canvas.restore()

        // Draw map border
        if (borderWidth > 0f) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(160, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = borderWidth
            }
            canvas.drawRoundRect(rect, cornerR, cornerR, borderPaint)
        }

        if (coordinateText != null) {
            val coordinateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(210, 25, 110, 190)
                textSize = coordinateTextSize
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(6f * scale, 0f, 3f * scale, Color.WHITE)
            }
            val lines = coordinateText.split("\n")
            lines.forEachIndexed { idx, line ->
                val textWidth = coordinateTextPaint.measureText(line)
                val offset = coordinateOffsetY + idx * (coordinateTextSize + 4f * scale)
                canvas.drawText(
                    line,
                    rect.centerX() - textWidth / 2f,
                    rect.bottom + offset,
                    coordinateTextPaint
                )
            }
        }
    }

    private fun PhotoStampRect.toRectF(): RectF = RectF(left, top, right, bottom)
}
