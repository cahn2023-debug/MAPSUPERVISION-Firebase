package com.mapsupervision.app.sync

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseMediaUploadModule {
    @Binds
    @Singleton
    abstract fun bindFirebaseMediaUploadScheduler(
        impl: WorkManagerFirebaseMediaUploadScheduler
    ): FirebaseMediaUploadScheduler
}
