package com.eldraft.android.ui.postulation

import com.eldraft.core.network.userMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.data.models.Postulation
import com.eldraft.domain.usecase.postulation.ApproveApplicantUseCase
import com.eldraft.domain.usecase.postulation.GetApplicantsUseCase
import com.eldraft.domain.usecase.postulation.RejectApplicantUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la pantalla de postulantes (vista del organizador). */
data class ApplicantsUiState(
    val applicants: List<Postulation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** IDs de postulaciones con una decisión en curso (para deshabilitar botones). */
    val decidingIds: Set<String> = emptySet(),
)

class ApplicantsViewModel(
    private val getApplicants: GetApplicantsUseCase,
    private val approveApplicant: ApproveApplicantUseCase,
    private val rejectApplicant: RejectApplicantUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ApplicantsUiState())
    val state: StateFlow<ApplicantsUiState> = _state.asStateFlow()

    fun load(convocatoryId: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val list = getApplicants(convocatoryId)
                _state.update { it.copy(applicants = list, isLoading = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.userMessage("No se pudieron cargar los postulantes"))
                }
            }
        }
    }

    fun approve(postulationId: String) = decide(postulationId) { approveApplicant(it) }

    fun reject(postulationId: String) = decide(postulationId) { rejectApplicant(it) }

    private fun decide(postulationId: String, action: suspend (String) -> Postulation) {
        if (postulationId in _state.value.decidingIds) return
        _state.update { it.copy(decidingIds = it.decidingIds + postulationId) }
        viewModelScope.launch {
            try {
                val updated = action(postulationId)
                _state.update { s ->
                    s.copy(
                        applicants = s.applicants.map { if (it.id == updated.id) updated else it },
                        decidingIds = s.decidingIds - postulationId,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        decidingIds = it.decidingIds - postulationId,
                        error = e.userMessage("No se pudo actualizar la postulación"),
                    )
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
