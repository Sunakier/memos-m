package org.example.memosm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.example.memosm.R
import org.example.memosm.model.Memo
import org.example.memosm.viewmodel.MemosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoSearchBar(
    viewModel: MemosViewModel,
    onMemoClick: (Memo) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.memo_search_placeholder)
) {
    val uiState by viewModel.uiState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val filteredMemos = remember(query, uiState.memos, uiState.exploreMemos) {
        if (query.isBlank()) {
            emptyList()
        } else {
            val allMemos = (uiState.memos + uiState.exploreMemos).distinctBy { it.name }
            allMemos.filter { it.content.contains(query, ignoreCase = true) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { isTraversalGroup = true }
            .zIndex(1f)
    ) {
        SearchBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .semantics { traversalIndex = 0f },
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { focusManager.clearFocus() },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    placeholder = { Text(placeholder) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                )
            },
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            SearchResultContent(
                query = query,
                filteredMemos = filteredMemos,
                uiState = uiState,
                onMemoClick = { memo ->
                    expanded = false
                    focusManager.clearFocus()
                    onMemoClick(memo)
                }
            )
        }
    }
}

@Composable
private fun SearchResultContent(
    query: String,
    filteredMemos: List<Memo>,
    uiState: org.example.memosm.viewmodel.MemosUiState,
    onMemoClick: (Memo) -> Unit
) {
    if (query.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.memo_search_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else if (filteredMemos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.memo_search_no_results),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredMemos, key = { it.name ?: it.content.hashCode() }) { memo ->
                MemoItem(
                    memo = memo,
                    user = uiState.users[memo.creator],
                    currentUser = uiState.user,
                    token = uiState.token,
                    onClick = { onMemoClick(memo) }
                )
            }
        }
    }
}
