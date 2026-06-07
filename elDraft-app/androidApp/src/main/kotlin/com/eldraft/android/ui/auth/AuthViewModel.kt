package com.eldraft.android.ui.auth

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.android.data.GoogleAuthClient
import com.eldraft.android.data.GoogleAuthException
import com.eldraft.android.data.SessionManager
import com.eldraft.data.remote.AuthApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado del flujo de autenticación, observado por LoginScreen. */
sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    /** Login OK; needsOnboarding decide a qué pantalla navegar. */
    data class Success(val needsOnboarding: Boolean) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel(
    private val authApi: AuthApi,
    private val session: SessionManager,
    private val googleAuth: GoogleAuthClient
) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /** Inicia Google Sign-In, intercambia el token con el backend y guarda la sesión. */
    fun signInWithGoogle() {
        if (_state.value is AuthUiState.Loading) return
        _state.value = AuthUiState.Loading

        viewModelScope.launch {
            try {
                val google = googleAuth.signIn()
                // El backend en modo mock espera un JSON Base64; en modo firebase
                // aceptará el idToken de Google directamente. Enviamos el idToken real;
                // el MockTokenVerifier hace fallback a tratar el token plano como uid.
                val firebaseToken = google.idToken

                val response = authApi.login(firebaseToken)
                // El token queda persistido; las APIs lo leen vía AuthTokenProvider.
                session.save(token = response.token, userId = response.user.id)

                _state.value = AuthUiState.Success(needsOnboarding = response.needsOnboarding)
            } catch (e: GoogleAuthException) {
                _state.value = AuthUiState.Error(e.message ?: "Error al iniciar sesión con Google")
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(e.message ?: "No se pudo conectar con el servidor")
            }
        }
    }

    /**
     * Login de desarrollo SIN Google: genera un token mock (JSON Base64) y lo envía.
     * Útil para probar el flujo en el emulador sin pasar por el selector de cuentas.
     */
    fun signInDev(uid: String = "dev-user", name: String = "Jugador Dev", email: String = "dev@eldraft.app") {
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val payload = """{"uid":"$uid","name":"$name","email":"$email"}"""
                val mockToken = Base64.encodeToString(payload.toByteArray(), Base64.NO_WRAP)

                val response = authApi.login(mockToken)
                session.save(token = response.token, userId = response.user.id)

                _state.value = AuthUiState.Success(needsOnboarding = response.needsOnboarding)
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(e.message ?: "No se pudo conectar con el servidor")
            }
        }
    }

    fun resetError() {
        if (_state.value is AuthUiState.Error) _state.value = AuthUiState.Idle
    }
}
