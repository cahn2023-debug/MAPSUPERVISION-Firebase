package com.mapsupervision.app.ai

import com.mapsupervision.ai.core.AIFacade
import com.mapsupervision.ai.agent.AiOrchestrator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiFacadeModule {
    @Binds
    @Singleton
    abstract fun bindAiFacade(impl: AiOrchestrator): AIFacade
}

