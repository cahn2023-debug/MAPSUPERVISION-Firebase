package com.mapsupervision.domain.usecase

import com.mapsupervision.domain.repository.ProjectRepository
import javax.inject.Inject

class CreateProjectUseCase @Inject constructor(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(name: String) = repository.create(name)
}
