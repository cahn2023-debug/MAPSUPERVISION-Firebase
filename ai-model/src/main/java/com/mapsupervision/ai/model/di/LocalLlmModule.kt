package com.mapsupervision.ai.model.di

import com.mapsupervision.ai.model.mediapipe.LocalLiteRtRepositoryImpl
import com.mapsupervision.domain.repository.LocalLlmRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalLlmModule {

    @Binds
    @Singleton
    abstract fun bindLocalLlmRepository(
        impl: LocalLiteRtRepositoryImpl
    ): LocalLlmRepository
}
