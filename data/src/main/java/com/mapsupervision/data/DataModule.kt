package com.mapsupervision.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.data.db.ProjectDeletionSqlGuards
import com.mapsupervision.data.db.dao.DailyLogDao
import com.mapsupervision.data.db.dao.GisNodeDao
import com.mapsupervision.data.db.dao.GisRouteDao
import com.mapsupervision.data.db.dao.AiDecisionCacheDao
import com.mapsupervision.data.db.dao.ChatHistoryDao
import com.mapsupervision.data.db.dao.WorkVolumeProgressDao
import com.mapsupervision.data.db.dao.NodeProgressDao
import com.mapsupervision.data.db.dao.NoteDao
import com.mapsupervision.data.db.dao.TaskDao
import com.mapsupervision.data.db.dao.ProjectDao
import com.mapsupervision.data.db.dao.SitePhotoDao
import com.mapsupervision.data.db.dao.WorkCategoryDao
import com.mapsupervision.data.repository.DailyLogRepositoryImpl
import com.mapsupervision.data.repository.AiDecisionCacheStoreImpl
import com.mapsupervision.data.repository.ChatHistoryRepositoryImpl
import com.mapsupervision.data.repository.GisRepositoryImpl
import com.mapsupervision.data.repository.WorkVolumeProgressRepositoryImpl
import com.mapsupervision.data.repository.PhotoRepositoryImpl
import com.mapsupervision.data.repository.ProgressRepositoryImpl
import com.mapsupervision.data.repository.ProjectRepositoryImpl
import com.mapsupervision.data.repository.NoteRepositoryImpl
import com.mapsupervision.data.repository.TaskRepositoryImpl
import com.mapsupervision.data.repository.WorkCategoryRepositoryImpl
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.ChatHistoryRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.WorkVolumeProgressRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProgressRepository
import com.mapsupervision.domain.repository.ProjectRepository
import com.mapsupervision.domain.repository.NoteRepository
import com.mapsupervision.domain.repository.TaskRepository
import com.mapsupervision.domain.repository.WorkCategoryRepository
import com.mapsupervision.domain.repository.StampDataRepository
import com.mapsupervision.domain.repository.FirebaseSyncRepository
import com.mapsupervision.domain.repository.FirebaseAccessRepository
import com.mapsupervision.data.repository.StampDataRepositoryImpl
import com.mapsupervision.data.db.dao.ReportDraftDao
import com.mapsupervision.data.db.dao.WorkPlanDao
import com.mapsupervision.data.repository.ReportDraftRepositoryImpl
import com.mapsupervision.domain.repository.ReportDraftRepository
import com.mapsupervision.data.db.dao.MaterialHandoverDao
import com.mapsupervision.data.repository.MaterialHandoverRepositoryImpl
import com.mapsupervision.domain.repository.MaterialHandoverRepository
import com.mapsupervision.data.repository.MaterialDeclarationRepositoryImpl
import com.mapsupervision.domain.repository.MaterialDeclarationRepository
import com.mapsupervision.data.repository.ImportLifecycleRepositoryImpl
import com.mapsupervision.domain.repository.ImportLifecycleRepository
import com.mapsupervision.domain.repository.ContractorColorPreferenceRepository
import com.mapsupervision.data.repository.ContractorColorPreferenceRepositoryImpl
import com.mapsupervision.data.mlkit.MlKitScannerService
import com.mapsupervision.domain.service.WeatherService
import com.mapsupervision.domain.service.PhotoOcrService
import com.mapsupervision.ai.core.rag.RagContextBuilder
import com.mapsupervision.ai.core.rag.RagIndexRepository
import com.mapsupervision.ai.core.rag.RagRetriever
import com.mapsupervision.ai.core.rag.TextEmbeddingEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MapSupervisionDatabase =
        Room.databaseBuilder(context, MapSupervisionDatabase::class.java, "mapsupervision.db")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    ProjectDeletionSqlGuards.install(db)
                    db.execSQL("PRAGMA foreign_keys = ON")
                    db.execSQL("PRAGMA synchronous = NORMAL")
                    db.execSQL("PRAGMA temp_store = MEMORY")
                }
            })
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideWorkPlanDao(db: MapSupervisionDatabase): WorkPlanDao = db.workPlanDao()

    @Provides
    fun provideAiActionLogDao(db: MapSupervisionDatabase): com.mapsupervision.data.db.dao.AiActionLogDao = db.aiActionLogDao()

    @Provides
    fun provideProjectDao(db: MapSupervisionDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideNodeProgressDao(db: MapSupervisionDatabase): NodeProgressDao = db.nodeProgressDao()

    @Provides
    fun provideSitePhotoDao(db: MapSupervisionDatabase): SitePhotoDao = db.sitePhotoDao()

    @Provides
    fun provideDailyLogDao(db: MapSupervisionDatabase): DailyLogDao = db.dailyLogDao()

    @Provides
    fun provideGisNodeDao(db: MapSupervisionDatabase): GisNodeDao = db.gisNodeDao()

    @Provides
    fun provideGisRouteDao(db: MapSupervisionDatabase): GisRouteDao = db.gisRouteDao()

    @Provides
    fun provideImportedFileDao(db: MapSupervisionDatabase): com.mapsupervision.data.db.dao.ImportedFileDao = db.importedFileDao()

    @Provides
    fun provideEventOutboxDao(db: MapSupervisionDatabase): com.mapsupervision.data.db.dao.EventOutboxDao = db.eventOutboxDao()

    @Provides
    fun provideWorkVolumeProgressDao(db: MapSupervisionDatabase): WorkVolumeProgressDao = db.workVolumeProgressDao()

    @Provides
    fun provideNoteDao(db: MapSupervisionDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideTaskDao(db: MapSupervisionDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideWorkCategoryDao(db: MapSupervisionDatabase): WorkCategoryDao = db.workCategoryDao()

    @Provides
    fun provideAiDecisionCacheDao(db: MapSupervisionDatabase): AiDecisionCacheDao = db.aiDecisionCacheDao()

    @Provides
    fun provideChatHistoryDao(db: MapSupervisionDatabase): ChatHistoryDao = db.chatHistoryDao()

    @Provides
    fun provideReportDraftDao(db: MapSupervisionDatabase): ReportDraftDao = db.reportDraftDao()

    @Provides
    fun provideMaterialHandoverDao(db: MapSupervisionDatabase): MaterialHandoverDao = db.materialHandoverDao()

    @Provides
    fun provideMaterialDeclarationDao(db: MapSupervisionDatabase): com.mapsupervision.data.db.dao.MaterialDeclarationDao = db.materialDeclarationDao()

    @Provides
    fun provideRagDocumentEmbeddingDao(db: MapSupervisionDatabase): com.mapsupervision.data.db.dao.RagDocumentEmbeddingDao = db.ragDocumentEmbeddingDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindModule {
    @Binds
    @Singleton
    abstract fun bindStampDataRepository(impl: StampDataRepositoryImpl): StampDataRepository

    @Binds
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds
    abstract fun bindPhotoRepository(impl: PhotoRepositoryImpl): PhotoRepository

    @Binds
    abstract fun bindDailyLogRepository(impl: DailyLogRepositoryImpl): DailyLogRepository

    @Binds
    abstract fun bindGisRepository(impl: GisRepositoryImpl): GisRepository

    @Binds
    abstract fun bindImportedFileRepository(impl: com.mapsupervision.data.repository.ImportedFileRepositoryImpl): com.mapsupervision.domain.repository.ImportedFileRepository

    @Binds
    abstract fun bindAiRepository(impl: com.mapsupervision.data.repository.GeminiRepositoryImpl): com.mapsupervision.ai.core.repository.AiRepository

    @Binds
    abstract fun bindWorkVolumeProgressRepository(impl: WorkVolumeProgressRepositoryImpl): WorkVolumeProgressRepository

    @Binds
    abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository

    @Binds
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    abstract fun bindWorkCategoryRepository(impl: WorkCategoryRepositoryImpl): WorkCategoryRepository

    @Binds
    abstract fun bindTfLiteRepository(impl: com.mapsupervision.data.tflite.TfLiteRepositoryImpl): com.mapsupervision.ai.core.repository.TfLiteRepository

    @Binds
    abstract fun bindWeatherService(impl: com.mapsupervision.data.network.WeatherServiceImpl): WeatherService

    @Binds
    abstract fun bindAiDecisionCacheStore(impl: AiDecisionCacheStoreImpl): com.mapsupervision.ai.core.repository.AiDecisionCacheStore



    @Binds
    abstract fun bindChatHistoryRepository(impl: ChatHistoryRepositoryImpl): ChatHistoryRepository

    @Binds
    abstract fun bindAiActionLogRepository(impl: com.mapsupervision.data.repository.AiActionLogRepositoryImpl): com.mapsupervision.domain.repository.AiActionLogRepository

    @Binds
    abstract fun bindReportDraftRepository(impl: ReportDraftRepositoryImpl): ReportDraftRepository

    @Binds
    abstract fun bindWorkPlanRepository(impl: com.mapsupervision.data.repository.WorkPlanRepositoryImpl): com.mapsupervision.domain.repository.WorkPlanRepository

    @Binds
    abstract fun bindMaterialHandoverRepository(impl: MaterialHandoverRepositoryImpl): MaterialHandoverRepository

    @Binds
    abstract fun bindMaterialDeclarationRepository(impl: MaterialDeclarationRepositoryImpl): MaterialDeclarationRepository

    @Binds
    abstract fun bindImportLifecycleRepository(impl: ImportLifecycleRepositoryImpl): ImportLifecycleRepository

    @Binds
    abstract fun bindPhotoOcrService(impl: MlKitScannerService): PhotoOcrService

    @Binds
    abstract fun bindTextEmbeddingEngine(impl: com.mapsupervision.data.rag.OptionalTfliteTextEmbeddingEngine): TextEmbeddingEngine

    @Binds
    abstract fun bindRagIndexRepository(impl: com.mapsupervision.data.rag.RagIndexRepositoryImpl): RagIndexRepository

    @Binds
    abstract fun bindRagRetriever(impl: com.mapsupervision.data.rag.RagRetrieverImpl): RagRetriever

    @Binds
    abstract fun bindRagContextBuilder(impl: com.mapsupervision.data.rag.RagContextBuilderImpl): RagContextBuilder

    @Binds
    abstract fun bindProjectStorageMigrationService(impl: com.mapsupervision.data.db.ProjectStorageMigrationServiceImpl): com.mapsupervision.domain.service.ProjectStorageMigrationService

    @Binds
    abstract fun bindContractorColorPreferenceRepository(impl: ContractorColorPreferenceRepositoryImpl): ContractorColorPreferenceRepository

    @Binds
    @Singleton
    abstract fun bindFirebaseSyncRepository(impl: com.mapsupervision.data.sync.FirebaseSyncRepositoryImpl): FirebaseSyncRepository

    @Binds
    @Singleton
    abstract fun bindFirebaseAccessRepository(impl: com.mapsupervision.data.sync.FirebaseAccessRepositoryImpl): FirebaseAccessRepository
}

