package com.eldraft.domain.usecase

import com.eldraft.data.models.LoginResponse
import com.eldraft.data.models.User
import com.eldraft.domain.auth.IdentitySessionCleaner
import com.eldraft.domain.repository.AuthRepository
import com.eldraft.domain.usecase.auth.LogoutUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeAuthRepoForLogout(private var userId: String? = "user-1") : AuthRepository {
    var logoutCalled = false
    override suspend fun login(firebaseToken: String): LoginResponse = error("no usado")
    override suspend fun updatePhone(phone: String) {}
    override suspend fun registerFcmToken(token: String) {}
    override suspend fun updateLocation(lat: Double, lng: Double) {}
    override suspend fun currentUserId(): String? = userId
    override suspend fun hasSession(): Boolean = userId != null
    override suspend fun logout() { logoutCalled = true; userId = null }
    override suspend fun getMe(): User = User(id = userId ?: "", name = "Test")
    override suspend fun updateAccount(name: String, avatarUrl: String?): User =
        User(id = userId ?: "", name = name, avatarUrl = avatarUrl)
}

private class FakeIdentityCleaner(
    private val falla: Boolean = false,
) : IdentitySessionCleaner {
    var clearCalled = false
    override suspend fun clear() {
        clearCalled = true
        if (falla) throw IllegalStateException("Credential Manager no disponible")
    }
}

class LogoutUseCaseTest {

    @Test
    fun `cierra la sesion de Firebase ademas de la local`() = runTest {
        val auth = FakeAuthRepoForLogout()
        val identity = FakeIdentityCleaner()

        LogoutUseCase(auth, identity).invoke()

        assertTrue(identity.clearCalled, "debe cerrar la sesión del proveedor de identidad")
        assertTrue(auth.logoutCalled, "debe limpiar la sesión local")
        assertFalse(auth.hasSession())
    }

    /**
     * El caso que importa: si Firebase o Credential Manager fallan, el usuario tiene
     * que quedar fuera igual. Dejarlo dentro de la app porque el proveedor no
     * respondió sería peor que arrastrar una sesión de Firebase colgada.
     */
    @Test
    fun `limpia la sesion local aunque falle el cierre del proveedor`() = runTest {
        val auth = FakeAuthRepoForLogout()
        val identity = FakeIdentityCleaner(falla = true)

        LogoutUseCase(auth, identity).invoke()

        assertTrue(identity.clearCalled)
        assertTrue(auth.logoutCalled, "el fallo del proveedor no debe abortar el logout")
        assertFalse(auth.hasSession())
    }
}
