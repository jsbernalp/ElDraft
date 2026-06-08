package com.eldraft.android.ui.draft

import com.eldraft.core.network.userMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.data.models.CreateConvocatoryRequest
import com.eldraft.domain.usecase.convocatory.CreateConvocatoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado del formulario de creación de convocatoria (El Draft). */
sealed interface CreateDraftUiState {
    data object Idle : CreateDraftUiState
    data object Saving : CreateDraftUiState
    data object Created : CreateDraftUiState
    data class Error(val message: String) : CreateDraftUiState
}

class CreateDraftViewModel(
    private val createConvocatory: CreateConvocatoryUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<CreateDraftUiState>(CreateDraftUiState.Idle)
    val state: StateFlow<CreateDraftUiState> = _state.asStateFlow()

    fun create(request: CreateConvocatoryRequest) {
        if (_state.value is CreateDraftUiState.Saving) return
        _state.value = CreateDraftUiState.Saving
        viewModelScope.launch {
            try {
                createConvocatory(request)
                _state.value = CreateDraftUiState.Created
            } catch (e: Exception) {
                _state.value = CreateDraftUiState.Error(
                    e.userMessage("No se pudo crear la convocatoria")
                )
            }
        }
    }

    fun resetError() {
        if (_state.value is CreateDraftUiState.Error) _state.value = CreateDraftUiState.Idle
    }
}
