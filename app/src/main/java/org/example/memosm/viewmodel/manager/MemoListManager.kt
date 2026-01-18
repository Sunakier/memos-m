package org.example.memosm.viewmodel.manager

import kotlinx.coroutines.CoroutineScope
import org.example.memosm.api.MemosApiV0353
import org.example.memosm.model.Memo
import org.example.memosm.model.User
import org.example.memosm.viewmodel.PaginatedListState

private const val PAGE_SIZE = 10

// Helper to filter/process memos if needed (e.g. attachment URL fixups)
// This mirrors the logic previously in MemosViewModel
private fun processMemoHelper(memo: Memo, baseUrl: String = ""): Memo {
    // If we needed to strip hostUrl or ensure absolute paths, we'd do it here.
    // For now assuming the data model passes through as-is or logic is simple.
    // If complex attachment processing is needed, we can inject a helper.
    return memo
}

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
        val response = api.listMemos(
            pageSize = PAGE_SIZE,
            pageToken = pageToken,
            filter = "visibilities == ['PUBLIC', 'PROTECTED']"
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
        val filter = "creator == '${user.name}' && row_status == 'ARCHIVED'"
        
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
    // We might also want to support sorting, but the API might not support it in the same call or it handles it via filter/params
    // The original code had an 'orderBy' param implementation in prepareSearch
    
    // Original ViewModel prepareSearch logic:
    // viewModel.prepareSearch(isExplore, filterString, orderBy)
    // Here we can just store the filter string.
    
    fun updateFilter(filter: String?) {
        currentFilter = filter
        reset() // Clear current results
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
