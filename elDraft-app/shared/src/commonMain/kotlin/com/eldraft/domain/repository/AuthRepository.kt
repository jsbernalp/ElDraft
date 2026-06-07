package com.eldraft.domain.repository

import com.eldraft.data.models.LoginResponse

/**
 * Operaciones de autenticación y sesión. Las implementaciones encapsulan la
 * llamada a la API, la persistencia del token y la lectura de la sesión.
 */
interface AuthRepository {

    /** Intercambia el token de Firebase/Google por el JWT del backend y lo persiste. */
    suspend fun login(firebaseToken: String): LoginResponse

    /** Actualiza el teléfono del usuario autenticado. */
    suspend fun updatePhone(phone: String)

    /** Id del usuario de la sesión actual (o null). */
    suspend fun currentUserId(): String?

    /** True si hay un token de sesión persistido. */
    suspend fun hasSession(): Boolean

    /** Cierra la sesión local. */
    suspend fun logout()
}
