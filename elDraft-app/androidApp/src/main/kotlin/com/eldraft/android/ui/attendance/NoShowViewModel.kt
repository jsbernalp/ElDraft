package com.eldraft.android.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.core.network.userMessage
import com.eldraft.data.models.NoShowStatus
import com.eldraft.domain.usecase.attendance.GetNoShowStatusUseCase
import com.eldraft.domain.usecase.attendance.ReportOrganizerNoShowUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del botón "el organizador no se presentó" por convocatoria. */
data class NoShowUiState(
    val status: NoShowStatus? = null,
    val isLoading: Boolean = false,
    val isReporting: Boolean = false,
    val error: String? = null,
)

/**
 * Maneja el reporte de no-show del organizador para una convocatoria concreta.
 * Carga el estado (¿puedo reportar?, votos, consenso) y emite el voto del
 * asistente. El estado se cachea por convocatoryId para reutilizar la instancia
 * entre tarjetas.
 */
class NoShowViewModel(
    private val getStatus: GetNoShowStatusUseCase,
    private val reportNoShow: ReportOrganizerNoShowUseCase,
) : ViewModel() {

    private val states = mutableMapOf<String, MutableStateFlow<NoShowUiState>>()

    private fun flowFor(convocatoryId: String): MutableStateFlow<NoShowUiState> =
        states.getOrPut(convocatoryId) { MutableStateFlow(NoShowUiState()) }

    fun stateFor(convocatoryId: String): StateFlow<NoShowUiState> =
        flowFor(convocatoryId).asStateFlow()

    /** Carga (o recarga) el estado del reporte para la convocatoria. */
    fun load(convocatoryId: String) {
        val flow = flowFor(convocatoryId)
        flow.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val status = getStatus(convocatoryId)
                flow.update { it.copy(status = status, isLoading = false) }
            } catch (e: Exception) {
                flow.update {
                    it.copy(isLoading = false, error = e.userMessage("No se pudo cargar el estado"))
                }
            }
        }
    }

    /** Reporta que el organizador no se presentó. */
    fun report(convocatoryId: String) {
        val flow = flowFor(convocatoryId)
        if (flow.value.isReporting) return
        flow.update { it.copy(isReporting = true, error = null) }
        viewModelScope.launch {
            try {
                val status = reportNoShow(convocatoryId)
                flow.update { it.copy(status = status, isReporting = false) }
            } catch (e: Exception) {
                flow.update {
                    it.copy(isReporting = false, error = e.userMessage("No se pudo reportar"))
                }
            }
        }
    }
}
