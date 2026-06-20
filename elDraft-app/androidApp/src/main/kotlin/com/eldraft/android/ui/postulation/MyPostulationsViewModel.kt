package com.eldraft.android.ui.postulation

import com.eldraft.core.network.userMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.android.notifications.NotificationRefreshBus
import com.eldraft.android.notifications.RefreshEvent
import com.eldraft.data.models.MyPostulation
import com.eldraft.domain.usecase.postulation.GetMyPostulationsUseCase
import com.eldraft.domain.usecase.postulation.WithdrawPostulationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de "mis partidos como jugador" (postulaciones propias). */
data class MyPostulationsUiState(
    val postulations: List<MyPostulation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val withdrawingId: String? = null,
    val withdrawSuccess: Boolean = false,
)

class MyPostulationsViewModel(
    private val getMyPostulations: GetMyPostulationsUseCase,
    private val withdrawPostulation: WithdrawPostulationUseCase,
    private val refreshBus: NotificationRefreshBus,
) : ViewModel() {

    private val _state = MutableStateFlow(MyPostulationsUiState())
    val state: StateFlow<MyPostulationsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            refreshBus.events
                .filter { it == RefreshEvent.MY_POSTULATIONS }
                .collect { load() }
        }
    }

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

    fun withdraw(postulationId: String) {
        _state.update { it.copy(withdrawingId = postulationId, error = null) }
        viewModelScope.launch {
            try {
                withdrawPostulation(postulationId)
                _state.update { it.copy(withdrawingId = null, withdrawSuccess = true) }
                load()
            } catch (e: Exception) {
                _state.update { it.copy(withdrawingId = null, error = e.userMessage("No se pudo retirar la postulación")) }
            }
        }
    }

    fun clearWithdrawSuccess() = _state.update { it.copy(withdrawSuccess = false) }
    fun clearError() = _state.update { it.copy(error = null) }
}
