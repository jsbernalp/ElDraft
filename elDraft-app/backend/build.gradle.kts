plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.eldraft.backend"
version = "0.1.0"

application {
    mainClass.set("com.eldraft.backend.ApplicationKt")
}

dependencies {
    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.serialization)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.cors)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.postgresql.driver)
    implementation(libs.hikari)

    // DI
    implementation(libs.koin.ktor)

    // Serialization & logging
    implementation(libs.serialization.json)
    implementation(libs.logback)

    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(17)
}
