package com.example.wifirttmeasurement.domain.model

data class ReceiverState(
    val publishers: List<PublisherDevice>,
    val measurements: List<MeasurementResult>,
    val dashboardStats: DashboardStats,
    val isScanning: Boolean,
    val isMeasuring: Boolean,
    val currentRoundNumber: Long,
) {
    companion object {
        val Initial = ReceiverState(
            publishers = emptyList(),
            measurements = emptyList(),
            dashboardStats = DashboardStats.Empty,
            isScanning = false,
            isMeasuring = false,
            currentRoundNumber = 0,
        )
    }
}
