package com.example.wifirttmeasurement.data.rtt

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.aware.PeerHandle
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.wifirttmeasurement.domain.model.MeasurementResult
import com.example.wifirttmeasurement.domain.model.MeasurementStatus
import com.example.wifirttmeasurement.domain.model.PublisherDevice
import com.example.wifirttmeasurement.domain.model.RttFailureReason
import android.util.Log
import com.example.wifirttmeasurement.domain.model.LogSeverity
import com.example.wifirttmeasurement.domain.repository.LogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class AndroidRttManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logRepository: LogRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val rttExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
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

        return withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
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
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0
                context.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), flags)
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
        } ?: emptyList()
    }

    /** Ranges to a Wi-Fi Aware [PeerHandle] (phone-to-phone) and returns the result. */
    suspend fun rangeAwarePeer(peerHandle: PeerHandle, publisher: PublisherDevice): MeasurementResult {
        val rttManager = wifiRttManager ?: return unsupportedResult(publisher)
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

        // Log device + RTT availability context once per call to aid diagnosis
        Log.d(TAG, "rangeAwarePeer() diagnostics:" +
            " handle=${peerHandle.hashCode()}" +
            " publisher=${publisher.id}" +
            " rttAvailable=${rttManager.isAvailable}" +
            " sdk=${Build.VERSION.SDK_INT}" +
            " device=${Build.MANUFACTURER} ${Build.MODEL}")
        uiLog("[RTT] startRanging → handle=${peerHandle.hashCode()} id=${publisher.id} rttAvail=${rttManager.isAvailable}")

        return withTimeoutOrNull(RTT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val request = RangingRequest.Builder()
                    .addWifiAwarePeer(peerHandle)
                    .setRttBurstSize(RangingRequest.getMaxRttBurstSize())
                    .build()
                Log.d(TAG, "rangeAwarePeer() request built: burstSize=${RangingRequest.getMaxRttBurstSize()}")
                rttManager.startRanging(
                    request,
                    rttExecutor,
                    object : RangingResultCallback() {
                        override fun onRangingResults(results: List<RangingResult>) {
                            val result = results.firstOrNull()
                            if (result == null) {
                                Log.w(TAG, "rangeAwarePeer: empty results list")
                                return cont.resume(unsupportedResult(publisher))
                            }
                            val success = result.status == RangingResult.STATUS_SUCCESS
                            Log.d(TAG, "rangeAwarePeer result:" +
                                " status=${statusCodeName(result.status)}(${result.status})" +
                                " distanceMm=${if (success) result.distanceMm else -1}" +
                                " stdDevMm=${if (success) result.distanceStdDevMm else -1}" +
                                " rssi=${if (success) result.rssi else -999}" +
                                " numAttempted=${if (success) result.numAttemptedMeasurements else -1}" +
                                " numSuccessful=${if (success) result.numSuccessfulMeasurements else -1}" +
                                " peerHandle=${peerHandle.hashCode()}" +
                                " sdk=${Build.VERSION.SDK_INT}" +
                                " device=${Build.MODEL}")
                            val sev = if (success) LogSeverity.Info else LogSeverity.Error
                            val distanceLog = if (success) "${result.distanceMm}mm" else "n/a"
                            val rssiLog = if (success) "${result.rssi}" else "n/a"
                            uiLog("[RTT] Aware result: ${statusCodeName(result.status)} dist=$distanceLog rssi=$rssiLog", sev)
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
                            val codeName = failureCodeName(code)
                            Log.e(TAG, "rangeAwarePeer onRangingFailure: code=$codeName($code) device=${Build.MODEL}")
                            uiLog("[RTT] Aware onRangingFailure: $codeName($code)", LogSeverity.Error)
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
        } ?: run {
            Log.w(TAG, "rangeAwarePeer: timed out after ${RTT_TIMEOUT_MS}ms")
            unsupportedResult(publisher)
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

        Log.d(TAG, "range() → startRanging AP bssid=${scanResult.BSSID} publisher=${publisher.id}")
        uiLog("[RTT] startRanging → AP bssid=${scanResult.BSSID}")
        return withTimeoutOrNull(RTT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val request = RangingRequest.Builder()
                    .addAccessPoint(scanResult)
                    .build()
                rttManager.startRanging(
                    request,
                    rttExecutor,
                    object : RangingResultCallback() {
                        override fun onRangingResults(results: List<RangingResult>) {
                            Log.d(TAG, "range onRangingResults: count=${results.size}")
                            val result = results.firstOrNull()
                            if (result == null) {
                                Log.w(TAG, "range: empty results list")
                                cont.resume(unsupportedResult(publisher))
                                return
                            }
                            val statusName = statusCodeName(result.status)
                            val success = result.status == RangingResult.STATUS_SUCCESS
                            val distanceLog = if (success) "${result.distanceMm}mm" else "n/a"
                            val rssiLog = if (success) "${result.rssi}" else "n/a"
                            Log.d(TAG, "range result: status=$statusName(${result.status}) distanceMm=$distanceLog rssi=$rssiLog")
                            val sev = if (success) LogSeverity.Info else LogSeverity.Error
                            uiLog("[RTT] AP result: $statusName dist=$distanceLog rssi=$rssiLog", sev)
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
                            val codeName = failureCodeName(code)
                            Log.e(TAG, "range onRangingFailure: code=$codeName($code)")
                            uiLog("[RTT] AP onRangingFailure: $codeName($code)", LogSeverity.Error)
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
        } ?: run {
            Log.w(TAG, "range: timed out after ${RTT_TIMEOUT_MS}ms")
            unsupportedResult(publisher)
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

    private fun statusCodeName(status: Int): String = when (status) {
        RangingResult.STATUS_SUCCESS -> "STATUS_SUCCESS"
        RangingResult.STATUS_FAIL -> "STATUS_FAIL"
        RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC -> "STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC"
        else -> "STATUS_UNKNOWN"
    }

    private fun failureCodeName(code: Int): String = when (code) {
        RangingResultCallback.STATUS_CODE_FAIL -> "STATUS_CODE_FAIL"
        RangingResultCallback.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE -> "STATUS_CODE_FAIL_RTT_NOT_AVAILABLE"
        else -> "STATUS_CODE_UNKNOWN"
    }

    private fun uiLog(message: String, severity: LogSeverity = LogSeverity.Info) {
        Log.d(TAG, message)
        scope.launch { logRepository.addLog(message, severity) }
    }

    companion object {
        private const val TAG = "AndroidRttManager"
        private const val SCAN_TIMEOUT_MS = 5_000L
        private const val RTT_TIMEOUT_MS = 4_000L
    }
}