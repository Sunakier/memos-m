package org.example.memosm.viewmodel.manager

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import org.example.memosm.api.MemosApi
import org.example.memosm.model.Memo
import org.example.memosm.model.User
import retrofit2.HttpException

private const val TAG = "MemoListManager"

class UserMemoListManager(
    scope: CoroutineScope,
    private val api: MemosApi,
    private val filterProvider: () -> String?,
    private val pageSizeProvider: () -> Int,
    cacheCallbacks: CacheCallbacks<Memo>? = null
) : BaseListManager<Memo>(scope, cacheCallbacks = cacheCallbacks) {

    override suspend fun fetchFromApi(pageToken: String?): Pair<List<Memo>, String?> {
        val filter = filterProvider()
        val pageSize = pageSizeProvider()
        Log.d(
            TAG,
            "UserMemoListManager fetch: filter=$filter, pageToken=$pageToken, pageSize=$pageSize"
        )

        try {
            val response = api.listMemos(
                pageSize = pageSize,
                pageToken = pageToken,
                filter = filter,
                orderBy = "pinned desc, display_time desc"
            )
            Log.d(TAG, "UserMemoListManager success: count=${response.memos?.size ?: 0}")
            return Pair(response.memos ?: emptyList(), response.nextPageToken)
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "UserMemoListManager failed: filter=$filter, error=$errorBody", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "UserMemoListManager failed: filter=$filter", e)
            throw e
        }
    }
}

class ExploreMemoListManager(
    scope: CoroutineScope,
    private val api: MemosApi,
    private val pageSizeProvider: () -> Int,
    cacheCallbacks: CacheCallbacks<Memo>? = null
) : BaseListManager<Memo>(scope, cacheCallbacks = cacheCallbacks) {

    override suspend fun fetchFromApi(pageToken: String?): Pair<List<Memo>, String?> {
        val filter = "visibility in ['PUBLIC', 'PROTECTED']"
        val pageSize = pageSizeProvider()
        Log.d(TAG, "ExploreMemoListManager fetch: filter=$filter, pageToken=$pageToken")

        try {
            val response = api.listMemos(
                pageSize = pageSize, pageToken = pageToken, filter = filter
            )
            Log.d(TAG, "ExploreMemoListManager success: count=${response.memos?.size ?: 0}")
            return Pair(response.memos ?: emptyList(), response.nextPageToken)
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "ExploreMemoListManager failed: filter=$filter, error=$errorBody", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "ExploreMemoListManager failed: filter=$filter", e)
            throw e
        }
    }
}

class ArchivedMemoListManager(
    scope: CoroutineScope,
    private val api: MemosApi,
    private val currentUserProvider: () -> User?,
    private val pageSizeProvider: () -> Int,
    cacheCallbacks: CacheCallbacks<Memo>? = null
) : BaseListManager<Memo>(scope, cacheCallbacks = cacheCallbacks) {

    override suspend fun fetchFromApi(pageToken: String?): Pair<List<Memo>, String?> {
        val user = currentUserProvider() ?: return Pair(emptyList(), null)
        val userId = user.name?.substringAfterLast("/") ?: ""

        // Use creator_id and row_status
        val filter = if (userId.isNotEmpty()) {
            "creator_id == $userId"
        } else {
            Log.e(TAG, "ArchivedMemoListManager failed: userId=$userId")
            return Pair(emptyList(), null)
        }

        val pageSize = pageSizeProvider()
        Log.d(TAG, "ArchivedMemoListManager fetch: filter=$filter, userId=$userId")

        try {
            val response = api.listMemos(
                pageSize = pageSize, pageToken = pageToken, filter = filter, state = "ARCHIVED"
            )
            return Pair(response.memos ?: emptyList(), response.nextPageToken)
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "ArchivedMemoListManager failed: filter=$filter, error=$errorBody", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "ArchivedMemoListManager failed: filter=$filter", e)
            throw e
        }
    }
}

class SearchMemoListManager(
    scope: CoroutineScope,
    private val api: MemosApi,
    private val pageSizeProvider: () -> Int
) : BaseListManager<Memo>(scope) {

    private var currentFilter: String? = null

    fun updateFilter(filter: String?) {
        currentFilter = filter
    }

    override suspend fun fetchFromApi(pageToken: String?): Pair<List<Memo>, String?> {
        if (currentFilter == null) return Pair(emptyList(), null)

        val pageSize = pageSizeProvider()
        Log.d(TAG, "SearchMemoListManager fetch: filter=$currentFilter")

        try {
            val response = api.listMemos(
                pageSize = pageSize, pageToken = pageToken, filter = currentFilter
            )
            return Pair(response.memos ?: emptyList(), response.nextPageToken)
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "SearchMemoListManager failed: filter=$currentFilter, error=$errorBody", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "SearchMemoListManager failed: filter=$currentFilter", e)
            throw e
        }
    }
}

