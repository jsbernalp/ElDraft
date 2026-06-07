package com.eldraft.core.di

import com.eldraft.core.network.createHttpClient
import com.eldraft.data.remote.AttendanceApi
import com.eldraft.data.remote.AuthApi
import com.eldraft.data.remote.ConvocatoryApi
import com.eldraft.data.remote.PlayerApi
import com.eldraft.data.remote.PostulationApi
import com.eldraft.data.repository.AuthRepositoryImpl
import com.eldraft.data.repository.ProfileRepositoryImpl
import com.eldraft.domain.repository.AuthRepository
import com.eldraft.domain.repository.ProfileRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Módulo Koin común (KMP). Provee la capa de red y las APIs por feature.
 *
 * Requiere que la plataforma provea:
 *  - [com.eldraft.core.config.ApiConfig]
 *  - [com.eldraft.core.network.AuthTokenProvider]
 */
val sharedModule = module {
    // Cliente HTTP único, compartido por todas las APIs
    single { createHttpClient() }

    // APIs por feature (autowiring: client + ApiConfig + AuthTokenProvider)
    singleOf(::AuthApi)
    singleOf(::PlayerApi)
    singleOf(::ConvocatoryApi)
    singleOf(::PostulationApi)
    singleOf(::AttendanceApi)

    // Repositorios (interfaz de dominio ← implementación de data)
    singleOf(::AuthRepositoryImpl) { bind<AuthRepository>() }
    singleOf(::ProfileRepositoryImpl) { bind<ProfileRepository>() }
}
