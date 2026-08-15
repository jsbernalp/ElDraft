package com.eldraft.domain.auth

/**
 * Cierra la sesión del proveedor de identidad de la plataforma.
 *
 * Existe porque cerrar sesión son dos cosas, no una: borrar el JWT propio
 * (`AuthRepository.logout`) y cerrar la sesión de Firebase Auth, que sobrevive al
 * primero. Sin esto el usuario "sale" de la app pero sigue autenticado ante
 * Firebase, y el siguiente login lo reconoce sin pedir credenciales.
 *
 * La implementación es específica de plataforma —en Android: Firebase Auth +
 * Credential Manager— para que [com.eldraft.domain.usecase.auth.LogoutUseCase]
 * pueda vivir en commonMain y probarse con un fake.
 */
interface IdentitySessionCleaner {
    suspend fun clear()
}
