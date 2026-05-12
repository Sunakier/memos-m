package org.example.memosm.viewmodel.manager

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import org.example.memosm.api.MemosApi
import org.example.memosm.api.MemoOrderBy
import org.example.memosm.api.buildMemoCreatorFilter
import org.example.memosm.api.resolveMemoOrderBy
import org.example.memosm.model.Memo
import org.example.memosm.model.User
import retrofit2.HttpException

private const val TAG = "MemoListManager"

class UserMemoListManager(
    scope: CoroutineScope,
    private val apiProvider: () -> MemosApi?,
    private val filterProvider: () -> String?,
    private val pageSizeProvider: () -> Int,
    cacheCallbacks: CacheCallbacks<Memo>? = null
) : BaseListManager<Memo>(scope, cacheCallbacks = cacheCallbacks) {

    override suspend fun fetchFromApi(pageToken: String?): Pair<List<Memo>, String?> {
        val api = apiProvider() ?: return Pair(emptyList(), null)
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
                orderBy = api.resolveMemoOrderBy(MemoOrderBy.PINNED_DESC)
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
    private val apiProvider: () -> MemosApi?,
    private val pageSizeProvider: () -> Int,
    cacheCallbacks: CacheCallbacks<Memo>? = null
) : BaseListManager<Memo>(scope, cacheCallbacks = cacheCallbacks) {

    override suspend fun fetchFromApi(pageToken: String?): Pair<List<Memo>, String?> {
        val api = apiProvider() ?: return Pair(emptyList(), null)
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
    private val apiProvider: () -> MemosApi?,
    private val currentUserProvider: () -> User?,
    private val pageSizeProvider: () -> Int,
    cacheCallbacks: CacheCallbacks<Memo>? = null
) : BaseListManager<Memo>(scope, cacheCallbacks = cacheCallbacks) {

    override suspend fun fetchFromApi(pageToken: String?): Pair<List<Memo>, String?> {
        val api = apiProvider() ?: return Pair(emptyList(), null)
        val user = currentUserProvider() ?: return Pair(emptyList(), null)
        val filter = api.buildMemoCreatorFilter(user)
        if (filter.isNullOrBlank()) {
            Log.e(TAG, "ArchivedMemoListManager failed: user=${user.name}")
            return Pair(emptyList(), null)
        }

        val pageSize = pageSizeProvider()
        Log.d(TAG, "ArchivedMemoListManager fetch: filter=$filter, user=${user.name}")

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
    private val apiProvider: () -> MemosApi?,
    private val pageSizeProvider: () -> Int
) : BaseListManager<Memo>(scope) {

    private var currentFilter: String? = null
    private var currentOrderBy: MemoOrderBy? = null

    fun updateFilter(filter: String?, orderBy: MemoOrderBy?) {
        currentFilter = filter
        currentOrderBy = orderBy
    }

    override suspend fun fetchFromApi(pageToken: String?): Pair<List<Memo>, String?> {
        val api = apiProvider() ?: return Pair(emptyList(), null)
        if (currentFilter == null) return Pair(emptyList(), null)

        val pageSize = pageSizeProvider()
        Log.d(TAG, "SearchMemoListManager fetch: filter=$currentFilter")

        try {
            val response = api.listMemos(
                pageSize = pageSize,
                pageToken = pageToken,
                filter = currentFilter,
                orderBy = api.resolveMemoOrderBy(currentOrderBy)
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
