package com.eldraft.backend

import com.eldraft.backend.plugins.*
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    configureSerialization()
    configureCors()
    configureAuth()
    configureDatabases()
    configureWebSockets()
    configureStatusPages()
    configureRouting()
}
