package com.eldraft.data.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class PlayerProfile(
    val userId: String,
    val positionPrimary: String,
    val positionSecondary: String? = null,
    val dominantFoot: String,
    val height: Int? = null,
    val build: String? = null,
    val speedRating: Int = 0,
    val precisionRating: Int = 0,
    val attendancePct: Double = 100.0,
    val skillScore: Double = 5.0,
    val sportsmanshipScore: Double = 5.0,
    val responsibilityScore: Double = 5.0,
    val totalMatches: Int = 0
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: User,
    val needsOnboarding: Boolean
)

/** Cuerpo para actualizar nombre y avatar del usuario autenticado. */
@Serializable
data class UpdateAccountRequest(
    val name: String,
    val avatarUrl: String? = null,
)

/** Cuerpo para crear/actualizar la ficha técnica (El Cromo). */
@Serializable
data class UpdateProfileRequest(
    val positionPrimary: String,
    val positionSecondary: String? = null,
    val dominantFoot: String,
    val height: Int? = null,
    val build: String? = null
)

/** Un requerimiento de cupos para una posición concreta en una convocatoria. */
@Serializable
data class PositionSlot(
    val position: String,
    val slots: Int,
)

@Serializable
data class Convocatory(
    val id: String,
    val organizerId: String,
    val lat: Double,
    val lng: Double,
    val addressText: String? = null,
    val slotsNeeded: Int,
    val positionRequired: String,
    val positionSlots: List<PositionSlot> = emptyList(),
    val fee: Double = 0.0,
    val format: String,
    val ambiente: String,
    val status: String = "active",
    val scheduledAt: String,
    /** True si el consenso marcó al organizador como ausente del partido. */
    val organizerNoShow: Boolean = false,
    /** Postulaciones sin gestionar (pendientes) de esta convocatoria. */
    val pendingCount: Int = 0
)

/**
 * Cuerpo para crear una convocatoria (el backend asigna id, organizerId y status).
 * `positionSlots` es la fuente de verdad de los cupos; el backend deriva de ahí
 * `slotsNeeded` (suma) y `positionRequired` (resumen).
 */
@Serializable
data class CreateConvocatoryRequest(
    val lat: Double,
    val lng: Double,
    val addressText: String? = null,
    val positionSlots: List<PositionSlot>,
    val fee: Double = 0.0,
    val format: String,
    val ambiente: String,
    val scheduledAt: String,
    /** Confirma cancelar las postulaciones propias que choquen de horario al crear. */
    val cancelConflicts: Boolean = false
)

/** Un partido propio que se cancelaría al crear una convocatoria que se cruza. */
@Serializable
data class ScheduleConflictItem(
    val format: String,
    val scheduledAt: String
)

@Serializable
data class Postulation(
    val id: String,
    val convocatoryId: String,
    val playerId: String,
    // Posición a la que aspira el jugador en este partido (la elige al postularse).
    val position: String? = null,
    val status: String = "pending",
    val createdAt: String? = null,
    val player: PostulantSummary? = null
)

/** Mi postulación con la convocatoria embebida (vista "mis partidos" del jugador). */
@Serializable
data class MyPostulation(
    val id: String,
    val status: String,
    val convocatory: Convocatory
)

/** Resumen del postulante para que el organizador decida (nombre + ficha clave). */
@Serializable
data class PostulantSummary(
    val userId: String,
    val name: String,
    val avatarUrl: String? = null,
    val positionPrimary: String? = null,
    val dominantFoot: String? = null,
    val skillScore: Double? = null,
    val sportsmanshipScore: Double? = null,
    val responsibilityScore: Double? = null,
    val attendancePct: Double? = null,
    val totalMatches: Int? = null
)

@Serializable
data class AttendanceQr(
    val qrCode: String,
    val expiresInSeconds: Long
)

/** Resultado de escanear el QR de asistencia. */
@Serializable
data class AttendanceScanResult(
    val validated: Boolean,
    val convocatoryId: String,
    val playerId: String
)

/**
 * Estado del reporte "el organizador no se presentó" para una convocatoria.
 * Alimenta el botón del asistente: si puede reportar, si ya reportó, si la
 * ventana está abierta y el avance del consenso (votos / asistentes).
 */
@Serializable
data class NoShowStatus(
    val canReport: Boolean = false,
    val alreadyReported: Boolean = false,
    val windowOpen: Boolean = false,
    val reports: Int = 0,
    val attendees: Int = 0,
    val consensusReached: Boolean = false,
    /** True si el organizador marcó a este convocado como ausente del partido. */
    val markedNoShow: Boolean = false
)

/**
 * Una fila de la lista de asistencia que declara el organizador. [scanned] = el
 * jugador registró asistencia por QR (presencia firme, no marcable como ausente);
 * [markedNoShow] = el organizador ya lo declaró ausente.
 */
@Serializable
data class PlayerAttendanceRow(
    val playerId: String,
    val name: String,
    val avatarUrl: String? = null,
    val positionPrimary: String? = null,
    val scanned: Boolean = false,
    val markedNoShow: Boolean = false
)

/** Compañero de partido calificable (asistió a la convocatoria). */
@Serializable
data class Teammate(
    val userId: String,
    val name: String,
    val avatarUrl: String? = null,
    val positionPrimary: String? = null,
    val alreadyRated: Boolean = false
)

@Serializable
data class MapPin(
    val id: String,
    val lat: Double,
    val lng: Double,
    val slots: Int,
    val format: String,
    val ambiente: String
)

@Serializable
data class MapEvent(
    val event: String,
    val data: MapPin
)

enum class PostulationStatus { PENDING, APPROVED, REJECTED }
enum class ConvocatoryStatus { ACTIVE, FULL, CANCELLED, COMPLETED }
enum class Ambiente { RECOCHA, COMPETITIVO }
enum class Format { FUTBOL_5, FUTBOL_7, FUTBOL_11 }
enum class DominantFoot { DERECHO, ZURDO, AMBIDIESTRO }
