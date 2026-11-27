package com.eldraft.domain.team

import com.eldraft.data.models.Team

/**
 * Reglas de equipos compartidas por Android e iOS. Reflejan las del backend
 * (`TeamService`): el servidor sigue siendo la autoridad, esto solo evita viajes
 * inútiles y deja a las dos apps validando igual.
 */
object TeamRules {
    /** Largo máximo del nombre del equipo (tras trim). */
    const val NAME_MAX_LENGTH = 40

    /** Máximo de equipos propios por usuario. */
    const val MAX_TEAMS = 5

    /** Máximo de jugadores (invitados + activos) por equipo. */
    const val MAX_MEMBERS = 30

    fun normalizeName(raw: String): String = raw.trim()

    /** True si el nombre, ya normalizado, tiene entre 1 y [NAME_MAX_LENGTH] caracteres. */
    fun isValidName(raw: String): Boolean = normalizeName(raw).length in 1..NAME_MAX_LENGTH

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    /**
     * Chequeo de forma, no de existencia: algo@algo.tld sin espacios. Quien
     * decide si el jugador existe es el backend (404 PLAYER_NOT_FOUND).
     */
    fun isPlausibleEmail(raw: String): Boolean = EMAIL_SHAPE.matches(normalizeEmail(raw))

    /** True si el dueño aún puede crear otro equipo dado cuántos tiene. */
    fun canCreateAnotherTeam(ownedCount: Int): Boolean = ownedCount < MAX_TEAMS

    /** True si al equipo le cabe otro jugador dado cuántos tiene (invitados + activos). */
    fun canAddMember(memberCount: Int): Boolean = memberCount < MAX_MEMBERS

    /**
     * True si el equipo se puede avisar primero en una convocatoria: necesita al
     * menos un miembro activo (con 0 el backend responde 400).
     */
    fun canPrioritize(team: Team): Boolean = team.activeCount > 0

    /** Los equipos de [teams] que se pueden avisar primero ([canPrioritize]). */
    fun prioritizable(teams: List<Team>): List<Team> = teams.filter { canPrioritize(it) }

    private val EMAIL_SHAPE = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
}
