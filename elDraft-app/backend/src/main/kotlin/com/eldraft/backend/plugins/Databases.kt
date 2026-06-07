package com.eldraft.backend.plugins

import com.eldraft.backend.db.tables.*
import com.eldraft.backend.repository.UserRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import io.ktor.util.AttributeKey
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

/** Acceso al repositorio de usuarios desde las rutas. */
val UserRepositoryKey = AttributeKey<UserRepository>("UserRepository")
val Application.userRepository: UserRepository get() = attributes[UserRepositoryKey]

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

    attributes.put(UserRepositoryKey, UserRepository())

    log.info("Database connected and schema synchronized")
}
