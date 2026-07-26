package com.example.wifirttmeasurement

import com.example.wifirttmeasurement.data.repository.ReceiverRepositoryImpl
import com.example.wifirttmeasurement.data.repository.PublisherRepositoryImpl
import com.example.wifirttmeasurement.data.rtt.RttMeasurementEngine
import com.example.wifirttmeasurement.data.rtt.RttScanCoordinator
import com.example.wifirttmeasurement.data.rtt.RttPublisherController
import com.example.wifirttmeasurement.domain.model.AwarePeer
import com.example.wifirttmeasurement.domain.model.ConnectionStatus
import com.example.wifirttmeasurement.domain.model.LogSeverity
import com.example.wifirttmeasurement.domain.model.MeasurementLog
import com.example.wifirttmeasurement.domain.model.MeasurementResult
import com.example.wifirttmeasurement.domain.model.MeasurementStatus
import com.example.wifirttmeasurement.domain.model.PublisherDevice
import com.example.wifirttmeasurement.domain.model.PublisherState
import com.example.wifirttmeasurement.domain.model.PublisherStatus
import com.example.wifirttmeasurement.domain.model.RttFailureReason
import com.example.wifirttmeasurement.domain.repository.LogRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Simulates a full phone-to-phone Wi-Fi RTT measurement session.
 *
 * Device roles:
 *   - S22 Ultra  → Publisher  (starts Aware, waits for RTT requests)
 *   - Pixel 9a   → Receiver   (discovers Publisher via Aware, measures distance)
 *
 * No Android framework is used — all hardware interactions are faked.
 */
class PhoneToPhoneMeasurementTest {

    // -------------------------------------------------------------------------
    // Shared fake log
    // -------------------------------------------------------------------------

    private val fakeLog = object : LogRepository {
        private val _logs = MutableStateFlow<List<MeasurementLog>>(emptyList())
        override val logs: StateFlow<List<MeasurementLog>> = _logs
        val recorded = mutableListOf<Pair<String, LogSeverity>>()
        override suspend fun addLog(message: String, severity: LogSeverity) {
            recorded.add(message to severity)
        }
    }

    // -------------------------------------------------------------------------
    // Publisher side (S22 Ultra)
    // -------------------------------------------------------------------------

    private lateinit var publisherController: RttPublisherController
    private lateinit var publisherRepository: PublisherRepositoryImpl

    // -------------------------------------------------------------------------
    // Receiver side (Pixel 9a)
    // -------------------------------------------------------------------------

    private lateinit var scanCoordinator: RttScanCoordinator
    private lateinit var measurementEngine: RttMeasurementEngine
    private lateinit var receiverRepository: ReceiverRepositoryImpl

    // The Aware peer that represents the S22 Ultra as seen by the Pixel 9a
    private val s22AwarePeerId = "s22ultra-peer"
    private val s22AsPublisherDevice = PublisherDevice(
        id = s22AwarePeerId,
        name = s22AwarePeerId,
        connectionStatus = ConnectionStatus.Disconnected,
        status = PublisherStatus.Waiting,
        lastMeasuredDistanceMeters = null,
        lastRssiDbm = null,
        lastMeasurementTimestampMillis = null,
        awarePeerId = s22AwarePeerId,
    )

    @Before
    fun setUp() {
        // Publisher side
        publisherController = mockk()
        every { publisherController.buildInitialState() } returns PublisherState.Initial
        publisherRepository = PublisherRepositoryImpl(publisherController, fakeLog)

        // Receiver side
        scanCoordinator = mockk()
        measurementEngine = mockk()
        receiverRepository = ReceiverRepositoryImpl(scanCoordinator, measurementEngine, fakeLog)
    }

    // -------------------------------------------------------------------------
    // Helper: build a measurement result for the S22 peer
    // -------------------------------------------------------------------------

    private fun awareResult(
        distanceMeters: Double? = 3.14,
        status: MeasurementStatus = MeasurementStatus.Success,
        failureReason: RttFailureReason = RttFailureReason.None,
    ) = MeasurementResult(
        timestampMillis = System.currentTimeMillis(),
        publisherId = s22AwarePeerId,
        publisherName = s22AwarePeerId,
        distanceMeters = distanceMeters,
        distanceStandardDeviationMeters = if (status == MeasurementStatus.Success) 0.12 else null,
        rssiDbm = if (status == MeasurementStatus.Success) -58 else null,
        status = status,
        failureReason = failureReason,
        roundNumber = 1,
        measurementNumber = 1,
    )

    // =========================================================================
    // 1. Publisher lifecycle
    // =========================================================================

    @Test
    fun `S22 starts publishing and enters Waiting state`() = runTest {
        val waitingState = PublisherState.Initial.copy(
            currentStatus = PublisherStatus.Waiting,
            connectionStatus = ConnectionStatus.Connected,
            isWaitingForRttRequests = true,
        )
        every { publisherController.startPublishing(any()) } returns waitingState

        publisherRepository.startPublishing()

        assertEquals(PublisherStatus.Waiting, publisherRepository.publisherState.value.currentStatus)
        assertTrue(publisherRepository.publisherState.value.isWaitingForRttRequests)
    }

    @Test
    fun `S22 stops publishing and enters Offline state`() = runTest {
        val stoppedState = PublisherState.Initial.copy(
            currentStatus = PublisherStatus.Offline,
            connectionStatus = ConnectionStatus.Disconnected,
            isWaitingForRttRequests = false,
        )
        every { publisherController.startPublishing(any()) } returns PublisherState.Initial.copy(
            currentStatus = PublisherStatus.Waiting,
            connectionStatus = ConnectionStatus.Connected,
            isWaitingForRttRequests = true,
        )
        every { publisherController.stopPublishing(any()) } returns stoppedState

        publisherRepository.startPublishing()
        publisherRepository.stopPublishing()

        assertEquals(PublisherStatus.Offline, publisherRepository.publisherState.value.currentStatus)
        assertEquals(ConnectionStatus.Disconnected, publisherRepository.publisherState.value.connectionStatus)
    }

    // =========================================================================
    // 2. Receiver discovers S22 via Wi-Fi Aware (no AP scan needed)
    // =========================================================================

    @Test
    fun `Pixel discovers S22 as Aware peer and it appears in publisher list`() = runTest {
        // No RTT-capable APs in range — only the S22 via Aware
        coEvery { scanCoordinator.scanPublishers() } returns emptyList()
        receiverRepository.scanPublishers()

        // ViewModel would call syncPublishers() with the merged list after Aware discovery
        receiverRepository.syncPublishers(listOf(s22AsPublisherDevice))

        assertEquals(1, receiverRepository.receiverState.value.publishers.size)
        assertEquals(s22AwarePeerId, receiverRepository.receiverState.value.publishers[0].id)
        assertEquals(s22AwarePeerId, receiverRepository.receiverState.value.publishers[0].awarePeerId)
    }

    @Test
    fun `syncPublishers preserves existing selection state`() = runTest {
        coEvery { scanCoordinator.scanPublishers() } returns emptyList()
        receiverRepository.syncPublishers(listOf(s22AsPublisherDevice))
        receiverRepository.togglePublisherSelection(s22AwarePeerId)
        assertTrue(receiverRepository.receiverState.value.publishers[0].isSelected)

        // Sync again (e.g. Aware re-emits) — selection must survive
        receiverRepository.syncPublishers(listOf(s22AsPublisherDevice))
        assertTrue(receiverRepository.receiverState.value.publishers[0].isSelected)
    }

    // =========================================================================
    // 3. Successful RTT measurement (happy path)
    // =========================================================================

    @Test
    fun `Pixel measures S22 via Aware and gets distance`() = runTest {
        coEvery { measurementEngine.measurePublisher(any(), any(), any()) } returns awareResult()

        receiverRepository.syncPublishers(listOf(s22AsPublisherDevice))
        receiverRepository.measureAll()

        val measurements = receiverRepository.receiverState.value.measurements
        assertEquals(1, measurements.size)
        assertEquals(3.14, measurements[0].distanceMeters!!, 0.001)
        assertEquals(MeasurementStatus.Success, measurements[0].status)
    }

    @Test
    fun `successful measurement marks S22 as Connected`() = runTest {
        coEvery { measurementEngine.measurePublisher(any(), any(), any()) } returns awareResult()

        receiverRepository.syncPublishers(listOf(s22AsPublisherDevice))
        receiverRepository.measureAll()

        assertEquals(
            ConnectionStatus.Connected,
            receiverRepository.receiverState.value.publishers[0].connectionStatus,
        )
    }

    @Test
    fun `successful measurement updates last distance on publisher`() = runTest {
        coEvery { measurementEngine.measurePublisher(any(), any(), any()) } returns awareResult(distanceMeters = 2.71)

        receiverRepository.syncPublishers(listOf(s22AsPublisherDevice))
        receiverRepository.measureAll()

        assertEquals(2.71, receiverRepository.receiverState.value.publishers[0].lastMeasuredDistanceMeters!!, 0.001)
    }

    @Test
    fun `dashboard stats are updated after successful measurement`() = runTest {
        coEvery { measurementEngine.measurePublisher(any(), any(), any()) } returns awareResult(distanceMeters = 5.0)

        receiverRepository.syncPublishers(listOf(s22AsPublisherDevice))
        receiverRepository.measureAll()

        val stats = receiverRepository.receiverState.value.dashboardStats
        assertEquals(5.0, stats.averageDistanceMeters!!, 0.001)
        assertEquals(1L, stats.totalMeasurements)
        assertEquals(1, stats.activePublishers)
    }

    // =========================================================================
    // 4. Failed RTT measurement paths
    // =========================================================================

    @Test
    fun `STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC marks S22 Unreachable`() = runTest {
        coEvery { measurementEngine.measurePublisher(any(), any(), any()) } returns awareResult(
            distanceMeters = null,
            status = MeasurementStatus.Failed,
            failureReason = RttFailureReason.ResponderUnavailable,
        )

        receiverRepository.syncPublishers(listOf(s22AsPublisherDevice))
        receiverRepository.measureAll()

        assertEquals(
            ConnectionStatus.Unreachable,
            receiverRepository.receiverState.value.publishers[0].connectionStatus,
        )
        assertEquals(
            RttFailureReason.ResponderUnavailable,
            receiverRepository.receiverState.value.measurements[0].failureReason,
        )
    }

    @Test
    fun `STATUS_FAIL logs warning with publisher name`() = runTest {
        coEvery { measurementEngine.measurePublisher(any(), any(), any()) } returns awareResult(
            distanceMeters = null,
            status = MeasurementStatus.Failed,
            failureReason = RttFailureReason.ApiFailure,
        )

        receiverRepository.syncPublishers(listOf(s22AsPublisherDevice))
        receiverRepository.measureAll()

        assertTrue(fakeLog.recorded.any { (msg, sev) ->
            sev == LogSeverity.Warning && msg.contains(s22AwarePeerId)
        })
    }

    @Test
    fun `getPeerHandle null logs error and returns ResponderUnavailable`() = runTest {
        // Engine returns ResponderUnavailable when PeerHandle is null (no Aware session)
        coEvery { measurementEngine.measurePublisher(any(), any(), any()) } returns awareResult(
            distanceMeters = null,
            status = MeasurementStatus.Failed,
            failureReason = RttFailureReason.ResponderUnavailable,
        )

        receiverRepository.syncPublishers(listOf(s22AsPublisherDevice))
        receiverRepository.measureAll()

        assertEquals(
            RttFailureReason.ResponderUnavailable,
            receiverRepository.receiverState.value.measurements[0].failureReason,
        )
    }

    // =========================================================================
    // 5. Multiple rounds
    // =========================================================================

    @Test
    fun `three consecutive rounds accumulate measurements`() = runTest {
        coEvery { measurementEngine.measurePublisher(any(), any(), any()) } returnsMany listOf(
            awareResult(distanceMeters = 1.0),
            awareResult(distanceMeters = 2.0),
            awareResult(distanceMeters = 3.0),
        )

        receiverRepository.syncPublishers(listOf(s22AsPublisherDevice))
        receiverRepository.measureAll()
        receiverRepository.measureAll()
        receiverRepository.measureAll()

        assertEquals(3, receiverRepository.receiverState.value.measurements.size)
        assertEquals(3L, receiverRepository.receiverState.value.currentRoundNumber)
    }

    @Test
    fun `round number increments on each measureAll call`() = runTest {
        coEvery { measurementEngine.measurePublisher(any(), any(), any()) } returns awareResult()

        receiverRepository.syncPublishers(listOf(s22AsPublisherDevice))
        assertEquals(0L, receiverRepository.receiverState.value.currentRoundNumber)
        receiverRepository.measureAll()
        assertEquals(1L, receiverRepository.receiverState.value.currentRoundNumber)
        receiverRepository.measureAll()
        assertEquals(2L, receiverRepository.receiverState.value.currentRoundNumber)
    }

    // =========================================================================
    // 6. Measure Selected (only checked publisher is measured)
    // =========================================================================

    @Test
    fun `measureSelected only ranges to selected S22 peer`() = runTest {
        val pixel = PublisherDevice(
            id = "pixel-peer",
            name = "pixel-peer",
            connectionStatus = ConnectionStatus.Disconnected,
            status = PublisherStatus.Waiting,
            lastMeasuredDistanceMeters = null,
            lastRssiDbm = null,
            lastMeasurementTimestampMillis = null,
            awarePeerId = "pixel-peer",
        )
        coEvery { measurementEngine.measurePublisher(any(), any(), any()) } returns awareResult()

        receiverRepository.syncPublishers(listOf(s22AsPublisherDevice, pixel))
        receiverRepository.togglePublisherSelection(s22AwarePeerId) // select only S22
        receiverRepository.measureSelected()

        coVerify(exactly = 1) { measurementEngine.measurePublisher(any(), any(), any()) }
    }

    // =========================================================================
    // 7. Publisher records RTT request received
    // =========================================================================

    @Test
    fun `S22 records RTT request and increments counter`() = runTest {
        publisherRepository.recordRttRequestReceived()
        assertEquals(1L, publisherRepository.publisherState.value.requestsReceived)
    }

    @Test
    fun `S22 records multiple RTT requests`() = runTest {
        repeat(3) { publisherRepository.recordRttRequestReceived() }
        assertEquals(3L, publisherRepository.publisherState.value.requestsReceived)
    }

    // =========================================================================
    // 8. Stop mid-session
    // =========================================================================

    @Test
    fun `stopMeasurements halts session and clears isMeasuring`() = runTest {
        receiverRepository.stopMeasurements()
        assertTrue(!receiverRepository.receiverState.value.isMeasuring)
        assertTrue(!receiverRepository.receiverState.value.isScanning)
    }
}
