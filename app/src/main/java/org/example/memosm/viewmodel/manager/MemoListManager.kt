package org.example.memosm.viewmodel.manager

import kotlinx.coroutines.CoroutineScope
import org.example.memosm.api.MemosApiV0353
import org.example.memosm.model.Memo
import org.example.memosm.model.User
import org.example.memosm.viewmodel.PaginatedListState

private const val PAGE_SIZE = 10

class UserMemoListManager(
    scope: CoroutineScope,
    private val api: MemosApiV0353,
    private val filterProvider: () -> String?
) : BaseListManager<Memo>(scope) {

    override suspend fun fetchFromApi(pageToken: String?): Pair<List<Memo>, String?> {
        val filter = filterProvider()


        val response = api.listMemos(
            pageSize = PAGE_SIZE,
            pageToken = pageToken,
            filter = filter
        )
        return Pair(response.memos ?: emptyList(), response.nextPageToken)
    }
}

class ExploreMemoListManager(
    scope: CoroutineScope,
    private val api: MemosApiV0353
) : BaseListManager<Memo>(scope) {

    override suspend fun fetchFromApi(pageToken: String?): Pair<List<Memo>, String?> {
        val filter = "row_status == 'NORMAL' && (visibility == 'PUBLIC' || visibility == 'PROTECTED')"
        val response = api.listMemos(
            pageSize = PAGE_SIZE,
            pageToken = pageToken,
            filter = filter
        )
        return Pair(response.memos ?: emptyList(), response.nextPageToken)
    }
}

class ArchivedMemoListManager(
    scope: CoroutineScope,
    private val api: MemosApiV0353,
    private val currentUserProvider: () -> User?
) : BaseListManager<Memo>(scope) {

    override suspend fun fetchFromApi(pageToken: String?): Pair<List<Memo>, String?> {
        val user = currentUserProvider() ?: return Pair(emptyList(), null)
        val userId = user.name?.substringAfterLast("/") ?: ""
        
        // Use creator_id and row_status
        val filter = if (userId.isNotEmpty()) {
            "creator_id == $userId && row_status == 'ARCHIVED'"
        } else {
            "row_status == 'ARCHIVED'"
        }
        
        val response = api.listMemos(
            pageSize = PAGE_SIZE,
            pageToken = pageToken,
            filter = filter
        )
        return Pair(response.memos ?: emptyList(), response.nextPageToken)
    }
}

class SearchMemoListManager(
    scope: CoroutineScope,
    private val api: MemosApiV0353
) : BaseListManager<Memo>(scope) {

    private var currentFilter: String? = null
    
    fun updateFilter(filter: String?) {
        currentFilter = filter
        reset() 
    }

    override suspend fun fetchFromApi(pageToken: String?): Pair<List<Memo>, String?> {
        if (currentFilter == null) return Pair(emptyList(), null)
        
        val response = api.listMemos(
            pageSize = PAGE_SIZE,
            pageToken = pageToken,
            filter = currentFilter
        )
        return Pair(response.memos ?: emptyList(), response.nextPageToken)
    }
}
