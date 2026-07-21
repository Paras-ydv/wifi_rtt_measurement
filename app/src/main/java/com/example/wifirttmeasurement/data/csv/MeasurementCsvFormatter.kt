package com.example.wifirttmeasurement.data.csv

import com.example.wifirttmeasurement.domain.model.MeasurementResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MeasurementCsvFormatter {
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    val header = "Timestamp,Publisher ID,Publisher Name,Distance (m),RSSI (dBm),Std Dev (m),Status,Measurement Number\n"

    fun formatRow(result: MeasurementResult): String {
        val timestamp = timestampFormat.format(Date(result.timestampMillis))
        val distance = result.distanceMeters?.toString() ?: ""
        val rssi = result.rssiDbm?.toString() ?: ""
        val stdDev = result.distanceStandardDeviationMeters?.toString() ?: ""
        return "$timestamp,${result.publisherId},${result.publisherName.escapeCsv()},$distance,$rssi,$stdDev,${result.status},${result.measurementNumber}\n"
    }

    private fun String.escapeCsv(): String {
        return if (contains(',') || contains('"') || contains('\n')) "\"${replace("\"", "\"\"")}\"" else this
    }
}
