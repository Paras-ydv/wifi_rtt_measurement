package com.example.wifirttmeasurement.domain.repository

import com.example.wifirttmeasurement.domain.model.LogSeverity
import com.example.wifirttmeasurement.domain.model.MeasurementLog
import kotlinx.coroutines.flow.StateFlow

interface LogRepository {
    val logs: StateFlow<List<MeasurementLog>>

    suspend fun addLog(
        message: String,
        severity: LogSeverity = LogSeverity.Info,
    )
}
