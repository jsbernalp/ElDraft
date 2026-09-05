package com.eldraft.core.di

import com.eldraft.domain.repository.AuthRepository
import com.eldraft.domain.usecase.auth.LogoutUseCase
import com.eldraft.domain.usecase.auth.SignInWithEmailUseCase
import com.eldraft.domain.usecase.auth.SignInWithGoogleUseCase
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
}
