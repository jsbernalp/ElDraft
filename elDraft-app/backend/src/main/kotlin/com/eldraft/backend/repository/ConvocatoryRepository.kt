package com.eldraft.backend.repository

import com.eldraft.backend.db.tables.ConvocatoriesTable
import com.eldraft.backend.db.tables.PostulationsTable
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.notExists
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Requerimiento de cupos para una posición concreta. */
@Serializable
data class PositionSlot(
    val position: String,
    val slots: Int,
)

/** Vista de dominio de una convocatoria (El Draft). */
data class ConvocatoryRecord(
    val id: UUID,
    val organizerId: UUID,
    val lat: Double,
    val lng: Double,
    val addressText: String?,
    val slotsNeeded: Int,
    val positionRequired: String,
    val positionSlots: List<PositionSlot>,
    val fee: Double,
    val format: String,
    val ambiente: String,
    val status: String,
    val scheduledAt: String,
    /** True si el consenso marcó al organizador como ausente del partido. */
    val organizerNoShow: Boolean = false,
    /** Postulaciones 'pending' (sin aprobar ni rechazar) de esta convocatoria. */
    val pendingCount: Int = 0,
    val cancellationReason: String? = null,
    val cancelledAt: String? = null,
)

/**
 * Datos para crear una convocatoria. `positionSlots` es la fuente de verdad;
 * `slotsNeeded` (suma) y `positionRequired` (resumen) se derivan al persistir.
 */
data class ConvocatoryCreate(
    val organizerId: UUID,
    val lat: Double,
    val lng: Double,
    val addressText: String?,
    val positionSlots: List<PositionSlot>,
    val fee: Double,
    val format: String,
    val ambiente: String,
    val scheduledAt: String,
    /**
     * Confirmación del organizador para cancelar sus postulaciones que choquen
     * con esta convocatoria. Si es false y hay choque, el service lanza
     * ConvocatoryScheduleConflict en vez de crear. No se persiste.
     */
    val cancelConflicts: Boolean = false,
)

/** (De)serialización del JSON de `convocatories.position_slots`. */
private val positionSlotsJson = Json { ignoreUnknownKeys = true }
private val positionSlotsSerializer = ListSerializer(PositionSlot.serializer())

internal fun List<PositionSlot>.toJson(): String =
    positionSlotsJson.encodeToString(positionSlotsSerializer, this)

internal fun parsePositionSlots(raw: String?): List<PositionSlot> =
    if (raw.isNullOrBlank()) emptyList()
    else try {
        positionSlotsJson.decodeFromString(positionSlotsSerializer, raw)
    } catch (_: Exception) {
        emptyList()
    }

/** Suma de cupos = total que alimenta el pin del mapa y el snippet "N cupos". */
private fun List<PositionSlot>.totalSlots(): Int = sumOf { it.slots }

/** Resumen legible para `position_required` (compat con UI/notificaciones). */
private fun List<PositionSlot>.summary(): String = when (size) {
    0 -> ""
    1 -> first().position
    else -> "Varias"
}

open class ConvocatoryRepository {

    /**
     * Inserta la convocatoria (vía Exposed) y rellena la columna PostGIS
     * `location` con ST_MakePoint en la misma transacción.
     */
    open fun create(data: ConvocatoryCreate): ConvocatoryRecord = transaction {
        val now = LocalDateTime.now()
        val scheduled = parseDateTime(data.scheduledAt)

        val newId = ConvocatoriesTable.insertAndGetId {
            it[organizerId] = data.organizerId
            it[locationLat] = data.lat
            it[locationLng] = data.lng
            it[addressText] = data.addressText
            // Derivamos total y resumen de positionSlots: una sola fuente de verdad.
            it[slotsNeeded] = data.positionSlots.totalSlots()
            it[positionRequired] = data.positionSlots.summary()
            it[positionSlots] = data.positionSlots.toJson()
            it[fee] = data.fee.toBigDecimal()
            it[format] = data.format
            it[ambiente] = data.ambiente
            it[status] = "active"
            it[scheduledAt] = scheduled
            it[createdAt] = now
        }.value

        // Rellenar la geometría PostGIS de la fila recién creada.
        exec(
            """
            UPDATE convocatories
            SET location = ST_SetSRID(ST_MakePoint(${data.lng}, ${data.lat}), 4326)::geography
            WHERE id = '$newId';
            """.trimIndent()
        )

        findById(newId) ?: error("Convocatoria no encontrada tras crear")
    }

    open fun findById(id: UUID): ConvocatoryRecord? = transaction {
        ConvocatoriesTable.selectAll()
            .where { ConvocatoriesTable.id eq id }
            .singleOrNull()
            ?.toRecord()
    }

    open fun findByOrganizer(organizerId: UUID): List<ConvocatoryRecord> = transaction {
        val records = ConvocatoriesTable.selectAll()
            .where { (ConvocatoriesTable.organizerId eq organizerId) and (ConvocatoriesTable.status eq "active") }
            .map { it.toRecord() }

        // Postulaciones pendientes por convocatoria: alimenta el badge "por
        // gestionar" en la card del organizador. Una consulta por card (su lista
        // de convocatorias activas es pequeña).
        records.map { it.copy(pendingCount = countPending(it.id)) }
    }

    /** Cuenta las postulaciones en estado 'pending' de una convocatoria. */
    private fun countPending(convocatoryId: UUID): Int =
        PostulationsTable.selectAll()
            .where {
                (PostulationsTable.convocatoryId eq convocatoryId) and
                    (PostulationsTable.status eq "pending")
            }
            .count()
            .toInt()

    /**
     * Convocatorias activas dentro de [radiusMeters] del punto dado, usando el
     * índice geoespacial vía ST_DWithin (PostGIS). Ordenadas por cercanía.
     */
    fun findNearby(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        excludeOrganizerId: UUID? = null,
    ): List<ConvocatoryRecord> = transaction {
        val results = mutableListOf<ConvocatoryRecord>()
        val point = "ST_SetSRID(ST_MakePoint($lng, $lat), 4326)::geography"
        // Oculta las convocatorias del propio organizador (no tiene sentido que
        // se postule a su partido). El UUID proviene del token, no de input crudo.
        val excludeClause = excludeOrganizerId?.let { "AND organizer_id <> '$it'" } ?: ""
        exec(
            """
            SELECT id, organizer_id, location_lat, location_lng, address_text,
                   slots_needed, position_required, position_slots, fee, format, ambiente, status, scheduled_at
            FROM convocatories
            WHERE status = 'active'
              AND location IS NOT NULL
              AND ST_DWithin(location, $point, $radiusMeters)
              $excludeClause
            ORDER BY ST_Distance(location, $point) ASC;
            """.trimIndent()
        ) { rs ->
            while (rs.next()) {
                results.add(
                    ConvocatoryRecord(
                        id = UUID.fromString(rs.getString("id")),
                        organizerId = UUID.fromString(rs.getString("organizer_id")),
                        lat = rs.getDouble("location_lat"),
                        lng = rs.getDouble("location_lng"),
                        addressText = rs.getString("address_text"),
                        slotsNeeded = rs.getInt("slots_needed"),
                        positionRequired = rs.getString("position_required"),
                        positionSlots = parsePositionSlots(rs.getString("position_slots")),
                        fee = rs.getBigDecimal("fee").toDouble(),
                        format = rs.getString("format"),
                        ambiente = rs.getString("ambiente"),
                        status = rs.getString("status"),
                        scheduledAt = rs.getTimestamp("scheduled_at").toLocalDateTime().format(ISO),
                    )
                )
            }
        }
        results
    }

    /**
     * Marca la convocatoria como cancelada. Devuelve true si se actualizó.
     * Solo afecta convocatorias con status 'active' o 'full'.
     */
    open fun cancel(id: UUID, reason: String): Boolean = transaction {
        ConvocatoriesTable.update({
            (ConvocatoriesTable.id eq id) and
                (ConvocatoriesTable.status inList listOf("active", "full"))
        }) {
            it[status] = "cancelled"
            it[cancellationReason] = reason
            it[cancelledAt] = LocalDateTime.now()
        } > 0
    }

    /**
     * Convocatorias activas, aún no vencidas y SIN ninguna postulación. Sirve
     * al recordatorio recurrente: en cuanto una recibe un postulante (o vence)
     * deja de aparecer aquí, así que el recordatorio se detiene solo.
     */
    open fun findActiveWithoutPostulations(): List<ConvocatoryRecord> = transaction {
        val notExists = org.jetbrains.exposed.sql.notExists(
            PostulationsTable.selectAll()
                .where { PostulationsTable.convocatoryId eq ConvocatoriesTable.id }
        )
        ConvocatoriesTable.selectAll()
            .where {
                (ConvocatoriesTable.status eq "active") and
                    (ConvocatoriesTable.scheduledAt greater LocalDateTime.now()) and
                    notExists
            }
            .map { it.toRecord() }
    }

    private fun ResultRow.toRecord() = ConvocatoryRecord(
        id = this[ConvocatoriesTable.id].value,
        organizerId = this[ConvocatoriesTable.organizerId].value,
        lat = this[ConvocatoriesTable.locationLat],
        lng = this[ConvocatoriesTable.locationLng],
        addressText = this[ConvocatoriesTable.addressText],
        slotsNeeded = this[ConvocatoriesTable.slotsNeeded],
        positionRequired = this[ConvocatoriesTable.positionRequired],
        positionSlots = parsePositionSlots(this[ConvocatoriesTable.positionSlots]),
        fee = this[ConvocatoriesTable.fee].toDouble(),
        format = this[ConvocatoriesTable.format],
        ambiente = this[ConvocatoriesTable.ambiente],
        status = this[ConvocatoriesTable.status],
        scheduledAt = this[ConvocatoriesTable.scheduledAt].format(ISO),
        organizerNoShow = this[ConvocatoriesTable.organizerNoShow],
        cancellationReason = this[ConvocatoriesTable.cancellationReason],
        cancelledAt = this[ConvocatoriesTable.cancelledAt]?.format(ISO),
    )

    private fun parseDateTime(raw: String): LocalDateTime =
        try {
            LocalDateTime.parse(raw, ISO)
        } catch (_: Exception) {
            // Aceptar también ISO con zona/offset → tomar la parte local.
            try {
                java.time.OffsetDateTime.parse(raw).toLocalDateTime()
            } catch (_: Exception) {
                throw IllegalArgumentException("Fecha inválida: '$raw' (usar ISO-8601, ej. 2026-06-10T19:00:00)")
            }
        }

    private companion object {
        val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }
}
