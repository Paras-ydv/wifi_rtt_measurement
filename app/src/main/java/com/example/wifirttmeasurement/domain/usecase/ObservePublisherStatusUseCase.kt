package com.example.wifirttmeasurement.domain.usecase

import com.example.wifirttmeasurement.domain.model.PublisherState
import com.example.wifirttmeasurement.domain.repository.PublisherRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObservePublisherStatusUseCase @Inject constructor(
    private val publisherRepository: PublisherRepository,
) {
    operator fun invoke(): StateFlow<PublisherState> {
        return publisherRepository.publisherState
    }
}
