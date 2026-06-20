package com.eldraft.domain.repository

import com.eldraft.data.models.MyPostulation
import com.eldraft.data.models.Postulation

/** Operaciones de postulación a convocatorias (lado jugador y organizador). */
interface PostulationRepository {

    /** El jugador autenticado se postula a una convocatoria en una posición concreta. */
    suspend fun apply(convocatoryId: String, position: String): Postulation

    /** Mis postulaciones (partidos a los que me postulé como jugador). */
    suspend fun getMine(): List<MyPostulation>

    /** Postulantes de una convocatoria (solo visible para el organizador). */
    suspend fun getApplicants(convocatoryId: String): List<Postulation>

    /** El organizador aprueba una postulación. */
    suspend fun approve(postulationId: String): Postulation

    /** El organizador rechaza una postulación. */
    suspend fun reject(postulationId: String): Postulation

    /** El jugador retira su propia postulación. */
    suspend fun withdraw(postulationId: String)
}
