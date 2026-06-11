package com.eldraft.backend.service

import com.eldraft.backend.repository.ProfileUpsert
import com.eldraft.backend.repository.UserRepository
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Verifica la validación de PlayerService.upsertProfile. La validación ocurre
 * ANTES de tocar el repositorio, por lo que los casos inválidos nunca llegan a
 * la base de datos (no se requiere conexión).
 */
class PlayerServiceValidationTest {

    private val service = PlayerService(UserRepository())
    private val userId = UUID.randomUUID()

    private fun upsert(
        positionPrimary: String = "Delantero",
        dominantFoot: String = "Derecho",
        height: Int? = null,
    ) = service.upsertProfile(
        userId,
        ProfileUpsert(
            positionPrimary = positionPrimary,
            positionSecondary = null,
            dominantFoot = dominantFoot,
            height = height,
            build = null,
        ),
    )

    @Test
    fun posicion_primaria_vacia_falla() {
        val ex = assertFailsWith<IllegalArgumentException> { upsert(positionPrimary = "") }
        assertEquals("La posición primaria es obligatoria", ex.message)
    }

    @Test
    fun pierna_dominante_vacia_falla() {
        val ex = assertFailsWith<IllegalArgumentException> { upsert(dominantFoot = "  ") }
        assertEquals("La pierna dominante es obligatoria", ex.message)
    }

    @Test
    fun altura_fuera_de_rango_falla() {
        assertFailsWith<IllegalArgumentException> { upsert(height = 50) }
        assertFailsWith<IllegalArgumentException> { upsert(height = 300) }
    }
}
