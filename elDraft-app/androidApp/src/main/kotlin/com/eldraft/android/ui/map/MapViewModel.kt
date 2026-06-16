package com.eldraft.android.ui.map

import com.eldraft.core.network.userMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.data.models.Convocatory
import com.eldraft.domain.repository.AuthRepository
import com.eldraft.domain.repository.ConvocatoryRepository
import com.eldraft.domain.usecase.auth.ReportLocationUseCase
import com.eldraft.domain.usecase.convocatory.ObserveMapEventsUseCase
import com.eldraft.domain.usecase.postulation.GetMyPostulationsUseCase
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
    /**
     * Convocatorias a las que el usuario ya se postuló, mapeadas a su estado
     * ("pending"/"approved"/"rejected"). Sirve para indicar en la card/sheet que
     * ya se postuló y deshabilitar el botón (el backend solo permite una
     * postulación por convocatoria).
     */
    val myPostulations: Map<String, String> = emptyMap(),
    /**
     * False hasta que termina la primera carga del área (incluye la espera de la
     * ubicación GPS antes de llamar a loadArea). La lista lo usa para mostrar un
     * loading inicial en vez del estado vacío mientras se prepara.
     */
    val hasLoadedOnce: Boolean = false,
)

class MapViewModel(
    private val convocatoryRepository: ConvocatoryRepository,
    private val observeMapEvents: ObserveMapEventsUseCase,
    private val reportLocationUseCase: ReportLocationUseCase,
    private val authRepository: AuthRepository,
    private val getMyPostulations: GetMyPostulationsUseCase,
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
                _state.update { it.copy(pins = nearby, isLoading = false, hasLoadedOnce = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        hasLoadedOnce = true,
                        error = e.userMessage("No se pudieron cargar los partidos"),
                    )
                }
            }
        }
        subscribeRealtime(lat, lng, radius)
        refreshMyPostulations()
    }

    /**
     * Recarga las postulaciones propias para reflejar en la UI a qué
     * convocatorias ya se postuló. Best-effort: si falla, simplemente no se
     * marca nada (la card sigue abriendo el sheet y el backend valida igual).
     * Se llama al cargar el área y tras postularse con éxito.
     */
    fun refreshMyPostulations() {
        viewModelScope.launch {
            val mine = runCatching { getMyPostulations() }.getOrNull() ?: return@launch
            _state.update { s ->
                s.copy(myPostulations = mine.associate { it.convocatory.id to it.status })
            }
        }
    }

    private fun subscribeRealtime(lat: Double, lng: Double, radius: Double) {
        wsJob?.cancel()
        wsJob = viewModelScope.launch {
            // El backend usa el userId para no reenviarnos nuestros propios pines.
            val userId = runCatching { authRepository.currentUserId() }.getOrNull()
            observeMapEvents(lat, lng, radius, userId)
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
