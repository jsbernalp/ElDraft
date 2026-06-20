package com.eldraft.android.ui.draft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.core.network.userMessage
import com.eldraft.domain.usecase.convocatory.CancelConvocatoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CancelConvocatoryUiState(
    val isLoading: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
)

class CancelConvocatoryViewModel(
    private val cancelConvocatory: CancelConvocatoryUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CancelConvocatoryUiState())
    val state: StateFlow<CancelConvocatoryUiState> = _state.asStateFlow()

    fun cancel(convocatoryId: String, reason: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                cancelConvocatory(convocatoryId, reason)
                _state.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.userMessage("No se pudo cancelar el partido"))
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun resetSuccess() = _state.update { it.copy(success = false) }
}
