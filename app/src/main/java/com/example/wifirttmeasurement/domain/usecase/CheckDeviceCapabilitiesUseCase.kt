package com.example.wifirttmeasurement.domain.usecase

import com.example.wifirttmeasurement.domain.model.DeviceCapability
import com.example.wifirttmeasurement.domain.repository.DeviceCapabilityRepository
import javax.inject.Inject

class CheckDeviceCapabilitiesUseCase @Inject constructor(
    private val deviceCapabilityRepository: DeviceCapabilityRepository,
) {
    operator fun invoke(): DeviceCapability {
        return deviceCapabilityRepository.checkCapabilities()
    }
}
