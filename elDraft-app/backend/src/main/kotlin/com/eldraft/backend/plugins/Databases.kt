package com.eldraft.backend.plugins

import com.eldraft.backend.db.tables.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabases() {
    val config = HikariConfig().apply {
        jdbcUrl = environment.config.property("database.url").getString()
        username = environment.config.property("database.user").getString()
        password = environment.config.property("database.password").getString()
        maximumPoolSize = environment.config.property("database.maxPoolSize").getString().toInt()
        driverClassName = "org.postgresql.Driver"
        validate()
    }

    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)

    // Reset de desarrollo ANTES de tocar el esquema. Dos motivos:
    //  1) Las columnas de `ratings` (skill/responsibility) son NOT NULL sin
    //     default; Exposed no puede agregarlas si la tabla tiene filas.
    //  2) Cupos por posición: arrancamos de cero las convocatorias para que todas
    //     tengan `position_slots`. Por decisión explícita no conservamos las viejas.
    // Borramos en orden de dependencias (FK). OJO: en producción esto debería
    // hacerse UNA sola vez a mano, no en cada arranque.
    transaction {
        exec("DELETE FROM ratings;")
        // Tabla nueva: puede no existir en el primer arranque (el reset corre
        // antes de crear el esquema). Bloque plpgsql que no falla si falta.
        exec(
            """
            DO ${'$'}${'$'} BEGIN
                IF to_regclass('public.organizer_no_show_reports') IS NOT NULL THEN
                    DELETE FROM organizer_no_show_reports;
                END IF;
            END ${'$'}${'$'};
            """.trimIndent()
        )
        exec(
            """
            DO ${'$'}${'$'} BEGIN
                IF to_regclass('public.player_no_show_marks') IS NOT NULL THEN
                    DELETE FROM player_no_show_marks;
                END IF;
            END ${'$'}${'$'};
            """.trimIndent()
        )
        exec("DELETE FROM attendance_records;")
        exec("DELETE FROM postulations;")
        exec("DELETE FROM convocatories;")
    }

    transaction {
        // Habilitar extensión PostGIS
        exec("CREATE EXTENSION IF NOT EXISTS postgis;")
        // Crear tablas
        SchemaUtils.createMissingTablesAndColumns(
            UsersTable,
            PlayerProfilesTable,
            ConvocatoriesTable,
            PostulationsTable,
            AttendanceRecordsTable,
            RatingsTable,
            OrganizerNoShowReportsTable,
            PlayerNoShowMarksTable
        )

        // Columna geoespacial PostGIS (Exposed no la modela nativamente).
        // Se gestiona vía SQL crudo: GEOGRAPHY(POINT,4326) + índice GIST.
        exec(
            """
            ALTER TABLE convocatories
            ADD COLUMN IF NOT EXISTS location geography(Point, 4326);
            """.trimIndent()
        )
        // Backfill de filas existentes que tengan lat/lng pero no location.
        exec(
            """
            UPDATE convocatories
            SET location = ST_SetSRID(ST_MakePoint(location_lng, location_lat), 4326)::geography
            WHERE location IS NULL;
            """.trimIndent()
        )
        // Índice geoespacial crítico para ST_DWithin.
        exec(
            """
            CREATE INDEX IF NOT EXISTS convocatories_location_idx
            ON convocatories USING GIST(location);
            """.trimIndent()
        )

        // Última ubicación conocida del usuario como geografía PostGIS, para
        // poder notificar convocatorias dentro de cierto radio. Mismo patrón
        // que `convocatories.location`: columna cruda + índice GIST.
        exec(
            """
            ALTER TABLE users
            ADD COLUMN IF NOT EXISTS location geography(Point, 4326);
            """.trimIndent()
        )
        exec(
            """
            UPDATE users
            SET location = ST_SetSRID(ST_MakePoint(last_lng, last_lat), 4326)::geography
            WHERE location IS NULL AND last_lat IS NOT NULL AND last_lng IS NOT NULL;
            """.trimIndent()
        )
        exec(
            """
            CREATE INDEX IF NOT EXISTS users_location_idx
            ON users USING GIST(location);
            """.trimIndent()
        )

        // Tras crear las columnas nuevas, reiniciamos los atributos derivados de
        // la calificación en player_profiles (la tabla ratings ya quedó vacía).
        exec(
            """
            UPDATE player_profiles
            SET skill_score = 5.0,
                sportsmanship_score = 5.0,
                responsibility_score = 5.0;
            """.trimIndent()
        )
    }

    log.info("Database connected and schema synchronized (PostGIS location + GIST index)")
}
