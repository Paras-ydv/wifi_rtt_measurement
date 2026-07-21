package com.example.wifirttmeasurement.domain.repository

import android.net.Uri
import com.example.wifirttmeasurement.domain.model.MeasurementResult

interface CsvExportRepository {
    suspend fun exportMeasurements(measurements: List<MeasurementResult>): Uri
}
