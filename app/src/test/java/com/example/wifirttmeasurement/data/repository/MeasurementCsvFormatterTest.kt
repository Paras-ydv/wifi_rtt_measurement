package com.example.wifirttmeasurement.data.repository

import com.example.wifirttmeasurement.data.csv.MeasurementCsvFormatter
import com.example.wifirttmeasurement.domain.model.MeasurementResult
import com.example.wifirttmeasurement.domain.model.MeasurementStatus
import com.example.wifirttmeasurement.domain.model.RttFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementCsvFormatterTest {

    private fun result(
        distance: Double? = 2.5,
        rssi: Int? = -54,
        stdDev: Double? = 0.1,
        status: MeasurementStatus = MeasurementStatus.Success,
        publisherName: String = "MyRouter",
        publisherId: String = "aa:bb:cc:dd:ee:ff",
        measurementNumber: Long = 1L,
        timestampMillis: Long = 0L,
    ) = MeasurementResult(
        timestampMillis = timestampMillis,
        publisherId = publisherId,
        publisherName = publisherName,
        distanceMeters = distance,
        distanceStandardDeviationMeters = stdDev,
        rssiDbm = rssi,
        status = status,
        failureReason = RttFailureReason.None,
        roundNumber = 1,
        measurementNumber = measurementNumber,
    )

    @Test
    fun `header contains all required columns`() {
        val header = MeasurementCsvFormatter.header
        assertTrue(header.contains("Timestamp"))
        assertTrue(header.contains("Publisher ID"))
        assertTrue(header.contains("Publisher Name"))
        assertTrue(header.contains("Distance"))
        assertTrue(header.contains("RSSI"))
        assertTrue(header.contains("Std Dev"))
        assertTrue(header.contains("Status"))
        assertTrue(header.contains("Measurement Number"))
    }

    @Test
    fun `formatRow contains publisher id`() {
        val row = MeasurementCsvFormatter.formatRow(result())
        assertTrue(row.contains("aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun `formatRow contains distance`() {
        val row = MeasurementCsvFormatter.formatRow(result(distance = 3.14))
        assertTrue(row.contains("3.14"))
    }

    @Test
    fun `formatRow contains rssi`() {
        val row = MeasurementCsvFormatter.formatRow(result(rssi = -67))
        assertTrue(row.contains("-67"))
    }

    @Test
    fun `formatRow contains status`() {
        val row = MeasurementCsvFormatter.formatRow(result(status = MeasurementStatus.Failed))
        assertTrue(row.contains("Failed"))
    }

    @Test
    fun `formatRow contains measurement number`() {
        val row = MeasurementCsvFormatter.formatRow(result(measurementNumber = 42L))
        assertTrue(row.contains("42"))
    }

    @Test
    fun `formatRow handles null distance gracefully`() {
        val row = MeasurementCsvFormatter.formatRow(result(distance = null))
        // Should not throw and should have empty field between commas
        val columns = row.trimEnd().split(",")
        assertEquals("", columns[3]) // distance column is empty
    }

    @Test
    fun `formatRow handles null rssi gracefully`() {
        val row = MeasurementCsvFormatter.formatRow(result(rssi = null))
        val columns = row.trimEnd().split(",")
        assertEquals("", columns[4]) // rssi column is empty
    }

    @Test
    fun `formatRow escapes publisher name with comma`() {
        val row = MeasurementCsvFormatter.formatRow(result(publisherName = "My,Router"))
        assertTrue(row.contains("\"My,Router\""))
    }

    @Test
    fun `formatRow escapes publisher name with double quote`() {
        val row = MeasurementCsvFormatter.formatRow(result(publisherName = "My\"Router"))
        assertTrue(row.contains("\"My\"\"Router\""))
    }

    @Test
    fun `formatRow ends with newline`() {
        val row = MeasurementCsvFormatter.formatRow(result())
        assertTrue(row.endsWith("\n"))
    }
}
