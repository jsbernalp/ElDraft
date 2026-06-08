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
    var submitted: Triple<String, String, Int>? = null
    override suspend fun getTeammates(convocatoryId: String): List<Teammate> =
        listOf(Teammate(userId = "u2", name = "Compa"))
    override suspend fun submitRating(convocatoryId: String, ratedPlayerId: String, score: Int) {
        submitted = Triple(convocatoryId, ratedPlayerId, score)
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
        SubmitRatingUseCase(repo)("c1", "u2", 4)
        assertEquals(Triple("c1", "u2", 4), repo.submitted)
    }

    @Test
    fun rating_fuera_de_rango_falla_sin_llamar_repo() = runTest {
        val repo = FakeRatingRepository()
        assertFailsWith<IllegalArgumentException> { SubmitRatingUseCase(repo)("c1", "u2", 0) }
        assertFailsWith<IllegalArgumentException> { SubmitRatingUseCase(repo)("c1", "u2", 6) }
        assertNull(repo.submitted)
    }

    @Test
    fun rating_jugador_vacio_falla() = runTest {
        val repo = FakeRatingRepository()
        assertFailsWith<IllegalArgumentException> { SubmitRatingUseCase(repo)("c1", "", 3) }
        assertNull(repo.submitted)
    }
}
