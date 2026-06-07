package com.eldraft.android.ui.draft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.data.models.Convocatory
import com.eldraft.domain.repository.ConvocatoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de "Mis convocatorias" (organizador). */
data class MyMatchesUiState(
    val matches: List<Convocatory> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class MyMatchesViewModel(
    private val convocatoryRepository: ConvocatoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MyMatchesUiState())
    val state: StateFlow<MyMatchesUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val mine = convocatoryRepository.getMine()
                _state.update { it.copy(matches = mine, isLoading = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "No se pudieron cargar tus convocatorias")
                }
            }
        }
    }
}
