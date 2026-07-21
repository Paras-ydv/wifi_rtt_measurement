package com.example.wifirttmeasurement.domain.model

data class DeviceCapability(
    val isRttAvailable: Boolean,
    val canActAsReceiver: Boolean,
    val canActAsPublisher: Boolean,
    val failureReason: RttFailureReason? = null,
) {
    companion object {
        val Unknown = DeviceCapability(
            isRttAvailable = false,
            canActAsReceiver = false,
            canActAsPublisher = false,
            failureReason = RttFailureReason.Unknown,
        )
    }
}
