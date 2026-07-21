package com.example.wifirttmeasurement.domain.model

data class DashboardStats(
    val currentMeasurements: Int,
    val averageDistanceMeters: Double?,
    val minimumDistanceMeters: Double?,
    val maximumDistanceMeters: Double?,
    val medianDistanceMeters: Double?,
    val standardDeviationMeters: Double?,
    val totalMeasurements: Long,
    val measurementRateHz: Double,
    val activePublishers: Int,
) {
    companion object {
        val Empty = DashboardStats(
            currentMeasurements = 0,
            averageDistanceMeters = null,
            minimumDistanceMeters = null,
            maximumDistanceMeters = null,
            medianDistanceMeters = null,
            standardDeviationMeters = null,
            totalMeasurements = 0,
            measurementRateHz = 0.0,
            activePublishers = 0,
        )
    }
}
