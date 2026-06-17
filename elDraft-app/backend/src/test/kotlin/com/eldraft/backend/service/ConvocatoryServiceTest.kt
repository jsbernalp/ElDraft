package com.eldraft.backend.service

import com.eldraft.backend.notifications.FcmService
import com.eldraft.backend.repository.ConvocatoryCreate
import com.eldraft.backend.repository.ConvocatoryRecord
import com.eldraft.backend.repository.ConvocatoryRepository
import com.eldraft.backend.repository.MyPostulationRecord
import com.eldraft.backend.repository.NearbyPlayer
import com.eldraft.backend.repository.PositionSlot
import com.eldraft.backend.repository.PostulationRepository
import com.eldraft.backend.repository.UserRepository
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Verifica la lógica de ConvocatoryService con repos falsos (sin base de datos):
 * la validación de creación y que al crear se consulta a los jugadores cercanos
 * con el radio (en metros) correcto y excluyendo al organizador. El push usa un
 * FcmService deshabilitado (no-op), así que no se envía nada real.
 */
class ConvocatoryServiceTest {

    private val organizerId = UUID.randomUUID()
    private val convocatoryId = UUID.randomUUID()

    private fun create(slots: Int = 3, lat: Double = 6.2, lng: Double = -75.5) = ConvocatoryCreate(
        organizerId = organizerId, lat = lat, lng = lng, addressText = null,
        positionSlots = listOf(PositionSlot("Delantero", slots)), fee = 0.0,
        format = "Fútbol 5", ambiente = "Recocha", scheduledAt = "2026-06-10T19:00:00",
    )

    private fun record(lat: Double = 6.2, lng: Double = -75.5) = ConvocatoryRecord(
        id = convocatoryId, organizerId = organizerId, lat = lat, lng = lng,
        addressText = null, slotsNeeded = 3, positionRequired = "Delantero",
        positionSlots = listOf(PositionSlot("Delantero", 3)),
        fee = 0.0, format = "Fútbol 5", ambiente = "Recocha", status = "active",
        scheduledAt = "2026-06-10T19:00:00",
    )

    /** Repo de convocatoria que no toca la BD: create() devuelve un record fijo. */
    private inner class FakeConvocatoryRepo(
        val pending: List<ConvocatoryRecord> = emptyList(),
        /** Convocatorias activas del organizador, para el chequeo de conflicto. */
        val organizing: List<ConvocatoryRecord> = emptyList(),
    ) : ConvocatoryRepository() {
        var createCalled = false
        override fun create(data: ConvocatoryCreate): ConvocatoryRecord {
            createCalled = true
            return record(lat = data.lat, lng = data.lng)
        }
        override fun findActiveWithoutPostulations(): List<ConvocatoryRecord> = pending
        override fun findByOrganizer(organizerId: UUID): List<ConvocatoryRecord> = organizing
    }

    /** Repo de postulaciones falso: expone las del jugador y registra cancelaciones. */
    private class FakePostulationRepo(
        val mine: List<MyPostulationRecord> = emptyList(),
    ) : PostulationRepository() {
        val statusUpdates = mutableListOf<Pair<UUID, String>>()
        override fun findByPlayer(playerId: UUID): List<MyPostulationRecord> = mine
        override fun updateStatus(id: UUID, status: String): Boolean {
            statusUpdates += id to status
            return true
        }
    }

    private fun convAt(schedule: String, status: String = "active") = ConvocatoryRecord(
        id = UUID.randomUUID(), organizerId = UUID.randomUUID(), lat = 6.2, lng = -75.5,
        addressText = null, slotsNeeded = 3, positionRequired = "Delantero",
        positionSlots = listOf(PositionSlot("Delantero", 3)),
        fee = 0.0, format = "Fútbol 5", ambiente = "Recocha", status = status,
        scheduledAt = schedule,
    )

    /** Captura los argumentos con que se buscó a los jugadores cercanos. */
    private class CapturingUserRepo(val recipients: List<NearbyPlayer>) : UserRepository() {
        var lat: Double? = null
        var lng: Double? = null
        var radiusMeters: Double? = null
        var excluded: UUID? = null
        var calls = 0
        override fun findNearbyPlayersToNotify(
            lat: Double, lng: Double, radiusMeters: Double, excludeUserId: UUID,
        ): List<NearbyPlayer> {
            calls++
            this.lat = lat; this.lng = lng
            this.radiusMeters = radiusMeters; this.excluded = excludeUserId
            return recipients
        }
        // FCM deshabilitado: sin token, el push de cancelación es no-op.
        override fun getFcmToken(userId: UUID): String? = null
    }

    private val fcmDisabled = FcmService(serviceAccountPath = null)

    private fun service(
        repo: ConvocatoryRepository,
        users: UserRepository,
        radiusKm: Double = 50.0,
        posts: PostulationRepository = FakePostulationRepo(),
    ) = ConvocatoryService(
        repository = repo, postulations = posts, users = users,
        fcm = fcmDisabled, nearbyRadiusKm = radiusKm,
    )

    @Test
    fun create_invalido_lanza_y_no_crea_ni_notifica() {
        val repo = FakeConvocatoryRepo()
        val users = CapturingUserRepo(emptyList())
        val service = service(repo, users)
        // slotsNeeded = 0 es inválido → falla antes de crear o notificar.
        assertFailsWith<IllegalArgumentException> { service.create(create(slots = 0)) }
        assertEquals(false, repo.createCalled)
        assertEquals(null, users.lat, "No debe consultarse cercanía si la validación falla")
    }

    @Test
    fun create_notifica_con_radio_en_metros_y_excluye_organizador() {
        val repo = FakeConvocatoryRepo()
        val users = CapturingUserRepo(listOf(NearbyPlayer(UUID.randomUUID(), "token-1")))
        val service = service(repo, users, radiusKm = 50.0)

        val created = service.create(create())

        assertEquals(convocatoryId, created.id)
        assertEquals(true, repo.createCalled)
        // El radio en km se convierte a metros para PostGIS.
        assertEquals(50_000.0, users.radiusMeters)
        // Se excluye al organizador y se usan las coordenadas de la convocatoria.
        assertEquals(organizerId, users.excluded)
        assertEquals(6.2, users.lat)
        assertEquals(-75.5, users.lng)
    }

    @Test
    fun create_sin_jugadores_cercanos_no_falla() {
        val repo = FakeConvocatoryRepo()
        val users = CapturingUserRepo(emptyList())
        val service = service(repo, users)
        // No debe lanzar aunque no haya destinatarios.
        val created = service.create(create())
        assertEquals(convocatoryId, created.id)
    }

    @Test
    fun recordatorio_reenvia_a_convocatorias_sin_postulantes() {
        // Dos convocatorias pendientes → dos reenvíos.
        val pending = listOf(record(), record(lat = 4.6, lng = -74.1))
        val repo = FakeConvocatoryRepo(pending = pending)
        val users = CapturingUserRepo(listOf(NearbyPlayer(UUID.randomUUID(), "token-1")))
        val service = service(repo, users)

        val recordadas = service.sendRemindersForPendingConvocatories()

        assertEquals(2, recordadas)
        assertEquals(2, users.calls, "Debe buscar cercanos una vez por convocatoria pendiente")
        assertEquals(50_000.0, users.radiusMeters)
        assertEquals(organizerId, users.excluded)
    }

    @Test
    fun recordatorio_sin_pendientes_no_notifica() {
        val repo = FakeConvocatoryRepo(pending = emptyList())
        val users = CapturingUserRepo(emptyList())
        val service = service(repo, users)

        val recordadas = service.sendRemindersForPendingConvocatories()

        assertEquals(0, recordadas)
        assertEquals(0, users.calls, "Sin convocatorias pendientes no debe consultarse cercanía")
    }

    // --- Conflicto de horario al crear (duración asumida de 60 min) ---

    /** Fecha futura para que el chequeo "active y futura" aplique. */
    private val futureAt = "2099-01-01T19:00:00"

    @Test
    fun crear_que_choca_con_otra_convocatoria_propia_falla() {
        // Ya organiza un partido a las 19:00 (futuro); el nuevo es a las 19:30 → choca.
        val repo = FakeConvocatoryRepo(organizing = listOf(convAt(futureAt)))
        val users = CapturingUserRepo(emptyList())
        val service = service(repo, users)
        val nueva = create().copy(scheduledAt = "2099-01-01T19:30:00")
        assertFailsWith<ConvocatoryConflict> { service.create(nueva) }
        assertEquals(false, repo.createCalled)
    }

    @Test
    fun crear_que_choca_con_postulacion_sin_confirmar_pide_confirmacion() {
        // Postulado (pending) a las 19:00; intenta organizar a las 19:00 → choca.
        // Sin cancelConflicts NO crea: lanza el conflicto con la lista.
        val conflictId = UUID.randomUUID()
        val posts = FakePostulationRepo(
            listOf(MyPostulationRecord(conflictId, "pending", convAt(futureAt))),
        )
        val repo = FakeConvocatoryRepo()
        val users = CapturingUserRepo(emptyList())
        val service = service(repo, users, posts = posts)
        val nueva = create().copy(scheduledAt = futureAt)

        val ex = assertFailsWith<ConvocatoryScheduleConflict> { service.create(nueva) }
        assertEquals(1, ex.conflicts.size)
        assertEquals(false, repo.createCalled, "No debe crear sin confirmación")
        assertEquals(true, posts.statusUpdates.isEmpty(), "No debe cancelar sin confirmación")
    }

    @Test
    fun crear_con_confirmacion_crea_y_cancela_la_postulacion() {
        // Aprobado como jugador a las 19:00; organiza a las 19:00 con cancelConflicts.
        val conflictId = UUID.randomUUID()
        val posts = FakePostulationRepo(
            listOf(MyPostulationRecord(conflictId, "approved", convAt(futureAt))),
        )
        val repo = FakeConvocatoryRepo()
        val users = CapturingUserRepo(emptyList())
        val service = service(repo, users, posts = posts)
        val nueva = create().copy(scheduledAt = futureAt, cancelConflicts = true)

        val created = service.create(nueva)
        assertEquals(true, repo.createCalled)
        assertEquals(convocatoryId, created.id)
        // La postulación en conflicto quedó cancelada.
        assertEquals(true, posts.statusUpdates.contains(conflictId to "cancelled"))
    }

    @Test
    fun crear_sin_solape_se_permite() {
        // Organiza a las 19:00 y juega a las 17:00; el nuevo a las 21:00 → sin choque.
        val repo = FakeConvocatoryRepo(organizing = listOf(convAt(futureAt)))
        val posts = FakePostulationRepo(
            listOf(MyPostulationRecord(UUID.randomUUID(), "approved", convAt("2099-01-01T17:00:00"))),
        )
        val users = CapturingUserRepo(emptyList())
        val service = service(repo, users, posts = posts)
        val nueva = create().copy(scheduledAt = "2099-01-01T21:00:00")
        val created = service.create(nueva)
        assertEquals(true, repo.createCalled)
        assertEquals(convocatoryId, created.id)
    }
}
