package com.mapsupervision.app.workspace

import androidx.lifecycle.viewModelScope
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.MaterialDeclaration
import com.mapsupervision.domain.model.MaterialHandover
import kotlinx.coroutines.launch
import java.util.UUID

fun WorkspaceViewModel.addMaterialDeclaration(
    workCategory: String,
    material: String,
    ratio: Float,
    unit: String
) {
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId ?: return@launch
        val decl = MaterialDeclaration(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            workName = workCategory,
            materialName = material,
            ratio = ratio,
            unit = unit,
            createdAtEpochMs = System.currentTimeMillis()
        )
        val result = materialDeclarationRepository.add(decl)
        if (result is AppResult.Error) {
            showMessage(result.throwable.message ?: "Không thể lưu định mức vật tư")
            return@launch
        }
        markProjectChanged(projectId, "material_declaration_added")
    }
}

fun WorkspaceViewModel.deleteMaterialDeclaration(decl: MaterialDeclaration) {
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId ?: return@launch
        val result = materialDeclarationRepository.delete(decl)
        if (result is AppResult.Error) {
            showMessage(result.throwable.message ?: "Không thể xóa định mức vật tư")
            return@launch
        }
        markProjectChanged(projectId, "material_declaration_deleted")
    }
}

fun WorkspaceViewModel.addMaterialHandover(
    nodeCode: String,
    workName: String,
    materialName: String,
    contractor: String,
    quantity: Float,
    unit: String,
    handoverDateEpochDay: Long,
    note: String,
    receiver: String = ""
) {
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId ?: return@launch
        val handover = MaterialHandover(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            nodeCode = nodeCode,
            workName = workName.trim(),
            materialName = materialName,
            contractor = contractor,
            quantity = quantity,
            unit = unit,
            handoverDateEpochDay = handoverDateEpochDay,
            note = note,
            receiver = receiver,
            createdAtEpochMs = System.currentTimeMillis()
        )
        val result = materialHandoverRepository.add(handover)
        if (result is AppResult.Error) {
            showMessage(result.throwable.message ?: "Không thể lưu phiếu giao nhận vật tư")
            return@launch
        }
        markProjectChanged(projectId, "material_handover_added")
    }
}

fun WorkspaceViewModel.deleteMaterialHandover(ho: MaterialHandover) {
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId ?: return@launch
        val result = materialHandoverRepository.delete(ho)
        if (result is AppResult.Error) {
            showMessage(result.throwable.message ?: "Không thể xóa phiếu giao nhận vật tư")
            return@launch
        }
        markProjectChanged(projectId, "material_handover_deleted")
    }
}
