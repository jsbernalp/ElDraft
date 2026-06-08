package com.eldraft.backend.service

import com.eldraft.backend.repository.RatingRepository
import com.eldraft.backend.repository.TeammateRecord
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RatingServiceTest {

    private val convocatoryId = UUID.randomUUID()
    private val rater = UUID.randomUUID()
    private val rated = UUID.randomUUID()

    /**
     * Repo falso configurable: [attendees] son los userIds que asistieron;
     * [rated] marca pares ya calificados.
     */
    private class FakeRatingRepo(
        val attendees: Set<UUID>,
        var alreadyRated: Boolean = false,
    ) : RatingRepository() {
        var saved = false
        var recomputed = false
        override fun attended(convocatoryId: UUID, userId: UUID) = userId in attendees
        override fun teammates(convocatoryId: UUID, requesterId: UUID): List<TeammateRecord> =
            attendees.filter { it != requesterId }.map {
                TeammateRecord(it, "Jugador", null, "Delantero", false)
            }
        override fun hasRated(convocatoryId: UUID, raterId: UUID, ratedPlayerId: UUID) = alreadyRated
        override fun saveRating(convocatoryId: UUID, raterId: UUID, ratedPlayerId: UUID, score: Int) { saved = true }
        override fun recomputeSportsmanship(ratedPlayerId: UUID) { recomputed = true }
    }

    private fun service(repo: RatingRepository) = RatingService(repo)

    // --- teammatesToRate ---

    @Test
    fun asistente_ve_companeros() {
        val svc = service(FakeRatingRepo(attendees = setOf(rater, rated)))
        val list = svc.teammatesToRate(convocatoryId, rater)
        assertEquals(1, list.size)
        assertEquals(rated, list[0].userId)
    }

    @Test
    fun no_asistente_no_ve_companeros() {
        val svc = service(FakeRatingRepo(attendees = setOf(rated)))
        assertFailsWith<RatingForbidden> { svc.teammatesToRate(convocatoryId, rater) }
    }

    // --- submit ---

    @Test
    fun asistente_califica_a_asistente() {
        val repo = FakeRatingRepo(attendees = setOf(rater, rated))
        service(repo).submit(convocatoryId, rater, rated, 4)
        assertTrue(repo.saved)
        assertTrue(repo.recomputed)
    }

    @Test
    fun score_fuera_de_rango_falla() {
        val repo = FakeRatingRepo(attendees = setOf(rater, rated))
        assertFailsWith<RatingInvalid> { service(repo).submit(convocatoryId, rater, rated, 0) }
        assertFailsWith<RatingInvalid> { service(repo).submit(convocatoryId, rater, rated, 6) }
    }

    @Test
    fun no_autocalificarse() {
        val repo = FakeRatingRepo(attendees = setOf(rater))
        assertFailsWith<RatingForbidden> { service(repo).submit(convocatoryId, rater, rater, 5) }
    }

    @Test
    fun rater_que_no_asistio_no_califica() {
        val repo = FakeRatingRepo(attendees = setOf(rated)) // rater no asistió
        assertFailsWith<RatingForbidden> { service(repo).submit(convocatoryId, rater, rated, 5) }
    }

    @Test
    fun no_calificar_a_quien_no_asistio() {
        val repo = FakeRatingRepo(attendees = setOf(rater)) // rated no asistió
        assertFailsWith<RatingForbidden> { service(repo).submit(convocatoryId, rater, rated, 5) }
    }

    @Test
    fun no_calificar_dos_veces() {
        val repo = FakeRatingRepo(attendees = setOf(rater, rated), alreadyRated = true)
        assertFailsWith<RatingConflict> { service(repo).submit(convocatoryId, rater, rated, 5) }
    }
}
