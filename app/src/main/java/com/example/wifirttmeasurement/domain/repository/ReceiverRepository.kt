package com.example.wifirttmeasurement.domain.repository

import com.example.wifirttmeasurement.domain.model.ReceiverState
import kotlinx.coroutines.flow.StateFlow

interface ReceiverRepository {
    val receiverState: StateFlow<ReceiverState>

    suspend fun scanPublishers()

    suspend fun syncPublishers(publishers: List<com.example.wifirttmeasurement.domain.model.PublisherDevice>)

    suspend fun togglePublisherSelection(publisherId: String)

    suspend fun measureSelected()

    suspend fun measureAll()

    suspend fun stopMeasurements()
}
