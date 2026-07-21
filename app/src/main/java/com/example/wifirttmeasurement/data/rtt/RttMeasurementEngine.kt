package com.example.wifirttmeasurement.data.rtt

import com.example.wifirttmeasurement.domain.model.MeasurementResult
import com.example.wifirttmeasurement.domain.model.MeasurementStatus
import com.example.wifirttmeasurement.domain.model.PublisherDevice
import com.example.wifirttmeasurement.domain.model.RttFailureReason
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RttMeasurementEngine @Inject constructor(
    private val androidRttManager: AndroidRttManager,
    private val rttScanCoordinator: RttScanCoordinator,
) {
    suspend fun measurePublisher(
        publisher: PublisherDevice,
        roundNumber: Long,
        measurementNumber: Long,
    ): MeasurementResult {
        val scanResult = rttScanCoordinator.getScanResult(publisher.id)
            ?: return MeasurementResult(
                timestampMillis = System.currentTimeMillis(),
                publisherId = publisher.id,
                publisherName = publisher.name,
                distanceMeters = null,
                distanceStandardDeviationMeters = null,
                rssiDbm = publisher.lastRssiDbm,
                status = MeasurementStatus.Failed,
                failureReason = RttFailureReason.ResponderUnavailable,
                roundNumber = roundNumber,
                measurementNumber = measurementNumber,
            )

        val result = androidRttManager.range(scanResult, publisher)
        return result.copy(roundNumber = roundNumber, measurementNumber = measurementNumber)
    }
}
