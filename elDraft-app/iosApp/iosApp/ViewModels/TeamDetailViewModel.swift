import Foundation
import Observation
import shared

/// Espejo de `TeamDetailViewModel.kt`: detalle de un equipo y su sheet
/// "Agregar jugador".
@MainActor
@Observable
final class TeamDetailViewModel {
    private(set) var team: TeamDetail?
    private(set) var isLoading = false
    private(set) var error: String?
    private(set) var isRenaming = false
    /// Evento de una vez: el renombre salió bien y el diálogo puede cerrarse.
    private(set) var renamed = false
    private(set) var isDeleting = false
    private(set) var isLeaving = false
    /// Evento de una vez: el equipo se borró o el usuario se salió → volver atrás.
    private(set) var closed = false
    /// Ids de jugadores que se están quitando (botón bloqueado).
    private(set) var removingIds: Set<String> = []

    // --- Sheet "Agregar jugador" ---
    private(set) var suggestions: [TeamSuggestion] = []
    private(set) var suggestionsLoading = false
    private(set) var suggestionsError: String?
    /// Ids de sugeridos con una invitación en curso (bloqueo de reenvío).
    private(set) var invitingIds: Set<String> = []
    private(set) var isInvitingByEmail = false
    /// Error de la pestaña "Por correo" (404 / 429 / 422 llegan con el texto del backend).
    private(set) var emailError: String?
    /// Nombre del último invitado por correo, para confirmarlo en el sheet.
    private(set) var emailInvitedName: String?
    private(set) var email = ""

    private var teamId: String?

    var isOwner: Bool { team?.isOwner == true }
    var members: [TeamMember] { team?.members ?? [] }
    var canAddMember: Bool { team != nil && TeamRules.shared.canAddMember(memberCount: Int32(members.count)) }
    var canSendEmail: Bool {
        canAddMember && !isInvitingByEmail && TeamRules.shared.isPlausibleEmail(raw: email)
    }

    init() {
        NotificationRefreshBus.shared.subscribe { [weak self] event in
            // Tras borrar o salirse, el propio VM emite .teams para la lista:
            // recargar aquí daría un 404 sobre un equipo que ya no es nuestro.
            guard event == .teams, let self, !self.closed, let id = self.teamId else { return }
            self.load(teamId: id)
        }
    }

    func load(teamId: String) {
        self.teamId = teamId
        isLoading = true
        error = nil
        Task {
            do {
                team = try await IosKoin.shared.getTeamDetail.invoke(teamId: teamId)
                isLoading = false
            } catch {
                isLoading = false
                self.error = error.kotlinMessage(fallback: "No se pudo cargar el equipo")
            }
        }
    }

    func rename(name: String) {
        guard let id = teamId, !isRenaming else { return }
        isRenaming = true
        Task {
            do {
                let updated = try await IosKoin.shared.renameTeam.invoke(teamId: id, name: name)
                team = team.map { replacing($0, name: updated.name) }
                isRenaming = false
                renamed = true
            } catch {
                isRenaming = false
                self.error = error.kotlinMessage(fallback: "No se pudo renombrar el equipo")
            }
        }
    }

    func consumeRenamed() { renamed = false }

    func delete() {
        guard let id = teamId, !isDeleting else { return }
        isDeleting = true
        Task {
            do {
                try await IosKoin.shared.deleteTeam.invoke(teamId: id)
                isDeleting = false
                closed = true
                NotificationRefreshBus.shared.emit(.teams)
            } catch {
                isDeleting = false
                self.error = error.kotlinMessage(fallback: "No se pudo borrar el equipo")
            }
        }
    }

    func remove(playerId: String) {
        guard let id = teamId, !removingIds.contains(playerId) else { return }
        removingIds.insert(playerId)
        Task {
            do {
                try await IosKoin.shared.removeTeamMember.invoke(teamId: id, playerId: playerId)
                team = team.map { replacing($0, members: $0.members.filter { $0.playerId != playerId }) }
                removingIds.remove(playerId)
            } catch {
                removingIds.remove(playerId)
                self.error = error.kotlinMessage(fallback: "No se pudo quitar al jugador")
            }
        }
    }

    /// El miembro se sale: es el mismo "quitar" con su propio id.
    func leave() {
        guard let id = teamId, !isLeaving else { return }
        isLeaving = true
        Task {
            do {
                let me = (try? await IosKoin.shared.authRepository.currentUserId()) ?? ""
                try await IosKoin.shared.removeTeamMember.invoke(teamId: id, playerId: me)
                isLeaving = false
                closed = true
                NotificationRefreshBus.shared.emit(.teams)
            } catch {
                isLeaving = false
                self.error = error.kotlinMessage(fallback: "No se pudo salir del equipo")
            }
        }
    }

    // MARK: - Sheet "Agregar jugador"

    /// Al abrir el sheet: sugerencias sin quienes ya están en el equipo.
    func openAddPlayer() {
        guard let id = teamId else { return }
        suggestionsLoading = true
        suggestionsError = nil
        email = ""
        emailError = nil
        emailInvitedName = nil
        Task {
            do {
                suggestions = try await IosKoin.shared.getTeamSuggestions.invoke(teamId: id)
                suggestionsLoading = false
            } catch {
                suggestionsLoading = false
                suggestionsError = error.kotlinMessage(fallback: "No se pudieron cargar las sugerencias")
            }
        }
    }

    func inviteSuggested(playerId: String) {
        guard let id = teamId, !invitingIds.contains(playerId), canAddMember else { return }
        invitingIds.insert(playerId)
        Task {
            do {
                let member = try await IosKoin.shared.inviteToTeam.invoke(
                    teamId: id,
                    target: TeamInviteTarget.ById(playerId: playerId)
                )
                withInvited(member)
                invitingIds.remove(playerId)
            } catch {
                invitingIds.remove(playerId)
                self.error = error.kotlinMessage(fallback: "No se pudo invitar al jugador")
            }
        }
    }

    func onEmailChange(_ value: String) {
        email = value
        emailError = nil
        emailInvitedName = nil
    }

    func inviteByEmail() {
        guard let id = teamId, !isInvitingByEmail, canAddMember else { return }
        let address = email
        isInvitingByEmail = true
        emailError = nil
        emailInvitedName = nil
        Task {
            do {
                let member = try await IosKoin.shared.inviteToTeam.invoke(
                    teamId: id,
                    target: TeamInviteTarget.ByEmail(email: address)
                )
                withInvited(member)
                isInvitingByEmail = false
                email = ""
                emailInvitedName = member.name
            } catch {
                isInvitingByEmail = false
                emailError = error.kotlinMessage(fallback: "No se pudo invitar a ese correo")
            }
        }
    }

    func clearError() { error = nil }

    /// El invitado aparece en la lista (como Invitado) y deja de ser sugerencia.
    private func withInvited(_ member: TeamMember) {
        if let current = team {
            team = replacing(current, members: current.members.filter { $0.playerId != member.playerId } + [member])
        }
        suggestions.removeAll { $0.playerId == member.playerId }
    }

    /// `TeamDetail` es inmutable en Kotlin (data class): se copia con `doCopy`.
    private func replacing(_ detail: TeamDetail, name: String? = nil, members: [TeamMember]? = nil) -> TeamDetail {
        detail.doCopy(
            id: detail.id,
            name: name ?? detail.name,
            ownerId: detail.ownerId,
            ownerName: detail.ownerName,
            isOwner: detail.isOwner,
            members: members ?? detail.members,
            createdAt: detail.createdAt
        )
    }
}
