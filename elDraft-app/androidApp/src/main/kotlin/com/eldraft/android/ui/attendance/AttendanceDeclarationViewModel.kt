package com.eldraft.android.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.core.network.ApiException
import com.eldraft.core.network.userMessage
import com.eldraft.data.models.PlayerAttendanceRow
import com.eldraft.domain.usecase.attendance.DeclareAttendanceUseCase
import com.eldraft.domain.usecase.attendance.GetAttendanceListUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado de la pantalla de declaración de asistencia (vista del organizador).
 * [rows] son los convocados aprobados; [absent] son los ids que el organizador
 * marca como ausentes (toggle local hasta guardar). Quien escaneó no es editable.
 */
data class AttendanceDeclarationUiState(
    val rows: List<PlayerAttendanceRow> = emptyList(),
    val absent: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    /** True si el backend bloqueó al organizador (403): fue marcado no-show. */
    val blocked: Boolean = false,
    val error: String? = null,
)

class AttendanceDeclarationViewModel(
    private val getAttendanceList: GetAttendanceListUseCase,
    private val declareAttendance: DeclareAttendanceUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AttendanceDeclarationUiState())
    val state: StateFlow<AttendanceDeclarationUiState> = _state.asStateFlow()

    fun load(convocatoryId: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val rows = getAttendanceList(convocatoryId)
                _state.update {
                    it.copy(
                        rows = rows,
                        // Pre-selecciona las ausencias ya declaradas previamente.
                        absent = rows.filter { r -> r.markedNoShow }.map { r -> r.playerId }.toSet(),
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        blocked = (e as? ApiException)?.status == 403,
                        error = e.userMessage("No se pudo cargar la asistencia"),
                    )
                }
            }
        }
    }

    /** Alterna la ausencia de un jugador. Ignora a quien escaneó (presencia firme). */
    fun toggleAbsent(playerId: String) {
        val row = _state.value.rows.firstOrNull { it.playerId == playerId } ?: return
        if (row.scanned) return
        _state.update {
            val absent = if (playerId in it.absent) it.absent - playerId else it.absent + playerId
            it.copy(absent = absent)
        }
    }

    fun save(convocatoryId: String) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val rows = declareAttendance(convocatoryId, _state.value.absent.toList())
                _state.update {
                    it.copy(
                        rows = rows,
                        absent = rows.filter { r -> r.markedNoShow }.map { r -> r.playerId }.toSet(),
                        isSaving = false,
                        saved = true,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSaving = false, error = e.userMessage("No se pudo guardar la asistencia"))
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
