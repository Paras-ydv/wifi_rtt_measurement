package com.example.wifirttmeasurement.data.repository

import android.net.Uri
import com.example.wifirttmeasurement.data.csv.CsvManager
import com.example.wifirttmeasurement.domain.model.MeasurementResult
import com.example.wifirttmeasurement.domain.repository.CsvExportRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExportRepositoryImpl @Inject constructor(
    private val csvManager: CsvManager,
) : CsvExportRepository {
    override suspend fun exportMeasurements(measurements: List<MeasurementResult>): Uri {
        return csvManager.writeCsv(measurements)
    }
}
