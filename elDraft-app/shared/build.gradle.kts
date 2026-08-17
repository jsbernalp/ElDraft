plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// Los targets de iOS solo se declaran en macOS. Kotlin/Native no puede compilarlos
// en Windows/Linux (los deshabilita y avisa), y además el import del IDE falla ahí:
// IdeCommonizedNativePlatformDependencyResolver lanza un NPE al resolver las
// librerías Apple contra una distribución que no es de macOS, y el sync de Android
// Studio se llena de stacktraces en appleMain/iosMain/nativeMain.
//
// Declararlos solo donde sirven deja el sync limpio sin cambiar nada en macOS: allí
// se siguen creando los tres targets y el framework `shared` igual que siempre.
val isMacOs = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }

    if (isMacOs) {
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "shared"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)

            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.koin.core)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.android)
            implementation(libs.coroutines.android)
        }

        // Solo existe si se declararon los targets de iOS (ver isMacOs arriba).
        if (isMacOs) {
            iosMain.dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
            implementation(libs.koin.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

android {
    namespace = "com.eldraft.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
