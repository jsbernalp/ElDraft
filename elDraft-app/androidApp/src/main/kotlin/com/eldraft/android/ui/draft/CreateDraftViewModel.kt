package com.eldraft.android.ui.draft

import com.eldraft.core.network.ApiException
import com.eldraft.core.network.userMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.data.models.CreateConvocatoryRequest
import com.eldraft.data.models.ScheduleConflictItem
import com.eldraft.data.models.Team
import com.eldraft.domain.team.TeamRules
import com.eldraft.domain.usecase.convocatory.CreateConvocatoryUseCase
import com.eldraft.domain.usecase.team.GetMyTeamsUseCase
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

    /**
     * La convocatoria choca con postulaciones del organizador. Se pide
     * confirmación: si acepta, se reintenta cancelándolas. [conflicts] son los
     * partidos que se cancelarían.
     */
    data class ConfirmCancel(val conflicts: List<ScheduleConflictItem>) : CreateDraftUiState
}

class CreateDraftViewModel(
    private val createConvocatory: CreateConvocatoryUseCase,
    private val getMyTeams: GetMyTeamsUseCase,
) : ViewModel() {

    private val _teams = MutableStateFlow<List<Team>>(emptyList())

    /**
     * Mis equipos que se pueden avisar primero: solo los que tienen al menos un
     * miembro activo (con 0 la convocatoria quedaría privada para nadie y el
     * backend responde 400). Si la carga falla queda vacía y la sección se oculta:
     * el equipo es opcional, no debe estorbar la creación.
     */
    val teams: StateFlow<List<Team>> = _teams.asStateFlow()

    init {
        viewModelScope.launch {
            _teams.value = try {
                TeamRules.prioritizable(getMyTeams())
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private val _state = MutableStateFlow<CreateDraftUiState>(CreateDraftUiState.Idle)
    val state: StateFlow<CreateDraftUiState> = _state.asStateFlow()

    // Último request, para reintentar con cancelConflicts=true si el usuario confirma.
    private var pendingRequest: CreateConvocatoryRequest? = null

    fun create(request: CreateConvocatoryRequest) {
        if (_state.value is CreateDraftUiState.Saving) return
        pendingRequest = request
        _state.value = CreateDraftUiState.Saving
        viewModelScope.launch {
            try {
                createConvocatory(request)
                _state.value = CreateDraftUiState.Created
            } catch (e: ApiException) {
                // Conflicto con sus postulaciones: ofrecer cancelar y reintentar.
                if (e.code == "SCHEDULE_CONFLICT" && e.scheduleConflicts.isNotEmpty()) {
                    _state.value = CreateDraftUiState.ConfirmCancel(e.scheduleConflicts)
                } else {
                    _state.value = CreateDraftUiState.Error(e.userMessage("No se pudo crear la convocatoria"))
                }
            } catch (e: Exception) {
                _state.value = CreateDraftUiState.Error(e.userMessage("No se pudo crear la convocatoria"))
            }
        }
    }

    /** El usuario confirmó cancelar sus postulaciones en conflicto: reintenta. */
    fun confirmCancelConflicts() {
        val request = pendingRequest ?: return
        create(request.copy(cancelConflicts = true))
    }

    /** El usuario rechazó el diálogo de conflicto: vuelve al formulario. */
    fun dismissConfirm() {
        if (_state.value is CreateDraftUiState.ConfirmCancel) _state.value = CreateDraftUiState.Idle
    }

    fun resetError() {
        if (_state.value is CreateDraftUiState.Error) _state.value = CreateDraftUiState.Idle
    }
}
