package com.eldraft.domain.usecase.auth

import com.eldraft.domain.repository.AuthRepository

/**
 * Reporta al backend la última ubicación conocida del dispositivo, para que el
 * usuario reciba notificaciones de convocatorias cercanas. Best-effort: el
 * repositorio ignora fallos de red para no afectar el flujo de la app.
 */
class ReportLocationUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(lat: Double, lng: Double) {
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return
        authRepository.updateLocation(lat, lng)
    }
}
