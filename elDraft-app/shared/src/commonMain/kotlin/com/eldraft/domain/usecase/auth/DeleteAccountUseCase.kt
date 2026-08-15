package com.eldraft.domain.usecase.auth

import com.eldraft.domain.auth.IdentitySessionCleaner
import com.eldraft.domain.repository.AuthRepository

/**
 * Borra la cuenta del usuario. Irreversible.
 *
 * Existe porque Google Play exige que toda app con cuentas ofrezca borrarlas desde
 * la propia app. En el servidor no se elimina la fila: se anonimiza, para no alterar
 * el historial de partidos de otros jugadores.
 */
class DeleteAccountUseCase(
    private val authRepository: AuthRepository,
    private val identitySession: IdentitySessionCleaner,
) {
    suspend operator fun invoke() {
        // Primero el servidor, y SIN runCatching: si falla, la excepción sube y no
        // se toca nada más. Al revés —limpiar la sesión y luego intentar borrar— el
        // usuario quedaría fuera de la app creyendo que su cuenta desapareció,
        // mientras sigue viva en el servidor y sin forma de volver a entrar a
        // reintentarlo.
        authRepository.deleteAccount()

        // Ya borrada: cerrar la sesión del proveedor es limpieza. Si falla aquí, no
        // tiene sentido revivir una cuenta que ya no existe, así que no aborta.
        runCatching { identitySession.clear() }
    }
}
