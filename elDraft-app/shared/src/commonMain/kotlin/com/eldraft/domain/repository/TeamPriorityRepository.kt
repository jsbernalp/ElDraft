package com.eldraft.domain.repository

import com.eldraft.data.models.Convocatory
import com.eldraft.data.models.TeamPriorityInvite

/**
 * Convocatoria con prioridad para un equipo: lo que ve el miembro invitado y la
 * acción del organizador. Va aparte de [ConvocatoryRepository] para no obligar a
 * cada fake de esa interfaz a implementar métodos que no usa.
 */
interface TeamPriorityRepository {

    /** Convocatorias de mis equipos que esperan mi respuesta, con la ventana abierta. */
    @Throws(Exception::class)
    suspend fun getPriorityInvites(): List<TeamPriorityInvite>

    /** "Voy" (con [position]) o "No puedo". */
    @Throws(Exception::class)
    suspend fun respond(convocatoryId: String, accept: Boolean, position: String?)

    /** El organizador abre la convocatoria al público antes de que venza la ventana. */
    @Throws(Exception::class)
    suspend fun openToPublic(convocatoryId: String): Convocatory
}
