package com.example.wifirttmeasurement.data.repository

import com.example.wifirttmeasurement.data.rtt.RttMeasurementEngine
import com.example.wifirttmeasurement.data.rtt.RttScanCoordinator
import com.example.wifirttmeasurement.domain.model.ConnectionStatus
import com.example.wifirttmeasurement.domain.model.LogSeverity
import com.example.wifirttmeasurement.domain.model.MeasurementStatus
import com.example.wifirttmeasurement.domain.model.PublisherDevice
import com.example.wifirttmeasurement.domain.model.ReceiverState
import com.example.wifirttmeasurement.domain.repository.LogRepository
import com.example.wifirttmeasurement.domain.repository.ReceiverRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class ReceiverRepositoryImpl @Inject constructor(
    private val rttScanCoordinator: RttScanCoordinator,
    private val rttMeasurementEngine: RttMeasurementEngine,
    private val logRepository: LogRepository,
) : ReceiverRepository {
    private val _receiverState = MutableStateFlow(ReceiverState.Initial)
    override val receiverState: StateFlow<ReceiverState> = _receiverState.asStateFlow()

    override suspend fun scanPublishers() {
        logRepository.addLog("Publisher scan started")
        _receiverState.update { it.copy(isScanning = true) }

        val discoveredPublishers = rttScanCoordinator.scanPublishers()
        _receiverState.update { currentState ->
            val publishers = mergePublishers(
                currentPublishers = currentState.publishers,
                discoveredPublishers = discoveredPublishers,
            )
            currentState.copy(
                publishers = publishers,
                dashboardStats = DashboardStatsCalculator.calculate(
                    publishers = publishers,
                    measurements = currentState.measurements,
                ),
                isScanning = false,
            )
        }

        if (discoveredPublishers.isEmpty()) {
            logRepository.addLog(
                message = "No Wi-Fi RTT publishers discovered",
                severity = LogSeverity.Warning,
            )
        } else {
            logRepository.addLog("Publisher Connected")
        }
    }

    override suspend fun togglePublisherSelection(publisherId: String) {
        _receiverState.update { currentState ->
            currentState.copy(
                publishers = currentState.publishers.map { publisher ->
                    if (publisher.id == publisherId) {
                        publisher.copy(isSelected = !publisher.isSelected)
                    } else {
                        publisher
                    }
                },
            )
        }
    }

    override suspend fun measureSelected() {
        val selectedPublishers = _receiverState.value.publishers.filter { it.isSelected }
        measurePublishers(selectedPublishers)
    }

    override suspend fun measureAll() {
        measurePublishers(_receiverState.value.publishers)
    }

    override suspend fun stopMeasurements() {
        _receiverState.update { it.copy(isMeasuring = false, isScanning = false) }
        logRepository.addLog("Measurement stopped")
    }

    private suspend fun measurePublishers(publishers: List<PublisherDevice>) {
        if (publishers.isEmpty()) {
            logRepository.addLog(
                message = "Measurement requested but no publishers are available",
                severity = LogSeverity.Warning,
            )
            return
        }

        val roundNumber = _receiverState.value.currentRoundNumber + 1
        _receiverState.update {
            it.copy(
                isMeasuring = true,
                currentRoundNumber = roundNumber,
            )
        }
        logRepository.addLog("Measurement Started")

        publishers.forEach { publisher ->
            if (!_receiverState.value.isMeasuring) return@forEach

            val measurementNumber = _receiverState.value.measurements.size + 1L
            val result = rttMeasurementEngine.measurePublisher(
                publisher = publisher,
                roundNumber = roundNumber,
                measurementNumber = measurementNumber,
            )

            _receiverState.update { currentState ->
                val measurements = (listOf(result) + currentState.measurements).take(MaxMeasurements)
                val updatedPublishers = currentState.publishers.map { existingPublisher ->
                    if (existingPublisher.id == publisher.id) {
                        existingPublisher.copy(
                            connectionStatus = if (result.status == MeasurementStatus.Success) {
                                ConnectionStatus.Connected
                            } else {
                                ConnectionStatus.Unreachable
                            },
                            lastMeasuredDistanceMeters = result.distanceMeters,
                            lastRssiDbm = result.rssiDbm,
                            lastMeasurementTimestampMillis = result.timestampMillis,
                        )
                    } else {
                        existingPublisher
                    }
                }

                currentState.copy(
                    publishers = updatedPublishers,
                    measurements = measurements,
                    dashboardStats = DashboardStatsCalculator.calculate(
                        publishers = updatedPublishers,
                        measurements = measurements,
                    ),
                )
            }

            if (result.status == MeasurementStatus.Success) {
                logRepository.addLog("Measurement Completed")
            } else {
                logRepository.addLog(
                    message = "Measurement Failed for ${publisher.name}: ${result.failureReason}",
                    severity = LogSeverity.Warning,
                )
            }
        }

        _receiverState.update { it.copy(isMeasuring = false) }
    }

    private fun mergePublishers(
        currentPublishers: List<PublisherDevice>,
        discoveredPublishers: List<PublisherDevice>,
    ): List<PublisherDevice> {
        val currentById = currentPublishers.associateBy { it.id }
        return discoveredPublishers.map { discoveredPublisher ->
            val currentPublisher = currentById[discoveredPublisher.id]
            discoveredPublisher.copy(isSelected = currentPublisher?.isSelected ?: false)
        }
    }
}

private const val MaxMeasurements = 1_000
