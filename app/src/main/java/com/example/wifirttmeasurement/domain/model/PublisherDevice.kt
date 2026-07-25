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
    /** Non-null when this device was discovered via Wi-Fi Aware (phone-to-phone). */
    val awarePeerId: String? = null,
)
