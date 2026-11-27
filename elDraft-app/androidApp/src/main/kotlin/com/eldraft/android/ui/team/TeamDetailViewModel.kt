package com.eldraft.android.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.android.notifications.NotificationRefreshBus
import com.eldraft.android.notifications.RefreshEvent
import com.eldraft.core.network.userMessage
import com.eldraft.data.models.TeamDetail
import com.eldraft.data.models.TeamMember
import com.eldraft.data.models.TeamSuggestion
import com.eldraft.domain.repository.AuthRepository
import com.eldraft.domain.team.TeamRules
import com.eldraft.domain.usecase.team.DeleteTeamUseCase
import com.eldraft.domain.usecase.team.GetTeamDetailUseCase
import com.eldraft.domain.usecase.team.GetTeamSuggestionsUseCase
import com.eldraft.domain.usecase.team.InviteToTeamUseCase
import com.eldraft.domain.usecase.team.RemoveTeamMemberUseCase
import com.eldraft.domain.usecase.team.RenameTeamUseCase
import com.eldraft.domain.usecase.team.TeamInviteTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del detalle de un equipo y de su sheet "Agregar jugador". */
data class TeamDetailUiState(
    val team: TeamDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRenaming: Boolean = false,
    /** Evento de una vez: el renombre salió bien y el diálogo puede cerrarse. */
    val renamed: Boolean = false,
    val isDeleting: Boolean = false,
    val isLeaving: Boolean = false,
    /** Evento de una vez: el equipo se borró o el usuario se salió → volver atrás. */
    val closed: Boolean = false,
    /** Ids de jugadores que se están quitando (botón bloqueado). */
    val removingIds: Set<String> = emptySet(),
    // --- Sheet "Agregar jugador" ---
    val suggestions: List<TeamSuggestion> = emptyList(),
    val suggestionsLoading: Boolean = false,
    val suggestionsError: String? = null,
    /** Ids de sugeridos con una invitación en curso (bloqueo de reenvío). */
    val invitingIds: Set<String> = emptySet(),
    val email: String = "",
    val isInvitingByEmail: Boolean = false,
    /** Error de la pestaña "Por correo" (404 / 429 / 422 llegan con el texto del backend). */
    val emailError: String? = null,
    /** Nombre del último invitado por correo, para confirmarlo en el sheet. */
    val emailInvitedName: String? = null,
) {
    val isOwner: Boolean get() = team?.isOwner == true
    val members: List<TeamMember> get() = team?.members.orEmpty()
    val canAddMember: Boolean get() = team != null && TeamRules.canAddMember(members.size)
    val canSendEmail: Boolean
        get() = canAddMember && !isInvitingByEmail && TeamRules.isPlausibleEmail(email)
}

class TeamDetailViewModel(
    private val getTeamDetail: GetTeamDetailUseCase,
    private val renameTeam: RenameTeamUseCase,
    private val deleteTeam: DeleteTeamUseCase,
    private val removeMember: RemoveTeamMemberUseCase,
    private val getSuggestions: GetTeamSuggestionsUseCase,
    private val inviteToTeam: InviteToTeamUseCase,
    private val authRepository: AuthRepository,
    private val refreshBus: NotificationRefreshBus,
) : ViewModel() {

    private val _state = MutableStateFlow(TeamDetailUiState())
    val state: StateFlow<TeamDetailUiState> = _state.asStateFlow()

    private var teamId: String? = null

    init {
        viewModelScope.launch {
            refreshBus.events
                // Tras borrar o salirse, el propio VM emite TEAMS para la lista:
                // recargar aquí daría un 404 sobre un equipo que ya no es nuestro.
                .filter { it == RefreshEvent.TEAMS && !_state.value.closed }
                .collect { teamId?.let { load(it) } }
        }
    }

    fun load(teamId: String) {
        this.teamId = teamId
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val detail = getTeamDetail(teamId)
                _state.update { it.copy(team = detail, isLoading = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.userMessage("No se pudo cargar el equipo"))
                }
            }
        }
    }

    fun rename(name: String) {
        val id = teamId ?: return
        if (_state.value.isRenaming) return
        _state.update { it.copy(isRenaming = true) }
        viewModelScope.launch {
            try {
                val updated = renameTeam(id, name)
                _state.update {
                    it.copy(
                        team = it.team?.copy(name = updated.name),
                        isRenaming = false,
                        renamed = true,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isRenaming = false, error = e.userMessage("No se pudo renombrar el equipo"))
                }
            }
        }
    }

    fun consumeRenamed() = _state.update { it.copy(renamed = false) }

    fun delete() {
        val id = teamId ?: return
        if (_state.value.isDeleting) return
        _state.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            try {
                deleteTeam(id)
                _state.update { it.copy(isDeleting = false, closed = true) }
                refreshBus.emit(RefreshEvent.TEAMS)
            } catch (e: Exception) {
                _state.update {
                    it.copy(isDeleting = false, error = e.userMessage("No se pudo borrar el equipo"))
                }
            }
        }
    }

    fun remove(playerId: String) {
        val id = teamId ?: return
        if (playerId in _state.value.removingIds) return
        _state.update { it.copy(removingIds = it.removingIds + playerId) }
        viewModelScope.launch {
            try {
                removeMember(id, playerId)
                _state.update { s ->
                    s.copy(
                        team = s.team?.copy(members = s.members.filterNot { it.playerId == playerId }),
                        removingIds = s.removingIds - playerId,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        removingIds = it.removingIds - playerId,
                        error = e.userMessage("No se pudo quitar al jugador"),
                    )
                }
            }
        }
    }

    /** El miembro se sale: es el mismo "quitar" con su propio id. */
    fun leave() {
        val id = teamId ?: return
        if (_state.value.isLeaving) return
        _state.update { it.copy(isLeaving = true) }
        viewModelScope.launch {
            try {
                val me = authRepository.currentUserId().orEmpty()
                removeMember(id, me)
                _state.update { it.copy(isLeaving = false, closed = true) }
                refreshBus.emit(RefreshEvent.TEAMS)
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLeaving = false, error = e.userMessage("No se pudo salir del equipo"))
                }
            }
        }
    }

    // --- Sheet "Agregar jugador" ---

    /** Al abrir el sheet: sugerencias sin quienes ya están en el equipo. */
    fun openAddPlayer() {
        val id = teamId ?: return
        _state.update {
            it.copy(
                suggestionsLoading = true,
                suggestionsError = null,
                email = "",
                emailError = null,
                emailInvitedName = null,
            )
        }
        viewModelScope.launch {
            try {
                val list = getSuggestions(id)
                _state.update { it.copy(suggestions = list, suggestionsLoading = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        suggestionsLoading = false,
                        suggestionsError = e.userMessage("No se pudieron cargar las sugerencias"),
                    )
                }
            }
        }
    }

    fun inviteSuggested(playerId: String) {
        val id = teamId ?: return
        val current = _state.value
        if (playerId in current.invitingIds || !current.canAddMember) return
        _state.update { it.copy(invitingIds = it.invitingIds + playerId) }
        viewModelScope.launch {
            try {
                val member = inviteToTeam(id, TeamInviteTarget.ById(playerId))
                _state.update { it.withInvited(member).copy(invitingIds = it.invitingIds - playerId) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        invitingIds = it.invitingIds - playerId,
                        error = e.userMessage("No se pudo invitar al jugador"),
                    )
                }
            }
        }
    }

    fun onEmailChange(value: String) =
        _state.update { it.copy(email = value, emailError = null, emailInvitedName = null) }

    fun inviteByEmail() {
        val id = teamId ?: return
        val current = _state.value
        if (current.isInvitingByEmail || !current.canAddMember) return
        _state.update { it.copy(isInvitingByEmail = true, emailError = null, emailInvitedName = null) }
        viewModelScope.launch {
            try {
                val member = inviteToTeam(id, TeamInviteTarget.ByEmail(current.email))
                _state.update {
                    it.withInvited(member).copy(
                        isInvitingByEmail = false,
                        email = "",
                        emailInvitedName = member.name,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isInvitingByEmail = false,
                        emailError = e.userMessage("No se pudo invitar a ese correo"),
                    )
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** El invitado aparece en la lista (como Invitado) y deja de ser sugerencia. */
    private fun TeamDetailUiState.withInvited(member: TeamMember): TeamDetailUiState = copy(
        team = team?.copy(members = members.filterNot { it.playerId == member.playerId } + member),
        suggestions = suggestions.filterNot { it.playerId == member.playerId },
    )
}
