package com.example.wifirttmeasurement.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilityTest {

    @Test
    fun `Unknown has all capabilities false and Unknown failure reason`() {
        assertFalse(DeviceCapability.Unknown.isRttAvailable)
        assertFalse(DeviceCapability.Unknown.canActAsReceiver)
        assertFalse(DeviceCapability.Unknown.canActAsPublisher)
        assertEquals(RttFailureReason.Unknown, DeviceCapability.Unknown.failureReason)
    }

    @Test
    fun `capable device has null failure reason`() {
        val cap = DeviceCapability(isRttAvailable = true, canActAsReceiver = true, canActAsPublisher = true)
        assertTrue(cap.isRttAvailable)
        assertNull(cap.failureReason)
    }
}

class DashboardStatsTest {

    @Test
    fun `Empty has all nulls and zeros`() {
        val e = DashboardStats.Empty
        assertNull(e.averageDistanceMeters)
        assertNull(e.minimumDistanceMeters)
        assertNull(e.maximumDistanceMeters)
        assertNull(e.medianDistanceMeters)
        assertNull(e.standardDeviationMeters)
        assertEquals(0L, e.totalMeasurements)
        assertEquals(0.0, e.measurementRateHz, 0.0)
        assertEquals(0, e.activePublishers)
        assertEquals(0, e.currentMeasurements)
    }
}

class ReceiverStateTest {

    @Test
    fun `Initial has empty publishers, measurements, and zero round number`() {
        val s = ReceiverState.Initial
        assertTrue(s.publishers.isEmpty())
        assertTrue(s.measurements.isEmpty())
        assertFalse(s.isScanning)
        assertFalse(s.isMeasuring)
        assertEquals(0L, s.currentRoundNumber)
        assertEquals(DashboardStats.Empty, s.dashboardStats)
    }
}

class PublisherStateTest {

    @Test
    fun `Initial has Offline status and zero requests`() {
        val s = PublisherState.Initial
        assertEquals(PublisherStatus.Offline, s.currentStatus)
        assertEquals(ConnectionStatus.Disconnected, s.connectionStatus)
        assertFalse(s.isWaitingForRttRequests)
        assertEquals(0L, s.requestsReceived)
        assertNull(s.batteryPercentage)
        assertNull(s.lastMeasurementTimestampMillis)
    }
}

class MeasurementResultTest {

    @Test
    fun `success result has non-null distance and None failure reason`() {
        val r = MeasurementResult(
            timestampMillis = 1000L,
            publisherId = "aa:bb:cc:dd:ee:ff",
            publisherName = "AP",
            distanceMeters = 3.5,
            distanceStandardDeviationMeters = 0.2,
            rssiDbm = -60,
            status = MeasurementStatus.Success,
            failureReason = RttFailureReason.None,
            roundNumber = 1,
            measurementNumber = 1,
        )
        assertEquals(MeasurementStatus.Success, r.status)
        assertEquals(3.5, r.distanceMeters!!, 0.001)
        assertEquals(RttFailureReason.None, r.failureReason)
    }

    @Test
    fun `failed result has null distance and non-None failure reason`() {
        val r = MeasurementResult(
            timestampMillis = 1000L,
            publisherId = "aa:bb:cc:dd:ee:ff",
            publisherName = "AP",
            distanceMeters = null,
            distanceStandardDeviationMeters = null,
            rssiDbm = null,
            status = MeasurementStatus.Failed,
            failureReason = RttFailureReason.ApiFailure,
            roundNumber = 1,
            measurementNumber = 1,
        )
        assertEquals(MeasurementStatus.Failed, r.status)
        assertNull(r.distanceMeters)
        assertEquals(RttFailureReason.ApiFailure, r.failureReason)
    }
}
