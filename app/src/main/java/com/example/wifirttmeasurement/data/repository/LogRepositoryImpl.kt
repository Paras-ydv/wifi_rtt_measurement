package com.example.wifirttmeasurement.data.repository

import com.example.wifirttmeasurement.domain.model.LogSeverity
import com.example.wifirttmeasurement.domain.model.MeasurementLog
import com.example.wifirttmeasurement.domain.repository.LogRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class LogRepositoryImpl @Inject constructor() : LogRepository {
    private val _logs = MutableStateFlow<List<MeasurementLog>>(emptyList())
    override val logs: StateFlow<List<MeasurementLog>> = _logs.asStateFlow()

    override suspend fun addLog(
        message: String,
        severity: LogSeverity,
    ) {
        val log = MeasurementLog(
            id = UUID.randomUUID().toString(),
            timestampMillis = System.currentTimeMillis(),
            message = message,
            severity = severity,
        )
        _logs.update { currentLogs ->
            (listOf(log) + currentLogs).take(MaxLogs)
        }
    }
}

private const val MaxLogs = 300
