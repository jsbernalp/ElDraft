package com.eldraft.backend

import com.eldraft.backend.di.backendModule
import com.eldraft.backend.plugins.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.plugin.Koin

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    // DI primero: el resto de la configuración resuelve servicios desde Koin.
    install(Koin) {
        modules(backendModule(environment.config))
    }

    configureSerialization()
    configureCors()
    configureDatabases()  // conecta la BD antes de atender peticiones
    configureAuth()
    configureWebSockets()
    configureStatusPages()
    configureRouting()
    configureScheduler()  // tareas periódicas (recordatorio de convocatorias)
}
