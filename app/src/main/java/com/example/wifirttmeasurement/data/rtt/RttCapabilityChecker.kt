package com.example.wifirttmeasurement.data.rtt

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.aware.WifiAwareManager
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
        val pm = context.packageManager
        val hasRttFeature = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)
        val hasAwareFeature = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)

        val wifiRttManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            context.getSystemService(WifiRttManager::class.java) else null
        val wifiAwareManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.getSystemService(WifiAwareManager::class.java) else null

        val isRttAvailable = hasRttFeature && wifiRttManager?.isAvailable == true
        val isAwareAvailable = hasAwareFeature && wifiAwareManager?.isAvailable == true

        return DeviceCapability(
            isRttAvailable = isRttAvailable,
            canActAsReceiver = isRttAvailable,
            canActAsPublisher = isRttAvailable && isAwareAvailable,
            failureReason = when {
                !isRttAvailable -> RttFailureReason.RttUnsupported
                !isAwareAvailable -> RttFailureReason.ResponderUnavailable
                else -> null
            },
        )
    }
}
