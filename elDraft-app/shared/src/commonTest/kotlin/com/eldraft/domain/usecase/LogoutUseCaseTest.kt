package com.eldraft.domain.usecase

import com.eldraft.data.models.LoginResponse
import com.eldraft.data.models.User
import com.eldraft.domain.auth.IdentitySessionCleaner
import com.eldraft.domain.repository.AuthRepository
import com.eldraft.domain.usecase.auth.DeleteAccountUseCase
import com.eldraft.domain.usecase.auth.LogoutUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeAuthRepoForLogout(
    private var userId: String? = "user-1",
    private val fallaBorrado: Boolean = false,
) : AuthRepository {
    var logoutCalled = false
    var deleteCalled = false
    override suspend fun deleteAccount() {
        deleteCalled = true
        if (fallaBorrado) throw IllegalStateException("servidor caído")
        userId = null
    }
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

/**
 * Borrado de cuenta. Comparte los fakes de este archivo porque son `private` a nivel
 * de archivo y ambos casos de uso operan sobre las mismas dos dependencias.
 */
class DeleteAccountUseCaseTest {

    @Test
    fun `borra en el servidor y cierra la sesion del proveedor`() = runTest {
        val auth = FakeAuthRepoForLogout()
        val identity = FakeIdentityCleaner()

        DeleteAccountUseCase(auth, identity).invoke()

        assertTrue(auth.deleteCalled, "debe pedir el borrado al servidor")
        assertTrue(identity.clearCalled, "debe cerrar la sesión de Firebase")
        assertFalse(auth.hasSession())
    }

    /**
     * El caso crítico, y el opuesto al del logout: si el servidor falla, la cuenta
     * SIGUE VIVA. Aquí no se puede degradar con elegancia — dejar al usuario fuera
     * de la app creyendo que se borró, cuando no fue así y ya no puede volver a
     * entrar a reintentarlo, es el peor resultado posible.
     */
    @Test
    fun `si el servidor falla propaga y no cierra la sesion`() = runTest {
        val auth = FakeAuthRepoForLogout(fallaBorrado = true)
        val identity = FakeIdentityCleaner()

        assertFailsWith<IllegalStateException> {
            DeleteAccountUseCase(auth, identity).invoke()
        }

        assertTrue(auth.deleteCalled)
        assertFalse(identity.clearCalled, "no debe cerrar sesión si la cuenta no se borró")
        assertTrue(auth.hasSession(), "el usuario debe seguir dentro para poder reintentar")
    }
}
