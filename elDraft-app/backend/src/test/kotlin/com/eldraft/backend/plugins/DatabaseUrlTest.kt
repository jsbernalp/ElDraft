package com.eldraft.backend.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * La URL de base de datos llega en dos formatos distintos según el entorno, y el
 * driver de PostgreSQL solo entiende uno. Estos tests fijan la traducción.
 *
 * El caso que motiva todo esto: Railway inyecta `DATABASE_URL` como URI de Postgres
 * con las credenciales embebidas. Antes se pasaba tal cual a HikariCP y el deploy
 * moría con "No suitable driver", que no apunta a la causa.
 */
class DatabaseUrlTest {

    @Test
    fun una_url_jdbc_se_deja_intacta_y_usa_las_credenciales_sueltas() {
        val result = normalizeDatabaseUrl(
            rawUrl = "jdbc:postgresql://localhost:5432/eldraft",
            fallbackUser = "eldraft",
            fallbackPassword = "eldraft",
        )

        assertEquals("jdbc:postgresql://localhost:5432/eldraft", result.jdbcUrl)
        assertEquals("eldraft", result.user)
        assertEquals("eldraft", result.password)
    }

    @Test
    fun una_uri_de_postgres_se_traduce_a_jdbc_y_extrae_las_credenciales() {
        // Formato exacto que inyecta el servicio de Postgres de Railway.
        val result = normalizeDatabaseUrl(
            rawUrl = "postgresql://postgres:sEcReT123@postgres.railway.internal:5432/railway",
            fallbackUser = "eldraft",
            fallbackPassword = "eldraft",
        )

        assertEquals("jdbc:postgresql://postgres.railway.internal:5432/railway", result.jdbcUrl)
        assertEquals("postgres", result.user)
        assertEquals("sEcReT123", result.password)
    }

    @Test
    fun el_esquema_corto_postgres_tambien_se_acepta() {
        val result = normalizeDatabaseUrl(
            rawUrl = "postgres://u:p@db.example.com:5432/mydb",
            fallbackUser = "x",
            fallbackPassword = "y",
        )

        assertEquals("jdbc:postgresql://db.example.com:5432/mydb", result.jdbcUrl)
    }

    @Test
    fun las_contrasenas_percent_encoded_se_decodifican() {
        // Los proveedores generan contraseñas con caracteres que rompen la URI si
        // no van codificados; si no las decodificamos, la autenticación falla.
        val result = normalizeDatabaseUrl(
            rawUrl = "postgresql://user:p%40ss%3Aword%2F@host:5432/db",
            fallbackUser = "x",
            fallbackPassword = "y",
        )

        assertEquals("p@ss:word/", result.password)
    }

    @Test
    fun sin_puerto_explicito_se_asume_5432() {
        val result = normalizeDatabaseUrl(
            rawUrl = "postgresql://user:pass@host/db",
            fallbackUser = "x",
            fallbackPassword = "y",
        )

        assertEquals("jdbc:postgresql://host:5432/db", result.jdbcUrl)
    }

    @Test
    fun los_parametros_de_query_se_conservan() {
        // sslmode=require es obligatorio al conectar por el proxy público.
        val result = normalizeDatabaseUrl(
            rawUrl = "postgresql://user:pass@host:5432/db?sslmode=require",
            fallbackUser = "x",
            fallbackPassword = "y",
        )

        assertEquals("jdbc:postgresql://host:5432/db?sslmode=require", result.jdbcUrl)
    }

    @Test
    fun una_uri_sin_credenciales_cae_a_las_variables_sueltas() {
        val result = normalizeDatabaseUrl(
            rawUrl = "postgresql://host:5432/db",
            fallbackUser = "eldraft",
            fallbackPassword = "secreto",
        )

        assertEquals("eldraft", result.user)
        assertEquals("secreto", result.password)
    }

    @Test
    fun un_esquema_desconocido_falla_con_un_mensaje_util() {
        val error = assertFailsWith<IllegalArgumentException> {
            normalizeDatabaseUrl("mysql://user:pass@host/db", "x", "y")
        }

        assertEquals(true, error.message?.contains("jdbc:postgresql://"))
    }

    @Test
    fun una_uri_sin_nombre_de_base_de_datos_falla() {
        assertFailsWith<IllegalArgumentException> {
            normalizeDatabaseUrl("postgresql://user:pass@host:5432", "x", "y")
        }
    }
}
