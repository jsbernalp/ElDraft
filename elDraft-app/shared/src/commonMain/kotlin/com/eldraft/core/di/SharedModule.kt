package com.eldraft.core.di

import com.eldraft.data.api.ElDraftApi
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Módulo Koin común (KMP). Provee dependencias que viven en commonMain.
 *
 * Requiere que la plataforma provea un [com.eldraft.core.config.ApiConfig]
 * (Android lo construye desde BuildConfig; iOS desde su propio config).
 */
val sharedModule = module {
    singleOf(::ElDraftApi)
}
