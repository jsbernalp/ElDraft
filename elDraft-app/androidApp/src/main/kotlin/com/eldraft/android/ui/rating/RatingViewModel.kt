package com.eldraft.android.ui.rating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.data.models.Teammate
import com.eldraft.domain.usecase.rating.GetTeammatesToRateUseCase
import com.eldraft.domain.usecase.rating.SubmitRatingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la pantalla de calificación post-partido. */
data class RatingUiState(
    val teammates: List<Teammate> = emptyList(),
    /** Puntuación elegida por jugador (userId -> 1..5). */
    val scores: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
)

class RatingViewModel(
    private val getTeammates: GetTeammatesToRateUseCase,
    private val submitRating: SubmitRatingUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(RatingUiState())
    val state: StateFlow<RatingUiState> = _state.asStateFlow()

    fun load(convocatoryId: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val list = getTeammates(convocatoryId)
                _state.update { it.copy(teammates = list, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "No se pudieron cargar los compañeros") }
            }
        }
    }

    fun setScore(userId: String, score: Int) {
        _state.update { it.copy(scores = it.scores + (userId to score)) }
    }

    /** Envía solo las calificaciones nuevas (no las ya enviadas). */
    fun submitAll(convocatoryId: String) {
        if (_state.value.isSubmitting) return
        val pending = _state.value.scores.filterKeys { userId ->
            _state.value.teammates.any { it.userId == userId && !it.alreadyRated }
        }
        if (pending.isEmpty()) {
            _state.update { it.copy(done = true) }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                pending.forEach { (userId, score) ->
                    submitRating(convocatoryId, userId, score)
                }
                _state.update { it.copy(isSubmitting = false, done = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSubmitting = false, error = e.message ?: "No se pudieron enviar las calificaciones") }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
