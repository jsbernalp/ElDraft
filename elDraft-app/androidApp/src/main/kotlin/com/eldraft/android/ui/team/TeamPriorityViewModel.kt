package com.eldraft.android.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.android.notifications.NotificationRefreshBus
import com.eldraft.android.notifications.RefreshEvent
import com.eldraft.core.network.userMessage
import com.eldraft.data.models.TeamPriorityInvite
import com.eldraft.domain.usecase.team.GetTeamPriorityInvitesUseCase
import com.eldraft.domain.usecase.team.RespondTeamPriorityUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Convocatorias de mis equipos esperando mi "Voy" / "No puedo" (pestaña Juego). */
data class TeamPriorityUiState(
    val invites: List<TeamPriorityInvite> = emptyList(),
    val isLoading: Boolean = false,
    /** Convocatoria cuya respuesta está en vuelo (deshabilita sus botones). */
    val respondingId: String? = null,
    val error: String? = null,
    /** True un instante tras responder, para avisar con un snackbar. */
    val responded: Boolean = false,
)

class TeamPriorityViewModel(
    private val getInvites: GetTeamPriorityInvitesUseCase,
    private val respondUseCase: RespondTeamPriorityUseCase,
    private val refreshBus: NotificationRefreshBus,
) : ViewModel() {

    private val _state = MutableStateFlow(TeamPriorityUiState())
    val state: StateFlow<TeamPriorityUiState> = _state.asStateFlow()

    init {
        // El push team_priority y cualquier cambio de postulaciones refrescan la card.
        viewModelScope.launch {
            refreshBus.events
                .filter { it == RefreshEvent.MY_POSTULATIONS }
                .collect { load() }
        }
    }

    fun load() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val invites = getInvites()
                _state.update { it.copy(invites = invites, isLoading = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.userMessage("No se pudieron cargar las convocatorias de tu equipo"))
                }
            }
        }
    }

    /** "Voy" ([accept] = true, con [position]) o "No puedo". */
    fun respond(convocatoryId: String, accept: Boolean, position: String?) {
        if (_state.value.respondingId != null) return
        _state.update { it.copy(respondingId = convocatoryId, error = null) }
        viewModelScope.launch {
            try {
                respondUseCase(convocatoryId, accept, position)
                _state.update {
                    it.copy(
                        respondingId = null,
                        responded = true,
                        invites = it.invites.filterNot { i -> i.convocatory.id == convocatoryId },
                    )
                }
                // Un "Voy" crea una postulación: la lista de Juego debe recargarse.
                refreshBus.emit(RefreshEvent.MY_POSTULATIONS)
            } catch (e: Exception) {
                _state.update { it.copy(respondingId = null, error = e.userMessage("No se pudo enviar tu respuesta")) }
                // Un 409 (sin cupo, ventana vencida) deja la card desactualizada.
                load()
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun clearResponded() = _state.update { it.copy(responded = false) }
}
