package com.eldraft.android.ui.draft

import com.eldraft.core.network.userMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.android.notifications.NotificationRefreshBus
import com.eldraft.android.notifications.RefreshEvent
import com.eldraft.data.models.Convocatory
import com.eldraft.domain.repository.ConvocatoryRepository
import com.eldraft.domain.usecase.team.OpenConvocatoryToPublicUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de "Mis convocatorias" (organizador). */
data class MyMatchesUiState(
    val matches: List<Convocatory> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Convocatoria que se está abriendo al público (deshabilita su botón). */
    val openingId: String? = null,
    /** Fallo al abrir: va aparte de [error] porque se muestra dentro del diálogo. */
    val openError: String? = null,
    val openSuccess: Boolean = false,
)

class MyMatchesViewModel(
    private val convocatoryRepository: ConvocatoryRepository,
    private val refreshBus: NotificationRefreshBus,
    private val openToPublic: OpenConvocatoryToPublicUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MyMatchesUiState())
    val state: StateFlow<MyMatchesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            refreshBus.events
                .filter { it == RefreshEvent.MY_MATCHES }
                .collect { load() }
        }
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val mine = convocatoryRepository.getMine()
                _state.update { it.copy(matches = mine, isLoading = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.userMessage("No se pudieron cargar tus convocatorias"))
                }
            }
        }
    }

    /** "Abrir ya al público": salta lo que falta de la ventana exclusiva del equipo. */
    fun openNow(convocatoryId: String) {
        if (_state.value.openingId != null) return
        _state.update { it.copy(openingId = convocatoryId, openError = null) }
        viewModelScope.launch {
            try {
                openToPublic(convocatoryId)
                _state.update { it.copy(openingId = null, openSuccess = true) }
                load()
                // Si se publicó, el pin aparece en el mapa de los demás.
                refreshBus.emit(RefreshEvent.MAP)
            } catch (e: Exception) {
                _state.update { it.copy(openingId = null, openError = e.userMessage("No se pudo abrir la convocatoria")) }
                // Típico: la ventana ya venció y se abrió sola. Se recarga para mostrarlo.
                load()
            }
        }
    }

    fun clearOpenError() = _state.update { it.copy(openError = null) }

    fun clearOpenSuccess() = _state.update { it.copy(openSuccess = false) }
}
