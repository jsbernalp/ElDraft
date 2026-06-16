package com.eldraft.data.repository

import com.eldraft.data.models.Convocatory
import com.eldraft.data.models.CreateConvocatoryRequest
import com.eldraft.data.models.MapEvent
import com.eldraft.data.remote.ConvocatoryApi
import com.eldraft.domain.repository.ConvocatoryRepository
import kotlinx.coroutines.flow.Flow

class ConvocatoryRepositoryImpl(
    private val convocatoryApi: ConvocatoryApi,
) : ConvocatoryRepository {

    override suspend fun create(request: CreateConvocatoryRequest): Convocatory =
        convocatoryApi.create(request)

    override suspend fun getNearby(lat: Double, lng: Double, radius: Double): List<Convocatory> =
        convocatoryApi.getNearby(lat, lng, radius)

    override suspend fun getMine(): List<Convocatory> =
        convocatoryApi.getMine()

    override suspend fun getById(id: String): Convocatory =
        convocatoryApi.getById(id)

    override fun observeMapEvents(lat: Double, lng: Double, radius: Double, userId: String?): Flow<MapEvent> =
        convocatoryApi.observeMapEvents(lat, lng, radius, userId)
}
