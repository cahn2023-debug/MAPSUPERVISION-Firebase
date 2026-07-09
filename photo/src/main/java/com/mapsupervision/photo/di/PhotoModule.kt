package com.mapsupervision.photo.di

import com.mapsupervision.domain.service.IPhotoLocationProvider
import com.mapsupervision.domain.service.IPhotoPipelineService
import com.mapsupervision.photo.location.PhotoLocationProvider
import com.mapsupervision.photo.worker.PhotoPipelineService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PhotoModule {

    @Binds
    @Singleton
    abstract fun bindPhotoPipelineService(
        impl: PhotoPipelineService
    ): IPhotoPipelineService

    @Binds
    @Singleton
    abstract fun bindPhotoLocationProvider(
        impl: PhotoLocationProvider
    ): IPhotoLocationProvider
}
