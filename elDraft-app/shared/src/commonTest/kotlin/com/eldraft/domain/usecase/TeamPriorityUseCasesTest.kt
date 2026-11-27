package com.eldraft.domain.usecase

import com.eldraft.data.models.Convocatory
import com.eldraft.data.models.TeamPriorityInvite
import com.eldraft.domain.repository.TeamPriorityRepository
import com.eldraft.domain.usecase.team.OpenConvocatoryToPublicUseCase
import com.eldraft.domain.usecase.team.RespondTeamPriorityUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeTeamPriorityRepository : TeamPriorityRepository {
    val responses = mutableListOf<Triple<String, Boolean, String?>>()
    val opened = mutableListOf<String>()
    override suspend fun getPriorityInvites(): List<TeamPriorityInvite> = emptyList()
    override suspend fun respond(convocatoryId: String, accept: Boolean, position: String?) {
        responses += Triple(convocatoryId, accept, position)
    }
    override suspend fun openToPublic(convocatoryId: String): Convocatory {
        opened += convocatoryId
        return Convocatory(
            id = convocatoryId, organizerId = "o", lat = 0.0, lng = 0.0, slotsNeeded = 1,
            positionRequired = "Delantero", format = "Fútbol 5", ambiente = "Recocha",
            status = "active", scheduledAt = "2026-06-10T19:00:00",
        )
    }
}

class TeamPriorityUseCasesTest {
    @Test
    fun voy_sin_posicion_no_llega_al_repositorio() = runTest {
        val repo = FakeTeamPriorityRepository()
        assertFailsWith<IllegalArgumentException> { RespondTeamPriorityUseCase(repo)("c1", true, null) }
        assertFailsWith<IllegalArgumentException> { RespondTeamPriorityUseCase(repo)("c1", true, "  ") }
        assertTrue(repo.responses.isEmpty())
    }

    @Test
    fun voy_manda_la_posicion() = runTest {
        val repo = FakeTeamPriorityRepository()
        RespondTeamPriorityUseCase(repo)("c1", true, "Defensa")
        assertEquals(Triple("c1", true, "Defensa"), repo.responses.single())
    }

    @Test
    fun no_puedo_no_manda_posicion_aunque_la_ui_la_conserve() = runTest {
        val repo = FakeTeamPriorityRepository()
        RespondTeamPriorityUseCase(repo)("c1", false, "Defensa")
        assertNull(repo.responses.single().third)
    }

    @Test
    fun id_vacio_se_rechaza() = runTest {
        val repo = FakeTeamPriorityRepository()
        assertFailsWith<IllegalArgumentException> { RespondTeamPriorityUseCase(repo)("", false, null) }
        assertFailsWith<IllegalArgumentException> { OpenConvocatoryToPublicUseCase(repo)("") }
    }

    @Test
    fun abrir_ya_devuelve_la_convocatoria_actualizada() = runTest {
        val repo = FakeTeamPriorityRepository()
        assertEquals("active", OpenConvocatoryToPublicUseCase(repo)("c1").status)
        assertEquals(listOf("c1"), repo.opened)
    }
}
