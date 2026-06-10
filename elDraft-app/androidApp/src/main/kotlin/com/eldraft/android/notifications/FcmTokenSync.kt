package com.eldraft.android.notifications

import android.util.Log
import com.eldraft.data.local.SessionStore
import com.eldraft.domain.usecase.auth.RegisterFcmTokenUseCase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Obtiene el token FCM actual del dispositivo y lo registra en el backend.
 *
 * Se invoca en dos momentos:
 *  - Tras un login exitoso (vía AuthViewModel).
 *  - En cada arranque de la app con sesión activa (vía SplashScreen).
 *
 * El segundo es clave: si la app se reinstala o el token rota, Firebase emite un
 * token nuevo y el viejo queda "UNREGISTERED" en la BD. Refrescarlo en cada
 * arranque corrige eso sin depender de que el usuario vuelva a iniciar sesión.
 *
 * Best-effort: cualquier fallo se loguea pero nunca rompe el flujo de la app.
 */
class FcmTokenSync(
    private val registerFcmToken: RegisterFcmTokenUseCase,
    private val sessionStore: SessionStore,
) {
    /** Refresca el token solo si hay sesión activa (necesita JWT para el endpoint). */
    suspend fun syncIfLoggedIn() {
        if (sessionStore.currentToken().isNullOrBlank()) return
        sync()
    }

    /** Obtiene y registra el token. Asume que ya hay sesión. */
    suspend fun sync() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            registerFcmToken(token)
        } catch (e: Exception) {
            Log.w("FcmTokenSync", "No se pudo registrar el token FCM: ${e.message}")
        }
    }
}
