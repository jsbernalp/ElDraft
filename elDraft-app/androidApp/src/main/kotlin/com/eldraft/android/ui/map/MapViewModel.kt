package com.eldraft.android.ui.map

import com.eldraft.core.network.userMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.data.models.Convocatory
import com.eldraft.domain.repository.ConvocatoryRepository
import com.eldraft.domain.usecase.auth.ReportLocationUseCase
import com.eldraft.domain.usecase.convocatory.ObserveMapEventsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del mapa: pines cargados + estado de carga/error. */
data class MapUiState(
    val pins: List<Convocatory> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class MapViewModel(
    private val convocatoryRepository: ConvocatoryRepository,
    private val observeMapEvents: ObserveMapEventsUseCase,
    private val reportLocationUseCase: ReportLocationUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private var wsJob: Job? = null

    /**
     * Reporta la ubicación REAL del usuario al backend (para notificarle
     * convocatorias cercanas). Best-effort: solo debe llamarse con una
     * ubicación real del dispositivo, nunca con el centro por defecto.
     */
    fun reportLocation(lat: Double, lng: Double) {
        viewModelScope.launch { reportLocationUseCase.invoke(lat, lng) }
    }

    /**
     * Carga los pines cercanos (snapshot REST) y se suscribe al WebSocket para
     * recibir nuevos pines en tiempo real, en el radio dado.
     */
    fun loadArea(lat: Double, lng: Double, radius: Double = 5000.0) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val nearby = convocatoryRepository.getNearby(lat, lng, radius)
                _state.update { it.copy(pins = nearby, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.userMessage("No se pudieron cargar los partidos")) }
            }
        }
        subscribeRealtime(lat, lng, radius)
    }

    private fun subscribeRealtime(lat: Double, lng: Double, radius: Double) {
        wsJob?.cancel()
        wsJob = viewModelScope.launch {
            observeMapEvents(lat, lng, radius)
                .catch { /* El WS puede cerrarse; ignoramos para no romper la UI. */ }
                .collect { event ->
                    when (event.event) {
                        "new_pin" -> addOrUpdatePin(event.data.toConvocatory())
                        "pin_closed" -> removePin(event.data.id)
                    }
                }
        }
    }

    private fun addOrUpdatePin(pin: Convocatory) {
        _state.update { s ->
            if (s.pins.any { it.id == pin.id }) s
            else s.copy(pins = s.pins + pin)
        }
    }

    private fun removePin(id: String) {
        _state.update { s -> s.copy(pins = s.pins.filterNot { it.id == id }) }
    }

    override fun onCleared() {
        wsJob?.cancel()
        super.onCleared()
    }
}

/** Convierte el pin del WebSocket (datos mínimos) a un Convocatory para el mapa. */
private fun com.eldraft.data.models.MapPin.toConvocatory() = Convocatory(
    id = id,
    organizerId = "",
    lat = lat,
    lng = lng,
    slotsNeeded = slots,
    positionRequired = "",
    format = format,
    ambiente = ambiente,
    scheduledAt = "",
)
