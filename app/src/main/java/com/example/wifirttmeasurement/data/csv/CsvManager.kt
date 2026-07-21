package com.example.wifirttmeasurement.data.csv

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.wifirttmeasurement.domain.model.MeasurementResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun writeCsv(measurements: List<MeasurementResult>): Uri {
        val dir = File(context.filesDir, "exports").also { it.mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "rtt_measurements_$timestamp.csv")

        file.bufferedWriter().use { writer ->
            writer.write(MeasurementCsvFormatter.header)
            measurements.forEach { writer.write(MeasurementCsvFormatter.formatRow(it)) }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }
}
