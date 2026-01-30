package org.example.memosm.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object for cached memos.
 */
@Dao
interface MemoDao {

    /**
     * Insert or replace cached memos.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemos(memos: List<CachedMemo>)

    /**
     * Get all cached memos for a specific account and list type.
     * Ordered by cachedAt descending to maintain approximate order.
     */
    @Query("SELECT * FROM cached_memos WHERE accountId = :accountId AND listType = :listType ORDER BY cachedAt DESC")
    suspend fun getMemos(accountId: String, listType: String): List<CachedMemo>

    /**
     * Delete all cached memos for a specific account and list type.
     */
    @Query("DELETE FROM cached_memos WHERE accountId = :accountId AND listType = :listType")
    suspend fun deleteMemos(accountId: String, listType: String)

    /**
     * Delete all cached memos for a specific account.
     */
    @Query("DELETE FROM cached_memos WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)

    /**
     * Delete all cached memos.
     */
    @Query("DELETE FROM cached_memos")
    suspend fun deleteAll()
}
