package com.eldraft.android

import android.app.Application
import com.eldraft.android.data.GoogleAuthClient
import com.eldraft.android.data.SessionManager
import com.eldraft.data.api.ElDraftApi

/**
 * Application con un service locator mínimo para la Fase 1.
 * (Más adelante se puede migrar a Koin si la grafo de dependencias crece.)
 */
class ElDraftApplication : Application() {

    val api: ElDraftApi by lazy {
        ElDraftApi(
            baseUrl = BuildConfig.API_BASE_URL,
            wsBaseUrl = BuildConfig.WS_BASE_URL
        )
    }

    val session: SessionManager by lazy { SessionManager(this) }

    val googleAuth: GoogleAuthClient by lazy {
        GoogleAuthClient(
            context = this,
            serverClientId = getString(R.string.default_web_client_id)
        )
    }
}
