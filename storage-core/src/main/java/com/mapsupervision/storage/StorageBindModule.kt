package com.mapsupervision.storage

import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.DomainEventBus
import com.mapsupervision.domain.repository.ProjectSyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageBindModule {
    @Binds
    abstract fun bindActiveProjectRepository(impl: ActiveProjectRepositoryImpl): ActiveProjectRepository

    @Binds
    abstract fun bindProjectSyncRepository(impl: ProjectSyncRepositoryImpl): ProjectSyncRepository

    @Binds
    abstract fun bindDomainEventBus(impl: DomainEventBusImpl): DomainEventBus
}
