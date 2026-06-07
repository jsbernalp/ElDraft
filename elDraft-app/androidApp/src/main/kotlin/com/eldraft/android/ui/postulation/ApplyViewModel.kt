package com.eldraft.android.ui.postulation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.domain.usecase.postulation.ApplyToConvocatoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado de la acción "Postularme a este cupo". */
sealed interface ApplyUiState {
    data object Idle : ApplyUiState
    data object Sending : ApplyUiState
    data object Applied : ApplyUiState
    data class Error(val message: String) : ApplyUiState
}

class ApplyViewModel(
    private val applyToConvocatory: ApplyToConvocatoryUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ApplyUiState>(ApplyUiState.Idle)
    val state: StateFlow<ApplyUiState> = _state.asStateFlow()

    fun apply(convocatoryId: String) {
        if (_state.value is ApplyUiState.Sending) return
        _state.value = ApplyUiState.Sending
        viewModelScope.launch {
            try {
                applyToConvocatory(convocatoryId)
                _state.value = ApplyUiState.Applied
            } catch (e: Exception) {
                _state.value = ApplyUiState.Error(
                    e.message ?: "No se pudo enviar tu postulación"
                )
            }
        }
    }

    fun reset() {
        _state.value = ApplyUiState.Idle
    }
}
