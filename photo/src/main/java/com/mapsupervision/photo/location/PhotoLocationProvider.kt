package com.mapsupervision.photo.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mapsupervision.domain.model.PhotoLocationSnapshot
import com.mapsupervision.domain.model.PhotoLocationStatus
import com.mapsupervision.domain.service.IPhotoLocationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Singleton
open class PhotoLocationProvider @Inject constructor(
    @ApplicationContext context: Context
) : IPhotoLocationProvider {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    open override suspend fun lastKnownLocation(): PhotoLocationSnapshot = suspendCancellableCoroutine { cont ->
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                if (location == null) {
                    cont.resume(PhotoLocationSnapshot())
                    return@addOnSuccessListener
                }
                val status = if (location.accuracy > 50f) {
                    PhotoLocationStatus.INACCURATE
                } else {
                    PhotoLocationStatus.OK
                }
                cont.resume(
                    PhotoLocationSnapshot(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyM = location.accuracy,
                        isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else false,
                        status = status
                    )
                )
            }
            .addOnFailureListener { cont.resume(PhotoLocationSnapshot()) }
    }
}
