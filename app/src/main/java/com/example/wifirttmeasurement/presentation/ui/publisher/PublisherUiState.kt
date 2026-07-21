package com.example.wifirttmeasurement.presentation.ui.publisher

import com.example.wifirttmeasurement.domain.model.ConnectionStatus
import com.example.wifirttmeasurement.domain.model.MeasurementLog
import com.example.wifirttmeasurement.domain.model.PublisherState
import com.example.wifirttmeasurement.domain.model.PublisherStatus

data class PublisherUiState(
    val deviceName: String = "Android Device",
    val publisherId: String = "unknown",
    val currentStatus: PublisherStatus = PublisherStatus.Offline,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val isWaitingForRttRequests: Boolean = false,
    val requestsReceived: Long = 0,
    val batteryPercentage: Int? = null,
    val lastMeasurementTimestampMillis: Long? = null,
    val logs: List<MeasurementLog> = emptyList(),
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
) {
    companion object {
        fun from(
            publisherState: PublisherState,
            logs: List<MeasurementLog>,
            isBusy: Boolean = false,
            errorMessage: String? = null,
        ): PublisherUiState {
            return PublisherUiState(
                deviceName = publisherState.deviceName,
                publisherId = publisherState.publisherId,
                currentStatus = publisherState.currentStatus,
                connectionStatus = publisherState.connectionStatus,
                isWaitingForRttRequests = publisherState.isWaitingForRttRequests,
                requestsReceived = publisherState.requestsReceived,
                batteryPercentage = publisherState.batteryPercentage,
                lastMeasurementTimestampMillis = publisherState.lastMeasurementTimestampMillis,
                logs = logs,
                isBusy = isBusy,
                errorMessage = errorMessage,
            )
        }
    }
}
