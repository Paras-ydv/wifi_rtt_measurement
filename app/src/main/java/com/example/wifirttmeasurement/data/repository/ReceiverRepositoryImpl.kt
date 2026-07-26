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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@Singleton
class ReceiverRepositoryImpl @Inject constructor(
    private val rttScanCoordinator: RttScanCoordinator,
    private val rttMeasurementEngine: RttMeasurementEngine,
    private val logRepository: LogRepository,
) : ReceiverRepository {
    private val _receiverState = MutableStateFlow(ReceiverState.Initial)
    override val receiverState: StateFlow<ReceiverState> = _receiverState.asStateFlow()

    // Sliding window of raw distances per publisher for median filtering
    private val distanceWindows = mutableMapOf<String, ArrayDeque<Double>>()

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

    override suspend fun syncPublishers(publishers: List<PublisherDevice>) {
        _receiverState.update { current ->
            val currentById = current.publishers.associateBy { it.id }
            // Preserve selection state from repo; preserve measurement results (rssi, distance,
            // connectionStatus) from the incoming list which may be more up-to-date.
            val synced = publishers.map { p ->
                val existing = currentById[p.id]
                p.copy(isSelected = existing?.isSelected ?: p.isSelected)
            }
            // Also keep any repo-only publishers not present in the incoming list.
            val incomingIds = publishers.map { it.id }.toSet()
            val repoOnly = current.publishers.filter { it.id !in incomingIds }
            current.copy(publishers = synced + repoOnly)
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
        _receiverState.update { it.copy(isMeasuring = true, currentRoundNumber = roundNumber) }
        logRepository.addLog("Measurement Started")

        withContext(Dispatchers.IO) {
            publishers.forEach { publisher ->
                if (!_receiverState.value.isMeasuring) return@forEach

                val measurementNumber = _receiverState.value.measurements.size + 1L
                val result = rttMeasurementEngine.measurePublisher(
                    publisher = publisher,
                    roundNumber = roundNumber,
                    measurementNumber = measurementNumber,
                )

                _receiverState.update { currentState ->
                    val filteredResult = result.distanceMeters?.let { raw ->
                        val window = distanceWindows.getOrPut(publisher.id) { ArrayDeque(MedianWindowSize) }
                        if (window.size >= MedianWindowSize) window.removeFirst()
                        window.addLast(raw)
                        val median = window.sorted()[window.size / 2]
                        result.copy(distanceMeters = median)
                    } ?: result
                    val updatedPublishers = currentState.publishers.map { existingPublisher ->
                        if (existingPublisher.id == publisher.id) {
                            existingPublisher.copy(
                                connectionStatus = if (result.status == MeasurementStatus.Success) {
                                    ConnectionStatus.Connected
                                } else {
                                    ConnectionStatus.Unreachable
                                },
                                lastMeasuredDistanceMeters = filteredResult.distanceMeters,
                                lastRssiDbm = result.rssiDbm,
                                lastMeasurementTimestampMillis = result.timestampMillis,
                            )
                        } else {
                            existingPublisher
                        }
                    }
                    currentState.copy(
                        publishers = updatedPublishers,
                        measurements = (listOf(filteredResult) + currentState.measurements).take(MaxMeasurements),
                        dashboardStats = DashboardStatsCalculator.calculate(
                            publishers = updatedPublishers,
                            measurements = (listOf(filteredResult) + currentState.measurements).take(MaxMeasurements),
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
private const val MedianWindowSize = 5
