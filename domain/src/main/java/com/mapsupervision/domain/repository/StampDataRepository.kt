package com.mapsupervision.domain.repository

import com.mapsupervision.domain.model.CaptureStampMapScene
import com.mapsupervision.domain.model.StampSnapshot
import kotlinx.coroutines.flow.StateFlow

interface StampDataRepository {
    val stampSnapshot: StateFlow<StampSnapshot?>
    val currentTile: StateFlow<Any?> // Platform specific Bitmap (e.g. android.graphics.Bitmap)
    
    fun updateInput(
        latitude: Double?,
        longitude: Double?,
        bearing: Float,
        note: String,
        address: String,
        mapScene: CaptureStampMapScene?
    )
    
    suspend fun getTile(lat: Double, lng: Double): Any? // Platform specific Bitmap (e.g. android.graphics.Bitmap)
    
    fun clearCache()
}
