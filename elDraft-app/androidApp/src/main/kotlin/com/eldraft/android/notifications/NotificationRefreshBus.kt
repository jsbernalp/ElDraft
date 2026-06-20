package com.eldraft.android.notifications

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class RefreshEvent {
    MY_MATCHES,       // Tab Organizo: llegó una nueva postulación al organizador
    MY_POSTULATIONS,  // Tab Juego: postulación aprobada o rechazada
    MAP,              // Tab Buscar Cupo: nueva convocatoria o recordatorio cercano
    APPLICANTS,       // Pantalla de postulantes (si está abierta)
}

class NotificationRefreshBus {
    private val _events = MutableSharedFlow<RefreshEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<RefreshEvent> = _events.asSharedFlow()

    fun emit(event: RefreshEvent) {
        _events.tryEmit(event)
    }
}
