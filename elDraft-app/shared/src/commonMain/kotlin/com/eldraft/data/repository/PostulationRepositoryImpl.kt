package com.eldraft.data.repository

import com.eldraft.data.models.MyPostulation
import com.eldraft.data.models.Postulation
import com.eldraft.data.remote.PostulationApi
import com.eldraft.domain.repository.PostulationRepository

class PostulationRepositoryImpl(
    private val postulationApi: PostulationApi,
) : PostulationRepository {

    override suspend fun apply(convocatoryId: String, position: String): Postulation =
        postulationApi.apply(convocatoryId, position)

    override suspend fun getMine(): List<MyPostulation> =
        postulationApi.getMine()

    override suspend fun getApplicants(convocatoryId: String): List<Postulation> =
        postulationApi.getApplicants(convocatoryId)

    override suspend fun approve(postulationId: String): Postulation =
        postulationApi.approve(postulationId)

    override suspend fun reject(postulationId: String): Postulation =
        postulationApi.reject(postulationId)
}
