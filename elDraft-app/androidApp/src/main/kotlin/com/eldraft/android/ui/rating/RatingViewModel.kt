package com.eldraft.android.ui.rating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.core.network.ApiException
import com.eldraft.core.network.userMessage
import com.eldraft.data.models.Teammate
import com.eldraft.domain.usecase.rating.GetTeammatesToRateUseCase
import com.eldraft.domain.usecase.rating.SubmitRatingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Los 3 criterios calificables (cada uno 0 = sin elegir, 1..5 = nota). */
enum class RatingCriterion { SKILL, SPORTSMANSHIP, RESPONSIBILITY }

/** Notas que el usuario asigna a un compañero, una por criterio. */
data class TeammateScores(
    val skill: Int = 0,
    val sportsmanship: Int = 0,
    val responsibility: Int = 0,
) {
    /** Las 3 notas están elegidas (listas para enviar). */
    val isComplete: Boolean get() = skill > 0 && sportsmanship > 0 && responsibility > 0

    fun with(criterion: RatingCriterion, value: Int) = when (criterion) {
        RatingCriterion.SKILL -> copy(skill = value)
        RatingCriterion.SPORTSMANSHIP -> copy(sportsmanship = value)
        RatingCriterion.RESPONSIBILITY -> copy(responsibility = value)
    }
}

/** Estado de la pantalla de calificación post-partido. */
data class RatingUiState(
    val teammates: List<Teammate> = emptyList(),
    /** Notas por jugador (userId -> notas de los 3 criterios). */
    val scores: Map<String, TeammateScores> = emptyMap(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val done: Boolean = false,
    /** True cuando el backend rechaza por no haber registrado asistencia (403). */
    val notAttended: Boolean = false,
    val error: String? = null,
)

class RatingViewModel(
    private val getTeammates: GetTeammatesToRateUseCase,
    private val submitRating: SubmitRatingUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(RatingUiState())
    val state: StateFlow<RatingUiState> = _state.asStateFlow()

    fun load(convocatoryId: String) {
        _state.update { it.copy(isLoading = true, error = null, notAttended = false) }
        viewModelScope.launch {
            try {
                val list = getTeammates(convocatoryId)
                _state.update { it.copy(teammates = list, isLoading = false) }
            } catch (e: Exception) {
                // 403 ⇒ el jugador aún no marcó asistencia: no es un error real,
                // es un estado que merece su propio mensaje (no un snackbar crudo).
                if (e is ApiException && e.status == 403) {
                    _state.update { it.copy(isLoading = false, notAttended = true) }
                } else {
                    _state.update {
                        it.copy(isLoading = false, error = e.userMessage("No se pudieron cargar los compañeros"))
                    }
                }
            }
        }
    }

    fun setCriterion(userId: String, criterion: RatingCriterion, value: Int) {
        _state.update {
            val current = it.scores[userId] ?: TeammateScores()
            it.copy(scores = it.scores + (userId to current.with(criterion, value)))
        }
    }

    /**
     * Envía solo las calificaciones nuevas (no las ya enviadas) y que tengan
     * los 3 criterios completos. Si no hay ninguna lista, termina sin enviar.
     */
    fun submitAll(convocatoryId: String) {
        if (_state.value.isSubmitting) return
        val pending = _state.value.scores.filter { (userId, scores) ->
            scores.isComplete &&
                _state.value.teammates.any { it.userId == userId && !it.alreadyRated }
        }
        if (pending.isEmpty()) {
            _state.update { it.copy(done = true) }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                pending.forEach { (userId, scores) ->
                    submitRating(
                        convocatoryId,
                        userId,
                        scores.skill,
                        scores.sportsmanship,
                        scores.responsibility,
                    )
                }
                _state.update { it.copy(isSubmitting = false, done = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSubmitting = false, error = e.userMessage("No se pudieron enviar las calificaciones")) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
