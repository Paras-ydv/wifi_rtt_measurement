package com.example.wifirttmeasurement.domain.model

data class MeasurementResult(
    val timestampMillis: Long,
    val publisherId: String,
    val publisherName: String,
    val distanceMeters: Double?,
    val distanceStandardDeviationMeters: Double?,
    val rssiDbm: Int?,
    val status: MeasurementStatus,
    val failureReason: RttFailureReason,
    val roundNumber: Long,
    val measurementNumber: Long,
)
