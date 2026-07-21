package com.example.wifirttmeasurement.presentation.ui.publisher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wifirttmeasurement.domain.model.PublisherState
import com.example.wifirttmeasurement.domain.usecase.ObserveLogsUseCase
import com.example.wifirttmeasurement.domain.usecase.ObservePublisherStatusUseCase
import com.example.wifirttmeasurement.domain.usecase.RefreshPublisherStatusUseCase
import com.example.wifirttmeasurement.domain.usecase.StartPublishingUseCase
import com.example.wifirttmeasurement.domain.usecase.StopPublishingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PublisherViewModel @Inject constructor(
    observePublisherStatusUseCase: ObservePublisherStatusUseCase,
    observeLogsUseCase: ObserveLogsUseCase,
    private val startPublishingUseCase: StartPublishingUseCase,
    private val stopPublishingUseCase: StopPublishingUseCase,
    private val refreshPublisherStatusUseCase: RefreshPublisherStatusUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PublisherUiState())
    val uiState: StateFlow<PublisherUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                observePublisherStatusUseCase(),
                observeLogsUseCase(),
            ) { publisherState, logs ->
                PublisherUiState.from(
                    publisherState = publisherState,
                    logs = logs,
                    isBusy = _uiState.value.isBusy,
                    errorMessage = _uiState.value.errorMessage,
                )
            }.collect { nextState ->
                _uiState.value = nextState
            }
        }

        refreshStatus()
    }

    fun startPublishing() {
        runPublisherAction {
            startPublishingUseCase()
        }
    }

    fun stopPublishing() {
        runPublisherAction {
            stopPublishingUseCase()
        }
    }

    fun refreshStatus() {
        runPublisherAction {
            refreshPublisherStatusUseCase()
        }
    }

    fun onErrorMessageShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun runPublisherAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                action()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        errorMessage = throwable.message ?: "Publisher action failed unexpectedly.",
                    )
                }
            }
            _uiState.update { it.copy(isBusy = false) }
        }
    }
}
