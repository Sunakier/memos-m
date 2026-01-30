package org.example.memosm.data.cache

import android.util.Log
import org.example.memosm.model.Memo

private const val TAG = "MemoCacheRepository"

/**
 * Repository for caching and retrieving memos from local storage.
 */
class MemoCacheRepository(private val memoDao: MemoDao) {

    /**
     * Cache a list of memos for a specific account and list type.
     * Replaces any existing cached memos for that list.
     */
    suspend fun cacheMemos(accountId: String, listType: CacheListType, memos: List<Memo>) {
        try {
            // Clear existing cache for this list type first
            memoDao.deleteMemos(accountId, listType.name)

            // Cache non-null-named memos
            val cachedMemos = memos
                .filter { it.name != null }
                .map { CachedMemo.fromMemo(it, accountId, listType) }

            if (cachedMemos.isNotEmpty()) {
                memoDao.insertMemos(cachedMemos)
                Log.d(TAG, "Cached ${cachedMemos.size} memos for $listType")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error caching memos for $listType", e)
        }
    }

    /**
     * Retrieve cached memos for a specific account and list type.
     * Returns empty list if no cache exists or on error.
     */
    suspend fun getCachedMemos(accountId: String, listType: CacheListType): List<Memo> {
        return try {
            val cached = memoDao.getMemos(accountId, listType.name)
            val memos = cached.mapNotNull { it.toMemo() }
            Log.d(TAG, "Retrieved ${memos.size} cached memos for $listType")
            memos
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving cached memos for $listType", e)
            emptyList()
        }
    }

    /**
     * Clear all cached memos for an account.
     */
    suspend fun clearCache(accountId: String) {
        try {
            memoDao.deleteAllForAccount(accountId)
            Log.d(TAG, "Cleared cache for account $accountId")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache for account $accountId", e)
        }
    }

    /**
     * Clear all cached memos.
     */
    suspend fun clearAllCache() {
        try {
            memoDao.deleteAll()
            Log.d(TAG, "Cleared all cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all cache", e)
        }
    }
}
