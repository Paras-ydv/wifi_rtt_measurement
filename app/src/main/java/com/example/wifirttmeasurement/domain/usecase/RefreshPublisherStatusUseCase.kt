package com.example.wifirttmeasurement.domain.usecase

import com.example.wifirttmeasurement.domain.repository.PublisherRepository
import javax.inject.Inject

class RefreshPublisherStatusUseCase @Inject constructor(
    private val publisherRepository: PublisherRepository,
) {
    suspend operator fun invoke() {
        publisherRepository.refreshStatus()
    }
}
