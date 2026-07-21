package com.example.wifirttmeasurement.domain.model

data class MeasurementLog(
    val id: String,
    val timestampMillis: Long,
    val message: String,
    val severity: LogSeverity,
)
