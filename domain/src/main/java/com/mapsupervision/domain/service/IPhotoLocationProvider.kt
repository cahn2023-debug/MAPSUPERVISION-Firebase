package com.mapsupervision.domain.service

import com.mapsupervision.domain.model.PhotoLocationSnapshot

interface IPhotoLocationProvider {
    suspend fun lastKnownLocation(): PhotoLocationSnapshot
}
