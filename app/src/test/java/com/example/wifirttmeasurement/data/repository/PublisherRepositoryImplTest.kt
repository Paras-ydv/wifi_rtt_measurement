package com.example.wifirttmeasurement.data.repository

import com.example.wifirttmeasurement.data.rtt.RttPublisherController
import com.example.wifirttmeasurement.domain.model.ConnectionStatus
import com.example.wifirttmeasurement.domain.model.LogSeverity
import com.example.wifirttmeasurement.domain.model.MeasurementLog
import com.example.wifirttmeasurement.domain.model.PublisherState
import com.example.wifirttmeasurement.domain.model.PublisherStatus
import com.example.wifirttmeasurement.domain.repository.LogRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PublisherRepositoryImplTest {

    private lateinit var controller: RttPublisherController
    private lateinit var repository: PublisherRepositoryImpl

    private val fakeLog = object : LogRepository {
        private val _logs = MutableStateFlow<List<MeasurementLog>>(emptyList())
        override val logs: StateFlow<List<MeasurementLog>> = _logs
        val recorded = mutableListOf<Pair<String, LogSeverity>>()
        override suspend fun addLog(message: String, severity: LogSeverity) {
            recorded.add(message to severity)
        }
    }

    private val waitingState = PublisherState.Initial.copy(
        currentStatus = PublisherStatus.Waiting,
        connectionStatus = ConnectionStatus.Connected,
        isWaitingForRttRequests = true,
    )

    private val offlineState = PublisherState.Initial.copy(
        currentStatus = PublisherStatus.Offline,
        connectionStatus = ConnectionStatus.Unreachable,
        isWaitingForRttRequests = false,
    )

    @Before
    fun setUp() {
        controller = mockk()
        every { controller.buildInitialState() } returns PublisherState.Initial
        repository = PublisherRepositoryImpl(controller, fakeLog)
    }

    @Test
    fun `initial state comes from controller`() {
        assertEquals(PublisherState.Initial, repository.publisherState.value)
    }

    // --- startPublishing ---

    @Test
    fun `startPublishing sets Waiting state when capable`() = runTest {
        every { controller.startPublishing(any()) } returns waitingState
        repository.startPublishing()
        assertEquals(PublisherStatus.Waiting, repository.publisherState.value.currentStatus)
        assertEquals(ConnectionStatus.Connected, repository.publisherState.value.connectionStatus)
    }

    @Test
    fun `startPublishing logs Connected when capable`() = runTest {
        every { controller.startPublishing(any()) } returns waitingState
        repository.startPublishing()
        assertTrue(fakeLog.recorded.any { it.first == "Publisher Connected" })
    }

    @Test
    fun `startPublishing sets Offline when not capable`() = runTest {
        every { controller.startPublishing(any()) } returns offlineState
        repository.startPublishing()
        assertEquals(PublisherStatus.Offline, repository.publisherState.value.currentStatus)
    }

    @Test
    fun `startPublishing logs Error when not capable`() = runTest {
        every { controller.startPublishing(any()) } returns offlineState
        repository.startPublishing()
        assertTrue(fakeLog.recorded.any { it.second == LogSeverity.Error })
    }

    // --- stopPublishing ---

    @Test
    fun `stopPublishing sets Offline and Disconnected`() = runTest {
        val stoppedState = PublisherState.Initial.copy(
            currentStatus = PublisherStatus.Offline,
            connectionStatus = ConnectionStatus.Disconnected,
            isWaitingForRttRequests = false,
        )
        every { controller.stopPublishing(any()) } returns stoppedState
        repository.stopPublishing()
        assertEquals(PublisherStatus.Offline, repository.publisherState.value.currentStatus)
        assertEquals(ConnectionStatus.Disconnected, repository.publisherState.value.connectionStatus)
    }

    @Test
    fun `stopPublishing logs Stopped and Disconnected`() = runTest {
        every { controller.stopPublishing(any()) } returns PublisherState.Initial
        repository.stopPublishing()
        assertTrue(fakeLog.recorded.any { it.first == "Publisher Stopped" })
        assertTrue(fakeLog.recorded.any { it.first == "Publisher Disconnected" })
    }

    // --- refreshStatus ---

    @Test
    fun `refreshStatus updates device name from controller`() = runTest {
        val refreshed = PublisherState.Initial.copy(deviceName = "Pixel 7")
        every { controller.refresh(any()) } returns refreshed
        repository.refreshStatus()
        assertEquals("Pixel 7", repository.publisherState.value.deviceName)
    }

    @Test
    fun `refreshStatus sets Connected and waiting when status is Waiting`() = runTest {
        every { controller.startPublishing(any()) } returns waitingState
        every { controller.refresh(any()) } returns waitingState
        repository.startPublishing()
        repository.refreshStatus()
        assertEquals(ConnectionStatus.Connected, repository.publisherState.value.connectionStatus)
        assertTrue(repository.publisherState.value.isWaitingForRttRequests)
    }

    // --- recordRttRequestReceived ---

    @Test
    fun `recordRttRequestReceived increments requestsReceived`() = runTest {
        assertEquals(0L, repository.publisherState.value.requestsReceived)
        repository.recordRttRequestReceived()
        assertEquals(1L, repository.publisherState.value.requestsReceived)
        repository.recordRttRequestReceived()
        assertEquals(2L, repository.publisherState.value.requestsReceived)
    }

    @Test
    fun `recordRttRequestReceived ends in Waiting status`() = runTest {
        repository.recordRttRequestReceived()
        assertEquals(PublisherStatus.Waiting, repository.publisherState.value.currentStatus)
        assertTrue(repository.publisherState.value.isWaitingForRttRequests)
    }

    @Test
    fun `recordRttRequestReceived sets lastMeasurementTimestampMillis`() = runTest {
        repository.recordRttRequestReceived()
        assertTrue((repository.publisherState.value.lastMeasurementTimestampMillis ?: 0L) > 0L)
    }

    @Test
    fun `recordRttRequestReceived logs Measurement Started and Completed`() = runTest {
        repository.recordRttRequestReceived()
        assertTrue(fakeLog.recorded.any { it.first == "Measurement Started" })
        assertTrue(fakeLog.recorded.any { it.first == "Measurement Completed" })
    }
}
