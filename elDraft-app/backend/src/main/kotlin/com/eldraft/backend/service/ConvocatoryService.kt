package com.eldraft.backend.service

import com.eldraft.backend.repository.ConvocatoryCreate
import com.eldraft.backend.repository.ConvocatoryRecord
import com.eldraft.backend.repository.ConvocatoryRepository
import java.util.UUID

class ConvocatoryService(
    private val repository: ConvocatoryRepository,
) {
    /** Crea una convocatoria tras validar los campos. */
    fun create(data: ConvocatoryCreate): ConvocatoryRecord {
        validate(data)
        return repository.create(data)
    }

    fun getById(id: UUID): ConvocatoryRecord? = repository.findById(id)

    fun getMine(organizerId: UUID): List<ConvocatoryRecord> =
        repository.findByOrganizer(organizerId)

    fun getNearby(lat: Double, lng: Double, radiusMeters: Double): List<ConvocatoryRecord> {
        require(lat in -90.0..90.0) { "Latitud fuera de rango" }
        require(lng in -180.0..180.0) { "Longitud fuera de rango" }
        require(radiusMeters in 1.0..50_000.0) { "El radio debe estar entre 1 y 50000 metros" }
        return repository.findNearby(lat, lng, radiusMeters)
    }

    private fun validate(data: ConvocatoryCreate) {
        require(data.lat in -90.0..90.0) { "Latitud fuera de rango" }
        require(data.lng in -180.0..180.0) { "Longitud fuera de rango" }
        require(data.slotsNeeded in 1..30) { "Los cupos deben estar entre 1 y 30" }
        require(data.positionRequired.isNotBlank()) { "La posición requerida es obligatoria" }
        require(data.format.isNotBlank()) { "El formato es obligatorio" }
        require(data.ambiente.isNotBlank()) { "El ambiente es obligatorio" }
        require(data.fee >= 0.0) { "La cuota no puede ser negativa" }
    }
}
