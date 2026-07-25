package com.example.wifirttmeasurement.domain.model

enum class AwareSessionStatus {
    Idle,
    Attaching,
    Attached,
    Publishing,
    Subscribing,
    Active,   // both publish + subscribe sessions live
    Error,
    Unavailable,
}

data class AwareDiscoveryState(
    val status: AwareSessionStatus = AwareSessionStatus.Idle,
    val peers: List<AwarePeer> = emptyList(),
    val errorMessage: String? = null,
) {
    val activePeers: List<AwarePeer> get() = peers.filter { it.isActive }
}
