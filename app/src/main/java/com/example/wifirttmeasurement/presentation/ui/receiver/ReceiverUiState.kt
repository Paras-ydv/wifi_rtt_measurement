package com.example.wifirttmeasurement.presentation.ui.receiver

import android.net.Uri
import com.example.wifirttmeasurement.domain.model.DashboardStats
import com.example.wifirttmeasurement.domain.model.MeasurementLog
import com.example.wifirttmeasurement.domain.model.MeasurementResult
import com.example.wifirttmeasurement.domain.model.PublisherDevice
import com.example.wifirttmeasurement.domain.model.ReceiverState
import com.example.wifirttmeasurement.domain.model.RttPermissionState

data class ReceiverUiState(
    val publishers: List<PublisherDevice> = emptyList(),
    val measurements: List<MeasurementResult> = emptyList(),
    val dashboardStats: DashboardStats = DashboardStats.Empty,
    val logs: List<MeasurementLog> = emptyList(),
    val isScanning: Boolean = false,
    val isMeasuring: Boolean = false,
    val errorMessage: String? = null,
    val permissionState: RttPermissionState = RttPermissionState.Denied,
    val showPermissionRequest: Boolean = false,
    val exportedCsvUri: Uri? = null,
    val showPermissionDeniedDialog: Boolean = false,
) {
    val hasPublishers: Boolean
        get() = publishers.isNotEmpty()

    val hasSelectedPublishers: Boolean
        get() = publishers.any { it.isSelected }

    companion object {
        fun from(
            receiverState: ReceiverState,
            logs: List<MeasurementLog>,
            errorMessage: String? = null,
            permissionState: RttPermissionState = RttPermissionState.Denied,
            showPermissionRequest: Boolean = false,
        ): ReceiverUiState {
            return ReceiverUiState(
                publishers = receiverState.publishers,
                measurements = receiverState.measurements,
                dashboardStats = receiverState.dashboardStats,
                logs = logs,
                isScanning = receiverState.isScanning,
                isMeasuring = receiverState.isMeasuring,
                errorMessage = errorMessage,
                permissionState = permissionState,
                showPermissionRequest = showPermissionRequest,
                exportedCsvUri = null,
            )
        }
    }
}
