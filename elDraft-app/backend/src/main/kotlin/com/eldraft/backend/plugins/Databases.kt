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
    }

    log.info("Database connected and schema synchronized")
}
