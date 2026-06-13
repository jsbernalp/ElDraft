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
     * Repo falso configurable: [attendees] son los userIds que asistieron
     * (escanearon QR); [organizer] es el organizador (calificable aunque no haya
     * asistido); [alreadyRated] marca pares ya calificados.
     */
    private class FakeRatingRepo(
        val attendees: Set<UUID>,
        val organizer: UUID? = null,
        var alreadyRated: Boolean = false,
    ) : RatingRepository() {
        var saved = false
        var recomputed = false
        override fun attended(convocatoryId: UUID, userId: UUID) = userId in attendees
        override fun isOrganizer(convocatoryId: UUID, userId: UUID) = userId == organizer
        override fun isRateable(convocatoryId: UUID, userId: UUID) =
            userId == organizer || userId in attendees
        override fun teammates(convocatoryId: UUID, requesterId: UUID): List<TeammateRecord> =
            (attendees + listOfNotNull(organizer)).filter { it != requesterId }.map {
                TeammateRecord(it, "Jugador", null, "Delantero", false)
            }
        override fun hasRated(convocatoryId: UUID, raterId: UUID, ratedPlayerId: UUID) = alreadyRated
        override fun saveRating(
            convocatoryId: UUID,
            raterId: UUID,
            ratedPlayerId: UUID,
            skill: Int,
            sportsmanship: Int,
            responsibility: Int,
        ) { saved = true }
        override fun recomputeScores(ratedPlayerId: UUID) { recomputed = true }
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
        service(repo).submit(convocatoryId, rater, rated, skill = 4, sportsmanship = 5, responsibility = 3)
        assertTrue(repo.saved)
        assertTrue(repo.recomputed)
    }

    @Test
    fun score_fuera_de_rango_falla() {
        val repo = FakeRatingRepo(attendees = setOf(rater, rated))
        // Cualquiera de los 3 criterios fuera de 1..5 invalida la calificación.
        assertFailsWith<RatingInvalid> { service(repo).submit(convocatoryId, rater, rated, 0, 3, 3) }
        assertFailsWith<RatingInvalid> { service(repo).submit(convocatoryId, rater, rated, 3, 6, 3) }
        assertFailsWith<RatingInvalid> { service(repo).submit(convocatoryId, rater, rated, 3, 3, 0) }
    }

    @Test
    fun no_autocalificarse() {
        val repo = FakeRatingRepo(attendees = setOf(rater))
        assertFailsWith<RatingForbidden> { service(repo).submit(convocatoryId, rater, rater, 5, 5, 5) }
    }

    @Test
    fun rater_que_no_asistio_no_califica() {
        val repo = FakeRatingRepo(attendees = setOf(rated)) // rater no asistió
        assertFailsWith<RatingForbidden> { service(repo).submit(convocatoryId, rater, rated, 5, 5, 5) }
    }

    @Test
    fun no_calificar_a_quien_no_asistio() {
        val repo = FakeRatingRepo(attendees = setOf(rater)) // rated no asistió
        assertFailsWith<RatingForbidden> { service(repo).submit(convocatoryId, rater, rated, 5, 5, 5) }
    }

    @Test
    fun no_calificar_dos_veces() {
        val repo = FakeRatingRepo(attendees = setOf(rater, rated), alreadyRated = true)
        assertFailsWith<RatingConflict> { service(repo).submit(convocatoryId, rater, rated, 5, 5, 5) }
    }

    // --- Caso borde: organizador ausente (no escaneó) ---

    @Test
    fun organizador_ausente_es_calificable_por_asistente() {
        // El organizador no asistió (no está en attendees) pero sí es calificable.
        val organizer = UUID.randomUUID()
        val repo = FakeRatingRepo(attendees = setOf(rater), organizer = organizer)
        service(repo).submit(convocatoryId, rater, organizer, skill = 1, sportsmanship = 2, responsibility = 1)
        assertTrue(repo.saved)
        assertTrue(repo.recomputed)
    }

    @Test
    fun organizador_ausente_no_puede_calificar() {
        // El organizador no escaneó → no asistió → no puede calificar a nadie.
        val organizer = UUID.randomUUID()
        val repo = FakeRatingRepo(attendees = setOf(rated), organizer = organizer)
        assertFailsWith<RatingForbidden> {
            service(repo).submit(convocatoryId, organizer, rated, 5, 5, 5)
        }
    }

    @Test
    fun organizador_presente_califica_normal() {
        // Si el organizador escaneó (está en attendees), califica como cualquiera.
        val organizer = UUID.randomUUID()
        val repo = FakeRatingRepo(attendees = setOf(organizer, rated), organizer = organizer)
        service(repo).submit(convocatoryId, organizer, rated, 4, 4, 4)
        assertTrue(repo.saved)
    }
}
