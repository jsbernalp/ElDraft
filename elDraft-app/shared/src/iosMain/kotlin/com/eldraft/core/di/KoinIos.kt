package com.eldraft.core.di

import com.eldraft.core.config.ApiConfig
import com.eldraft.domain.auth.EmailAuthProvider
import com.eldraft.domain.auth.GoogleSignInProvider
import com.eldraft.domain.auth.IdentitySessionCleaner
import org.koin.core.context.startKoin

/**
 * Punto de arranque de Koin desde Swift, llamado una vez al inicio de la app
 * (`ElDraftApp.swift`, `@main`). Equivalente a `ElDraftApplication.onCreate()`
 * en Android: el grafo común vive en [sharedModule], este arma el específico
 * de iOS con lo que solo Swift puede construir.
 *
 * Se llama `startKoinIos` y no `initKoinIos`: Kotlin/Native antepone "do" a
 * cualquier función exportada a Objective-C cuyo nombre empiece en "init"
 * (prefijo con significado especial en las convenciones de memoria de Cocoa),
 * y `doInitKoinIos(...)` es un nombre confuso de ver desde Swift.
 */
fun startKoinIos(
    apiConfig: ApiConfig,
    googleSignIn: GoogleSignInProvider,
    emailAuth: EmailAuthProvider,
    identityCleaner: IdentitySessionCleaner,
) {
    startKoin {
        modules(sharedModule, iosModule(apiConfig, googleSignIn, emailAuth, identityCleaner))
    }
}
