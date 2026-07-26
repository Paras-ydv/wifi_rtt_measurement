package com.example.wifirttmeasurement.data.rtt

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.wifirttmeasurement.domain.model.AwareDiscoveryState
import com.example.wifirttmeasurement.domain.model.AwarePeer
import com.example.wifirttmeasurement.domain.model.AwareSessionStatus
import com.example.wifirttmeasurement.domain.model.LogSeverity
import com.example.wifirttmeasurement.domain.repository.LogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manages Wi-Fi Aware peer discovery for phone-to-phone RTT ranging.
 *
 * Peer lifecycle (single canonical entry per physical device):
 *
 *   1. Subscriber fires onServiceDiscovered(handle=H) → peer created with
 *      peerId="peer-H" and peerHandle=H (subscribe-session handle).
 *   2. Subscriber sends its own device ID to H.
 *   3. Publisher receives the ID message and replies with its own device ID.
 *   4. Subscriber fires onMessageReceived(handle=H, text="id:XXXX") →
 *      existing peer (found by handle H) is updated: peerId = "XXXX".
 *
 * The subscribe-session handle H is the ONLY handle stored and used for RTT.
 * The publish-session handle is NEVER stored — it is a different object and
 * invalid for ranging.
 */
@Singleton
class WifiAwareDiscoveryManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logRepository: LogRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val wifiAwareManager: WifiAwareManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.getSystemService(WifiAwareManager::class.java)
        else null

    private val _state = MutableStateFlow(AwareDiscoveryState())
    val state: StateFlow<AwareDiscoveryState> = _state.asStateFlow()

    private var awareSession: WifiAwareSession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null

    private var publishActive = false
    private var subscribeActive = false
    private var availabilityReceiver: BroadcastReceiver? = null
    private val peerTimeoutMillis = 30_000L

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun start() {
        log("start() called — SDK=${Build.VERSION.SDK_INT} awareManager=${wifiAwareManager != null}")
        if (wifiAwareManager == null) {
            log("Wi-Fi Aware not supported on this device", LogSeverity.Error)
            _state.update { it.copy(status = AwareSessionStatus.Unavailable, errorMessage = "Wi-Fi Aware not supported") }
            return
        }
        if (!hasRequiredPermissions()) {
            _state.update { it.copy(status = AwareSessionStatus.Error, errorMessage = "Permissions denied") }
            return
        }
        log("isAvailable=${wifiAwareManager.isAvailable}")
        registerAvailabilityReceiver()
        if (wifiAwareManager.isAvailable) attach() else {
            log("Wi-Fi Aware not currently available — waiting for availability broadcast", LogSeverity.Warning)
            _state.update { it.copy(status = AwareSessionStatus.Unavailable) }
        }
    }

    fun stop() {
        log("Stopping Wi-Fi Aware discovery")
        unregisterAvailabilityReceiver()
        closeAllSessions()
        _state.update { AwareDiscoveryState(status = AwareSessionStatus.Idle) }
    }

    /**
     * Returns the subscribe-session PeerHandle for [peerId].
     * This is the only handle valid for RTT ranging.
     */
    fun getPeerHandle(peerId: String): PeerHandle? =
        _state.value.activePeers.firstOrNull { it.peerId == peerId }?.peerHandle

    // -------------------------------------------------------------------------
    // Attach
    // -------------------------------------------------------------------------

    private fun attach() {
        if (awareSession != null) { log("attach() skipped — session already active"); return }
        log("attach() → calling WifiAwareManager.attach()")
        _state.update { it.copy(status = AwareSessionStatus.Attaching, errorMessage = null) }

        wifiAwareManager?.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                log("onAttached() ✓ session=$session")
                awareSession = session
                _state.update { it.copy(status = AwareSessionStatus.Attached) }
                startPublish(session)
                startSubscribe(session)
            }

            override fun onAttachFailed() {
                log("onAttachFailed() — check NEARBY_WIFI_DEVICES + location permissions", LogSeverity.Error)
                _state.update { it.copy(status = AwareSessionStatus.Error, errorMessage = "Attach failed") }
                mainHandler.postDelayed({ attach() }, RETRY_DELAY_MS)
            }

            override fun onAwareSessionTerminated() {
                log("onAwareSessionTerminated() — reattaching in ${RETRY_DELAY_MS}ms", LogSeverity.Warning)
                awareSession = null; publishSession = null; subscribeSession = null
                publishActive = false; subscribeActive = false
                _state.update { it.copy(status = AwareSessionStatus.Error, errorMessage = "Session terminated") }
                mainHandler.postDelayed({ attach() }, RETRY_DELAY_MS)
            }
        }, mainHandler)
    }

    // -------------------------------------------------------------------------
    // Publish — advertises this device so subscribers can find it.
    // The publish session handle is ONLY used to reply with our own device ID.
    // It is NEVER stored as a peer handle.
    // -------------------------------------------------------------------------

    private fun startPublish(session: WifiAwareSession) {
        log("startPublish() → service='$SERVICE_NAME'")
        val config = PublishConfig.Builder()
            .setServiceName(SERVICE_NAME)
            .setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)
            .build()

        session.publish(config, object : DiscoverySessionCallback() {
            override fun onPublishStarted(session: PublishDiscoverySession) {
                log("onPublishStarted() ✓")
                publishSession = session
                publishActive = true
                checkBothSessionsActive()
            }

            // Subscriber sent us its device ID — reply with ours so it can update its peer entry.
            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                val text = message.toString(Charsets.UTF_8)
                log("onMessageReceived(publish) handle=${peerHandle.hashCode()} text='$text'")
                if (text.startsWith(DEVICE_ID_PREFIX)) {
                    // Reply with our own ID so the subscriber can name the peer
                    val reply = "$DEVICE_ID_PREFIX${deviceId()}"
                    publishSession?.sendMessage(peerHandle, MESSAGE_ID, reply.toByteArray(Charsets.UTF_8))
                    log("onMessageReceived(publish) replied with id='$reply' to handle=${peerHandle.hashCode()}")
                }
                // Do NOT create or update any peer entry here — publish-side handles are not valid for RTT
            }

            override fun onMessageSendSucceeded(messageId: Int) { log("onMessageSendSucceeded(publish) id=$messageId") }
            override fun onMessageSendFailed(messageId: Int) { log("onMessageSendFailed(publish) id=$messageId", LogSeverity.Warning) }

            override fun onSessionTerminated() {
                log("onSessionTerminated(publish) — restarting", LogSeverity.Warning)
                publishSession = null; publishActive = false
                awareSession?.let { startPublish(it) }
            }

            // Publish side should not discover services — log if it happens unexpectedly
            override fun onServiceDiscovered(peerHandle: PeerHandle, serviceSpecificInfo: ByteArray?, matchFilter: List<ByteArray>?) {
                log("onServiceDiscovered(publish side) handle=${peerHandle.hashCode()} — ignored, not stored")
            }
        }, mainHandler)
    }

    // -------------------------------------------------------------------------
    // Subscribe — discovers peers. The handle from onServiceDiscovered is the
    // ONLY handle stored and used for RTT ranging.
    // -------------------------------------------------------------------------

    private fun startSubscribe(session: WifiAwareSession) {
        log("startSubscribe() → service='$SERVICE_NAME'")
        val config = SubscribeConfig.Builder()
            .setServiceName(SERVICE_NAME)
            .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)
            .build()

        session.subscribe(config, object : DiscoverySessionCallback() {
            override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                log("onSubscribeStarted() ✓")
                subscribeSession = session
                subscribeActive = true
                checkBothSessionsActive()
            }

            // Step 1: peer found. Store handle immediately (valid for RTT). Send our ID.
            override fun onServiceDiscovered(peerHandle: PeerHandle, serviceSpecificInfo: ByteArray?, matchFilter: List<ByteArray>?) {
                val handleId = peerHandle.hashCode()
                log("onServiceDiscovered(subscribe) handle=$handleId — storing peer, sending ID")
                upsertPeerByHandle(peerHandle, newPeerId = null)
                val idMessage = "$DEVICE_ID_PREFIX${deviceId()}"
                subscribeSession?.sendMessage(peerHandle, MESSAGE_ID, idMessage.toByteArray(Charsets.UTF_8))
                log("sendMessage() id='$idMessage' to handle=$handleId")
            }

            // Step 2: publisher replied with its device ID. Update the EXISTING peer (same handle).
            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                val text = message.toString(Charsets.UTF_8)
                val handleId = peerHandle.hashCode()
                log("onMessageReceived(subscribe) handle=$handleId text='$text'")
                if (text.startsWith(DEVICE_ID_PREFIX)) {
                    val peerId = text.removePrefix(DEVICE_ID_PREFIX)
                    log("onMessageReceived(subscribe) handle=$handleId → updating peerId to '$peerId'")
                    upsertPeerByHandle(peerHandle, newPeerId = peerId)
                }
            }

            override fun onServiceLost(peerHandle: PeerHandle, reason: Int) {
                log("onServiceLost() handle=${peerHandle.hashCode()} reason=$reason", LogSeverity.Warning)
                markPeerInactive(peerHandle)
            }

            override fun onMessageSendSucceeded(messageId: Int) { log("onMessageSendSucceeded(subscribe) id=$messageId") }
            override fun onMessageSendFailed(messageId: Int) { log("onMessageSendFailed(subscribe) id=$messageId — ID exchange failed", LogSeverity.Warning) }

            override fun onSessionTerminated() {
                log("onSessionTerminated(subscribe) — restarting", LogSeverity.Warning)
                subscribeSession = null; subscribeActive = false
                awareSession?.let { startSubscribe(it) }
            }
        }, mainHandler)
    }

    // -------------------------------------------------------------------------
    // Peer management — single source of truth, keyed by subscribe handle
    // -------------------------------------------------------------------------

    /**
     * Creates or updates the peer entry for [peerHandle].
     * If [newPeerId] is non-null, renames the peer (ID exchange completed).
     * Never creates a duplicate — one physical device = one entry.
     */
    private fun upsertPeerByHandle(peerHandle: PeerHandle, newPeerId: String?) {
        val now = System.currentTimeMillis()
        val handleId = peerHandle.hashCode()
        _state.update { current ->
            val existing = current.peers.indexOfFirst { it.peerHandle.hashCode() == handleId }
            val updated = if (existing >= 0) {
                current.peers.toMutableList().also { list ->
                    val old = list[existing]
                    list[existing] = old.copy(
                        peerHandle = peerHandle,
                        peerId = newPeerId ?: old.peerId,
                        lastSeenMillis = now,
                        isActive = true,
                    )
                    log("upsertPeerByHandle: updated handle=$handleId peerId=${list[existing].peerId}")
                }
            } else {
                val peerId = newPeerId ?: "peer-$handleId"
                log("upsertPeerByHandle: new peer handle=$handleId peerId=$peerId")
                current.peers + AwarePeer(
                    peerHandle = peerHandle,
                    peerId = peerId,
                    discoveredAtMillis = now,
                    lastSeenMillis = now,
                )
            }
            current.copy(peers = updated)
        }
        schedulePeerTimeoutCheck()
    }

    private fun markPeerInactive(peerHandle: PeerHandle) {
        val handleId = peerHandle.hashCode()
        log("markPeerInactive: handle=$handleId")
        _state.update { current ->
            current.copy(peers = current.peers.map {
                if (it.peerHandle.hashCode() == handleId) it.copy(isActive = false) else it
            })
        }
    }

    private fun schedulePeerTimeoutCheck() {
        scope.launch {
            delay(peerTimeoutMillis + 1_000)
            val cutoff = System.currentTimeMillis() - peerTimeoutMillis
            _state.update { current ->
                current.copy(peers = current.peers.map {
                    if (it.lastSeenMillis < cutoff) it.copy(isActive = false) else it
                })
            }
        }
    }

    // -------------------------------------------------------------------------
    // Session state
    // -------------------------------------------------------------------------

    private fun checkBothSessionsActive() {
        if (publishActive && subscribeActive) {
            log("Both publish and subscribe sessions active — discovery running ✓")
            _state.update { it.copy(status = AwareSessionStatus.Active, errorMessage = null) }
        }
    }

    // -------------------------------------------------------------------------
    // Availability receiver
    // -------------------------------------------------------------------------

    private fun registerAvailabilityReceiver() {
        if (availabilityReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val available = wifiAwareManager?.isAvailable == true
                if (available) {
                    log("Wi-Fi Aware became available — attaching (session=${awareSession != null})")
                    if (awareSession == null) attach()
                } else {
                    log("Wi-Fi Aware became unavailable", LogSeverity.Warning)
                    closeAllSessions()
                    _state.update { it.copy(status = AwareSessionStatus.Unavailable, peers = emptyList()) }
                }
            }
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0
        context.registerReceiver(receiver, IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED), flags)
        availabilityReceiver = receiver
    }

    private fun unregisterAvailabilityReceiver() {
        availabilityReceiver?.let { runCatching { context.unregisterReceiver(it) }; availabilityReceiver = null }
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    private fun closeAllSessions() {
        publishActive = false; subscribeActive = false
        runCatching { publishSession?.close() }
        runCatching { subscribeSession?.close() }
        runCatching { awareSession?.close() }
        publishSession = null; subscribeSession = null; awareSession = null
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun hasRequiredPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val nearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
        else PackageManager.PERMISSION_GRANTED
        val fineOk = fine == PackageManager.PERMISSION_GRANTED
        val nearbyOk = nearby == PackageManager.PERMISSION_GRANTED
        if (!fineOk) log("Missing ACCESS_FINE_LOCATION", LogSeverity.Error)
        if (!nearbyOk) log("Missing NEARBY_WIFI_DEVICES (required on API 33+)", LogSeverity.Error)
        return fineOk && nearbyOk
    }

    private fun deviceId(): String = android.provider.Settings.Secure.getString(
        context.contentResolver, android.provider.Settings.Secure.ANDROID_ID,
    ).takeLast(8)

    private fun log(message: String, severity: LogSeverity = LogSeverity.Info) {
        scope.launch { logRepository.addLog("[Aware] $message", severity) }
    }

    companion object {
        const val SERVICE_NAME = "wifi_rtt_measurement"
        private const val MESSAGE_ID = 1
        private const val DEVICE_ID_PREFIX = "id:"
        private const val RETRY_DELAY_MS = 3_000L
    }
}
