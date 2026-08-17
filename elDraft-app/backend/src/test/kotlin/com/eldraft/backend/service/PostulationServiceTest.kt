package com.eldraft.backend.service

import com.eldraft.backend.notifications.FcmService
import com.eldraft.backend.repository.ConvocatoryRecord
import com.eldraft.backend.repository.ConvocatoryRepository
import com.eldraft.backend.repository.MyPostulationRecord
import com.eldraft.backend.repository.PositionSlot
import com.eldraft.backend.repository.PostulationRecord
import com.eldraft.backend.repository.PostulationRepository
import com.eldraft.backend.repository.UserRecord
import com.eldraft.backend.repository.UserRepository
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Verifica la lógica de autorización y conflictos de PostulationService usando
 * repos falsos (sin base de datos). Las clases de repo se hicieron `open` para
 * poder sustituir solo los métodos que el servicio consulta.
 */
class PostulationServiceTest {

    private val organizerId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()
    private val convocatoryId = UUID.randomUUID()
    private val postulationId = UUID.randomUUID()

    private fun convocatory(status: String = "active", organizer: UUID = organizerId) =
        ConvocatoryRecord(
            id = convocatoryId, organizerId = organizer, lat = 6.2, lng = -75.5,
            addressText = null, slotsNeeded = 3, positionRequired = "Delantero",
            positionSlots = listOf(PositionSlot("Delantero", 3)),
            fee = 0.0, format = "Fútbol 5", ambiente = "Recocha", status = status,
            scheduledAt = "2026-06-10T19:00:00",
        )

    private fun postulation(status: String = "pending") = PostulationRecord(
        id = postulationId, convocatoryId = convocatoryId, playerId = playerId,
        position = "Delantero", status = status, createdAt = "2026-06-07T10:00:00", player = null,
    )

    private class FakeConvocatoryRepo(val record: ConvocatoryRecord?) : ConvocatoryRepository() {
        override fun findById(id: UUID): ConvocatoryRecord? = record
    }

    private class FakePostulationRepo(
        val createResult: PostulationRecord? = null,
        val byId: PostulationRecord? = null,
        val mine: List<MyPostulationRecord> = emptyList(),
    ) : PostulationRepository() {
        var lastStatus: String? = null
        // Todas las actualizaciones de estado (id -> nuevo estado), para verificar
        // tanto la decisión principal como las cancelaciones automáticas.
        val statusUpdates = mutableListOf<Pair<UUID, String>>()
        override fun create(convocatoryId: UUID, playerId: UUID, position: String): PostulationRecord? = createResult
        override fun findById(id: UUID): PostulationRecord? = byId
        override fun findByPlayer(playerId: UUID): List<MyPostulationRecord> = mine
        override fun updateStatus(id: UUID, status: String): Boolean {
            lastStatus = status
            statusUpdates += id to status
            return true
        }
    }

    /** Convocatoria mínima a una hora dada, para armar MyPostulationRecord en los fakes. */
    private fun convAt(id: UUID, schedule: String) = ConvocatoryRecord(
        id = id, organizerId = organizerId, lat = 6.2, lng = -75.5,
        addressText = null, slotsNeeded = 3, positionRequired = "Delantero",
        positionSlots = listOf(PositionSlot("Delantero", 3)),
        fee = 0.0, format = "Fútbol 5", ambiente = "Recocha", status = "active",
        scheduledAt = schedule,
    )

    private fun mine(id: UUID, status: String, schedule: String) =
        MyPostulationRecord(id = id, status = status, convocatory = convAt(id, schedule))

    /** UserRepo falso: el servicio solo consulta nombre y token FCM para el push. */
    private class FakeUserRepo : UserRepository() {
        override fun findById(userId: UUID): UserRecord? =
            UserRecord(id = userId, firebaseUid = "uid", name = "Test", email = null, phone = null, avatarUrl = null)
        override fun getFcmToken(userId: UUID): String? = null
    }

    /** FCM deshabilitado (sin service account): los envíos son no-ops. */
    private val fcmDisabled = FcmService(serviceAccountPath = null)

    /** Crea el servicio con las dependencias de notificación inertes. */
    private fun service(post: PostulationRepository, conv: ConvocatoryRepository) =
        PostulationService(post, conv, FakeUserRepo(), fcmDisabled, inertMatchLifecycle())

    @Test
    fun apply_exitoso_crea_postulacion() {
        val service = service(
            FakePostulationRepo(createResult = postulation()),
            FakeConvocatoryRepo(convocatory()),
        )
        val result = service.apply(convocatoryId, playerId, "Delantero")
        assertEquals("pending", result.status)
    }

    @Test
    fun apply_a_convocatoria_inexistente_falla() {
        val service = service(FakePostulationRepo(), FakeConvocatoryRepo(null))
        assertFailsWith<PostulationNotFound> { service.apply(convocatoryId, playerId, "Delantero") }
    }

    @Test
    fun apply_a_convocatoria_cerrada_falla() {
        val service = service(
            FakePostulationRepo(createResult = postulation()),
            FakeConvocatoryRepo(convocatory(status = "full")),
        )
        assertFailsWith<PostulationConflict> { service.apply(convocatoryId, playerId, "Delantero") }
    }

    @Test
    fun organizador_no_puede_postularse_a_su_convocatoria() {
        val service = service(
            FakePostulationRepo(createResult = postulation()),
            FakeConvocatoryRepo(convocatory()),
        )
        assertFailsWith<PostulationConflict> { service.apply(convocatoryId, organizerId, "Delantero") }
    }

    @Test
    fun postulacion_duplicada_falla() {
        // El repo devuelve null cuando ya existe.
        val service = service(
            FakePostulationRepo(createResult = null),
            FakeConvocatoryRepo(convocatory()),
        )
        assertFailsWith<PostulationConflict> { service.apply(convocatoryId, playerId, "Delantero") }
    }

    @Test
    fun solo_organizador_ve_postulantes() {
        val service = service(FakePostulationRepo(), FakeConvocatoryRepo(convocatory()))
        assertFailsWith<PostulationForbidden> { service.getApplicants(convocatoryId, playerId) }
    }

    @Test
    fun solo_organizador_aprueba() {
        val service = service(
            FakePostulationRepo(byId = postulation()),
            FakeConvocatoryRepo(convocatory()),
        )
        assertFailsWith<PostulationForbidden> { service.approve(postulationId, playerId) }
    }

    @Test
    fun organizador_aprueba_correctamente() {
        val postRepo = FakePostulationRepo(byId = postulation())
        val service = service(postRepo, FakeConvocatoryRepo(convocatory()))
        service.approve(postulationId, organizerId)
        assertEquals("approved", postRepo.lastStatus)
    }

    @Test
    fun organizador_rechaza_correctamente() {
        val postRepo = FakePostulationRepo(byId = postulation())
        val service = service(postRepo, FakeConvocatoryRepo(convocatory()))
        service.reject(postulationId, organizerId)
        assertEquals("rejected", postRepo.lastStatus)
    }

    // --- Conflicto de horario (duración asumida de 60 min) ---

    @Test
    fun no_puede_postularse_si_ya_tiene_un_aprobado_solapado() {
        // Ya aprobado a las 19:00; la nueva convocatoria es a las 19:00 (choca).
        val postRepo = FakePostulationRepo(
            createResult = postulation(),
            mine = listOf(mine(UUID.randomUUID(), "approved", "2026-06-10T19:00:00")),
        )
        val service = service(postRepo, FakeConvocatoryRepo(convocatory()))
        assertFailsWith<PostulationConflict> { service.apply(convocatoryId, playerId, "Delantero") }
    }

    @Test
    fun puede_postularse_si_el_aprobado_no_solapa() {
        // Aprobado a las 17:30 (termina 18:30); la nueva es a las 19:00 → no choca.
        val postRepo = FakePostulationRepo(
            createResult = postulation(),
            mine = listOf(mine(UUID.randomUUID(), "approved", "2026-06-10T17:30:00")),
        )
        val service = service(postRepo, FakeConvocatoryRepo(convocatory()))
        assertEquals("pending", service.apply(convocatoryId, playerId, "Delantero").status)
    }

    @Test
    fun una_pendiente_solapada_no_bloquea_postularse() {
        // Solo las APROBADAS bloquean; una pendiente a la misma hora sí se permite.
        val postRepo = FakePostulationRepo(
            createResult = postulation(),
            mine = listOf(mine(UUID.randomUUID(), "pending", "2026-06-10T19:00:00")),
        )
        val service = service(postRepo, FakeConvocatoryRepo(convocatory()))
        assertEquals("pending", service.apply(convocatoryId, playerId, "Delantero").status)
    }

    @Test
    fun al_aprobar_se_cancelan_las_pendientes_solapadas() {
        val clashId = UUID.randomUUID()   // pendiente que choca con el aprobado (19:00)
        val farId = UUID.randomUUID()     // pendiente que NO choca (21:00)
        val postRepo = FakePostulationRepo(
            byId = postulation(),         // la que se aprueba, convocatoria a las 19:00
            mine = listOf(
                mine(clashId, "pending", "2026-06-10T19:30:00"),
                mine(farId, "pending", "2026-06-10T21:00:00"),
            ),
        )
        val service = service(postRepo, FakeConvocatoryRepo(convocatory()))
        service.approve(postulationId, organizerId)

        // El aprobado quedó approved y la pendiente solapada quedó cancelled.
        assertTrue(postRepo.statusUpdates.contains(postulationId to "approved"))
        assertTrue(postRepo.statusUpdates.contains(clashId to "cancelled"))
        // La pendiente lejana NO se tocó.
        assertTrue(postRepo.statusUpdates.none { it.first == farId })
    }
}
