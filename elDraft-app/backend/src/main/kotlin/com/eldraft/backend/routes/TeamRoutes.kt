package com.eldraft.backend.routes

import com.eldraft.backend.plugins.currentUserId
import com.eldraft.backend.repository.TeamInvitationRecord
import com.eldraft.backend.repository.TeamMemberRecord
import com.eldraft.backend.repository.TeamSummaryRecord
import com.eldraft.backend.service.TeamDetail
import com.eldraft.backend.service.TeamService
import com.eldraft.backend.service.TeamSuggestion
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.get
import java.util.UUID

/** Un equipo en una lista. `invitedCount` solo viaja al dueño (null en /memberships). */
@Serializable
data class TeamDto(
    val id: String,
    val name: String,
    val ownerId: String,
    val ownerName: String,
    val activeCount: Int,
    val invitedCount: Int? = null,
    val createdAt: String,
)

/** Miembro de un equipo. Nunca lleva el correo. `status`: "invited" | "active". */
@Serializable
data class TeamMemberDto(
    val playerId: String,
    val name: String,
    val avatarUrl: String? = null,
    val positionPrimary: String? = null,
    val status: String,
    val invitedAt: String,
    val joinedAt: String? = null,
)

@Serializable
data class TeamDetailDto(
    val id: String,
    val name: String,
    val ownerId: String,
    val ownerName: String,
    val isOwner: Boolean,
    val members: List<TeamMemberDto>,
    val createdAt: String,
)

@Serializable
data class TeamSuggestionDto(
    val playerId: String,
    val name: String,
    val avatarUrl: String? = null,
    val positionPrimary: String? = null,
    val matchesTogether: Int,
    val lastPlayedAt: String,
)

@Serializable
data class TeamInvitationDto(
    val teamId: String,
    val teamName: String,
    val ownerId: String,
    val ownerName: String,
    val ownerAvatarUrl: String? = null,
    val invitedAt: String,
)

@Serializable
data class CreateTeamRequest(val name: String)

@Serializable
data class RenameTeamRequest(val name: String)

@Serializable
data class InvitationResponseRequest(val accept: Boolean)

/** Exactamente uno de los dos: `playerId` (sugerencias) o `email`. */
@Serializable
data class AddMemberRequest(val playerId: String? = null, val email: String? = null)

fun Route.teamRoutes() {
    val service = application.get<TeamService>()

    authenticate("firebase-auth") {
        route("/teams") {

            // Mis equipos (dueño), con conteo de activos e invitados.
            // get("") y no get {}: el get() de Koin choca con la sobrecarga sin ruta.
            get("") {
                val userId = call.currentUserId()
                call.respond(HttpStatusCode.OK, service.owned(userId).map { it.toDto(withInvited = true) })
            }

            post {
                val userId = call.currentUserId()
                val body = call.receive<CreateTeamRequest>()
                call.respond(HttpStatusCode.Created, service.create(userId, body.name).toDto(withInvited = true))
            }

            // Las rutas fijas van antes de /{id} para que no se lean como un id.
            get("/suggestions") {
                val userId = call.currentUserId()
                val teamId = call.request.queryParameters["teamId"]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { parseUuid(it, "teamId") }
                call.respond(HttpStatusCode.OK, service.suggestions(userId, teamId).map { it.toDto() })
            }

            get("/invitations") {
                val userId = call.currentUserId()
                call.respond(HttpStatusCode.OK, service.invitations(userId).map { it.toDto() })
            }

            // Equipos ajenos en los que soy miembro activo.
            get("/memberships") {
                val userId = call.currentUserId()
                call.respond(HttpStatusCode.OK, service.memberships(userId).map { it.toDto(withInvited = false) })
            }

            route("/{id}") {
                get("") {
                    val teamId = parseUuid(call.parameters["id"], "id")
                    call.respond(HttpStatusCode.OK, service.detail(teamId, call.currentUserId()).toDto())
                }

                patch {
                    val teamId = parseUuid(call.parameters["id"], "id")
                    val body = call.receive<RenameTeamRequest>()
                    val updated = service.rename(teamId, call.currentUserId(), body.name)
                    call.respond(HttpStatusCode.OK, updated.toDto(withInvited = true))
                }

                delete {
                    val teamId = parseUuid(call.parameters["id"], "id")
                    service.delete(teamId, call.currentUserId())
                    call.respond(HttpStatusCode.NoContent)
                }

                post("/members") {
                    val teamId = parseUuid(call.parameters["id"], "id")
                    val body = call.receive<AddMemberRequest>()
                    val playerId = body.playerId?.takeIf { it.isNotBlank() }
                    val email = body.email?.takeIf { it.isNotBlank() }
                    if ((playerId == null) == (email == null)) {
                        throw IllegalArgumentException("Envía playerId o email, solo uno")
                    }
                    val created = service.addMember(
                        teamId = teamId,
                        requesterId = call.currentUserId(),
                        playerId = playerId?.let { parseUuid(it, "playerId") },
                        email = email,
                    )
                    call.respond(HttpStatusCode.Created, created.toDto())
                }

                // El dueño quita a un jugador, o el propio miembro se sale.
                delete("/members/{playerId}") {
                    val teamId = parseUuid(call.parameters["id"], "id")
                    val playerId = parseUuid(call.parameters["playerId"], "playerId")
                    service.removeMember(teamId, call.currentUserId(), playerId)
                    call.respond(HttpStatusCode.NoContent)
                }

                // El invitado acepta o rechaza.
                post("/invitation") {
                    val teamId = parseUuid(call.parameters["id"], "id")
                    val body = call.receive<InvitationResponseRequest>()
                    service.respondInvitation(teamId, call.currentUserId(), body.accept)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private fun TeamSummaryRecord.toDto(withInvited: Boolean) = TeamDto(
    id = team.id.toString(),
    name = team.name,
    ownerId = team.ownerId.toString(),
    ownerName = team.ownerName,
    activeCount = activeCount,
    invitedCount = if (withInvited) invitedCount else null,
    createdAt = team.createdAt.toString(),
)

private fun TeamMemberRecord.toDto() = TeamMemberDto(
    playerId = playerId.toString(),
    name = name,
    avatarUrl = avatarUrl,
    positionPrimary = positionPrimary,
    status = status,
    invitedAt = invitedAt.toString(),
    joinedAt = joinedAt?.toString(),
)

private fun TeamDetail.toDto() = TeamDetailDto(
    id = team.id.toString(),
    name = team.name,
    ownerId = team.ownerId.toString(),
    ownerName = team.ownerName,
    isOwner = isOwner,
    members = members.map { it.toDto() },
    createdAt = team.createdAt.toString(),
)

private fun TeamSuggestion.toDto() = TeamSuggestionDto(
    playerId = playerId.toString(),
    name = name,
    avatarUrl = avatarUrl,
    positionPrimary = positionPrimary,
    matchesTogether = matchesTogether,
    lastPlayedAt = lastPlayedAt.toString(),
)

private fun TeamInvitationRecord.toDto() = TeamInvitationDto(
    teamId = teamId.toString(),
    teamName = teamName,
    ownerId = ownerId.toString(),
    ownerName = ownerName,
    ownerAvatarUrl = ownerAvatarUrl,
    invitedAt = invitedAt.toString(),
)

private fun parseUuid(raw: String?, field: String): UUID {
    if (raw.isNullOrBlank()) throw IllegalArgumentException("Falta $field")
    return try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("$field no es un UUID válido")
    }
}
