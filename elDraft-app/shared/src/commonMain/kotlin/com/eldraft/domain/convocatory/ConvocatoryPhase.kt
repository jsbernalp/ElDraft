package com.eldraft.domain.convocatory

import com.eldraft.data.models.Convocatory

/** En qué punto de la prioridad del equipo está una convocatoria. */
enum class ConvocatoryPhase {
    /** Nació pública (sin equipo), o terminó/canceló: nada especial que mostrar. */
    STANDARD,

    /** Solo el equipo la ve y puede responder. */
    TEAM_PRIORITY,

    /** Se abrió al público y quedan cupos libres. */
    OPEN_TO_PUBLIC,

    /** Se abrió pero el equipo ya llenó los cupos: no se publicó. */
    TEAM_FILLED,
}

fun Convocatory.phase(): ConvocatoryPhase = when {
    status == "team_only" -> ConvocatoryPhase.TEAM_PRIORITY
    teamId == null || status != "active" -> ConvocatoryPhase.STANDARD
    (freeSlots ?: 0) > 0 -> ConvocatoryPhase.OPEN_TO_PUBLIC
    else -> ConvocatoryPhase.TEAM_FILLED
}

/**
 * El organizador puede cancelar mientras la convocatoria siga viva. `team_only`
 * cuenta: cancelar en la fase privada es válido (no hay pin que cerrar).
 */
fun Convocatory.canCancel(): Boolean = status == "active" || status == "full" || status == "team_only"

/** "Abrir ya al público" solo existe durante la prioridad del equipo. */
fun Convocatory.canOpenNow(): Boolean = status == "team_only"

/**
 * Cupos que se muestran al público (pin, lista, detalle). Con equipo, el total
 * engañaría —parte ya la apartó el equipo—, así que gana `freeSlots`; sin equipo
 * el backend no lo manda y es `slotsNeeded`.
 */
fun Convocatory.displaySlots(): Int = freeSlots ?: slotsNeeded
