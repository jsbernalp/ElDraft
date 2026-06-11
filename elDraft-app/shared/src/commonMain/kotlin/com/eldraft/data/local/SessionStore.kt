package com.eldraft.data.local

/**
 * Almacenamiento de la sesión (JWT + userId). Abstracción multiplataforma;
 * la implementación de Android usa Jetpack DataStore.
 */
interface SessionStore {
    suspend fun currentToken(): String?
    suspend fun currentUserId(): String?
    suspend fun save(token: String, userId: String)
    suspend fun clear()
}
