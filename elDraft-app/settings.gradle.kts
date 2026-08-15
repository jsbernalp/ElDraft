pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "elDraft"

include(":backend")

// `:shared` y `:androidApp` aplican plugins de Android, que en la fase de
// configuración exigen un SDK de Android instalado (sdk.dir en local.properties o
// ANDROID_HOME). Gradle configura TODOS los proyectos aunque solo pidas una tarea
// del backend, así que en una imagen Docker o en un runner de CI sin SDK el build
// del backend fallaría sin siquiera compilar nada suyo.
//
// El backend no depende de ninguno de los dos, así que un build backend-only los
// excluye: ./gradlew -PbackendOnly :backend:installDist
if (!providers.gradleProperty("backendOnly").isPresent) {
    include(":shared")
    include(":androidApp")
}
