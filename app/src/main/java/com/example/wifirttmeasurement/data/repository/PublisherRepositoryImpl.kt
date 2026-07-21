package com.example.wifirttmeasurement.data.repository

import com.example.wifirttmeasurement.data.rtt.RttPublisherController
import com.example.wifirttmeasurement.domain.model.ConnectionStatus
import com.example.wifirttmeasurement.domain.model.LogSeverity
import com.example.wifirttmeasurement.domain.model.PublisherState
import com.example.wifirttmeasurement.domain.model.PublisherStatus
import com.example.wifirttmeasurement.domain.repository.LogRepository
import com.example.wifirttmeasurement.domain.repository.PublisherRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class PublisherRepositoryImpl @Inject constructor(
    private val publisherController: RttPublisherController,
    private val logRepository: LogRepository,
) : PublisherRepository {
    private val _publisherState = MutableStateFlow(publisherController.buildInitialState())
    override val publisherState: StateFlow<PublisherState> = _publisherState.asStateFlow()

    override suspend fun startPublishing() {
        logRepository.addLog("Publisher Started")
        val nextState = publisherController.startPublishing(_publisherState.value)
        _publisherState.value = nextState

        if (nextState.currentStatus == PublisherStatus.Waiting) {
            logRepository.addLog("Publisher Connected")
        } else {
            logRepository.addLog(
                message = "Publisher could not start because Wi-Fi RTT responder capability is unavailable",
                severity = LogSeverity.Error,
            )
        }
    }

    override suspend fun stopPublishing() {
        _publisherState.value = publisherController.stopPublishing(_publisherState.value)
        logRepository.addLog("Publisher Stopped")
        logRepository.addLog("Publisher Disconnected")
    }

    override suspend fun refreshStatus() {
        _publisherState.update { currentState ->
            val refreshedState = publisherController.refresh(currentState)
            if (currentState.currentStatus == PublisherStatus.Waiting) {
                refreshedState.copy(
                    connectionStatus = ConnectionStatus.Connected,
                    isWaitingForRttRequests = true,
                )
            } else {
                refreshedState
            }
        }
    }

    override suspend fun recordRttRequestReceived() {
        _publisherState.update { currentState ->
            currentState.copy(
                currentStatus = PublisherStatus.Busy,
                requestsReceived = currentState.requestsReceived + 1,
                lastMeasurementTimestampMillis = System.currentTimeMillis(),
            )
        }
        logRepository.addLog("Measurement Started")
        _publisherState.update { currentState ->
            currentState.copy(
                currentStatus = PublisherStatus.Waiting,
                isWaitingForRttRequests = true,
            )
        }
        logRepository.addLog("Measurement Completed")
    }
}
