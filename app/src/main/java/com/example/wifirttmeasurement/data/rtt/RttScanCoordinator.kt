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
    private val wifiAwareDiscoveryManager: WifiAwareDiscoveryManager,
) {
    // Cache BSSID -> ScanResult so the measurement engine can look up the AP.
    private val scanCache = mutableMapOf<String, ScanResult>()

    suspend fun scanPublishers(): List<PublisherDevice> {
        val apResults = androidRttManager.scanRttCapableAps()
        scanCache.clear()
        apResults.forEach { scanCache[it.BSSID] = it }
        val apDevices = apResults.map { it.toPublisherDevice() }

        // Merge in any active Wi-Fi Aware peers (phone-to-phone) that aren't already in the AP list
        val apIds = apDevices.map { it.id }.toSet()
        val awareDevices = wifiAwareDiscoveryManager.state.value.activePeers
            .filter { it.peerId !in apIds }
            .map { peer ->
                PublisherDevice(
                    id = peer.peerId,
                    name = peer.peerId,
                    connectionStatus = ConnectionStatus.Disconnected,
                    status = PublisherStatus.Waiting,
                    lastMeasuredDistanceMeters = null,
                    lastRssiDbm = null,
                    lastMeasurementTimestampMillis = null,
                    awarePeerId = peer.peerId,
                )
            }

        return apDevices + awareDevices
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
