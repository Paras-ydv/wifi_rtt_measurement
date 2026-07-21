package com.example.wifirttmeasurement.domain.usecase

import com.example.wifirttmeasurement.domain.model.ReceiverState
import com.example.wifirttmeasurement.domain.repository.ReceiverRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class ObserveReceiverStateUseCase @Inject constructor(
    private val receiverRepository: ReceiverRepository,
) {
    operator fun invoke(): StateFlow<ReceiverState> {
        return receiverRepository.receiverState
    }
}
