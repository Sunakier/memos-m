package org.example.memosm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.example.memosm.model.Account

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {

    private val gson = Gson()
    private var cachedAccounts: List<Account>? = null

    companion object {
        val ACCOUNTS_JSON = stringPreferencesKey("accounts_json")
        val PAGE_SIZE = intPreferencesKey("page_size")
        const val DEFAULT_PAGE_SIZE = 10
        val HEADER_SCALE = androidx.datastore.preferences.core.floatPreferencesKey("header_scale")
        const val DEFAULT_HEADER_SCALE = 1.0f
    }

    val accounts: Flow<List<Account>> = context.dataStore.data
        .map { it[ACCOUNTS_JSON] }
        .distinctUntilChanged()
        .map { json ->
            if (json.isNullOrEmpty()) {
                emptyList()
            } else {
                val type = object : TypeToken<List<Account>>() {}.type
                try {
                    val list: List<Account> = gson.fromJson(json, type)
                    cachedAccounts = list
                    list
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

    val account: Flow<Account?> = accounts.map { list ->
        list.find { it.isActive }
    }

    suspend fun saveAccounts(accounts: List<Account>) {
        context.dataStore.edit { preferences ->
            preferences[ACCOUNTS_JSON] = gson.toJson(accounts)
        }
        cachedAccounts = accounts
    }


    // --- Account Helpers ---

    suspend fun getAccounts(): List<Account> {
        cachedAccounts?.let { return it }

        val json = context.dataStore.data.map { it[ACCOUNTS_JSON] }.first()

        if (json.isNullOrEmpty()) {
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
        } else {
            cachedAccounts = final
        }

        return final
    }

    suspend fun addAccount(
        hostUrl: String,
        accessToken: String
    ) {
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
            updated.add(
                Account(
                    hostUrl = hostUrl,
                    accessToken = accessToken,
                    isActive = true
                )
            )
            saveAccounts(updated)
        }
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

        // If we deleted the active one, set new active
        if (updated.isNotEmpty() && updated.none { it.isActive }) {
            setActiveAccount(updated.first().id)
        }
    }

    suspend fun updateAccount(
        id: String,
        hostUrl: String,
        token: String
    ) {
        val current = getAccounts()
        val updated = current.map {
            if (it.id == id) {
                it.copy(hostUrl = hostUrl, accessToken = token)
            } else it
        }
        saveAccounts(updated)
    }

    suspend fun updateAccountUser(id: String, user: org.example.memosm.model.User) {
        val current = getAccounts()
        val updated = current.map {
            if (it.id == id) {
                it.copy(
                    user = user,
                    name = user.username,
                    displayName = user.displayName,
                    avatarUrl = user.avatarUrl,
                    email = user.email,
                    description = user.description
                )
            } else it
        }
        saveAccounts(updated)
    }

    suspend fun updateAccountToken(id: String, token: String) {
        val current = getAccounts()
        val updated = current.map {
            if (it.id == id) {
                it.copy(accessToken = token)
            } else it
        }
        saveAccounts(updated)
    }

    val pageSize: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PAGE_SIZE] ?: DEFAULT_PAGE_SIZE
    }

    suspend fun savePageSize(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[PAGE_SIZE] = size
        }
    }

    val headerScale: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[HEADER_SCALE] ?: DEFAULT_HEADER_SCALE
    }

    suspend fun saveHeaderScale(scale: Float) {
        context.dataStore.edit { preferences ->
            preferences[HEADER_SCALE] = scale
        }
    }
}
