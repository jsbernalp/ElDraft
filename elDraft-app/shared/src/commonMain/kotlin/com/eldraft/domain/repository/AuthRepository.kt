package com.eldraft.domain.repository

import com.eldraft.data.models.LoginResponse
import com.eldraft.data.models.User

/**
 * Operaciones de autenticación y sesión. Las implementaciones encapsulan la
 * llamada a la API, la persistencia del token y la lectura de la sesión.
 */
interface AuthRepository {

    /** Intercambia el token de Firebase/Google por el JWT del backend y lo persiste. */
    suspend fun login(firebaseToken: String): LoginResponse

    /** Actualiza el teléfono del usuario autenticado. */
    suspend fun updatePhone(phone: String)

    /** Registra el token FCM del dispositivo (best-effort: ignora fallos de red). */
    suspend fun registerFcmToken(token: String)

    /** Reporta la última ubicación conocida (best-effort: ignora fallos de red). */
    suspend fun updateLocation(lat: Double, lng: Double)

    /** Id del usuario de la sesión actual (o null). */
    suspend fun currentUserId(): String?

    /** True si hay un token de sesión persistido. */
    suspend fun hasSession(): Boolean

    /** Cierra la sesión local. */
    suspend fun logout()

    /** Devuelve los datos del usuario autenticado. */
    suspend fun getMe(): User

    /** Actualiza el nombre y avatar del usuario autenticado. */
    suspend fun updateAccount(name: String, avatarUrl: String?): User

    /**
     * Borra la cuenta del usuario y limpia la sesión local. Irreversible.
     * Lanza si el servidor falla, dejando la sesión intacta.
     */
    suspend fun deleteAccount()
}
