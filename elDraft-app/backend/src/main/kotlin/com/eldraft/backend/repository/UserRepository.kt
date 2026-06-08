package com.eldraft.backend.repository

import com.eldraft.backend.auth.VerifiedIdentity
import com.eldraft.backend.db.tables.PlayerProfilesTable
import com.eldraft.backend.db.tables.UsersTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

/** Vista de dominio de un usuario (sin acoplar a Exposed). */
data class UserRecord(
    val id: UUID,
    val firebaseUid: String,
    val name: String,
    val email: String?,
    val phone: String?,
    val avatarUrl: String?
)

/** Vista de dominio de la ficha técnica (El Cromo). */
data class PlayerProfileRecord(
    val userId: UUID,
    val positionPrimary: String,
    val positionSecondary: String?,
    val dominantFoot: String,
    val height: Int?,
    val build: String?,
    val speedRating: Int,
    val precisionRating: Int,
    val attendancePct: Double,
    val sportsmanshipScore: Double,
    val totalMatches: Int
)

/** Datos editables por el usuario al configurar su Cromo. */
data class ProfileUpsert(
    val positionPrimary: String,
    val positionSecondary: String?,
    val dominantFoot: String,
    val height: Int?,
    val build: String?
)

open class UserRepository {

    /** Busca un usuario por su Firebase UID, o lo crea si no existe. */
    fun findOrCreateByIdentity(identity: VerifiedIdentity): UserRecord = transaction {
        val existing = UsersTable
            .selectAll()
            .where { UsersTable.firebaseUid eq identity.firebaseUid }
            .singleOrNull()

        if (existing != null) {
            existing.toUserRecord()
        } else {
            val newId = UsersTable.insertAndGetId {
                it[firebaseUid] = identity.firebaseUid
                it[name] = identity.name
                it[email] = identity.email
                it[avatarUrl] = identity.avatarUrl
                it[createdAt] = LocalDateTime.now()
            }.value
            UserRecord(
                id = newId,
                firebaseUid = identity.firebaseUid,
                name = identity.name,
                email = identity.email,
                phone = null,
                avatarUrl = identity.avatarUrl
            )
        }
    }

    open fun findById(userId: UUID): UserRecord? = transaction {
        UsersTable.selectAll()
            .where { UsersTable.id eq userId }
            .singleOrNull()
            ?.toUserRecord()
    }

    fun updatePhone(userId: UUID, phone: String): Boolean = transaction {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.phone] = phone
        } > 0
    }

    /**
     * Actualiza el nombre y/o avatar URL del usuario.
     * Devuelve el [UserRecord] actualizado, o null si el usuario no existe.
     */
    fun updateAccount(userId: UUID, name: String, avatarUrl: String?): UserRecord? = transaction {
        val updated = UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.name] = name
            it[UsersTable.avatarUrl] = avatarUrl
        }
        if (updated == 0) null else findById(userId)
    }

    /** Guarda (o limpia) el token FCM del dispositivo del usuario. */
    fun updateFcmToken(userId: UUID, token: String?): Boolean = transaction {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[fcmToken] = token
        } > 0
    }

    /** Token FCM del usuario, o null si no tiene uno registrado. */
    open fun getFcmToken(userId: UUID): String? = transaction {
        UsersTable.selectAll()
            .where { UsersTable.id eq userId }
            .singleOrNull()
            ?.get(UsersTable.fcmToken)
    }

    fun getProfile(userId: UUID): PlayerProfileRecord? = transaction {
        PlayerProfilesTable.selectAll()
            .where { PlayerProfilesTable.userId eq userId }
            .singleOrNull()
            ?.toProfileRecord()
    }

    /** Crea o actualiza la ficha técnica del jugador. */
    fun upsertProfile(userId: UUID, data: ProfileUpsert): PlayerProfileRecord = transaction {
        val exists = PlayerProfilesTable.selectAll()
            .where { PlayerProfilesTable.userId eq userId }
            .empty().not()

        if (exists) {
            PlayerProfilesTable.update({ PlayerProfilesTable.userId eq userId }) {
                it[positionPrimary] = data.positionPrimary
                it[positionSecondary] = data.positionSecondary
                it[dominantFoot] = data.dominantFoot
                it[height] = data.height
                it[build] = data.build
            }
        } else {
            PlayerProfilesTable.insert {
                it[PlayerProfilesTable.userId] = userId
                it[positionPrimary] = data.positionPrimary
                it[positionSecondary] = data.positionSecondary
                it[dominantFoot] = data.dominantFoot
                it[height] = data.height
                it[build] = data.build
            }
        }

        getProfile(userId) ?: error("Perfil no encontrado tras upsert")
    }

    private fun ResultRow.toUserRecord() = UserRecord(
        id = this[UsersTable.id].value,
        firebaseUid = this[UsersTable.firebaseUid],
        name = this[UsersTable.name],
        email = this[UsersTable.email],
        phone = this[UsersTable.phone],
        avatarUrl = this[UsersTable.avatarUrl]
    )

    private fun ResultRow.toProfileRecord() = PlayerProfileRecord(
        userId = this[PlayerProfilesTable.userId].value,
        positionPrimary = this[PlayerProfilesTable.positionPrimary],
        positionSecondary = this[PlayerProfilesTable.positionSecondary],
        dominantFoot = this[PlayerProfilesTable.dominantFoot],
        height = this[PlayerProfilesTable.height],
        build = this[PlayerProfilesTable.build],
        speedRating = this[PlayerProfilesTable.speedRating],
        precisionRating = this[PlayerProfilesTable.precisionRating],
        attendancePct = this[PlayerProfilesTable.attendancePct],
        sportsmanshipScore = this[PlayerProfilesTable.sportsmanshipScore],
        totalMatches = this[PlayerProfilesTable.totalMatches]
    )
}
