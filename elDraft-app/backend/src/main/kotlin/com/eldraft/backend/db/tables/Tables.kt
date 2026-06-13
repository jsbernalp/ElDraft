package com.eldraft.backend.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime

object UsersTable : UUIDTable("users") {
    val firebaseUid = varchar("firebase_uid", 512).uniqueIndex()
    val name = varchar("name", 200)
    val email = varchar("email", 200).nullable()
    val phone = varchar("phone", 20).nullable()
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val fcmToken = varchar("fcm_token", 512).nullable()
    // Última ubicación conocida del usuario (reportada por la app). Se usa para
    // notificar convocatorias cercanas. La columna geográfica PostGIS `location`
    // se gestiona aparte vía SQL crudo en configureDatabases().
    val lastLat = double("last_lat").nullable()
    val lastLng = double("last_lng").nullable()
    val lastLocationAt = datetime("last_location_at").nullable()
    val createdAt = datetime("created_at")
}

object PlayerProfilesTable : UUIDTable("player_profiles") {
    val userId = reference("user_id", UsersTable).uniqueIndex()
    val positionPrimary = varchar("position_primary", 50)
    val positionSecondary = varchar("position_secondary", 50).nullable()
    val dominantFoot = varchar("dominant_foot", 20)
    val height = integer("height").nullable()
    val build = varchar("build", 30).nullable()
    val speedRating = integer("speed_rating").default(0)
    val precisionRating = integer("precision_rating").default(0)
    val attendancePct = double("attendance_pct").default(100.0)
    // Atributos alimentados por la calificación post-partido de los compañeros.
    val skillScore = double("skill_score").default(5.0)
    val sportsmanshipScore = double("sportsmanship_score").default(5.0)
    val responsibilityScore = double("responsibility_score").default(5.0)
    val totalMatches = integer("total_matches").default(0)
}

// location se almacena como texto WKT y se convierte con PostGIS
// Ej: ST_GeomFromText('POINT(-75.5 6.2)', 4326)
object ConvocatoriesTable : UUIDTable("convocatories") {
    val organizerId = reference("organizer_id", UsersTable)
    val locationLat = double("location_lat")
    val locationLng = double("location_lng")
    val addressText = varchar("address_text", 500).nullable()
    val slotsNeeded = integer("slots_needed")
    val positionRequired = varchar("position_required", 50)
    // Desglose de cupos por posición, JSON serializado:
    // [{"position":"Arquero","slots":1},{"position":"Defensa","slots":2}]
    // `slots_needed` (suma) y `position_required` (resumen) se derivan de aquí.
    val positionSlots = text("position_slots").nullable()
    val fee = decimal("fee", 10, 2).default(0.toBigDecimal())
    val format = varchar("format", 30)
    val ambiente = varchar("ambiente", 20)
    val status = varchar("status", 20).default("active")
    // True cuando los asistentes alcanzan consenso de que el organizador no se
    // presentó al partido. Visible para futuros postulantes.
    val organizerNoShow = bool("organizer_no_show").default(false)
    val scheduledAt = datetime("scheduled_at")
    val createdAt = datetime("created_at")
}

object PostulationsTable : UUIDTable("postulations") {
    val convocatoryId = reference("convocatory_id", ConvocatoriesTable)
    val playerId = reference("player_id", UsersTable)
    // Posición a la que aspira el jugador en este partido (debe estar entre las
    // que pide la convocatoria). Nullable por compatibilidad con datos viejos.
    val position = varchar("position", 50).nullable()
    val status = varchar("status", 20).default("pending")
    val createdAt = datetime("created_at")
}

object AttendanceRecordsTable : UUIDTable("attendance_records") {
    val convocatoryId = reference("convocatory_id", ConvocatoriesTable)
    val playerId = reference("player_id", UsersTable)
    val qrCode = varchar("qr_code", 256).uniqueIndex()
    val qrExpiresAt = datetime("qr_expires_at")
    val scannedAt = datetime("scanned_at").nullable()
    val validated = bool("validated").default(false)
}

/**
 * Reportes de "el organizador no se presentó". Cada asistente validado puede
 * emitir un voto único por convocatoria (índice único reporter+convocatory).
 * Cuando los reportes superan la mayoría de asistentes, se marca el no-show.
 */
object OrganizerNoShowReportsTable : UUIDTable("organizer_no_show_reports") {
    val convocatoryId = reference("convocatory_id", ConvocatoriesTable)
    val reporterId = reference("reporter_id", UsersTable)
    val createdAt = datetime("created_at")

    init {
        uniqueIndex(convocatoryId, reporterId)
    }
}

object RatingsTable : UUIDTable("ratings") {
    val convocatoryId = reference("convocatory_id", ConvocatoriesTable)
    val raterId = reference("rater_id", UsersTable)
    val ratedPlayerId = reference("rated_player_id", UsersTable)
    // Una fila = las 3 notas (1-5) que un jugador da a otro en un partido.
    val skillScore = integer("skill_score")
    val sportsmanshipScore = integer("sportsmanship_score")
    val responsibilityScore = integer("responsibility_score")
    val createdAt = datetime("created_at")
}
