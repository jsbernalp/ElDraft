import Foundation
import Observation
import shared

/// Espejo de `TeamsViewModel.kt`: "Mis equipos" — los propios, aquellos en los
/// que soy miembro y las invitaciones pendientes.
@MainActor
@Observable
final class TeamsViewModel {
    private(set) var myTeams: [Team] = []
    private(set) var memberships: [Team] = []
    private(set) var invitations: [TeamInvitation] = []
    private(set) var isLoading = false
    private(set) var error: String?
    private(set) var isCreating = false
    /// Id del equipo recién creado (evento de una vez: la vista navega y lo consume).
    private(set) var createdTeamId: String?
    /// Ids de equipo cuya invitación se está aceptando/rechazando (fila bloqueada).
    private(set) var respondingIds: Set<String> = []

    /// Lo que el usuario va escribiendo en el diálogo "Crear equipo".
    var newTeamName = ""

    /// Largo del nombre tal como llegará al backend (sin espacios sobrantes).
    var newTeamNameLength: Int { TeamRules.shared.normalizeName(raw: newTeamName).count }
    var canCreateAnotherTeam: Bool { TeamRules.shared.canCreateAnotherTeam(ownedCount: Int32(myTeams.count)) }
    var isNewTeamNameValid: Bool { TeamRules.shared.isValidName(raw: newTeamName) }
    var canSubmitNewTeam: Bool { canCreateAnotherTeam && isNewTeamNameValid && !isCreating }

    init() {
        NotificationRefreshBus.shared.subscribe { [weak self] event in
            guard event == .teams else { return }
            self?.load()
        }
    }

    func load() {
        isLoading = true
        error = nil
        Task {
            do {
                async let mine = IosKoin.shared.getMyTeams.invoke()
                async let member = IosKoin.shared.getMyTeamMemberships.invoke()
                async let invites = IosKoin.shared.getTeamInvitations.invoke()
                let (m, ms, inv) = try await (mine, member, invites)
                myTeams = m
                memberships = ms
                invitations = inv
                isLoading = false
            } catch {
                isLoading = false
                self.error = error.kotlinMessage(fallback: "No se pudieron cargar tus equipos")
            }
        }
    }

    func resetNewTeam() { newTeamName = "" }

    func create() {
        guard canSubmitNewTeam else { return }
        let name = newTeamName
        isCreating = true
        Task {
            do {
                let team = try await IosKoin.shared.createTeam.invoke(name: name)
                myTeams.append(team)
                isCreating = false
                newTeamName = ""
                createdTeamId = team.id
            } catch {
                isCreating = false
                self.error = error.kotlinMessage(fallback: "No se pudo crear el equipo")
            }
        }
    }

    func consumeCreatedTeam() { createdTeamId = nil }

    func accept(teamId: String) { respond(teamId: teamId, accept: true) }

    func decline(teamId: String) { respond(teamId: teamId, accept: false) }

    private func respond(teamId: String, accept: Bool) {
        guard !respondingIds.contains(teamId) else { return }
        respondingIds.insert(teamId)
        Task {
            do {
                _ = try await IosKoin.shared.respondTeamInvitation.invoke(teamId: teamId, accept: accept)
                // Al aceptar, el equipo pasa a "Soy miembro": se pide de nuevo la
                // lista para traer los conteos reales en vez de inventarlos aquí.
                if accept, let fresh = try? await IosKoin.shared.getMyTeamMemberships.invoke() {
                    memberships = fresh
                }
                invitations.removeAll { $0.teamId == teamId }
                respondingIds.remove(teamId)
            } catch {
                respondingIds.remove(teamId)
                self.error = error.kotlinMessage(fallback: "No se pudo responder la invitación")
            }
        }
    }

    func clearError() { error = nil }
}
