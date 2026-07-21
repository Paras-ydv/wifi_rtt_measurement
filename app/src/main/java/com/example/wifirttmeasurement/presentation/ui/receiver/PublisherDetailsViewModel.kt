package com.example.wifirttmeasurement.presentation.ui.receiver

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wifirttmeasurement.domain.repository.ReceiverRepository
import com.example.wifirttmeasurement.presentation.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PublisherDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    receiverRepository: ReceiverRepository,
) : ViewModel() {
    private val publisherId: String =
        checkNotNull(savedStateHandle[AppRoute.PublisherDetails.PublisherIdArgument])

    val uiState = receiverRepository.receiverState
        .map { state ->
            PublisherDetailsUiState.from(
                publisherId = publisherId,
                allMeasurements = state.measurements,
                publishers = state.publishers,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PublisherDetailsUiState(),
        )
}
