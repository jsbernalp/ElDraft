package com.eldraft.android.di

import com.eldraft.android.BuildConfig
import com.eldraft.android.R
import com.eldraft.android.data.GoogleAuthClient
import com.eldraft.android.data.SessionManager
import com.eldraft.android.ui.auth.AuthViewModel
import com.eldraft.android.ui.profile.ProfileViewModel
import com.eldraft.core.config.ApiConfig
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Módulo Koin específico de Android: configuración de endpoints, fuentes de datos
 * propias de la plataforma (DataStore, Credential Manager) y los ViewModels.
 */
val androidModule = module {

    // Endpoints desde BuildConfig (difieren entre debug/release)
    single {
        ApiConfig(
            baseUrl = BuildConfig.API_BASE_URL,
            wsBaseUrl = BuildConfig.WS_BASE_URL,
        )
    }

    // Sesión persistida (DataStore) — Android-only
    single { SessionManager(androidContext()) }

    // Google Sign-In (Credential Manager) — Android-only
    single {
        GoogleAuthClient(
            context = androidContext(),
            serverClientId = androidContext().getString(R.string.default_web_client_id),
        )
    }

    // ViewModels (autowiring por constructor)
    viewModelOf(::AuthViewModel)
    viewModelOf(::ProfileViewModel)
}
