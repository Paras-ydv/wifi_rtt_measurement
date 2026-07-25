package com.example.wifirttmeasurement.data.rtt

import android.net.wifi.aware.PeerHandle
import com.example.wifirttmeasurement.domain.model.AwareDiscoveryState
import com.example.wifirttmeasurement.domain.model.AwarePeer
import com.example.wifirttmeasurement.domain.model.AwareSessionStatus
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AwareDiscoveryStateTest {

    private fun peer(active: Boolean, id: String = "peer-1") = AwarePeer(
        peerHandle = mockk<PeerHandle>(),
        peerId = id,
        discoveredAtMillis = 1000L,
        lastSeenMillis = 1000L,
        isActive = active,
    )

    @Test
    fun `initial state is Idle with no peers`() {
        val state = AwareDiscoveryState()
        assertEquals(AwareSessionStatus.Idle, state.status)
        assertTrue(state.peers.isEmpty())
        assertNull(state.errorMessage)
    }

    @Test
    fun `activePeers filters only active peers`() {
        val state = AwareDiscoveryState(
            peers = listOf(peer(true, "a"), peer(false, "b"), peer(true, "c")),
        )
        assertEquals(2, state.activePeers.size)
        assertTrue(state.activePeers.all { it.isActive })
    }

    @Test
    fun `activePeers is empty when all peers inactive`() {
        val state = AwareDiscoveryState(peers = listOf(peer(false), peer(false)))
        assertTrue(state.activePeers.isEmpty())
    }

    @Test
    fun `copy preserves unmodified fields`() {
        val state = AwareDiscoveryState(status = AwareSessionStatus.Active)
        val updated = state.copy(errorMessage = "oops")
        assertEquals(AwareSessionStatus.Active, updated.status)
        assertEquals("oops", updated.errorMessage)
    }
}

class AwarePeerTest {

    @Test
    fun `peer is active by default`() {
        val peer = AwarePeer(
            peerHandle = mockk(),
            peerId = "test",
            discoveredAtMillis = 0L,
            lastSeenMillis = 0L,
        )
        assertTrue(peer.isActive)
    }

    @Test
    fun `copy with isActive false marks peer inactive`() {
        val peer = AwarePeer(
            peerHandle = mockk(),
            peerId = "test",
            discoveredAtMillis = 0L,
            lastSeenMillis = 0L,
        )
        assertFalse(peer.copy(isActive = false).isActive)
    }

    @Test
    fun `peerId can be updated via copy`() {
        val peer = AwarePeer(
            peerHandle = mockk(),
            peerId = "peer-abc12345",
            discoveredAtMillis = 0L,
            lastSeenMillis = 0L,
        )
        assertEquals("real-device-id", peer.copy(peerId = "real-device-id").peerId)
    }
}
