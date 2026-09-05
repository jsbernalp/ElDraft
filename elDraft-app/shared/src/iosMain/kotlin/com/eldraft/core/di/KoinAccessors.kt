package com.eldraft.core.di

import com.eldraft.domain.repository.AuthRepository
import com.eldraft.domain.repository.ConvocatoryRepository
import com.eldraft.domain.usecase.auth.LogoutUseCase
import com.eldraft.domain.usecase.auth.ReportLocationUseCase
import com.eldraft.domain.usecase.auth.SignInWithEmailUseCase
import com.eldraft.domain.usecase.auth.SignInWithGoogleUseCase
import com.eldraft.domain.usecase.convocatory.ObserveMapEventsUseCase
import com.eldraft.domain.usecase.postulation.ApplyToConvocatoryUseCase
import com.eldraft.domain.usecase.postulation.GetMyPostulationsUseCase
import com.eldraft.domain.usecase.profile.SaveProfileUseCase
import org.koin.mp.KoinPlatformTools

/**
 * Fachada mínima sobre Koin para que Swift no necesite conocer su API
 * (`getKoin<T>()` con reified generics no se exporta a Objective-C/Swift).
 * Se amplía a medida que cada fase agrega pantallas que necesitan un nuevo
 * repositorio o caso de uso — ver [startKoinIos].
 */
object IosKoin {
    val authRepository: AuthRepository get() = KoinPlatformTools.defaultContext().get().get()
    val signInWithEmail: SignInWithEmailUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val signInWithGoogle: SignInWithGoogleUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val saveProfile: SaveProfileUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val logout: LogoutUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val convocatoryRepository: ConvocatoryRepository get() = KoinPlatformTools.defaultContext().get().get()
    val observeMapEvents: ObserveMapEventsUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val reportLocation: ReportLocationUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val getMyPostulations: GetMyPostulationsUseCase get() = KoinPlatformTools.defaultContext().get().get()
    val applyToConvocatory: ApplyToConvocatoryUseCase get() = KoinPlatformTools.defaultContext().get().get()
}
