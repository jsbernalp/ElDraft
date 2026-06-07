import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.gms.google-services")
}

// Lee secretos locales (no versionados) desde local.properties.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val mapsApiKey: String = localProps.getProperty("MAPS_API_KEY") ?: ""

// Forzar kotlin-stdlib a la versión del proyecto para evitar que dependencias
// transitivas (ej. play-services-location) suban a una versión incompatible
configurations.all {
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib:2.1.21")
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.21")
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.21")
}

android {
    namespace = "com.eldraft.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eldraft.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-mvp"

        // API key de Google Maps inyectada en el manifest (desde local.properties).
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
            buildConfigField("String", "WS_BASE_URL", "\"ws://10.0.2.2:8080\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"https://api.eldraft.app\"")
            buildConfigField("String", "WS_BASE_URL", "\"wss://api.eldraft.app\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":shared"))

    // AppCompat (necesario para Theme.AppCompat en styles.xml)
    implementation(libs.androidx.appcompat)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Auth — Credential Manager + Google ID
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // DataStore — sesión (JWT)
    implementation(libs.androidx.datastore.preferences)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)

    // Maps & Location
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // Camera & ML Kit
    implementation(libs.mlkit.barcode)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // DI
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // Image loading
    implementation(libs.coil)
}
