import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.eldraft.backend"
version = "0.1.0"

// Lee local.properties del directorio raíz del proyecto (un nivel arriba del backend/).
// El archivo está gitignored — nunca se commitea.
val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
}

application {
    mainClass.set("com.eldraft.backend.ApplicationKt")
}

// Inyecta variables de entorno locales al ejecutar el backend con :backend:run
// (Android Studio o terminal). Los valores vienen de local.properties (gitignored).
tasks.named<JavaExec>("run") {
    localProps.getProperty("FIREBASE_SERVICE_ACCOUNT_PATH")?.let { path ->
        environment("FIREBASE_SERVICE_ACCOUNT_PATH", path)
    }
    // Marca este arranque como desarrollo: sin esto, la validación de
    // Application.kt abortaría por usar los secretos por defecto de
    // application.conf. Producción nunca pasa por este task.
    environment("KTOR_DEVELOPMENT", "true")
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

    // Firebase Admin SDK (envío de push FCM desde el servidor)
    implementation(libs.firebase.admin)

    // Serialization & logging
    implementation(libs.serialization.json)
    implementation(libs.logback)

    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(17)
}
