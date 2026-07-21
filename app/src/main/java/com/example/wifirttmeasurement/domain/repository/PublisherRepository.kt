package com.example.wifirttmeasurement.domain.repository

import com.example.wifirttmeasurement.domain.model.PublisherState
import kotlinx.coroutines.flow.StateFlow

interface PublisherRepository {
    val publisherState: StateFlow<PublisherState>

    suspend fun startPublishing()

    suspend fun stopPublishing()

    suspend fun refreshStatus()

    suspend fun recordRttRequestReceived()
}
