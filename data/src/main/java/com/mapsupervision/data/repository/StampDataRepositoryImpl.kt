package com.mapsupervision.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.domain.model.CaptureStampMapScene
import com.mapsupervision.domain.model.RoundedLocationKey
import com.mapsupervision.domain.model.StampSnapshot
import com.mapsupervision.domain.repository.StampDataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StampDataRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : StampDataRepository {

    private val scope = CoroutineScope(Dispatchers.Default)
    
    private val _stampSnapshot = MutableStateFlow<StampSnapshot?>(null)
    override val stampSnapshot: StateFlow<StampSnapshot?> = _stampSnapshot.asStateFlow()

    private val _currentTile = MutableStateFlow<Any?>(null)
    override val currentTile: StateFlow<Any?> = _currentTile.asStateFlow()

    // In-memory tile cache
    private val memCache = java.util.concurrent.ConcurrentHashMap<RoundedLocationKey, Bitmap>()
    
    // Cache directory for tiles
    private val cacheDir = File(context.cacheDir, "map_tiles").apply { mkdirs() }
    private val maxDiskTiles = 100

    override fun updateInput(
        latitude: Double?,
        longitude: Double?,
        bearing: Float,
        note: String,
        address: String,
        mapScene: CaptureStampMapScene?
    ) {
        val locationKey = roundedLocationKey(latitude, longitude)
        val bearingBucket = bearing.toInt()
        val current = _stampSnapshot.value

        // Check if any threshold or input data changed materially
        val locationChanged = current?.locationKey != locationKey
        val bearingChanged = current?.bearingBucket != bearingBucket
        val addressChanged = current?.address != address
        val noteChanged = current?.note != note
        val mapSceneChanged = current?.mapScene != mapScene

        if (current == null || locationChanged || bearingChanged || addressChanged || noteChanged || mapSceneChanged) {
            val nextSnapshot = StampSnapshot(
                timestampMs = System.currentTimeMillis(),
                locationKey = locationKey,
                address = address,
                bearingBucket = bearingBucket,
                note = note,
                mapScene = mapScene,
                tileCacheKey = locationKey
            )
            _stampSnapshot.value = nextSnapshot

            // If location changed, trigger tile loading asynchronously
            if (locationChanged && latitude != null && longitude != null) {
                scope.launch {
                    val tile = getTile(latitude, longitude)
                    _currentTile.value = tile
                }
            } else if (latitude == null || longitude == null) {
                _currentTile.value = null
            }
        }
    }

    override suspend fun getTile(lat: Double, lng: Double): Any? {
        val key = roundedLocationKey(lat, lng) ?: return null
        
        // 1. In-memory hit
        memCache[key]?.let { return it }

        return withContext(Dispatchers.IO) {
            // 2. Disk cache hit
            val cacheFile = File(cacheDir, "${key.latitudeE4}_${key.longitudeE4}.png")
            if (cacheFile.exists()) {
                val cachedBitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (cachedBitmap != null) {
                    memCache[key] = cachedBitmap
                    return@withContext cachedBitmap
                }
            }

            // 3. Network fetch
            val fetched = fetchOsmTile(lat, lng, MINIMAP_TILE_ZOOM)
            if (fetched != null) {
                saveTileToDisk(cacheFile, fetched)
                memCache[key] = fetched
                pruneDiskCacheIfNeeded()
                return@withContext fetched
            }

            // 4. Default to null (offline fallback is handled by the renderer during drawStamp)
            null
        }
    }

    private fun saveTileToDisk(file: File, bitmap: Bitmap) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            AppLogger.e(e, "StampDataRepositoryImpl: Failed to save tile to disk")
        }
    }

    private fun pruneDiskCacheIfNeeded() {
        try {
            val files = cacheDir.listFiles() ?: return
            if (files.size > maxDiskTiles) {
                // Sort by last modified time ascending (oldest first)
                val sorted = files.sortedBy { it.lastModified() }
                val toDelete = files.size - maxDiskTiles
                for (i in 0 until toDelete) {
                    sorted[i].delete()
                }
            }
        } catch (e: Exception) {
            AppLogger.e(e, "StampDataRepositoryImpl: Failed to prune disk cache")
        }
    }

    override fun clearCache() {
        memCache.forEach { (_, bitmap) -> bitmap.recycle() }
        memCache.clear()
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            AppLogger.e(e, "StampDataRepositoryImpl: Failed to clear disk cache")
        }
        _currentTile.value = null
    }

    private fun roundedLocationKey(latitude: Double?, longitude: Double?): RoundedLocationKey? {
        if (latitude == null || longitude == null) return null
        val scale = 10000.0 // 4 decimal places
        return RoundedLocationKey(
            latitudeE4 = kotlin.math.round(latitude * scale).toInt(),
            longitudeE4 = kotlin.math.round(longitude * scale).toInt()
        )
    }

    private fun fetchOsmTile(lat: Double, lng: Double, zoom: Int): Bitmap? {
        return try {
            val n = 1 shl zoom
            val xTile = ((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
            val latRad = Math.toRadians(lat)
            val yTile = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n)
                .toInt().coerceIn(0, n - 1)
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

    companion object {
        private const val MINIMAP_TILE_ZOOM = 18
    }
}
