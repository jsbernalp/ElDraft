package com.eldraft.data.repository

import com.eldraft.data.local.SessionStore
import com.eldraft.data.models.LoginResponse
import com.eldraft.data.remote.AuthApi
import com.eldraft.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val sessionStore: SessionStore,
) : AuthRepository {

    override suspend fun login(firebaseToken: String): LoginResponse {
        val response = authApi.login(firebaseToken)
        // Persistir la sesión; las APIs leen el token vía AuthTokenProvider.
        sessionStore.save(token = response.token, userId = response.user.id)
        return response
    }

    override suspend fun updatePhone(phone: String) {
        authApi.updatePhone(phone)
    }

    override suspend fun currentUserId(): String? = sessionStore.currentUserId()

    override suspend fun hasSession(): Boolean = !sessionStore.currentToken().isNullOrBlank()

    override suspend fun logout() = sessionStore.clear()
}
