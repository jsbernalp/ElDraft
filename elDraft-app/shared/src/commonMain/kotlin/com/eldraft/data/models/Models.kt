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
    val sportsmanshipScore: Double = 5.0,
    val totalMatches: Int = 0
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: User,
    val needsOnboarding: Boolean
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

@Serializable
data class Convocatory(
    val id: String,
    val organizerId: String,
    val lat: Double,
    val lng: Double,
    val addressText: String? = null,
    val slotsNeeded: Int,
    val positionRequired: String,
    val fee: Double = 0.0,
    val format: String,
    val ambiente: String,
    val status: String = "active",
    val scheduledAt: String
)

@Serializable
data class Postulation(
    val id: String,
    val convocatoryId: String,
    val playerId: String,
    val status: String = "pending",
    val player: PlayerProfile? = null
)

@Serializable
data class AttendanceQr(
    val qrCode: String,
    val expiresInSeconds: Int
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
