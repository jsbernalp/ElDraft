package com.eldraft.core.di

import com.eldraft.core.network.createHttpClient
import com.eldraft.data.remote.AttendanceApi
import com.eldraft.data.remote.AuthApi
import com.eldraft.data.remote.ConvocatoryApi
import com.eldraft.data.remote.PlayerApi
import com.eldraft.data.remote.PostulationApi
import com.eldraft.data.repository.AuthRepositoryImpl
import com.eldraft.data.repository.ConvocatoryRepositoryImpl
import com.eldraft.data.repository.PostulationRepositoryImpl
import com.eldraft.data.repository.ProfileRepositoryImpl
import com.eldraft.domain.repository.AuthRepository
import com.eldraft.domain.repository.ConvocatoryRepository
import com.eldraft.domain.repository.PostulationRepository
import com.eldraft.domain.repository.ProfileRepository
import com.eldraft.domain.usecase.auth.SignInDevUseCase
import com.eldraft.domain.usecase.auth.SignInWithGoogleUseCase
import com.eldraft.domain.usecase.convocatory.CreateConvocatoryUseCase
import com.eldraft.domain.usecase.convocatory.ObserveMapEventsUseCase
import com.eldraft.domain.usecase.postulation.ApplyToConvocatoryUseCase
import com.eldraft.domain.usecase.postulation.ApproveApplicantUseCase
import com.eldraft.domain.usecase.postulation.GetApplicantsUseCase
import com.eldraft.domain.usecase.postulation.RejectApplicantUseCase
import com.eldraft.domain.usecase.profile.SaveProfileUseCase
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
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
    singleOf(::ConvocatoryRepositoryImpl) { bind<ConvocatoryRepository>() }
    singleOf(::PostulationRepositoryImpl) { bind<PostulationRepository>() }

    // Casos de uso (orquestación de negocio). Requiere GoogleSignInProvider
    // provisto por la plataforma.
    factoryOf(::SignInWithGoogleUseCase)
    factoryOf(::SignInDevUseCase)
    factoryOf(::SaveProfileUseCase)
    factoryOf(::CreateConvocatoryUseCase)
    factoryOf(::ObserveMapEventsUseCase)
    factoryOf(::ApplyToConvocatoryUseCase)
    factoryOf(::GetApplicantsUseCase)
    factoryOf(::ApproveApplicantUseCase)
    factoryOf(::RejectApplicantUseCase)
}
