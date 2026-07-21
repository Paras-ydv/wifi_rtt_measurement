package com.example.wifirttmeasurement.data.mapper

import android.net.wifi.rtt.RangingResult
import com.example.wifirttmeasurement.domain.model.MeasurementResult
import com.example.wifirttmeasurement.domain.model.MeasurementStatus
import com.example.wifirttmeasurement.domain.model.RttFailureReason

object RttResultMapper {
    fun map(
        result: RangingResult,
        publisherName: String,
        roundNumber: Long,
        measurementNumber: Long,
    ): MeasurementResult {
        val success = result.status == RangingResult.STATUS_SUCCESS
        return MeasurementResult(
            timestampMillis = System.currentTimeMillis(),
            publisherId = result.macAddress.toString(),
            publisherName = publisherName,
            distanceMeters = if (success) result.distanceMm / 1000.0 else null,
            distanceStandardDeviationMeters = if (success) result.distanceStdDevMm / 1000.0 else null,
            rssiDbm = if (success) result.rssi else null,
            status = if (success) MeasurementStatus.Success else MeasurementStatus.Failed,
            failureReason = if (success) RttFailureReason.None else statusToFailureReason(result.status),
            roundNumber = roundNumber,
            measurementNumber = measurementNumber,
        )
    }

    private fun statusToFailureReason(status: Int): RttFailureReason = when (status) {
        RangingResult.STATUS_FAIL -> RttFailureReason.ApiFailure
        RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC -> RttFailureReason.ResponderUnavailable
        else -> RttFailureReason.Unknown
    }
}
