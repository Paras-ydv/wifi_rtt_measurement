package com.example.wifirttmeasurement.presentation.ui.role

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wifirttmeasurement.domain.model.AppRole
import com.example.wifirttmeasurement.domain.usecase.CheckDeviceCapabilitiesUseCase
import com.example.wifirttmeasurement.domain.usecase.ObserveSelectedRoleUseCase
import com.example.wifirttmeasurement.domain.usecase.SaveSelectedRoleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class RoleSelectionViewModel @Inject constructor(
    private val checkDeviceCapabilitiesUseCase: CheckDeviceCapabilitiesUseCase,
    private val observeSelectedRoleUseCase: ObserveSelectedRoleUseCase,
    private val saveSelectedRoleUseCase: SaveSelectedRoleUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoleSelectionUiState())
    val uiState: StateFlow<RoleSelectionUiState> = _uiState.asStateFlow()

    private val events = Channel<RoleSelectionEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        observeSelectedRole()
        refreshCapabilities()
    }

    fun onRoleSelected(role: AppRole) {
        val state = _uiState.value
        val isRoleAllowed = when (role) {
            AppRole.Receiver -> state.canChooseReceiver
            AppRole.Publisher -> state.canChoosePublisher
        }

        if (!isRoleAllowed) {
            _uiState.update {
                it.copy(errorMessage = "This device is not currently available for ${role.name} mode.")
            }
            return
        }

        viewModelScope.launch {
            runCatching {
                saveSelectedRoleUseCase(role)
            }.onSuccess {
                events.send(RoleSelectionEvent.NavigateToRole(role))
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.message ?: "Unable to save selected role.")
                }
            }
        }
    }

    fun onErrorMessageShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun refreshCapabilities() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val capability = checkDeviceCapabilitiesUseCase()
            _uiState.update {
                it.copy(
                    capability = capability,
                    isLoading = false,
                )
            }
        }
    }

    private fun observeSelectedRole() {
        viewModelScope.launch {
            observeSelectedRoleUseCase()
                .catch { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Unable to read saved role.",
                        )
                    }
                }
                .collect { selectedRole ->
                    _uiState.update { it.copy(selectedRole = selectedRole) }
                }
        }
    }
}
