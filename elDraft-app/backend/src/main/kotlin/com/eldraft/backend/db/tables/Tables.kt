package com.eldraft.backend.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime

object UsersTable : UUIDTable("users") {
    val firebaseUid = varchar("firebase_uid", 512).uniqueIndex()
    val name = varchar("name", 200)
    val email = varchar("email", 200).nullable()
    val phone = varchar("phone", 20).nullable()
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val createdAt = datetime("created_at")
}

object PlayerProfilesTable : UUIDTable("player_profiles") {
    val userId = reference("user_id", UsersTable).uniqueIndex()
    val positionPrimary = varchar("position_primary", 50)
    val positionSecondary = varchar("position_secondary", 50).nullable()
    val dominantFoot = varchar("dominant_foot", 10)
    val height = integer("height").nullable()
    val build = varchar("build", 30).nullable()
    val speedRating = integer("speed_rating").default(0)
    val precisionRating = integer("precision_rating").default(0)
    val attendancePct = double("attendance_pct").default(100.0)
    val sportsmanshipScore = double("sportsmanship_score").default(5.0)
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
    val fee = decimal("fee", 10, 2).default(0.toBigDecimal())
    val format = varchar("format", 30)
    val ambiente = varchar("ambiente", 20)
    val status = varchar("status", 20).default("active")
    val scheduledAt = datetime("scheduled_at")
    val createdAt = datetime("created_at")
}

object PostulationsTable : UUIDTable("postulations") {
    val convocatoryId = reference("convocatory_id", ConvocatoriesTable)
    val playerId = reference("player_id", UsersTable)
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

object RatingsTable : UUIDTable("ratings") {
    val convocatoryId = reference("convocatory_id", ConvocatoriesTable)
    val raterId = reference("rater_id", UsersTable)
    val ratedPlayerId = reference("rated_player_id", UsersTable)
    val sportsmanshipScore = integer("sportsmanship_score")
    val createdAt = datetime("created_at")
}
