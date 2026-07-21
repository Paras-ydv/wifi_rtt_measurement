package com.example.wifirttmeasurement.data.rtt

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import com.example.wifirttmeasurement.domain.model.ConnectionStatus
import com.example.wifirttmeasurement.domain.model.PublisherState
import com.example.wifirttmeasurement.domain.model.PublisherStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RttPublisherController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val capabilityChecker: RttCapabilityChecker,
) {
    fun buildInitialState(): PublisherState {
        return PublisherState.Initial.copy(
            deviceName = deviceName(),
            publisherId = publisherId(),
            batteryPercentage = batteryPercentage(),
        )
    }

    fun startPublishing(currentState: PublisherState): PublisherState {
        val capability = capabilityChecker.checkCapabilities()
        if (!capability.canActAsPublisher) {
            return currentState.copy(
                currentStatus = PublisherStatus.Offline,
                connectionStatus = ConnectionStatus.Unreachable,
                isWaitingForRttRequests = false,
                batteryPercentage = batteryPercentage(),
            )
        }

        return currentState.copy(
            deviceName = deviceName(),
            publisherId = publisherId(),
            currentStatus = PublisherStatus.Waiting,
            connectionStatus = ConnectionStatus.Connected,
            isWaitingForRttRequests = true,
            batteryPercentage = batteryPercentage(),
        )
    }

    fun stopPublishing(currentState: PublisherState): PublisherState {
        return currentState.copy(
            currentStatus = PublisherStatus.Offline,
            connectionStatus = ConnectionStatus.Disconnected,
            isWaitingForRttRequests = false,
            batteryPercentage = batteryPercentage(),
        )
    }

    fun refresh(currentState: PublisherState): PublisherState {
        return currentState.copy(
            deviceName = deviceName(),
            publisherId = publisherId(),
            batteryPercentage = batteryPercentage(),
        )
    }

    private fun deviceName(): String {
        val model = Build.MODEL.orEmpty().ifBlank { "Android Device" }
        val manufacturer = Build.MANUFACTURER.orEmpty()
        return if (manufacturer.isBlank() || model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    private fun publisherId(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        )
        return "pub-${androidId.takeLast(8)}"
    }

    private fun batteryPercentage(): Int? {
        val batteryManager = context.getSystemService(BatteryManager::class.java) ?: return null
        val percentage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return percentage.takeIf { it in 0..100 }
    }
}
