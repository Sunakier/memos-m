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
            try {
                val list: List<Account> = gson.fromJson(json, type)
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
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

    // --- Account Helpers ---

    suspend fun getAccounts(): List<Account> {
        val json = context.dataStore.data.map { it[ACCOUNTS_JSON] }.first()

        // If accounts list is empty, but we have legacy credentials, migrate them
        if (json.isNullOrEmpty()) {
            val legacyHost = context.dataStore.data.map { it[HOST_URL] }.first()
            val legacyToken = context.dataStore.data.map { it[ACCESS_TOKEN] }.first()

            if (!legacyHost.isNullOrBlank() && !legacyToken.isNullOrBlank()) {
                val newAccount = Account(
                    hostUrl = legacyHost, accessToken = legacyToken, isActive = true
                )
                val list = listOf(newAccount)
                saveAccounts(list)
                return list
            }
            return emptyList()
        }

        val type = object : TypeToken<List<Account>>() {}.type
        val list: List<Account> = try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }

        var needsSave = false
        val sanitized = list.map { account ->
            @Suppress("SENSELESS_COMPARISON") if (account.id == null || account.hostUrl == null || account.accessToken == null) {
                needsSave = true
                @Suppress("USELESS_ELVIS") account.copy(
                    id = account.id ?: java.util.UUID.randomUUID().toString(),
                    hostUrl = account.hostUrl ?: "",
                    accessToken = account.accessToken ?: ""
                )
            } else {
                account
            }
        }

        val final = if (sanitized.isNotEmpty() && sanitized.none { it.isActive }) {
            needsSave = true
            sanitized.mapIndexed { index, account ->
                if (index == 0) account.copy(isActive = true) else account
            }
        } else {
            sanitized
        }

        if (needsSave) {
            saveAccounts(final)
        }

        return final
    }

    suspend fun addAccount(hostUrl: String, accessToken: String, cookies: Map<String, String> = emptyMap()) {
        val current = getAccounts().toMutableList()
        // Check if account already exists to avoid duplicates
        val existingIndex =
            current.indexOfFirst { it.hostUrl == hostUrl && it.accessToken == accessToken }

        if (existingIndex != -1) {
            // Just activate it
            setActiveAccount(current[existingIndex].id)
        } else {
            // Deactivate others
            val updated = current.map { it.copy(isActive = false) }.toMutableList()
            updated.add(Account(hostUrl = hostUrl, accessToken = accessToken, isActive = true, cookies = cookies))
            saveAccounts(updated)
        }

        // Also update legacy credentials for backward compatibility if needed, 
        // or just to keep MainScreen working for now.
        saveCredentials(hostUrl, accessToken)
    }

    suspend fun setActiveAccount(id: String) {
        val current = getAccounts()
        val updated = current.map { it.copy(isActive = it.id == id) }
        saveAccounts(updated)

        // Update legacy credentials to the active one
        val active = updated.find { it.isActive }
        if (active != null) {
            saveCredentials(active.hostUrl, active.accessToken)
        }
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

        // If we deleted the active one, clear legacy credentials or set new active
        if (updated.isEmpty()) {
            clearCredentials()
        } else if (updated.none { it.isActive }) {
            setActiveAccount(updated.first().id)
        }
    }

    suspend fun updateAccount(id: String, hostUrl: String, token: String, cookies: Map<String, String> = emptyMap()) {
        val current = getAccounts()
        val updated = current.map {
            if (it.id == id) {
                val newAcc = it.copy(hostUrl = hostUrl, accessToken = token, cookies = cookies)
                if (newAcc.isActive) saveCredentials(hostUrl, token)
                newAcc
            } else it
        }
        saveAccounts(updated)
    }

    suspend fun clearCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(HOST_URL)
            preferences.remove(ACCESS_TOKEN)
        }
    }

    suspend fun updateAccountUser(id: String, user: org.example.memosm.model.User) {
        val current = getAccounts()
        val updated = current.map {
            if (it.id == id) it.copy(user = user) else it
        }
        saveAccounts(updated)
    }

    suspend fun updateAccountCookies(id: String, newCookies: Map<String, String>) {
        val current = getAccounts()
        val updated = current.map { account ->
            if (account.id == id) {
                // Merge existing cookies with new cookies
                val mergedCookies = account.cookies.toMutableMap()
                mergedCookies.putAll(newCookies)
                account.copy(cookies = mergedCookies)
            } else {
                account
            }
        }
        saveAccounts(updated)
    }

    suspend fun updateAccountToken(id: String, token: String) {
        val current = getAccounts()
        val updated = current.map {
            if (it.id == id) {
                val newAcc = it.copy(accessToken = token)
                if (newAcc.isActive) saveCredentials(newAcc.hostUrl, token)
                newAcc
            } else it
        }
        saveAccounts(updated)
    }
}
