package com.example.wifirttmeasurement.domain.usecase

import com.example.wifirttmeasurement.domain.model.ConnectionStatus
import com.example.wifirttmeasurement.domain.model.DashboardStats
import com.example.wifirttmeasurement.domain.model.MeasurementResult
import com.example.wifirttmeasurement.domain.model.MeasurementStatus
import com.example.wifirttmeasurement.domain.model.PublisherDevice
import com.example.wifirttmeasurement.domain.model.PublisherStatus
import com.example.wifirttmeasurement.domain.model.ReceiverState
import com.example.wifirttmeasurement.domain.model.RttFailureReason
import com.example.wifirttmeasurement.domain.repository.ReceiverRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UseCaseTest {

    private lateinit var receiverRepository: ReceiverRepository
    private val initialState = ReceiverState.Initial

    @Before
    fun setUp() {
        receiverRepository = mockk(relaxed = true)
        every { receiverRepository.receiverState } returns MutableStateFlow(initialState)
    }

    @Test
    fun `ScanPublishersUseCase delegates to repository`() = runTest {
        val useCase = ScanPublishersUseCase(receiverRepository)
        useCase()
        coVerify { receiverRepository.scanPublishers() }
    }

    @Test
    fun `MeasureAllPublishersUseCase delegates to repository`() = runTest {
        val useCase = MeasureAllPublishersUseCase(receiverRepository)
        useCase()
        coVerify { receiverRepository.measureAll() }
    }

    @Test
    fun `MeasureSelectedPublishersUseCase delegates to repository`() = runTest {
        val useCase = MeasureSelectedPublishersUseCase(receiverRepository)
        useCase()
        coVerify { receiverRepository.measureSelected() }
    }

    @Test
    fun `StopMeasurementSessionUseCase delegates to repository`() = runTest {
        val useCase = StopMeasurementSessionUseCase(receiverRepository)
        useCase()
        coVerify { receiverRepository.stopMeasurements() }
    }

    @Test
    fun `TogglePublisherSelectionUseCase delegates with correct id`() = runTest {
        val useCase = TogglePublisherSelectionUseCase(receiverRepository)
        useCase("aa:bb:cc:dd:ee:ff")
        coVerify { receiverRepository.togglePublisherSelection("aa:bb:cc:dd:ee:ff") }
    }

    @Test
    fun `ObserveReceiverStateUseCase returns repository state flow`() = runTest {
        val stateFlow = MutableStateFlow(initialState)
        every { receiverRepository.receiverState } returns stateFlow
        val useCase = ObserveReceiverStateUseCase(receiverRepository)
        assertEquals(stateFlow, useCase())
    }
}
