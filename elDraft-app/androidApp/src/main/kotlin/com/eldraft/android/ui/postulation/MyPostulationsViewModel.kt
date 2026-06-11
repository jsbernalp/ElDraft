package com.eldraft.android.ui.postulation

import com.eldraft.core.network.userMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.data.models.MyPostulation
import com.eldraft.domain.usecase.postulation.GetMyPostulationsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de "mis partidos como jugador" (postulaciones propias). */
data class MyPostulationsUiState(
    val postulations: List<MyPostulation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class MyPostulationsViewModel(
    private val getMyPostulations: GetMyPostulationsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MyPostulationsUiState())
    val state: StateFlow<MyPostulationsUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val list = getMyPostulations()
                _state.update { it.copy(postulations = list, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.userMessage("No se pudieron cargar tus postulaciones")) }
            }
        }
    }
}
