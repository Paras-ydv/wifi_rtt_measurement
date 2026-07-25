package com.example.wifirttmeasurement.domain.usecase

import com.example.wifirttmeasurement.domain.model.ConnectionStatus
import com.example.wifirttmeasurement.domain.model.DeviceCapability
import com.example.wifirttmeasurement.domain.model.LogSeverity
import com.example.wifirttmeasurement.domain.model.MeasurementLog
import com.example.wifirttmeasurement.domain.model.PublisherState
import com.example.wifirttmeasurement.domain.model.PublisherStatus
import com.example.wifirttmeasurement.domain.model.RttFailureReason
import com.example.wifirttmeasurement.domain.repository.DeviceCapabilityRepository
import com.example.wifirttmeasurement.domain.repository.LogRepository
import com.example.wifirttmeasurement.domain.repository.PublisherRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PublisherUseCaseTest {

    private lateinit var publisherRepository: PublisherRepository

    @Before
    fun setUp() {
        publisherRepository = mockk(relaxed = true)
        every { publisherRepository.publisherState } returns MutableStateFlow(PublisherState.Initial)
    }

    @Test
    fun `StartPublishingUseCase delegates to repository`() = runTest {
        val useCase = StartPublishingUseCase(publisherRepository)
        useCase()
        coVerify { publisherRepository.startPublishing() }
    }

    @Test
    fun `StopPublishingUseCase delegates to repository`() = runTest {
        val useCase = StopPublishingUseCase(publisherRepository)
        useCase()
        coVerify { publisherRepository.stopPublishing() }
    }

    @Test
    fun `RefreshPublisherStatusUseCase delegates to repository`() = runTest {
        val useCase = RefreshPublisherStatusUseCase(publisherRepository)
        useCase()
        coVerify { publisherRepository.refreshStatus() }
    }

    @Test
    fun `ObservePublisherStatusUseCase returns repository state flow`() {
        val stateFlow = MutableStateFlow(PublisherState.Initial)
        every { publisherRepository.publisherState } returns stateFlow
        val useCase = ObservePublisherStatusUseCase(publisherRepository)
        assertEquals(stateFlow, useCase())
    }
}

class ObserveLogsUseCaseTest {

    @Test
    fun `ObserveLogsUseCase returns log state flow`() {
        val logFlow = MutableStateFlow<List<MeasurementLog>>(emptyList())
        val logRepository = object : LogRepository {
            override val logs: StateFlow<List<MeasurementLog>> = logFlow
            override suspend fun addLog(message: String, severity: LogSeverity) {}
        }
        val useCase = ObserveLogsUseCase(logRepository)
        assertEquals(logFlow, useCase())
    }
}

class CheckDeviceCapabilitiesUseCaseTest {

    @Test
    fun `returns capability from repository`() {
        val capability = DeviceCapability(
            isRttAvailable = true,
            canActAsReceiver = true,
            canActAsPublisher = false,
        )
        val repo = mockk<DeviceCapabilityRepository>()
        every { repo.checkCapabilities() } returns capability
        val result = CheckDeviceCapabilitiesUseCase(repo)()
        assertTrue(result.isRttAvailable)
        assertTrue(result.canActAsReceiver)
        assertFalse(result.canActAsPublisher)
    }

    @Test
    fun `returns Unknown capability when unavailable`() {
        val repo = mockk<DeviceCapabilityRepository>()
        every { repo.checkCapabilities() } returns DeviceCapability.Unknown
        val result = CheckDeviceCapabilitiesUseCase(repo)()
        assertFalse(result.isRttAvailable)
        assertEquals(RttFailureReason.Unknown, result.failureReason)
    }
}
