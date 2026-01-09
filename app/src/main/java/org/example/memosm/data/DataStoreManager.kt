package org.example.memosm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {

    companion object {
        val HOST_URL = stringPreferencesKey("host_url")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
    }

    val hostUrl: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[HOST_URL]
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACCESS_TOKEN]
    }

    suspend fun saveCredentials(url: String, token: String) {
        context.dataStore.edit { preferences ->
            preferences[HOST_URL] = url
            preferences[ACCESS_TOKEN] = token
        }
    }

    suspend fun clearCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(HOST_URL)
            preferences.remove(ACCESS_TOKEN)
        }
    }
}
