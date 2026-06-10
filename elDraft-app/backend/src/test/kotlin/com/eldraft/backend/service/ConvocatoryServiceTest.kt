package com.eldraft.backend.service

import com.eldraft.backend.notifications.FcmService
import com.eldraft.backend.repository.ConvocatoryCreate
import com.eldraft.backend.repository.ConvocatoryRecord
import com.eldraft.backend.repository.ConvocatoryRepository
import com.eldraft.backend.repository.NearbyPlayer
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
        slotsNeeded = slots, positionRequired = "Delantero", fee = 0.0,
        format = "Fútbol 5", ambiente = "Recocha", scheduledAt = "2026-06-10T19:00:00",
    )

    private fun record(lat: Double = 6.2, lng: Double = -75.5) = ConvocatoryRecord(
        id = convocatoryId, organizerId = organizerId, lat = lat, lng = lng,
        addressText = null, slotsNeeded = 3, positionRequired = "Delantero",
        fee = 0.0, format = "Fútbol 5", ambiente = "Recocha", status = "active",
        scheduledAt = "2026-06-10T19:00:00",
    )

    /** Repo de convocatoria que no toca la BD: create() devuelve un record fijo. */
    private inner class FakeConvocatoryRepo(
        val pending: List<ConvocatoryRecord> = emptyList(),
    ) : ConvocatoryRepository() {
        var createCalled = false
        override fun create(data: ConvocatoryCreate): ConvocatoryRecord {
            createCalled = true
            return record(lat = data.lat, lng = data.lng)
        }
        override fun findActiveWithoutPostulations(): List<ConvocatoryRecord> = pending
    }

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
    }

    private val fcmDisabled = FcmService(serviceAccountPath = null)

    private fun service(repo: ConvocatoryRepository, users: UserRepository, radiusKm: Double = 50.0) =
        ConvocatoryService(repository = repo, users = users, fcm = fcmDisabled, nearbyRadiusKm = radiusKm)

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
}
