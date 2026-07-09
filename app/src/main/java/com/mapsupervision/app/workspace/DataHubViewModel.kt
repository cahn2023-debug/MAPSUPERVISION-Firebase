package com.mapsupervision.app.workspace

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DataHubScreenUiState(
    val isDesignTab: Boolean = true,
    val searchQuery: String = "",
    val contractorFilter: String = "Chọn nhà thầu",
    val contractorMenuExpanded: Boolean = false,
    val objectTypeFilter: String = "Tất cả",
    val sortOrder: String = "Mã A→Z",
    val sortMenuExpanded: Boolean = false,
    val showNotesAndTasksSheet: Boolean = false,
    val notesAndTasksObjectCode: String = ""
)

@HiltViewModel
class DataHubViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DataHubScreenUiState(
            isDesignTab = savedStateHandle[KEY_IS_DESIGN_TAB] ?: true,
            searchQuery = savedStateHandle[KEY_SEARCH_QUERY] ?: "",
            contractorFilter = savedStateHandle[KEY_CONTRACTOR_FILTER] ?: "Chọn nhà thầu",
            contractorMenuExpanded = false,
            objectTypeFilter = savedStateHandle[KEY_OBJECT_TYPE_FILTER] ?: "Tất cả",
            sortOrder = savedStateHandle[KEY_SORT_ORDER] ?: "Mã A→Z",
            sortMenuExpanded = false,
            showNotesAndTasksSheet = savedStateHandle[KEY_SHOW_NOTES_SHEET] ?: false,
            notesAndTasksObjectCode = savedStateHandle[KEY_NOTES_OBJECT_CODE] ?: ""
        )
    )
    val uiState: StateFlow<DataHubScreenUiState> = _uiState.asStateFlow()

    fun setDesignTab(isDesignTab: Boolean) {
        updateState { it.copy(isDesignTab = isDesignTab) }
        savedStateHandle[KEY_IS_DESIGN_TAB] = isDesignTab
    }

    fun updateSearchQuery(searchQuery: String) {
        updateState { it.copy(searchQuery = searchQuery) }
        savedStateHandle[KEY_SEARCH_QUERY] = searchQuery
    }

    fun updateContractorFilter(contractorFilter: String) {
        updateState { it.copy(contractorFilter = contractorFilter) }
        savedStateHandle[KEY_CONTRACTOR_FILTER] = contractorFilter
    }

    fun setContractorMenuExpanded(expanded: Boolean) {
        updateState { it.copy(contractorMenuExpanded = expanded) }
    }

    fun updateObjectTypeFilter(objectTypeFilter: String) {
        updateState { it.copy(objectTypeFilter = objectTypeFilter) }
        savedStateHandle[KEY_OBJECT_TYPE_FILTER] = objectTypeFilter
    }

    fun updateSortOrder(sortOrder: String) {
        updateState { it.copy(sortOrder = sortOrder) }
        savedStateHandle[KEY_SORT_ORDER] = sortOrder
    }

    fun setSortMenuExpanded(expanded: Boolean) {
        updateState { it.copy(sortMenuExpanded = expanded) }
    }

    fun showNotesAndTasks(objectCode: String) {
        updateState {
            it.copy(
                showNotesAndTasksSheet = true,
                notesAndTasksObjectCode = objectCode
            )
        }
        savedStateHandle[KEY_SHOW_NOTES_SHEET] = true
        savedStateHandle[KEY_NOTES_OBJECT_CODE] = objectCode
    }

    fun dismissNotesAndTasks() {
        updateState {
            it.copy(
                showNotesAndTasksSheet = false,
                notesAndTasksObjectCode = ""
            )
        }
        savedStateHandle[KEY_SHOW_NOTES_SHEET] = false
        savedStateHandle[KEY_NOTES_OBJECT_CODE] = ""
    }

    private fun updateState(transform: (DataHubScreenUiState) -> DataHubScreenUiState) {
        _uiState.update(transform)
    }

    private companion object {
        const val KEY_IS_DESIGN_TAB = "data_hub_is_design_tab"
        const val KEY_SEARCH_QUERY = "data_hub_search_query"
        const val KEY_CONTRACTOR_FILTER = "data_hub_contractor_filter"
        const val KEY_OBJECT_TYPE_FILTER = "data_hub_object_type_filter"
        const val KEY_SORT_ORDER = "data_hub_sort_order"
        const val KEY_SHOW_NOTES_SHEET = "data_hub_show_notes_sheet"
        const val KEY_NOTES_OBJECT_CODE = "data_hub_notes_object_code"
    }
}
