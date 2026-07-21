package com.example.wifirttmeasurement.domain.usecase

import com.example.wifirttmeasurement.domain.repository.ReceiverRepository
import javax.inject.Inject

class ScanPublishersUseCase @Inject constructor(
    private val receiverRepository: ReceiverRepository,
) {
    suspend operator fun invoke() {
        receiverRepository.scanPublishers()
    }
}
