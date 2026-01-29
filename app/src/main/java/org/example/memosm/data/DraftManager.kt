package org.example.memosm.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.example.memosm.model.Draft
import java.io.File

/**
 * Manages draft storage in the app's cache directory.
 * Drafts are stored per-account as JSON files: drafts_{accountId}.json
 */
class DraftManager(private val context: Context) {
    
    private val gson = Gson()
    private val mutex = Mutex()
    
    companion object {
        private const val TAG = "DraftManager"
        private const val DRAFTS_DIR = "drafts"
        private fun draftsFileName(accountId: String) = "drafts_$accountId.json"
    }
    
    private fun getDraftsDir(): File {
        val dir = File(context.cacheDir, DRAFTS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    private fun getDraftsFile(accountId: String): File {
        return File(getDraftsDir(), draftsFileName(accountId))
    }
    
    /**
     * Get all drafts for an account.
     */
    suspend fun getDrafts(accountId: String): List<Draft> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val file = getDraftsFile(accountId)
                if (!file.exists()) return@withContext emptyList()
                
                val json = file.readText()
                if (json.isBlank()) return@withContext emptyList()
                
                val type = object : TypeToken<List<Draft>>() {}.type
                gson.fromJson<List<Draft>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Error reading drafts for account $accountId", e)
                emptyList()
            }
        }
    }
    
    /**
     * Save or update a draft. If a draft with the same ID exists, it's updated.
     */
    suspend fun saveDraft(accountId: String, draft: Draft): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val drafts = getDraftsInternal(accountId).toMutableList()
                val existingIndex = drafts.indexOfFirst { it.id == draft.id }
                
                val updatedDraft = draft.copy(updatedAt = System.currentTimeMillis())
                
                if (existingIndex >= 0) {
                    drafts[existingIndex] = updatedDraft
                } else {
                    drafts.add(0, updatedDraft) // Add at beginning (newest first)
                }
                
                saveDraftsInternal(accountId, drafts)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving draft for account $accountId", e)
            }
        }
    }
    
    /**
     * Delete a specific draft by ID.
     */
    suspend fun deleteDraft(accountId: String, draftId: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val drafts = getDraftsInternal(accountId).toMutableList()
                val removed = drafts.removeAll { it.id == draftId }
                if (removed) {
                    saveDraftsInternal(accountId, drafts)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting draft $draftId for account $accountId", e)
            }
        }
    }
    
    /**
     * Clear all drafts for an account.
     */
    suspend fun clearDrafts(accountId: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val file = getDraftsFile(accountId)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing drafts for account $accountId", e)
            }
        }
    }
    
    /**
     * Get draft count for an account (for badge display).
     */
    suspend fun getDraftCount(accountId: String): Int = withContext(Dispatchers.IO) {
        getDrafts(accountId).size
    }
    
    // Internal helpers (must be called within mutex lock)
    
    private fun getDraftsInternal(accountId: String): List<Draft> {
        val file = getDraftsFile(accountId)
        if (!file.exists()) return emptyList()
        
        val json = file.readText()
        if (json.isBlank()) return emptyList()
        
        return try {
            val type = object : TypeToken<List<Draft>>() {}.type
            gson.fromJson<List<Draft>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing drafts JSON", e)
            emptyList()
        }
    }
    
    private fun saveDraftsInternal(accountId: String, drafts: List<Draft>) {
        val file = getDraftsFile(accountId)
        file.writeText(gson.toJson(drafts))
    }
}
