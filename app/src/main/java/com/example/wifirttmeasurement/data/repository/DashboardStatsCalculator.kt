package com.example.wifirttmeasurement.data.repository

import com.example.wifirttmeasurement.domain.model.ConnectionStatus
import com.example.wifirttmeasurement.domain.model.DashboardStats
import com.example.wifirttmeasurement.domain.model.MeasurementResult
import com.example.wifirttmeasurement.domain.model.PublisherDevice
import kotlin.math.pow
import kotlin.math.sqrt

object DashboardStatsCalculator {
    fun calculate(
        publishers: List<PublisherDevice>,
        measurements: List<MeasurementResult>,
    ): DashboardStats {
        val distances = measurements.mapNotNull { it.distanceMeters }
        val totalMeasurements = measurements.size.toLong()
        val measurementRateHz = measurementRate(measurements)

        if (distances.isEmpty()) {
            return DashboardStats.Empty.copy(
                totalMeasurements = totalMeasurements,
                measurementRateHz = measurementRateHz,
                activePublishers = publishers.count { it.connectionStatus == ConnectionStatus.Connected },
            )
        }

        val sorted = distances.sorted()
        val average = distances.average()
        val variance = distances.map { distance -> (distance - average).pow(2) }.average()

        return DashboardStats(
            currentMeasurements = distances.size,
            averageDistanceMeters = average,
            minimumDistanceMeters = sorted.first(),
            maximumDistanceMeters = sorted.last(),
            medianDistanceMeters = median(sorted),
            standardDeviationMeters = sqrt(variance),
            totalMeasurements = totalMeasurements,
            measurementRateHz = measurementRateHz,
            activePublishers = publishers.count { it.connectionStatus == ConnectionStatus.Connected },
        )
    }

    private fun median(sortedValues: List<Double>): Double {
        val middle = sortedValues.size / 2
        return if (sortedValues.size % 2 == 0) {
            (sortedValues[middle - 1] + sortedValues[middle]) / 2.0
        } else {
            sortedValues[middle]
        }
    }

    private fun measurementRate(measurements: List<MeasurementResult>): Double {
        if (measurements.size < 2) return 0.0
        val first = measurements.minOf { it.timestampMillis }
        val last = measurements.maxOf { it.timestampMillis }
        val elapsedSeconds = (last - first) / 1000.0
        return if (elapsedSeconds > 0.0) measurements.size / elapsedSeconds else 0.0
    }
}
