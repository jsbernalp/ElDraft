package com.eldraft.domain.usecase

import com.eldraft.data.models.LoginResponse
import com.eldraft.data.models.PlayerProfile
import com.eldraft.data.models.UpdateProfileRequest
import com.eldraft.data.models.User
import com.eldraft.domain.repository.AuthRepository
import com.eldraft.domain.repository.ProfileRepository
import com.eldraft.domain.usecase.profile.SaveProfileInput
import com.eldraft.domain.usecase.profile.SaveProfileUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeAuthRepository(
    private var userId: String? = "user-1",
) : AuthRepository {
    var updatePhoneCalledWith: String? = null
    override suspend fun login(firebaseToken: String): LoginResponse = error("no usado")
    override suspend fun updatePhone(phone: String) { updatePhoneCalledWith = phone }
    override suspend fun registerFcmToken(token: String) {}
    override suspend fun updateLocation(lat: Double, lng: Double) {}
    override suspend fun currentUserId(): String? = userId
    override suspend fun hasSession(): Boolean = userId != null
    override suspend fun logout() { userId = null }
    override suspend fun getMe(): User = User(id = userId ?: "", name = "Test")
    override suspend fun updateAccount(name: String, avatarUrl: String?): User =
        User(id = userId ?: "", name = name, avatarUrl = avatarUrl)
}

private class FakeProfileRepository : ProfileRepository {
    var lastPlayerId: String? = null
    var lastRequest: UpdateProfileRequest? = null
    override suspend fun getProfile(playerId: String): PlayerProfile = error("no usado")
    override suspend fun updateProfile(playerId: String, request: UpdateProfileRequest): PlayerProfile {
        lastPlayerId = playerId
        lastRequest = request
        return PlayerProfile(
            userId = playerId,
            positionPrimary = request.positionPrimary,
            positionSecondary = request.positionSecondary,
            dominantFoot = request.dominantFoot,
            height = request.height,
            build = request.build,
        )
    }
}

class SaveProfileUseCaseTest {

    @Test
    fun guarda_perfil_con_telefono_actualiza_ambos() = runTest {
        val auth = FakeAuthRepository(userId = "user-42")
        val profile = FakeProfileRepository()
        val useCase = SaveProfileUseCase(auth, profile)

        val result = useCase(
            SaveProfileInput(
                positionPrimary = "Delantero",
                positionSecondary = "Extremo",
                dominantFoot = "Derecho",
                height = 180,
                build = "Atlético",
                phone = "3001112233",
            )
        )

        assertEquals("3001112233", auth.updatePhoneCalledWith)
        assertEquals("user-42", profile.lastPlayerId)
        assertEquals("Delantero", result.positionPrimary)
        assertEquals(180, result.height)
    }

    @Test
    fun sin_telefono_no_llama_updatePhone() = runTest {
        val auth = FakeAuthRepository(userId = "user-1")
        val profile = FakeProfileRepository()
        val useCase = SaveProfileUseCase(auth, profile)

        useCase(
            SaveProfileInput(
                positionPrimary = "Defensa",
                positionSecondary = null,
                dominantFoot = "Zurdo",
                height = null,
                build = null,
                phone = null,
            )
        )

        assertEquals(null, auth.updatePhoneCalledWith)
        assertEquals("user-1", profile.lastPlayerId)
    }

    @Test
    fun blanquea_campos_vacios_a_null() = runTest {
        val auth = FakeAuthRepository(userId = "user-1")
        val profile = FakeProfileRepository()
        val useCase = SaveProfileUseCase(auth, profile)

        useCase(
            SaveProfileInput(
                positionPrimary = "Arquero",
                positionSecondary = "   ",
                dominantFoot = "Derecho",
                height = null,
                build = "",
                phone = "  ",
            )
        )

        assertEquals(null, profile.lastRequest?.positionSecondary)
        assertEquals(null, profile.lastRequest?.build)
        // phone en blanco no debe disparar updatePhone
        assertEquals(null, auth.updatePhoneCalledWith)
    }

    @Test
    fun sin_sesion_lanza_error() = runTest {
        val auth = FakeAuthRepository(userId = null)
        val profile = FakeProfileRepository()
        val useCase = SaveProfileUseCase(auth, profile)

        assertFailsWith<IllegalStateException> {
            useCase(
                SaveProfileInput(
                    positionPrimary = "Delantero",
                    positionSecondary = null,
                    dominantFoot = "Derecho",
                    height = null,
                    build = null,
                    phone = null,
                )
            )
        }
        assertFalse(profile.lastPlayerId != null)
        assertTrue(profile.lastRequest == null)
    }
}
