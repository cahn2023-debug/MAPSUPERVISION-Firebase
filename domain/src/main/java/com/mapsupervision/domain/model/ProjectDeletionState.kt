package com.mapsupervision.domain.model

enum class ProjectDeletionState {
    ACTIVE,
    CLOUD_DECISION_PENDING,
    CLOUD_RETAINED,
    RESTORE_PENDING,
    LOCAL_DELETE_FAILED,
    DELETING,
    DELETE_FAILED,
    DELETED
}
