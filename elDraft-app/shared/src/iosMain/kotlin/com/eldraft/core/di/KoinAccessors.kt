package com.eldraft.core.di

import com.eldraft.domain.repository.AuthRepository
import org.koin.mp.KoinPlatformTools

/**
 * Fachada mínima sobre Koin para que Swift no necesite conocer su API
 * (`getKoin<T>()` con reified generics no se exporta a Objective-C/Swift).
 * Se amplía a medida que cada fase agrega pantallas que necesitan un nuevo
 * repositorio o caso de uso — ver [startKoinIos].
 */
object IosKoin {
    val authRepository: AuthRepository get() = KoinPlatformTools.defaultContext().get().get()
}
