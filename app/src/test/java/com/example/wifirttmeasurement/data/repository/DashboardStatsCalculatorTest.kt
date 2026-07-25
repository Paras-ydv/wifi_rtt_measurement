package com.example.wifirttmeasurement.data.repository

import com.example.wifirttmeasurement.domain.model.ConnectionStatus
import com.example.wifirttmeasurement.domain.model.MeasurementResult
import com.example.wifirttmeasurement.domain.model.MeasurementStatus
import com.example.wifirttmeasurement.domain.model.PublisherDevice
import com.example.wifirttmeasurement.domain.model.PublisherStatus
import com.example.wifirttmeasurement.domain.model.RttFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardStatsCalculatorTest {

    private fun measurement(distanceMeters: Double?, timestampMillis: Long = 1000L) = MeasurementResult(
        timestampMillis = timestampMillis,
        publisherId = "pub-1",
        publisherName = "Test AP",
        distanceMeters = distanceMeters,
        distanceStandardDeviationMeters = null,
        rssiDbm = -60,
        status = if (distanceMeters != null) MeasurementStatus.Success else MeasurementStatus.Failed,
        failureReason = RttFailureReason.None,
        roundNumber = 1,
        measurementNumber = 1,
    )

    private fun publisher(connected: Boolean) = PublisherDevice(
        id = "pub-1",
        name = "Test AP",
        connectionStatus = if (connected) ConnectionStatus.Connected else ConnectionStatus.Disconnected,
        status = PublisherStatus.Waiting,
        lastMeasuredDistanceMeters = null,
        lastRssiDbm = null,
        lastMeasurementTimestampMillis = null,
    )

    @Test
    fun `empty measurements returns Empty stats`() {
        val result = DashboardStatsCalculator.calculate(emptyList(), emptyList())
        assertNull(result.averageDistanceMeters)
        assertNull(result.minimumDistanceMeters)
        assertNull(result.maximumDistanceMeters)
        assertEquals(0L, result.totalMeasurements)
        assertEquals(0.0, result.measurementRateHz, 0.0)
    }

    @Test
    fun `all failed measurements returns no distance stats`() {
        val measurements = listOf(measurement(null), measurement(null))
        val result = DashboardStatsCalculator.calculate(emptyList(), measurements)
        assertNull(result.averageDistanceMeters)
        assertEquals(2L, result.totalMeasurements)
    }

    @Test
    fun `single successful measurement`() {
        val result = DashboardStatsCalculator.calculate(emptyList(), listOf(measurement(3.0)))
        assertEquals(3.0, result.averageDistanceMeters!!, 0.001)
        assertEquals(3.0, result.minimumDistanceMeters!!, 0.001)
        assertEquals(3.0, result.maximumDistanceMeters!!, 0.001)
        assertEquals(3.0, result.medianDistanceMeters!!, 0.001)
        assertEquals(0.0, result.standardDeviationMeters!!, 0.001)
    }

    @Test
    fun `average is correct for multiple measurements`() {
        val measurements = listOf(measurement(2.0), measurement(4.0), measurement(6.0))
        val result = DashboardStatsCalculator.calculate(emptyList(), measurements)
        assertEquals(4.0, result.averageDistanceMeters!!, 0.001)
    }

    @Test
    fun `min and max are correct`() {
        val measurements = listOf(measurement(1.5), measurement(5.0), measurement(3.0))
        val result = DashboardStatsCalculator.calculate(emptyList(), measurements)
        assertEquals(1.5, result.minimumDistanceMeters!!, 0.001)
        assertEquals(5.0, result.maximumDistanceMeters!!, 0.001)
    }

    @Test
    fun `median is correct for even count`() {
        val measurements = listOf(measurement(1.0), measurement(2.0), measurement(3.0), measurement(4.0))
        val result = DashboardStatsCalculator.calculate(emptyList(), measurements)
        assertEquals(2.5, result.medianDistanceMeters!!, 0.001)
    }

    @Test
    fun `median is correct for odd count`() {
        val measurements = listOf(measurement(1.0), measurement(3.0), measurement(5.0))
        val result = DashboardStatsCalculator.calculate(emptyList(), measurements)
        assertEquals(3.0, result.medianDistanceMeters!!, 0.001)
    }

    @Test
    fun `standard deviation is correct`() {
        // values: 2, 4, 4, 4, 5, 5, 7, 9 — known std dev = 2.0
        val values = listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)
        val measurements = values.map { measurement(it) }
        val result = DashboardStatsCalculator.calculate(emptyList(), measurements)
        assertEquals(2.0, result.standardDeviationMeters!!, 0.001)
    }

    @Test
    fun `active publishers count only connected publishers`() {
        val publishers = listOf(publisher(true), publisher(false), publisher(true))
        val result = DashboardStatsCalculator.calculate(publishers, emptyList())
        assertEquals(2, result.activePublishers)
    }

    @Test
    fun `measurement rate is zero for single measurement`() {
        val result = DashboardStatsCalculator.calculate(emptyList(), listOf(measurement(1.0)))
        assertEquals(0.0, result.measurementRateHz, 0.0)
    }

    @Test
    fun `measurement rate is calculated correctly`() {
        // 3 measurements over 2 seconds = 1.5 Hz
        val measurements = listOf(
            measurement(1.0, timestampMillis = 0L),
            measurement(2.0, timestampMillis = 1000L),
            measurement(3.0, timestampMillis = 2000L),
        )
        val result = DashboardStatsCalculator.calculate(emptyList(), measurements)
        assertEquals(1.5, result.measurementRateHz, 0.01)
    }

    @Test
    fun `null distances are excluded from stats`() {
        val measurements = listOf(measurement(2.0), measurement(null), measurement(4.0))
        val result = DashboardStatsCalculator.calculate(emptyList(), measurements)
        assertEquals(2, result.currentMeasurements)
        assertEquals(3L, result.totalMeasurements)
        assertEquals(3.0, result.averageDistanceMeters!!, 0.001)
    }
}
