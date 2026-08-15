package com.eldraft.backend.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Páginas legales públicas. Google Play exige dos URLs accesibles sin instalar la
 * app ni iniciar sesión: la política de privacidad y una página donde el usuario
 * pueda pedir el borrado de su cuenta.
 *
 * Se sirven desde el propio backend en vez de un hosting aparte para no depender de
 * otro servicio. Van fuera de `/api/v1` y fuera de `authenticate`: son públicas a
 * propósito.
 *
 * ⚠️ Estas URLs quedan registradas en la ficha de Play Console. Si algún día se
 * migra al dominio propio (ver deuda conocida del plan de despliegue), hay que
 * actualizarlas allá y mantener las viejas respondiendo o redirigiendo.
 */
fun Route.legalRoutes() {
    get("/privacidad") { call.respondLegalPage("privacidad.html") }
    get("/eliminar-cuenta") { call.respondLegalPage("eliminar-cuenta.html") }
}

private suspend fun ApplicationCall.respondLegalPage(fileName: String) {
    // El classloader es el del jar sombreado; los .html viajan dentro de él.
    val html = LegalPagesAnchor::class.java.classLoader
        ?.getResourceAsStream("legal/$fileName")
        ?.bufferedReader()
        ?.use { it.readText() }

    if (html == null) {
        // Empaquetado roto: mejor un 500 explícito que una página en blanco que
        // Play daría por buena en la revisión.
        application.log.error("No se encontró el recurso legal/$fileName en el classpath")
        respond(HttpStatusCode.InternalServerError, "Página no disponible")
        return
    }
    respondText(html, ContentType.Text.Html.withCharset(Charsets.UTF_8))
}

/** Ancla de classloader para localizar los recursos empaquetados. */
private object LegalPagesAnchor
