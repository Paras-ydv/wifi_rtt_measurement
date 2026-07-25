package com.example.wifirttmeasurement.domain.model

import android.net.wifi.aware.PeerHandle

data class AwarePeer(
    val peerHandle: PeerHandle,
    val peerId: String,           // device identifier exchanged via message
    val discoveredAtMillis: Long,
    val lastSeenMillis: Long,
    val isActive: Boolean = true,
)
