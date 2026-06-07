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
            RatingsTable
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
    }

    log.info("Database connected and schema synchronized (PostGIS location + GIST index)")
}
