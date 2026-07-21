package com.example.wifirttmeasurement.presentation.ui.receiver

import com.example.wifirttmeasurement.domain.model.ConnectionStatus
import com.example.wifirttmeasurement.domain.model.MeasurementResult
import com.example.wifirttmeasurement.domain.model.MeasurementStatus
import com.example.wifirttmeasurement.domain.model.PublisherDevice
import com.example.wifirttmeasurement.domain.model.PublisherStatus

data class PublisherDetailsUiState(
    val publisher: PublisherDevice? = null,
    val measurements: List<MeasurementResult> = emptyList(),
    val averageDistanceMeters: Double? = null,
    val minDistanceMeters: Double? = null,
    val maxDistanceMeters: Double? = null,
    val successRate: Double? = null,
) {
    companion object {
        fun from(publisherId: String, allMeasurements: List<MeasurementResult>, publishers: List<PublisherDevice>): PublisherDetailsUiState {
            val publisher = publishers.find { it.id == publisherId }
            val measurements = allMeasurements.filter { it.publisherId == publisherId }
            val distances = measurements.mapNotNull { it.distanceMeters }
            val successCount = measurements.count { it.status == MeasurementStatus.Success }
            return PublisherDetailsUiState(
                publisher = publisher ?: PublisherDevice(
                    id = publisherId,
                    name = publisherId,
                    connectionStatus = ConnectionStatus.Disconnected,
                    status = PublisherStatus.Offline,
                    lastMeasuredDistanceMeters = null,
                    lastRssiDbm = null,
                    lastMeasurementTimestampMillis = null,
                ),
                measurements = measurements,
                averageDistanceMeters = distances.takeIf { it.isNotEmpty() }?.average(),
                minDistanceMeters = distances.minOrNull(),
                maxDistanceMeters = distances.maxOrNull(),
                successRate = if (measurements.isNotEmpty()) successCount.toDouble() / measurements.size else null,
            )
        }
    }
}
