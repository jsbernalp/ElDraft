package com.eldraft.domain.usecase

import com.eldraft.data.models.Teammate
import com.eldraft.domain.repository.RatingRepository
import com.eldraft.domain.usecase.rating.GetTeammatesToRateUseCase
import com.eldraft.domain.usecase.rating.SubmitRatingUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private class FakeRatingRepository : RatingRepository {
    /** (convocatoryId, ratedPlayerId, skill, sportsmanship, responsibility) */
    var submitted: List<Any>? = null
    override suspend fun getTeammates(convocatoryId: String): List<Teammate> =
        listOf(Teammate(userId = "u2", name = "Compa"))
    override suspend fun submitRating(
        convocatoryId: String,
        ratedPlayerId: String,
        skill: Int,
        sportsmanship: Int,
        responsibility: Int,
    ) {
        submitted = listOf(convocatoryId, ratedPlayerId, skill, sportsmanship, responsibility)
    }
}

class RatingUseCasesTest {

    @Test
    fun lista_companeros() = runTest {
        val list = GetTeammatesToRateUseCase(FakeRatingRepository())("c1")
        assertEquals(1, list.size)
    }

    @Test
    fun enviar_rating_valido() = runTest {
        val repo = FakeRatingRepository()
        SubmitRatingUseCase(repo)("c1", "u2", skill = 4, sportsmanship = 5, responsibility = 3)
        assertEquals(listOf<Any>("c1", "u2", 4, 5, 3), repo.submitted)
    }

    @Test
    fun rating_fuera_de_rango_falla_sin_llamar_repo() = runTest {
        val repo = FakeRatingRepository()
        // Cualquiera de los 3 criterios fuera de 1..5 debe fallar.
        assertFailsWith<IllegalArgumentException> { SubmitRatingUseCase(repo)("c1", "u2", 0, 3, 3) }
        assertFailsWith<IllegalArgumentException> { SubmitRatingUseCase(repo)("c1", "u2", 3, 6, 3) }
        assertFailsWith<IllegalArgumentException> { SubmitRatingUseCase(repo)("c1", "u2", 3, 3, 0) }
        assertNull(repo.submitted)
    }

    @Test
    fun rating_jugador_vacio_falla() = runTest {
        val repo = FakeRatingRepository()
        assertFailsWith<IllegalArgumentException> { SubmitRatingUseCase(repo)("c1", "", 3, 3, 3) }
        assertNull(repo.submitted)
    }
}
