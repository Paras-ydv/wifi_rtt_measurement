package com.example.wifirttmeasurement.domain.usecase

import com.example.wifirttmeasurement.domain.model.MeasurementLog
import com.example.wifirttmeasurement.domain.repository.LogRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveLogsUseCase @Inject constructor(
    private val logRepository: LogRepository,
) {
    operator fun invoke(): StateFlow<List<MeasurementLog>> {
        return logRepository.logs
    }
}
