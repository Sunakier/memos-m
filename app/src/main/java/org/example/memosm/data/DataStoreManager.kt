package org.example.memosm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.example.memosm.model.Account

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {

    private val gson = Gson()

    companion object {
        val HOST_URL = stringPreferencesKey("host_url")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val ATTACHMENT_CELL_WIDTH = floatPreferencesKey("attachment_cell_width")
        val MEMO_DRAFT_JSON = stringPreferencesKey("memo_draft_json")
        val ACCOUNTS_JSON = stringPreferencesKey("accounts_json")
    }

    val hostUrl: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[HOST_URL]
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACCESS_TOKEN]
    }

    val accounts: Flow<List<Account>> = context.dataStore.data.map { preferences ->
        val json = preferences[ACCOUNTS_JSON]
        if (json.isNullOrEmpty()) {
            emptyList()
        } else {
            val type = object : TypeToken<List<Account>>() {}.type
            gson.fromJson(json, type)
        }
    }

    val attachmentCellWidth: Flow<Float?> = context.dataStore.data.map { preferences ->
        preferences[ATTACHMENT_CELL_WIDTH]
    }

    val memoDraftJson: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[MEMO_DRAFT_JSON]
    }

    suspend fun saveCredentials(url: String, token: String) {
        context.dataStore.edit { preferences ->
            preferences[HOST_URL] = url
            preferences[ACCESS_TOKEN] = token
        }
    }

    suspend fun saveAccounts(accounts: List<Account>) {
        context.dataStore.edit { preferences ->
            preferences[ACCOUNTS_JSON] = gson.toJson(accounts)
        }
    }

    suspend fun saveAttachmentCellWidth(width: Float) {
        context.dataStore.edit { preferences ->
            preferences[ATTACHMENT_CELL_WIDTH] = width
        }
    }

    suspend fun saveMemoDraft(json: String) {
        context.dataStore.edit { preferences ->
            preferences[MEMO_DRAFT_JSON] = json
        }
    }

    suspend fun clearMemoDraft() {
        context.dataStore.edit { preferences ->
            preferences.remove(MEMO_DRAFT_JSON)
        }
    }

    suspend fun clearCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(HOST_URL)
            preferences.remove(ACCESS_TOKEN)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    // --- Account Helpers ---

    suspend fun getAccounts(): List<Account> {
        return accounts.first()
    }

    suspend fun setActiveAccount(id: String) {
        val current = getAccounts()
        val updated = current.map { it.copy(isActive = it.id == id) }
        saveAccounts(updated)
    }

    suspend fun updateAccountLastUsed(id: String, timestamp: Long) {
        val current = getAccounts()
        val updated = current.map {
            if (it.id == id) it.copy(lastUsed = timestamp) else it
        }
        saveAccounts(updated)
    }

    suspend fun deleteAccount(id: String) {
        val current = getAccounts()
        val updated = current.filterNot { it.id == id }
        saveAccounts(updated)
    }

    suspend fun updateAccount(id: String, hostUrl: String, token: String) {
        val current = getAccounts()
        val updated = current.map {
            if (it.id == id) it.copy(hostUrl = hostUrl, accessToken = token) else it
        }
        saveAccounts(updated)
    }
}
