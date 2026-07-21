package com.example.wifirttmeasurement.domain.model

data class PublisherDevice(
    val id: String,
    val name: String,
    val connectionStatus: ConnectionStatus,
    val status: PublisherStatus,
    val lastMeasuredDistanceMeters: Double?,
    val lastRssiDbm: Int?,
    val lastMeasurementTimestampMillis: Long?,
    val isSelected: Boolean = false,
)
