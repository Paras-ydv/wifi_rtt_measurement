package com.example.wifirttmeasurement.data.rtt

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.wifirttmeasurement.domain.model.MeasurementResult
import com.example.wifirttmeasurement.domain.model.MeasurementStatus
import com.example.wifirttmeasurement.domain.model.PublisherDevice
import com.example.wifirttmeasurement.domain.model.RttFailureReason
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class AndroidRttManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val wifiManager: WifiManager? =
        context.getSystemService(WifiManager::class.java)

    private val wifiRttManager: WifiRttManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            context.getSystemService(WifiRttManager::class.java)
        else null

    val isRttAvailable: Boolean
        get() = wifiRttManager?.isAvailable == true

    /** Triggers a Wi-Fi scan and returns RTT-capable [ScanResult]s. */
    suspend fun scanRttCapableAps(): List<ScanResult> {
        if (!hasLocationPermission()) return emptyList()

        val cached = wifiManager?.scanResults
            ?.filter { it.is80211mcResponder }
            ?: emptyList()

        if (cached.isNotEmpty()) return cached

        return suspendCancellableCoroutine { cont ->
            var receiverRegistered = false
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    receiverRegistered = false
                    context.unregisterReceiver(this)
                    val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    val results = if (success) {
                        wifiManager?.scanResults?.filter { it.is80211mcResponder } ?: emptyList()
                    } else {
                        emptyList()
                    }
                    cont.resume(results)
                }
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Context.RECEIVER_NOT_EXPORTED
            } else {
                0
            }
            context.registerReceiver(
                receiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                flags,
            )
            receiverRegistered = true
            cont.invokeOnCancellation {
                if (receiverRegistered) {
                    receiverRegistered = false
                    runCatching { context.unregisterReceiver(receiver) }
                }
            }
            val started = wifiManager?.startScan() ?: false
            if (!started) {
                if (receiverRegistered) {
                    receiverRegistered = false
                    context.unregisterReceiver(receiver)
                }
                cont.resume(emptyList())
            }
        }
    }

    /** Ranges to a single [ScanResult] responder and returns the [RangingResult]. */
    suspend fun range(scanResult: ScanResult, publisher: PublisherDevice): MeasurementResult {
        val rttManager = wifiRttManager
            ?: return unsupportedResult(publisher)

        if (!hasLocationPermission()) {
            return MeasurementResult(
                timestampMillis = System.currentTimeMillis(),
                publisherId = publisher.id,
                publisherName = publisher.name,
                distanceMeters = null,
                distanceStandardDeviationMeters = null,
                rssiDbm = null,
                status = MeasurementStatus.PermissionDenied,
                failureReason = RttFailureReason.PermissionDenied,
                roundNumber = 0,
                measurementNumber = 0,
            )
        }

        return suspendCancellableCoroutine { cont ->
            val request = RangingRequest.Builder()
                .addAccessPoint(scanResult)
                .build()

            val executor = Executor { runnable -> runnable.run() }

            rttManager.startRanging(
                request,
                executor,
                object : RangingResultCallback() {
                    override fun onRangingResults(results: List<RangingResult>) {
                        val result = results.firstOrNull()
                        if (result == null) {
                            cont.resume(unsupportedResult(publisher))
                            return
                        }
                        val success = result.status == RangingResult.STATUS_SUCCESS
                        cont.resume(
                            MeasurementResult(
                                timestampMillis = System.currentTimeMillis(),
                                publisherId = publisher.id,
                                publisherName = publisher.name,
                                distanceMeters = if (success) result.distanceMm / 1000.0 else null,
                                distanceStandardDeviationMeters = if (success) result.distanceStdDevMm / 1000.0 else null,
                                rssiDbm = if (success) result.rssi else publisher.lastRssiDbm,
                                status = if (success) MeasurementStatus.Success else MeasurementStatus.Failed,
                                failureReason = if (success) RttFailureReason.None else apiStatusToReason(result.status),
                                roundNumber = 0,
                                measurementNumber = 0,
                            ),
                        )
                    }

                    override fun onRangingFailure(code: Int) {
                        cont.resume(
                            MeasurementResult(
                                timestampMillis = System.currentTimeMillis(),
                                publisherId = publisher.id,
                                publisherName = publisher.name,
                                distanceMeters = null,
                                distanceStandardDeviationMeters = null,
                                rssiDbm = publisher.lastRssiDbm,
                                status = MeasurementStatus.Failed,
                                failureReason = callbackCodeToReason(code),
                                roundNumber = 0,
                                measurementNumber = 0,
                            ),
                        )
                    }
                },
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED
    }

    private fun unsupportedResult(publisher: PublisherDevice) = MeasurementResult(
        timestampMillis = System.currentTimeMillis(),
        publisherId = publisher.id,
        publisherName = publisher.name,
        distanceMeters = null,
        distanceStandardDeviationMeters = null,
        rssiDbm = publisher.lastRssiDbm,
        status = MeasurementStatus.Unsupported,
        failureReason = RttFailureReason.RttUnsupported,
        roundNumber = 0,
        measurementNumber = 0,
    )

    private fun apiStatusToReason(status: Int): RttFailureReason = when (status) {
        RangingResult.STATUS_FAIL -> RttFailureReason.ApiFailure
        RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC -> RttFailureReason.ResponderUnavailable
        else -> RttFailureReason.Unknown
    }

    private fun callbackCodeToReason(code: Int): RttFailureReason = when (code) {
        RangingResultCallback.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE -> RttFailureReason.RttUnsupported
        else -> RttFailureReason.ApiFailure
    }
}
