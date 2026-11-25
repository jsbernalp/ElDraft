package com.eldraft.core.di

import com.eldraft.core.config.ApiConfig
import com.eldraft.core.network.AuthTokenProvider
import com.eldraft.data.local.IosSessionStore
import com.eldraft.data.local.SessionStore
import com.eldraft.domain.auth.EmailAuthProvider
import com.eldraft.domain.auth.GoogleSignInProvider
import com.eldraft.domain.auth.IdentitySessionCleaner
import org.koin.dsl.module

/**
 * Módulo Koin específico de iOS — equivalente a `AndroidModule.kt`: la sesión
 * persistida (aquí, Keychain en vez de DataStore) y los proveedores de
 * identidad, que Swift implementa envolviendo sus propios closures (ver los
 * adaptadores `Ios*Provider` / `IosIdentitySessionCleaner`).
 */
fun iosModule(
    apiConfig: ApiConfig,
    googleSignIn: GoogleSignInProvider,
    emailAuth: EmailAuthProvider,
    identityCleaner: IdentitySessionCleaner,
) = module {
    single { apiConfig }

    single<SessionStore> { IosSessionStore() }

    // El token de auth siempre se lee de la sesión (fuente de verdad), igual que en Android.
    single<AuthTokenProvider> {
        val session = get<SessionStore>()
        AuthTokenProvider { session.currentToken() }
    }

    single { googleSignIn }
    single { emailAuth }
    single { identityCleaner }
}
