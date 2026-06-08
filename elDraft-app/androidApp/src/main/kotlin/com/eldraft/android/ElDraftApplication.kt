package com.eldraft.android

import android.app.Application
import com.eldraft.android.di.androidModule
import com.eldraft.android.notifications.NotificationHelper
import com.eldraft.core.di.sharedModule
import com.google.android.libraries.places.api.Places
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Punto de arranque de Koin. El grafo de dependencias se declara en
 * [sharedModule] (común KMP) y [androidModule] (específico de Android).
 */
class ElDraftApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.ERROR)
            androidContext(this@ElDraftApplication)
            modules(sharedModule, androidModule)
        }

        // Places SDK (autocompletado de direcciones en el mapa).
        if (BuildConfig.MAPS_API_KEY.isNotBlank() && !Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }

        // Canal de notificaciones (FCM + locales).
        NotificationHelper.ensureChannel(applicationContext)
    }
}
