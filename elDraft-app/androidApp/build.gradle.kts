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
// En CI no hay local.properties: los mismos secretos llegan por variable de entorno.
val mapsApiKey: String = localProps.getProperty("MAPS_API_KEY") ?: System.getenv("MAPS_API_KEY") ?: ""
// IP del backend en desarrollo. Emulador usa 10.0.2.2; dispositivo físico necesita
// la IP real del Mac en la red WiFi. Cámbiala en local.properties: DEV_HOST=192.168.x.x
val devHost: String = localProps.getProperty("DEV_HOST") ?: "10.0.2.2"

// Host del backend en release.
//
// ⚠️ DEUDA CONOCIDA — migrar a dominio propio antes de repartir la app fuera del
// círculo de amigos. Decisión del 15 ago 2026: se lanza el MVP contra el subdominio
// de Railway en vez de registrar eldraft.app.
//
// El motivo por el que esto NO es gratis: esta URL queda compilada dentro del APK,
// así que cada instalación llama a este host hasta que el usuario actualice. Si la
// URL de Railway cambia alguna vez —recrear el servicio, migrar de proyecto, salir
// de Railway— todas las instalaciones existentes quedan rotas y no hay arreglo
// posible del lado del servidor: hay que publicar una actualización y esperar a que
// todos la instalen, con revisión de Play Store de por medio. Un dominio propio
// convierte eso en un cambio de DNS que hasta las versiones viejas siguen.
//
// Es asumible mientras puedas escribirle a cada usuario y pedirle que actualice.
// El día que no puedas, hay que haberlo migrado ya. Cambiar `prodApiHost` a
// "api.eldraft.app" es todo lo que hace falta aquí.
val prodApiHost = "backend-production-70f7.up.railway.app"

// Orden de resolución: -PreleaseApiHost > local.properties > prodApiHost. Tiene que
// leerse de local.properties y no solo de -P porque un build lanzado desde Android
// Studio no recibe propiedades de línea de comandos: con -P solo, el IDE generaba un
// APK apuntando a un host equivocado sin avisar, y el fallo salía en el teléfono.
val releaseApiHost: String = (findProperty("releaseApiHost") as String?)
    ?: localProps.getProperty("RELEASE_API_HOST")
    ?: prodApiHost

// Firma de release. Ni el keystore ni sus contraseñas se versionan: salen de
// keystore.properties (gitignored) en local, o de variables de entorno en CI.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun releaseSecret(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: System.getenv(env)

val storeFilePath = releaseSecret("storeFile", "RELEASE_STORE_FILE")
val storePasswordValue = releaseSecret("storePassword", "RELEASE_STORE_PASSWORD")
val keyAliasValue = releaseSecret("keyAlias", "RELEASE_KEY_ALIAS")
val keyPasswordValue = releaseSecret("keyPassword", "RELEASE_KEY_PASSWORD")

// El signingConfig solo se declara si están los cuatro datos Y el keystore existe:
// declararlo a medias rompería el sync del IDE y cualquier build de debug.
val releaseSigningReady = storeFilePath != null &&
    storePasswordValue != null &&
    keyAliasValue != null &&
    keyPasswordValue != null &&
    rootProject.file(storeFilePath).exists()

// Guard de release. El riesgo no es que el build falle: es que NO falle. Sin
// MAPS_API_KEY el mapa queda muerto y `bundleRelease` compila igual de limpio,
// así que el problema aparecería en Play Store y no aquí.
val buildingRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
if (buildingRelease) {
    val faltantes = buildList {
        if (mapsApiKey.isBlank()) {
            add("MAPS_API_KEY — en local.properties o como variable de entorno")
        }
        if (!releaseSigningReady) {
            add(
                "firma de release — keystore.properties con storeFile/storePassword/keyAlias/" +
                    "keyPassword, o las variables RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, " +
                    "RELEASE_KEY_ALIAS y RELEASE_KEY_PASSWORD"
            )
        }
    }
    if (faltantes.isNotEmpty()) {
        error(
            "Build de release incompleto. Falta:\n" +
                faltantes.joinToString("\n") { "  - $it" } +
                "\n\nVer la Fase 4 del plan de despliegue."
        )
    }

    // A qué backend apunta este build. Se imprime SIEMPRE: es invisible una vez
    // compilado y equivocarse cuesta un ciclo entero de instalar y probar.
    logger.lifecycle("[release] backend: https://$releaseApiHost")

    // El .aab es el artefacto que va a Play Store. Publicar uno apuntando a un host
    // de pruebas sería irreversible para esa versión, así que aquí no se asume nada.
    val buildingBundle = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
    if (buildingBundle && releaseApiHost != prodApiHost && findProperty("allowNonProdBundle") != "true") {
        error(
            "Se está generando un .aab apuntando a '$releaseApiHost', que no es producción " +
                "($prodApiHost).\n\nSi es a propósito (validar el bundle antes de tener el " +
                "dominio), repite con -PallowNonProdBundle=true. Para el bundle que sube a Play " +
                "Store, quita RELEASE_API_HOST de local.properties."
        )
    }
}

// Forzar kotlin-stdlib a la versión del proyecto para evitar que dependencias
// transitivas (ej. play-services-location) suban a una versión incompatible
configurations.all {
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib:2.1.21")
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.21")
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.21")
}

android {
    namespace = "com.eldraft.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.eldraft.android"
        minSdk = 26
        targetSdk = 36
        // Play rechaza cualquier subida que repita un versionCode ya usado, y no hay
        // forma de reciclar uno: el 1 se gastó con el .aab del 15 ago 2026. El workflow
        // de publicación inyecta VERSION_CODE a partir del número de ejecución, que solo
        // crece, así que dos publicaciones nunca chocan. (El workflow admite forzar el
        // número a mano; ahí la responsabilidad de no repetirlo es de quien lo escribe.)
        //
        // En local se queda en el valor fijo a propósito. Que un build de escritorio no
        // pueda producir un bundle con un código arbitrario es la red de seguridad: si
        // subes uno a mano con un número alto, quemas todo el rango por debajo y el
        // workflow deja de poder publicar hasta que subas VERSION_CODE_BASE.
        //
        // `takeIf { isNotBlank() }` y no un `?:` a secas: el input opcional del workflow
        // llega como cadena VACÍA, no ausente, y un versionName en blanco produce un
        // bundle que Play rechaza.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "0.1.0-mvp"

        // API key de Google Maps inyectada en el manifest (desde local.properties).
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        // Misma key disponible en código para inicializar el Places SDK.
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")

        // Páginas legales publicadas (política de privacidad, borrado de cuenta).
        // Siempre apuntan a producción, incluso en debug: son documentos públicos
        // registrados en Play Console, no una API. Un build de debug apuntando al
        // backend local abriría una página que solo existe en esta máquina.
        buildConfigField("String", "LEGAL_BASE_URL", "\"https://$prodApiHost\"")
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = rootProject.file(storeFilePath!!)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "API_BASE_URL", "\"http://$devHost:8080\"")
            buildConfigField("String", "WS_BASE_URL", "\"ws://$devHost:8080\"")
        }
        release {
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"https://$releaseApiHost\"")
            buildConfigField("String", "WS_BASE_URL", "\"wss://$releaseApiHost\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // NonNullableMutableLiveDataDetector (androidx.lifecycle) crashea con
        // IncompatibleClassChangeError: viene compilado contra una versión de la API
        // de análisis de Kotlin distinta a la que trae el lint de AGP 8.5.2. Tumba
        // lintVitalAnalyzeRelease y con él assembleRelease (bundleRelease no lo corre).
        //
        // Desactivarlo no esconde nada: el proyecto no usa LiveData en ningún archivo
        // — todo el estado va por StateFlow — así que esta regla no tiene qué revisar.
        // Revisar si sigue haciendo falta al subir AGP.
        disable += "NullSafeMutableLiveData"
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

    // Splash screen nativo del sistema (escudo al arrancar, sin flash)
    implementation(libs.androidx.core.splashscreen)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
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
    implementation(libs.places)

    // Camera & ML Kit
    implementation(libs.mlkit.barcode)
    implementation(libs.zxing.core)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // DI
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // Image loading
    implementation(libs.coil)
    implementation(libs.coil.network)

    // Tests unitarios de JVM (lógica pura: fechas, validaciones). Sin Robolectric:
    // aquí no se instrumenta UI, solo funciones que no tocan el framework.
    testImplementation(libs.kotlin.test)
}
