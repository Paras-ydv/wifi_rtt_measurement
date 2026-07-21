package com.example.wifirttmeasurement.domain.usecase

import android.net.Uri
import com.example.wifirttmeasurement.domain.repository.CsvExportRepository
import com.example.wifirttmeasurement.domain.repository.ReceiverRepository
import javax.inject.Inject

class ExportMeasurementsCsvUseCase @Inject constructor(
    private val receiverRepository: ReceiverRepository,
    private val csvExportRepository: CsvExportRepository,
) {
    suspend operator fun invoke(): Uri {
        val measurements = receiverRepository.receiverState.value.measurements
        return csvExportRepository.exportMeasurements(measurements)
    }
}
