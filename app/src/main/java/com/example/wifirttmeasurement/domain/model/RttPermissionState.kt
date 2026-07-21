package com.example.wifirttmeasurement.domain.model

data class RttPermissionState(
    val hasFineLocation: Boolean,
    val hasNearbyWifiDevices: Boolean,
) {
    val allGranted: Boolean
        get() = hasFineLocation && hasNearbyWifiDevices

    companion object {
        val Denied = RttPermissionState(
            hasFineLocation = false,
            hasNearbyWifiDevices = false,
        )
    }
}
