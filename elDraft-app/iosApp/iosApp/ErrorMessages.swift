import Foundation
import shared

extension Error {
    /// La excepción Kotlin original, si este error vino de `shared`.
    ///
    /// Una excepción Kotlin llega a Swift como `NSError` (dominio
    /// `KotlinException`) con la excepción original en `userInfo`; el error NO
    /// es un `KotlinThrowable`, así que `error as? KotlinThrowable` o
    /// `error as? ApiException` siempre dan `nil`.
    var kotlinThrowable: KotlinThrowable? {
        (self as NSError).userInfo["KotlinException"] as? KotlinThrowable
    }

    /// `ApiException` de `shared` (código, status HTTP y conflictos de horario).
    var apiException: ApiException? { kotlinThrowable as? ApiException }

    /// Mensaje amable de una excepción de `shared` (`Throwable.userMessage`):
    /// el del backend cuando lo hay ("Completa tu perfil para crear equipos",
    /// "No existe un jugador con ese correo"…) y `fallback` si no.
    func kotlinMessage(fallback: String) -> String {
        kotlinThrowable?.userMessage(fallback: fallback) ?? fallback
    }
}
