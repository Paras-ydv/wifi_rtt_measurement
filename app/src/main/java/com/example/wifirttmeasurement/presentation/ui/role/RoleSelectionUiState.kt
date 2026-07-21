package com.example.wifirttmeasurement.presentation.ui.role

import com.example.wifirttmeasurement.domain.model.AppRole
import com.example.wifirttmeasurement.domain.model.DeviceCapability

data class RoleSelectionUiState(
    val selectedRole: AppRole? = null,
    val capability: DeviceCapability = DeviceCapability.Unknown,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val canChooseReceiver: Boolean
        get() = !isLoading && capability.canActAsReceiver

    val canChoosePublisher: Boolean
        get() = !isLoading && capability.canActAsPublisher
}
