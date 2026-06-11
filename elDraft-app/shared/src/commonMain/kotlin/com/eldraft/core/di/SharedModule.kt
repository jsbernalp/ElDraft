package com.eldraft.core.di

import com.eldraft.core.network.createHttpClient
import com.eldraft.data.remote.AttendanceApi
import com.eldraft.data.remote.AuthApi
import com.eldraft.data.remote.ConvocatoryApi
import com.eldraft.data.remote.PlayerApi
import com.eldraft.data.remote.PostulationApi
import com.eldraft.data.remote.RatingApi
import com.eldraft.data.repository.AttendanceRepositoryImpl
import com.eldraft.data.repository.AuthRepositoryImpl
import com.eldraft.data.repository.ConvocatoryRepositoryImpl
import com.eldraft.data.repository.PostulationRepositoryImpl
import com.eldraft.data.repository.ProfileRepositoryImpl
import com.eldraft.data.repository.RatingRepositoryImpl
import com.eldraft.domain.repository.AttendanceRepository
import com.eldraft.domain.repository.AuthRepository
import com.eldraft.domain.repository.ConvocatoryRepository
import com.eldraft.domain.repository.PostulationRepository
import com.eldraft.domain.repository.ProfileRepository
import com.eldraft.domain.repository.RatingRepository
import com.eldraft.domain.usecase.auth.GetMyAccountUseCase
import com.eldraft.domain.usecase.auth.LogoutUseCase
import com.eldraft.domain.usecase.auth.RegisterFcmTokenUseCase
import com.eldraft.domain.usecase.auth.ReportLocationUseCase
import com.eldraft.domain.usecase.auth.SignInDevUseCase
import com.eldraft.domain.usecase.auth.SignInWithGoogleUseCase
import com.eldraft.domain.usecase.auth.UpdateAccountUseCase
import com.eldraft.domain.usecase.convocatory.CreateConvocatoryUseCase
import com.eldraft.domain.usecase.convocatory.ObserveMapEventsUseCase
import com.eldraft.domain.usecase.attendance.GenerateAttendanceQrUseCase
import com.eldraft.domain.usecase.attendance.ScanAttendanceUseCase
import com.eldraft.domain.usecase.rating.GetTeammatesToRateUseCase
import com.eldraft.domain.usecase.rating.SubmitRatingUseCase
import com.eldraft.domain.usecase.postulation.ApplyToConvocatoryUseCase
import com.eldraft.domain.usecase.postulation.ApproveApplicantUseCase
import com.eldraft.domain.usecase.postulation.GetApplicantsUseCase
import com.eldraft.domain.usecase.postulation.GetMyPostulationsUseCase
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
    singleOf(::RatingApi)

    // Repositorios (interfaz de dominio ← implementación de data)
    singleOf(::AuthRepositoryImpl) { bind<AuthRepository>() }
    singleOf(::ProfileRepositoryImpl) { bind<ProfileRepository>() }
    singleOf(::ConvocatoryRepositoryImpl) { bind<ConvocatoryRepository>() }
    singleOf(::PostulationRepositoryImpl) { bind<PostulationRepository>() }
    singleOf(::AttendanceRepositoryImpl) { bind<AttendanceRepository>() }
    singleOf(::RatingRepositoryImpl) { bind<RatingRepository>() }

    // Casos de uso (orquestación de negocio). Requiere GoogleSignInProvider
    // provisto por la plataforma.
    factoryOf(::SignInWithGoogleUseCase)
    factoryOf(::SignInDevUseCase)
    factoryOf(::RegisterFcmTokenUseCase)
    factoryOf(::ReportLocationUseCase)
    factoryOf(::GetMyAccountUseCase)
    factoryOf(::UpdateAccountUseCase)
    factoryOf(::LogoutUseCase)
    factoryOf(::SaveProfileUseCase)
    factoryOf(::CreateConvocatoryUseCase)
    factoryOf(::ObserveMapEventsUseCase)
    factoryOf(::ApplyToConvocatoryUseCase)
    factoryOf(::GetMyPostulationsUseCase)
    factoryOf(::GetApplicantsUseCase)
    factoryOf(::ApproveApplicantUseCase)
    factoryOf(::RejectApplicantUseCase)
    factoryOf(::GenerateAttendanceQrUseCase)
    factoryOf(::ScanAttendanceUseCase)
    factoryOf(::GetTeammatesToRateUseCase)
    factoryOf(::SubmitRatingUseCase)
}
