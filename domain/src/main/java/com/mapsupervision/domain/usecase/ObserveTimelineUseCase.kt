package com.mapsupervision.domain.usecase

import com.mapsupervision.domain.model.TimelineSnapshot
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProgressRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ObserveTimelineUseCase @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val dailyLogRepository: DailyLogRepository,
    private val photoRepository: PhotoRepository
) {
    operator fun invoke(projectId: String): Flow<TimelineSnapshot> {
        return combine(
            progressRepository.observeByProject(projectId),
            dailyLogRepository.observeByProject(projectId),
            photoRepository.observeByProject(projectId)
        ) { progress, logs, photos ->
            TimelineSnapshot(
                projectId = projectId,
                progress = progress,
                logs = logs,
                photoCount = photos.size
            )
        }.distinctUntilChanged()
    }
}
