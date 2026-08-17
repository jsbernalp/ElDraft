package com.eldraft.backend.plugins

import com.eldraft.backend.db.tables.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabases() {
    // La URL puede llegar en formato JDBC (dev, application.conf) o como URI de
    // Postgres con credenciales embebidas (lo que inyectan Railway y compañía).
    val credentials = normalizeDatabaseUrl(
        rawUrl = environment.config.property("database.url").getString(),
        fallbackUser = environment.config.property("database.user").getString(),
        fallbackPassword = environment.config.property("database.password").getString(),
    )

    // Zona de la sesión de Postgres: el NOW() de las consultas crudas (p. ej. el
    // filtro de convocatorias vigentes) tiene que estar en el mismo reloj que las
    // horas locales que guarda `scheduled_at`. El driver ya manda la zona de la
    // JVM en el handshake, pero dejarlo explícito hace que el fix no dependa de
    // ese detalle del driver. El valor se valida como ZoneId al arrancar.
    val timeZone = environment.config.propertyOrNull("app.timezone")
        ?.getString()?.takeIf { it.isNotBlank() } ?: com.eldraft.backend.DEFAULT_TIMEZONE

    val config = HikariConfig().apply {
        jdbcUrl = credentials.jdbcUrl
        username = credentials.user
        password = credentials.password
        maximumPoolSize = environment.config.property("database.maxPoolSize").getString().toInt()
        driverClassName = "org.postgresql.Driver"
        connectionInitSql = "SET TIME ZONE '${java.time.ZoneId.of(timeZone)}'"
        validate()
    }

    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)

    // Reset destructivo de desarrollo. Existe por dos motivos históricos:
    //  1) Las columnas de `ratings` (skill/responsibility) son NOT NULL sin
    //     default; Exposed no puede agregarlas si la tabla tiene filas.
    //  2) Cupos por posición: arrancar de cero para que toda convocatoria tenga
    //     `position_slots`. Por decisión explícita no se conservaron las viejas.
    //
    // Corría en CADA arranque, lo que en producción significa que cualquier
    // redeploy o restart borra las convocatorias, postulaciones y asistencias en
    // curso. Ahora es opt-in explícito y por defecto está APAGADO: una base de
    // producción nueva se crea completa desde cero (createMissingTablesAndColumns
    // aplica todas las columnas), así que no necesita este reset nunca.
    //
    // Para usarlo en desarrollo: DATABASE_RESET_ON_START=true
    val resetOnStart = environment.config
        .propertyOrNull("database.resetOnStart")?.getString()?.toBoolean() ?: false

    if (resetOnStart) {
        log.warn(
            "database.resetOnStart=true -> BORRANDO ratings, asistencias, postulaciones y " +
                "convocatorias. Esto es solo para desarrollo; nunca lo actives en producción."
        )
        // Borramos en orden de dependencias (FK).
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

        // Los atributos derivados de la calificación solo se reinician junto con
        // el borrado de `ratings`: sin esa tabla vacía, este UPDATE destruiría las
        // reputaciones acumuladas de todos los jugadores en cada arranque.
        if (resetOnStart) {
            exec(
                """
                UPDATE player_profiles
                SET skill_score = 5.0,
                    sportsmanship_score = 5.0,
                    responsibility_score = 5.0;
                """.trimIndent()
            )
        }
    }

    log.info("Database connected and schema synchronized (PostGIS location + GIST index)")
}
