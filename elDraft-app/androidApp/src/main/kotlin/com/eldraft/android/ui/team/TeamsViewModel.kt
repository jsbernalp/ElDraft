package com.eldraft.android.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.android.notifications.NotificationRefreshBus
import com.eldraft.android.notifications.RefreshEvent
import com.eldraft.core.network.userMessage
import com.eldraft.data.models.Team
import com.eldraft.data.models.TeamInvitation
import com.eldraft.domain.team.TeamRules
import com.eldraft.domain.usecase.team.CreateTeamUseCase
import com.eldraft.domain.usecase.team.GetMyTeamMembershipsUseCase
import com.eldraft.domain.usecase.team.GetMyTeamsUseCase
import com.eldraft.domain.usecase.team.GetTeamInvitationsUseCase
import com.eldraft.domain.usecase.team.RespondTeamInvitationUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de "Mis equipos": los propios, en los que soy miembro y las invitaciones. */
data class TeamsUiState(
    val myTeams: List<Team> = emptyList(),
    val memberships: List<Team> = emptyList(),
    val invitations: List<TeamInvitation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Lo que el usuario va escribiendo en el diálogo "Crear equipo". */
    val newTeamName: String = "",
    val isCreating: Boolean = false,
    /** Id del equipo recién creado (evento de una vez: la pantalla navega y lo consume). */
    val createdTeamId: String? = null,
    /** Ids de equipo cuya invitación se está aceptando/rechazando (fila bloqueada). */
    val respondingIds: Set<String> = emptySet(),
) {
    /** Largo del nombre tal como llegará al backend (sin espacios sobrantes). */
    val newTeamNameLength: Int get() = TeamRules.normalizeName(newTeamName).length
    val canCreateAnotherTeam: Boolean get() = TeamRules.canCreateAnotherTeam(myTeams.size)
    val isNewTeamNameValid: Boolean get() = TeamRules.isValidName(newTeamName)
    val canSubmitNewTeam: Boolean get() = canCreateAnotherTeam && isNewTeamNameValid && !isCreating
}

class TeamsViewModel(
    private val getMyTeams: GetMyTeamsUseCase,
    private val getMemberships: GetMyTeamMembershipsUseCase,
    private val getInvitations: GetTeamInvitationsUseCase,
    private val createTeam: CreateTeamUseCase,
    private val respondInvitation: RespondTeamInvitationUseCase,
    private val refreshBus: NotificationRefreshBus,
) : ViewModel() {

    private val _state = MutableStateFlow(TeamsUiState())
    val state: StateFlow<TeamsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            refreshBus.events
                .filter { it == RefreshEvent.TEAMS }
                .collect { load() }
        }
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                coroutineScope {
                    val mine = async { getMyTeams() }
                    val member = async { getMemberships() }
                    val invites = async { getInvitations() }
                    val myTeams = mine.await()
                    val memberships = member.await()
                    val invitations = invites.await()
                    _state.update {
                        it.copy(
                            myTeams = myTeams,
                            memberships = memberships,
                            invitations = invitations,
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.userMessage("No se pudieron cargar tus equipos"))
                }
            }
        }
    }

    fun onNewTeamNameChange(value: String) = _state.update { it.copy(newTeamName = value) }

    fun resetNewTeam() = _state.update { it.copy(newTeamName = "") }

    fun create() {
        val current = _state.value
        if (!current.canSubmitNewTeam) return
        _state.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            try {
                val team = createTeam(current.newTeamName)
                _state.update {
                    it.copy(
                        myTeams = it.myTeams + team,
                        isCreating = false,
                        newTeamName = "",
                        createdTeamId = team.id,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isCreating = false, error = e.userMessage("No se pudo crear el equipo"))
                }
            }
        }
    }

    fun consumeCreatedTeam() = _state.update { it.copy(createdTeamId = null) }

    fun accept(teamId: String) = respond(teamId, accept = true)

    fun decline(teamId: String) = respond(teamId, accept = false)

    private fun respond(teamId: String, accept: Boolean) {
        if (teamId in _state.value.respondingIds) return
        _state.update { it.copy(respondingIds = it.respondingIds + teamId) }
        viewModelScope.launch {
            try {
                respondInvitation(teamId, accept)
                // Al aceptar, el equipo pasa a "Soy miembro": se pide de nuevo la
                // lista para traer los conteos reales en vez de inventarlos aquí.
                val memberships = if (accept) runCatching { getMemberships() }.getOrNull() else null
                _state.update { s ->
                    s.copy(
                        invitations = s.invitations.filterNot { it.teamId == teamId },
                        memberships = memberships ?: s.memberships,
                        respondingIds = s.respondingIds - teamId,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        respondingIds = it.respondingIds - teamId,
                        error = e.userMessage("No se pudo responder la invitación"),
                    )
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
