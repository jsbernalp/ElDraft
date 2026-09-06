package com.eldraft.domain.repository

import com.eldraft.data.models.MyPostulation
import com.eldraft.data.models.Postulation

/** Operaciones de postulación a convocatorias (lado jugador y organizador). */
interface PostulationRepository {

    /** El jugador autenticado se postula a una convocatoria en una posición concreta. */
    @Throws(Exception::class)
    suspend fun apply(convocatoryId: String, position: String): Postulation

    /** Mis postulaciones (partidos a los que me postulé como jugador). */
    @Throws(Exception::class)
    suspend fun getMine(): List<MyPostulation>

    /** Postulantes de una convocatoria (solo visible para el organizador). */
    @Throws(Exception::class)
    suspend fun getApplicants(convocatoryId: String): List<Postulation>

    /** El organizador aprueba una postulación. */
    @Throws(Exception::class)
    suspend fun approve(postulationId: String): Postulation

    /** El organizador rechaza una postulación. */
    @Throws(Exception::class)
    suspend fun reject(postulationId: String): Postulation

    /** El jugador retira su propia postulación. */
    @Throws(Exception::class)
    suspend fun withdraw(postulationId: String)
}
