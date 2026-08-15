package com.eldraft.android.di

import com.eldraft.android.BuildConfig
import com.eldraft.android.R
import com.eldraft.android.data.EmailAuthClient
import com.eldraft.android.data.GoogleAuthClient
import com.eldraft.android.data.GoogleSignInProviderImpl
import com.eldraft.android.data.IdentitySessionCleanerImpl
import com.eldraft.android.data.SessionManager
import com.eldraft.android.notifications.FcmTokenSync
import com.eldraft.android.notifications.NotificationRefreshBus
import com.eldraft.android.ui.auth.AuthViewModel
import com.eldraft.android.ui.attendance.AttendanceDeclarationViewModel
import com.eldraft.android.ui.attendance.AttendanceViewModel
import com.eldraft.android.ui.attendance.NoShowViewModel
import com.eldraft.android.ui.draft.CancelConvocatoryViewModel
import com.eldraft.android.ui.draft.CreateDraftViewModel
import com.eldraft.android.ui.draft.MyMatchesViewModel
import com.eldraft.android.ui.map.MapViewModel
import com.eldraft.android.ui.postulation.ApplyViewModel
import com.eldraft.android.ui.postulation.MyPostulationsViewModel
import com.eldraft.android.ui.rating.RatingViewModel
import com.eldraft.android.ui.postulation.ApplicantsViewModel
import com.eldraft.android.ui.profile.ProfileEditViewModel
import com.eldraft.android.ui.profile.ProfileViewModel
import com.eldraft.core.config.ApiConfig
import com.eldraft.core.network.AuthTokenProvider
import com.eldraft.data.local.SessionStore
import com.eldraft.domain.auth.GoogleSignInProvider
import com.eldraft.domain.auth.IdentitySessionCleaner
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
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

    // Sesión persistida (DataStore) — Android-only.
    // Se expone como SessionManager y como SessionStore (abstracción común).
    single { SessionManager(androidContext()) }
    single<SessionStore> { get<SessionManager>() }

    // El token de auth siempre se lee de la sesión (fuente de verdad).
    // Reemplaza el antiguo ElDraftApi.setToken() manual.
    single<AuthTokenProvider> {
        val session = get<SessionStore>()
        AuthTokenProvider { session.currentToken() }
    }

    // Google Sign-In (Credential Manager) — Android-only
    single {
        GoogleAuthClient(
            context = androidContext(),
            serverClientId = androidContext().getString(R.string.default_web_client_id),
        )
    }
    // Adaptador a la abstracción de dominio (usado por SignInWithGoogleUseCase)
    singleOf(::GoogleSignInProviderImpl) { bind<GoogleSignInProvider>() }
    // Cierre de la sesión de Firebase al hacer logout (usado por LogoutUseCase).
    // Aplica a los dos proveedores: FirebaseAuth.signOut() es global a la app.
    singleOf(::IdentitySessionCleanerImpl) { bind<IdentitySessionCleaner>() }

    // Email + contraseña (Firebase Auth)
    single { EmailAuthClient() }

    // Sincronización del token FCM (login y arranque con sesión activa)
    single { FcmTokenSync(registerFcmToken = get(), sessionStore = get()) }

    // Bus de eventos de notificación: el servicio FCM emite, los ViewModels escuchan
    single { NotificationRefreshBus() }

    // ViewModels (autowiring por constructor)
    viewModelOf(::AuthViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::ProfileEditViewModel)
    viewModelOf(::CreateDraftViewModel)
    viewModelOf(::MyMatchesViewModel)
    viewModelOf(::CancelConvocatoryViewModel)
    viewModelOf(::MapViewModel)
    viewModelOf(::ApplyViewModel)
    viewModelOf(::ApplicantsViewModel)
    viewModelOf(::AttendanceViewModel)
    viewModelOf(::AttendanceDeclarationViewModel)
    viewModelOf(::NoShowViewModel)
    viewModelOf(::RatingViewModel)
    viewModelOf(::MyPostulationsViewModel)
}
