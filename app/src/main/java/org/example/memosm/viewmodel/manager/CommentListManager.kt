package org.example.memosm.viewmodel.manager

import kotlinx.coroutines.CoroutineScope
import org.example.memosm.api.MemosApi
import org.example.memosm.model.Memo

private const val COMMENT_PAGE_SIZE = 100 // Comments usually loaded all or in large batches

class CommentListManager(
    scope: CoroutineScope,
    private val api: MemosApi
) : BaseListManager<Memo>(scope) {

    private var currentMemoName: String? = null

    fun setMemo(memoName: String) {
        if (currentMemoName != memoName) {
            currentMemoName = memoName
            reset()
            fetch()
        }
    }

    override suspend fun fetchFromApi(pageToken: String?): Pair<List<Memo>, String?> {
        val parent = currentMemoName ?: return Pair(emptyList(), null)

        val response = api.listMemoComments(
            memo = parent,
            pageSize = COMMENT_PAGE_SIZE,
            pageToken = pageToken
        )

        // Sort comments by display_time asc (oldest first) usually
        // API might default to desc. We can sort client side or use sort param if available.
        // For now, returning as is.
        val sorted = response.memos?.sortedBy { it.createTime } ?: emptyList()

        return Pair(sorted, response.nextPageToken)
    }
}
