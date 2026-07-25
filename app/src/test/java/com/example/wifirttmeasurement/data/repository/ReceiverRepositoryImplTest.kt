package com.example.wifirttmeasurement.data.repository

import com.example.wifirttmeasurement.domain.model.ConnectionStatus
import com.example.wifirttmeasurement.domain.model.LogSeverity
import com.example.wifirttmeasurement.domain.model.MeasurementLog
import com.example.wifirttmeasurement.domain.model.MeasurementResult
import com.example.wifirttmeasurement.domain.model.MeasurementStatus
import com.example.wifirttmeasurement.domain.model.PublisherDevice
import com.example.wifirttmeasurement.domain.model.PublisherStatus
import com.example.wifirttmeasurement.domain.model.RttFailureReason
import com.example.wifirttmeasurement.domain.repository.LogRepository
import com.example.wifirttmeasurement.data.rtt.RttMeasurementEngine
import com.example.wifirttmeasurement.data.rtt.RttScanCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReceiverRepositoryImplTest {

    private lateinit var scanCoordinator: RttScanCoordinator
    private lateinit var measurementEngine: RttMeasurementEngine
    private lateinit var logRepository: LogRepository
    private lateinit var repository: ReceiverRepositoryImpl

    private val fakePublisher = PublisherDevice(
        id = "aa:bb:cc:dd:ee:ff",
        name = "Test AP",
        connectionStatus = ConnectionStatus.Disconnected,
        status = PublisherStatus.Waiting,
        lastMeasuredDistanceMeters = null,
        lastRssiDbm = -55,
        lastMeasurementTimestampMillis = null,
    )

    private val successResult = MeasurementResult(
        timestampMillis = 1000L,
        publisherId = "aa:bb:cc:dd:ee:ff",
        publisherName = "Test AP",
        distanceMeters = 2.5,
        distanceStandardDeviationMeters = 0.1,
        rssiDbm = -55,
        status = MeasurementStatus.Success,
        failureReason = RttFailureReason.None,
        roundNumber = 1,
        measurementNumber = 1,
    )

    private val failResult = successResult.copy(
        distanceMeters = null,
        status = MeasurementStatus.Failed,
        failureReason = RttFailureReason.ApiFailure,
    )

    // Fake LogRepository — no Android deps
    private val fakeLogRepository = object : LogRepository {
        private val _logs = MutableStateFlow<List<MeasurementLog>>(emptyList())
        override val logs: StateFlow<List<MeasurementLog>> = _logs
        val recorded = mutableListOf<Pair<String, LogSeverity>>()
        override suspend fun addLog(message: String, severity: LogSeverity) {
            recorded.add(message to severity)
        }
    }

    @Before
    fun setUp() {
        scanCoordinator = mockk()
        measurementEngine = mockk()
        logRepository = fakeLogRepository
        repository = ReceiverRepositoryImpl(scanCoordinator, measurementEngine, logRepository)
    }

    // --- scanPublishers ---

    @Test
    fun `scanPublishers sets isScanning true then false`() = runTest {
        coEvery { scanCoordinator.scanPublishers() } returns emptyList()
        repository.scanPublishers()
        assertFalse(repository.receiverState.value.isScanning)
    }

    @Test
    fun `scanPublishers populates publishers list`() = runTest {
        coEvery { scanCoordinator.scanPublishers() } returns listOf(fakePublisher)
        repository.scanPublishers()
        assertEquals(1, repository.receiverState.value.publishers.size)
        assertEquals("aa:bb:cc:dd:ee:ff", repository.receiverState.value.publishers[0].id)
    }

    @Test
    fun `scanPublishers logs warning when no publishers found`() = runTest {
        coEvery { scanCoordinator.scanPublishers() } returns emptyList()
        repository.scanPublishers()
        assertTrue(fakeLogRepository.recorded.any { it.second == LogSeverity.Warning })
    }

    @Test
    fun `scanPublishers preserves selection state of existing publishers`() = runTest {
        coEvery { scanCoordinator.scanPublishers() } returns listOf(fakePublisher)
        repository.scanPublishers()
        repository.togglePublisherSelection("aa:bb:cc:dd:ee:ff")
        // Scan again — selection should be preserved
        repository.scanPublishers()
        assertTrue(repository.receiverState.value.publishers[0].isSelected)
    }

    // --- togglePublisherSelection ---

    @Test
    fun `togglePublisherSelection toggles isSelected`() = runTest {
        coEvery { scanCoordinator.scanPublishers() } returns listOf(fakePublisher)
        repository.scanPublishers()
        assertFalse(repository.receiverState.value.publishers[0].isSelected)
        repository.togglePublisherSelection("aa:bb:cc:dd:ee:ff")
        assertTrue(repository.receiverState.value.publishers[0].isSelected)
        repository.togglePublisherSelection("aa:bb:cc:dd:ee:ff")
        assertFalse(repository.receiverState.value.publishers[0].isSelected)
    }

    @Test
    fun `togglePublisherSelection ignores unknown id`() = runTest {
        coEvery { scanCoordinator.scanPublishers() } returns listOf(fakePublisher)
        repository.scanPublishers()
        repository.togglePublisherSelection("unknown-id")
        assertFalse(repository.receiverState.value.publishers[0].isSelected)
    }

    // --- measureAll ---

    @Test
    fun `measureAll calls engine for each publisher`() = runTest {
        coEvery { scanCoordinator.scanPublishers() } returns listOf(fakePublisher)
        coEvery { measurementEngine.measurePublisher(any(), any(), any()) } returns successResult
        repository.scanPublishers()
        repository.measureAll()
        coVerify(exactly = 1) { measurementEngine.measurePublisher(any(), any(), any()) }
    }

    @Test
    fun `measureAll adds successful result to measurements`() = runTest {
        coEvery { scanCoordinator.scanPublishers() } returns listOf(fakePublisher)
        coEvery { measurementEngine.measurePublisher(any(), any(), any()) } returns successResult
        repository.scanPublishers()
        repository.measureAll()
        assertEquals(1, repository.receiverState.value.measurements.size)
        assertEquals(2.5, repository.receiverState.value.measurements[0].distanceMeters!!, 0.001)
    }

    @Test
    fun `measureAll marks publisher Connected on success`() = runTest {
        coEvery { scanCoordinator.scanPublishers() } returns listOf(fakePublisher)
        coEvery { measurementEngine.measurePublisher(any(), any(), any()) } returns successResult
        repository.scanPublishers()
        repository.measureAll()
        assertEquals(ConnectionStatus.Connected, repository.receiverState.value.publishers[0].connectionStatus)
    }

    @Test
    fun `measureAll marks publisher Unreachable on failure`() = runTest {
        coEvery { scanCoordinator.scanPublishers() } returns listOf(fakePublisher)
        coEvery { measurementEngine.measurePublisher(any(), any(), any()) } returns failResult
        repository.scanPublishers()
        repository.measureAll()
        assertEquals(ConnectionStatus.Unreachable, repository.receiverState.value.publishers[0].connectionStatus)
    }

    @Test
    fun `measureAll with no publishers logs warning and does not measure`() = runTest {
        repository.measureAll()
        coVerify(exactly = 0) { measurementEngine.measurePublisher(any(), any(), any()) }
        assertTrue(fakeLogRepository.recorded.any { it.second == LogSeverity.Warning })
    }

    @Test
    fun `measureAll increments round number`() = runTest {
        coEvery { scanCoordinator.scanPublishers() } returns listOf(fakePublisher)
        coEvery { measurementEngine.measurePublisher(any(), any(), any()) } returns successResult
        repository.scanPublishers()
        assertEquals(0L, repository.receiverState.value.currentRoundNumber)
        repository.measureAll()
        assertEquals(1L, repository.receiverState.value.currentRoundNumber)
        repository.measureAll()
        assertEquals(2L, repository.receiverState.value.currentRoundNumber)
    }

    // --- measureSelected ---

    @Test
    fun `measureSelected only measures selected publishers`() = runTest {
        val pub2 = fakePublisher.copy(id = "11:22:33:44:55:66", name = "AP2")
        coEvery { scanCoordinator.scanPublishers() } returns listOf(fakePublisher, pub2)
        coEvery { measurementEngine.measurePublisher(any(), any(), any()) } returns successResult
        repository.scanPublishers()
        repository.togglePublisherSelection("aa:bb:cc:dd:ee:ff") // select only first
        repository.measureSelected()
        coVerify(exactly = 1) { measurementEngine.measurePublisher(any(), any(), any()) }
    }

    // --- stopMeasurements ---

    @Test
    fun `stopMeasurements sets isMeasuring and isScanning to false`() = runTest {
        repository.stopMeasurements()
        assertFalse(repository.receiverState.value.isMeasuring)
        assertFalse(repository.receiverState.value.isScanning)
    }

    // --- dashboard stats ---

    @Test
    fun `dashboard stats are recalculated after successful measurement`() = runTest {
        coEvery { scanCoordinator.scanPublishers() } returns listOf(fakePublisher)
        coEvery { measurementEngine.measurePublisher(any(), any(), any()) } returns successResult
        repository.scanPublishers()
        repository.measureAll()
        val stats = repository.receiverState.value.dashboardStats
        assertEquals(2.5, stats.averageDistanceMeters!!, 0.001)
        assertEquals(1L, stats.totalMeasurements)
    }
}
