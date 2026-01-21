package org.example.memosm.viewmodel.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.example.memosm.viewmodel.PaginatedListState

interface ListManager<T> {
    val listState: StateFlow<PaginatedListState<T>>

    // Refresh: true to reload from page 1, false to fetch if empty
    // SoftRefresh: when true, keeps existing items visible during refresh (no reset)
    fun fetch(refresh: Boolean = false, softRefresh: Boolean = false)

    // Load next page if available
    fun loadMore()

    // Reset the list completely (e.g. on logout)
    fun reset()
}

abstract class BaseListManager<T>(
    private val scope: CoroutineScope,
    private val initialState: PaginatedListState<T> = PaginatedListState()
) : ListManager<T> {

    protected val _listState = MutableStateFlow(initialState)
    override val listState: StateFlow<PaginatedListState<T>> = _listState.asStateFlow()

    // Abstract methods to be implemented by specific managers
    // Returns a Pair of (Items, NextPageToken)
    protected abstract suspend fun fetchFromApi(pageToken: String?): Pair<List<T>, String?>

    // Optional: Process item before adding to state (e.g. resolve relative URLs)
    protected open suspend fun processItem(item: T): T = item

    override fun fetch(refresh: Boolean, softRefresh: Boolean) {
        android.util.Log.d(
            "MemosListManager",
            "fetch: refresh=$refresh, softRefresh=$softRefresh, currentItems=${_listState.value.items.size}"
        )
        if (refresh && !softRefresh) {
            reset()
        }

        // If already loading, skip
        if (_listState.value.isLoading) {
            android.util.Log.d("MemosListManager", "fetch: already loading, skipping")
            return
        }

        // If not refreshing and we already have items, we don't need to fetch page 1 again.
        // The user should use loadMore() for the next page.
        // This prevents resetting the list to page 1 when navigating back to a screen that has data.
        if (!refresh && _listState.value.items.isNotEmpty()) {
            android.util.Log.d(
                "MemosListManager", "fetch: items exist and not refreshing, skipping"
            )
            return
        }

        loadInternal(pageToken = null)
    }

    override fun loadMore() {
        android.util.Log.d(
            "MemosListManager",
            "loadMore: isLoading=${_listState.value.isLoading}, nextToken=${_listState.value.nextPageToken}"
        )
        if (_listState.value.isLoading || _listState.value.nextPageToken.isNullOrBlank()) return
        loadInternal(pageToken = _listState.value.nextPageToken)
    }

    override fun reset() {
        android.util.Log.d("MemosListManager", "reset")
        _listState.value = initialState
    }

    // Helper to allow external updates (e.g. CRUD operations updating the list locally)
    fun updateState(transform: (PaginatedListState<T>) -> PaginatedListState<T>) {
        _listState.value = transform(_listState.value)
    }

    // Insert or update an item. 
    // isSameItem checks identity (e.g. ID match).
    // comparator (optional) sorts the list after insertion.
    fun upsert(
        item: T, isSameItem: (T) -> Boolean, comparator: Comparator<T>? = null
    ) {
        updateState { state ->
            val existingIndex = state.items.indexOfFirst(isSameItem)
            val newItems = if (existingIndex != -1) {
                // Replace existing
                state.items.toMutableList().apply { set(existingIndex, item) }
            } else {
                // Add new
                (state.items + item)
            }

            val sortedItems = if (comparator != null) {
                newItems.sortedWith(comparator)
            } else {
                newItems
            }
            state.copy(items = sortedItems)
        }
    }

    // Replace an item only if it exists.
    fun replace(item: T, isSameItem: (T) -> Boolean) {
        updateState { state ->
            val newItems = state.items.map { if (isSameItem(it)) item else it }
            state.copy(items = newItems)
        }
    }

    // Remove items matching the predicate.
    fun remove(predicate: (T) -> Boolean) {
        updateState { state ->
            state.copy(items = state.items.filterNot(predicate))
        }
    }

    private fun loadInternal(pageToken: String?) {
        scope.launch {
            try {
                android.util.Log.d("MemosListManager", "loadInternal: pageToken=$pageToken")
                _listState.value = _listState.value.copy(isLoading = true)

                val (newItems, rawNextToken) = fetchFromApi(pageToken)
                val nextToken = if (rawNextToken.isNullOrBlank()) null else rawNextToken

                val processedItems = newItems.map { processItem(it) }

                android.util.Log.d(
                    "MemosListManager",
                    "loadInternal: fetched ${newItems.size} items, rawToken='$rawNextToken' -> nextToken=$nextToken"
                )

                _listState.value = _listState.value.copy(
                    items = if (pageToken == null) processedItems else _listState.value.items + processedItems,
                    nextPageToken = nextToken,
                    isLoading = false
                )
            } catch (e: Exception) {
                // In a real app we might want to expose the error in the state
                e.printStackTrace()
                android.util.Log.e("MemosListManager", "loadInternal error", e)
                _listState.value = _listState.value.copy(isLoading = false)
            }
        }
    }
}
