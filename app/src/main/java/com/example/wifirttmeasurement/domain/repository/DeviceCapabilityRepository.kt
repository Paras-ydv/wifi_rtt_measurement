package com.example.wifirttmeasurement.domain.repository

import com.example.wifirttmeasurement.domain.model.DeviceCapability

interface DeviceCapabilityRepository {
    fun checkCapabilities(): DeviceCapability
}
