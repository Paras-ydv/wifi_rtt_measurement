package com.example.wifirttmeasurement.data.repository

import com.example.wifirttmeasurement.data.rtt.RttCapabilityChecker
import com.example.wifirttmeasurement.domain.model.DeviceCapability
import com.example.wifirttmeasurement.domain.repository.DeviceCapabilityRepository
import javax.inject.Inject

class DeviceCapabilityRepositoryImpl @Inject constructor(
    private val rttCapabilityChecker: RttCapabilityChecker,
) : DeviceCapabilityRepository {
    override fun checkCapabilities(): DeviceCapability {
        return rttCapabilityChecker.checkCapabilities()
    }
}
