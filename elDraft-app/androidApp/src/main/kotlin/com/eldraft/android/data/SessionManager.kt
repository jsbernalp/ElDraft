package com.eldraft.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "eldraft_session")

/**
 * Sesión persistida del usuario: el JWT emitido por el backend y el userId.
 * Usa Jetpack DataStore (asíncrono, observable vía Flow).
 */
class SessionManager(private val context: Context) {

    private object Keys {
        val TOKEN = stringPreferencesKey("jwt_token")
        val USER_ID = stringPreferencesKey("user_id")
    }

    /** JWT actual (o null si no hay sesión). Observable. */
    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[Keys.TOKEN] }

    /** userId actual (o null). Observable. */
    val userIdFlow: Flow<String?> = context.dataStore.data.map { it[Keys.USER_ID] }

    suspend fun currentToken(): String? = tokenFlow.first()
    suspend fun currentUserId(): String? = userIdFlow.first()

    suspend fun save(token: String, userId: String) {
        context.dataStore.edit {
            it[Keys.TOKEN] = token
            it[Keys.USER_ID] = userId
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
