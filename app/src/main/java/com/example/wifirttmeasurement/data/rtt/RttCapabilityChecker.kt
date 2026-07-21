package com.example.wifirttmeasurement.data.rtt

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import com.example.wifirttmeasurement.domain.model.DeviceCapability
import com.example.wifirttmeasurement.domain.model.RttFailureReason
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class RttCapabilityChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun checkCapabilities(): DeviceCapability {
        val packageManager = context.packageManager
        val hasRttFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)
        val wifiRttManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.getSystemService(WifiRttManager::class.java)
        } else {
            null
        }
        val isRttAvailable = hasRttFeature && wifiRttManager?.isAvailable == true

        return DeviceCapability(
            isRttAvailable = isRttAvailable,
            canActAsReceiver = isRttAvailable,
            canActAsPublisher = isRttAvailable,
            failureReason = if (isRttAvailable) null else RttFailureReason.RttUnsupported,
        )
    }
}
