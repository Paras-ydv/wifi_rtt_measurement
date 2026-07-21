package com.example.wifirttmeasurement.data.rtt

import android.net.wifi.ScanResult
import com.example.wifirttmeasurement.domain.model.ConnectionStatus
import com.example.wifirttmeasurement.domain.model.PublisherDevice
import com.example.wifirttmeasurement.domain.model.PublisherStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RttScanCoordinator @Inject constructor(
    private val androidRttManager: AndroidRttManager,
) {
    // Cache BSSID -> ScanResult so the measurement engine can look up the AP.
    private val scanCache = mutableMapOf<String, ScanResult>()

    suspend fun scanPublishers(): List<PublisherDevice> {
        val results = androidRttManager.scanRttCapableAps()
        scanCache.clear()
        results.forEach { scanCache[it.BSSID] = it }
        return results.map { it.toPublisherDevice() }
    }

    fun getScanResult(publisherId: String): ScanResult? = scanCache[publisherId]

    private fun ScanResult.toPublisherDevice() = PublisherDevice(
        id = BSSID,
        name = SSID.ifBlank { BSSID },
        connectionStatus = ConnectionStatus.Disconnected,
        status = PublisherStatus.Waiting,
        lastMeasuredDistanceMeters = null,
        lastRssiDbm = level,
        lastMeasurementTimestampMillis = null,
        isSelected = false,
    )
}
