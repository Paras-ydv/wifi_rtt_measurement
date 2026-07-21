package com.example.wifirttmeasurement.presentation.ui.receiver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wifirttmeasurement.domain.usecase.ExportMeasurementsCsvUseCase
import com.example.wifirttmeasurement.domain.usecase.MeasureAllPublishersUseCase
import com.example.wifirttmeasurement.domain.usecase.MeasureSelectedPublishersUseCase
import com.example.wifirttmeasurement.domain.usecase.ObserveLogsUseCase
import com.example.wifirttmeasurement.domain.usecase.ObserveReceiverStateUseCase
import com.example.wifirttmeasurement.domain.usecase.ScanPublishersUseCase
import com.example.wifirttmeasurement.domain.usecase.StopMeasurementSessionUseCase
import com.example.wifirttmeasurement.domain.usecase.TogglePublisherSelectionUseCase
import com.example.wifirttmeasurement.domain.model.RttPermissionState
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReceiverUiState())
    val uiState: StateFlow<ReceiverUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                observeReceiverStateUseCase(),
                observeLogsUseCase(),
            ) { receiverState, logs ->
                ReceiverUiState.from(
                    receiverState = receiverState,
                    logs = logs,
                    errorMessage = _uiState.value.errorMessage,
                    permissionState = _uiState.value.permissionState,
                    showPermissionRequest = _uiState.value.showPermissionRequest,
                )
            }.collect { nextState ->
                _uiState.value = nextState
            }
        }
    }

    fun scanPublishers() {
        if (!_uiState.value.permissionState.allGranted) {
            _uiState.update { it.copy(showPermissionRequest = true) }
            return
        }
        runReceiverAction { scanPublishersUseCase() }
    }

    fun togglePublisherSelection(publisherId: String) {
        runReceiverAction {
            togglePublisherSelectionUseCase(publisherId)
        }
    }

    fun measureSelected() {
        runReceiverAction {
            measureSelectedPublishersUseCase()
        }
    }

    fun measureAll() {
        runReceiverAction {
            measureAllPublishersUseCase()
        }
    }

    fun stop() {
        runReceiverAction {
            stopMeasurementSessionUseCase()
        }
    }

    fun onPermissionsResult(fineLocation: Boolean, nearbyWifi: Boolean) {
        val state = RttPermissionState(hasFineLocation = fineLocation, hasNearbyWifiDevices = nearbyWifi)
        val denied = !state.allGranted
        _uiState.update { it.copy(permissionState = state, showPermissionRequest = false, showPermissionDeniedDialog = denied) }
        if (state.allGranted) runReceiverAction { scanPublishersUseCase() }
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
