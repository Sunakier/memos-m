package org.example.memosm.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.*

@Composable
fun rememberScrollContext(
    listState: LazyListState, onScrollDown: () -> Unit = {}, onScrollUp: () -> Unit = {}
): ScrollContext {
    val scrollContext = remember { ScrollContext() }

    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousScrollOffset = listState.firstVisibleItemScrollOffset

        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (currentIndex, currentOffset) ->
                if (currentIndex > previousIndex) {
                    scrollContext.isScrollingDown = true
                    onScrollDown()
                } else if (currentIndex < previousIndex) {
                    scrollContext.isScrollingDown = false
                    onScrollUp()
                } else if (currentOffset > previousScrollOffset + 10) {
                    scrollContext.isScrollingDown = true
                    onScrollDown()
                } else if (currentOffset < previousScrollOffset - 10) {
                    scrollContext.isScrollingDown = false
                    onScrollUp()
                }

                previousIndex = currentIndex
                previousScrollOffset = currentOffset
            }
    }

    return scrollContext
}

@Composable
fun rememberStaggeredGridScrollContext(
    listState: LazyStaggeredGridState, onScrollDown: () -> Unit = {}, onScrollUp: () -> Unit = {}
): ScrollContext {
    val scrollContext = remember { ScrollContext() }

    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousScrollOffset = listState.firstVisibleItemScrollOffset

        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (currentIndex, currentOffset) ->
                if (currentIndex > previousIndex) {
                    scrollContext.isScrollingDown = true
                    onScrollDown()
                } else if (currentIndex < previousIndex) {
                    scrollContext.isScrollingDown = false
                    onScrollUp()
                } else if (currentOffset > previousScrollOffset + 10) {
                    scrollContext.isScrollingDown = true
                    onScrollDown()
                } else if (currentOffset < previousScrollOffset - 10) {
                    scrollContext.isScrollingDown = false
                    onScrollUp()
                }

                previousIndex = currentIndex
                previousScrollOffset = currentOffset
            }
    }

    return scrollContext
}

class ScrollContext {
    var isScrollingDown by mutableStateOf(false)
}
