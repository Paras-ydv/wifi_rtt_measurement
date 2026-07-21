package com.example.wifirttmeasurement.domain.model

data class PublisherState(
    val deviceName: String,
    val publisherId: String,
    val currentStatus: PublisherStatus,
    val connectionStatus: ConnectionStatus,
    val isWaitingForRttRequests: Boolean,
    val requestsReceived: Long,
    val batteryPercentage: Int?,
    val lastMeasurementTimestampMillis: Long?,
) {
    companion object {
        val Initial = PublisherState(
            deviceName = "Android Device",
            publisherId = "unknown",
            currentStatus = PublisherStatus.Offline,
            connectionStatus = ConnectionStatus.Disconnected,
            isWaitingForRttRequests = false,
            requestsReceived = 0,
            batteryPercentage = null,
            lastMeasurementTimestampMillis = null,
        )
    }
}
