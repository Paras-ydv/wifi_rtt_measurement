package com.example.wifirttmeasurement.data.mapper

import android.net.MacAddress
import android.net.wifi.rtt.RangingResult
import com.example.wifirttmeasurement.domain.model.MeasurementStatus
import com.example.wifirttmeasurement.domain.model.RttFailureReason
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RttResultMapperTest {

    private fun mockResult(
        status: Int,
        distanceMm: Int = 2500,
        stdDevMm: Int = 100,
        rssi: Int = -55,
        mac: String = "aa:bb:cc:dd:ee:ff",
    ): RangingResult {
        val result = mockk<RangingResult>()
        val macAddress = mockk<MacAddress>()
        every { macAddress.toString() } returns mac
        every { result.status } returns status
        every { result.macAddress } returns macAddress
        every { result.distanceMm } returns distanceMm
        every { result.distanceStdDevMm } returns stdDevMm
        every { result.rssi } returns rssi
        return result
    }

    @Test
    fun `success maps distance mm to meters`() {
        val mapped = RttResultMapper.map(mockResult(RangingResult.STATUS_SUCCESS), "AP", 1, 1)
        assertEquals(MeasurementStatus.Success, mapped.status)
        assertEquals(2.5, mapped.distanceMeters!!, 0.001)
        assertEquals(0.1, mapped.distanceStandardDeviationMeters!!, 0.001)
        assertEquals(-55, mapped.rssiDbm)
        assertEquals(RttFailureReason.None, mapped.failureReason)
    }

    @Test
    fun `success maps mac address as publisher id`() {
        val mapped = RttResultMapper.map(mockResult(RangingResult.STATUS_SUCCESS, mac = "11:22:33:44:55:66"), "AP", 1, 1)
        assertEquals("11:22:33:44:55:66", mapped.publisherId)
    }

    @Test
    fun `success maps publisher name, round and measurement number`() {
        val mapped = RttResultMapper.map(mockResult(RangingResult.STATUS_SUCCESS), "MyRouter", 3, 7)
        assertEquals("MyRouter", mapped.publisherName)
        assertEquals(3L, mapped.roundNumber)
        assertEquals(7L, mapped.measurementNumber)
    }

    @Test
    fun `failure has null distance, stdDev and rssi`() {
        val mapped = RttResultMapper.map(mockResult(RangingResult.STATUS_FAIL), "AP", 1, 1)
        assertEquals(MeasurementStatus.Failed, mapped.status)
        assertNull(mapped.distanceMeters)
        assertNull(mapped.distanceStandardDeviationMeters)
        assertNull(mapped.rssiDbm)
    }

    @Test
    fun `STATUS_FAIL maps to ApiFailure`() {
        val mapped = RttResultMapper.map(mockResult(RangingResult.STATUS_FAIL), "AP", 1, 1)
        assertEquals(RttFailureReason.ApiFailure, mapped.failureReason)
    }

    @Test
    fun `STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC maps to ResponderUnavailable`() {
        val mapped = RttResultMapper.map(
            mockResult(RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC), "AP", 1, 1
        )
        assertEquals(RttFailureReason.ResponderUnavailable, mapped.failureReason)
    }

    @Test
    fun `unknown status maps to Unknown failure reason`() {
        val mapped = RttResultMapper.map(mockResult(status = 999), "AP", 1, 1)
        assertEquals(RttFailureReason.Unknown, mapped.failureReason)
    }

    @Test
    fun `result has positive timestamp`() {
        val mapped = RttResultMapper.map(mockResult(RangingResult.STATUS_SUCCESS), "AP", 1, 1)
        assertTrue(mapped.timestampMillis > 0)
    }
}
