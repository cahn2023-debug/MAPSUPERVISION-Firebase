package com.mapsupervision.storage.importer

import com.mapsupervision.domain.repository.ImportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImportBindModule {
    @Binds
    @Singleton
    abstract fun bindImportRepository(impl: UserFileImportService): ImportRepository
}
