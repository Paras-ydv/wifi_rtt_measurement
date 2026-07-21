package com.example.wifirttmeasurement.presentation.ui.role

import com.example.wifirttmeasurement.domain.model.AppRole

sealed interface RoleSelectionEvent {
    data class NavigateToRole(val role: AppRole) : RoleSelectionEvent
}
