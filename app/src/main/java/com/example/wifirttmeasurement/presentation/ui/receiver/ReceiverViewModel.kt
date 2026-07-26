package com.example.wifirttmeasurement.presentation.ui.receiver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wifirttmeasurement.data.rtt.WifiAwareDiscoveryManager
import com.example.wifirttmeasurement.domain.model.ConnectionStatus
import com.example.wifirttmeasurement.domain.model.PublisherDevice
import com.example.wifirttmeasurement.domain.model.PublisherStatus
import com.example.wifirttmeasurement.domain.model.RttPermissionState
import com.example.wifirttmeasurement.domain.usecase.ExportMeasurementsCsvUseCase
import com.example.wifirttmeasurement.domain.usecase.MeasureAllPublishersUseCase
import com.example.wifirttmeasurement.domain.usecase.MeasureSelectedPublishersUseCase
import com.example.wifirttmeasurement.domain.usecase.ObserveLogsUseCase
import com.example.wifirttmeasurement.domain.usecase.ObserveReceiverStateUseCase
import com.example.wifirttmeasurement.domain.usecase.ScanPublishersUseCase
import com.example.wifirttmeasurement.domain.usecase.StopMeasurementSessionUseCase
import com.example.wifirttmeasurement.domain.usecase.TogglePublisherSelectionUseCase
import com.example.wifirttmeasurement.domain.repository.ReceiverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ReceiverViewModel @Inject constructor(
    observeReceiverStateUseCase: ObserveReceiverStateUseCase,
    observeLogsUseCase: ObserveLogsUseCase,
    private val scanPublishersUseCase: ScanPublishersUseCase,
    private val togglePublisherSelectionUseCase: TogglePublisherSelectionUseCase,
    private val measureSelectedPublishersUseCase: MeasureSelectedPublishersUseCase,
    private val measureAllPublishersUseCase: MeasureAllPublishersUseCase,
    private val stopMeasurementSessionUseCase: StopMeasurementSessionUseCase,
    private val exportMeasurementsCsvUseCase: ExportMeasurementsCsvUseCase,
    private val receiverRepository: ReceiverRepository,
    val wifiAwareDiscoveryManager: WifiAwareDiscoveryManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReceiverUiState())
    val uiState: StateFlow<ReceiverUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                observeReceiverStateUseCase(),
                observeLogsUseCase(),
                wifiAwareDiscoveryManager.state,
            ) { receiverState, logs, awareState ->
                // Merge Aware peers: preserve existing PublisherDevice state (connectionStatus,
                // lastRssiDbm, etc.) so measurement results are not overwritten on re-emission.
                val repoIds = receiverState.publishers.map { it.id }.toSet()
                // All current active peer IDs from Aware (may include renamed peers)
                val activePeerIds = awareState.activePeers.map { it.peerId }.toSet()
                val existingAwareById = _uiState.value.publishers
                    .filter { it.awarePeerId != null }
                    .associateBy { it.id }
                val awarePeers = awareState.activePeers
                    .filter { it.peerId !in repoIds }
                    .map { peer ->
                        // Reuse existing entry if id matches, OR if it was a placeholder
                        // (peer-<handle>) that has since been renamed to a real id.
                        existingAwareById[peer.peerId]
                            ?: existingAwareById["peer-${peer.peerHandle.hashCode()}"]
                                ?.copy(id = peer.peerId, name = peer.peerId, awarePeerId = peer.peerId)
                            ?: PublisherDevice(
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
                // Merge repo publishers with Aware peers. Drop stale placeholder entries
                // (peer-<handle>) whose peer has since been renamed to a real id.
                val mergedById = (receiverState.publishers + awarePeers).associateBy { it.id }
                val mergedPublishers = mergedById.values
                    .filter { p ->
                        val isPlaceholder = p.awarePeerId?.startsWith("peer-") == true
                        !isPlaceholder || p.awarePeerId in activePeerIds
                    }
                    .toList()
                ReceiverUiState.from(
                    receiverState = receiverState.copy(publishers = mergedPublishers),
                    logs = logs,
                    errorMessage = _uiState.value.errorMessage,
                    permissionState = _uiState.value.permissionState,
                    showPermissionRequest = _uiState.value.showPermissionRequest,
                    awareDiscoveryState = awareState,
                )
            }.collect { nextState ->
                _uiState.update { current ->
                    nextState.copy(
                        exportedCsvUri = current.exportedCsvUri,
                        showPermissionDeniedDialog = current.showPermissionDeniedDialog,
                    )
                }
            }
        }
    }

    /** Called once when the screen is ready and permissions are confirmed. */
    fun startAwareDiscovery() {
        if (_uiState.value.permissionState.allGranted) wifiAwareDiscoveryManager.start()
    }

    fun scanPublishers() {
        if (!_uiState.value.permissionState.allGranted) {
            _uiState.update { it.copy(showPermissionRequest = true) }
            return
        }
        // Ensure Aware is running (no-op if already attached)
        wifiAwareDiscoveryManager.start()
        runReceiverAction { scanPublishersUseCase() }
    }

    fun togglePublisherSelection(publisherId: String) {
        runReceiverAction { togglePublisherSelectionUseCase(publisherId) }
    }

    fun measureSelected() {
        runReceiverAction {
            receiverRepository.syncPublishers(_uiState.value.publishers)
            measureSelectedPublishersUseCase()
        }
    }

    fun measureAll() {
        runReceiverAction {
            receiverRepository.syncPublishers(_uiState.value.publishers)
            measureAllPublishersUseCase()
        }
    }

    fun stop() {
        wifiAwareDiscoveryManager.stop()
        runReceiverAction { stopMeasurementSessionUseCase() }
    }

    fun onPermissionsResult(fineLocation: Boolean, nearbyWifi: Boolean) {
        val state = RttPermissionState(hasFineLocation = fineLocation, hasNearbyWifiDevices = nearbyWifi)
        _uiState.update {
            it.copy(
                permissionState = state,
                showPermissionRequest = false,
                showPermissionDeniedDialog = !state.allGranted,
            )
        }
        if (state.allGranted) {
            wifiAwareDiscoveryManager.start()
            runReceiverAction { scanPublishersUseCase() }
        }
    }

    fun dismissPermissionRequest() {
        _uiState.update { it.copy(showPermissionRequest = false, showPermissionDeniedDialog = false) }
    }

    fun exportCsv() {
        runReceiverAction {
            val uri = exportMeasurementsCsvUseCase()
            _uiState.update { it.copy(exportedCsvUri = uri) }
        }
    }

    fun onErrorMessageShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onExportUriConsumed() {
        _uiState.update { it.copy(exportedCsvUri = null) }
    }

    override fun onCleared() {
        super.onCleared()
        wifiAwareDiscoveryManager.stop()
    }

    private fun runReceiverAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching {
                action()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.message ?: "Receiver action failed unexpectedly.")
                }
            }
        }
    }
}
