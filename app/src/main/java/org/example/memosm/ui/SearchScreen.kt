package org.example.memosm.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.example.memosm.R
import org.example.memosm.model.Memo
import org.example.memosm.viewmodel.MemosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoSearchBar(
    viewModel: MemosViewModel,
    isExplore: Boolean = false,
    onMemoClick: (Memo) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.memo_search_placeholder)
) {
    val uiState by viewModel.uiState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    
    // Maintain a set of selected tags for AND filtering within the search context
    var searchSelectedTags by rememberSaveable { mutableStateOf(setOf<String>()) }

    // Fetch search-specific memos when expanded
    LaunchedEffect(expanded) {
        if (expanded) {
            viewModel.prepareSearch(isExplore)
        }
    }

    // Aggregate tags from the search pool
    val availableTags = remember(uiState.searchMemos) {
        val tags = mutableMapOf<String, Int>()
        uiState.searchMemos.forEach { memo ->
            val regex = "#(\\w+)".toRegex()
            regex.findAll(memo.content).forEach { match ->
                val tag = match.groupValues[1]
                tags[tag] = (tags[tag] ?: 0) + 1
            }
        }
        tags.toList().sortedByDescending { it.second }.toMap()
    }

    val filteredMemos = remember(query, searchSelectedTags, uiState.searchMemos) {
        uiState.searchMemos.filter { memo ->
            val matchesQuery = if (query.isBlank()) true else memo.content.contains(query, ignoreCase = true)
            val matchesTags = if (searchSelectedTags.isEmpty()) true else {
                searchSelectedTags.all { tag -> memo.content.contains("#$tag", ignoreCase = true) }
            }
            matchesQuery && matchesTags
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
                        if (query.isNotEmpty() || searchSelectedTags.isNotEmpty()) {
                            IconButton(onClick = { 
                                query = "" 
                                searchSelectedTags = emptySet()
                            }) {
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
                selectedTags = searchSelectedTags,
                availableTags = availableTags,
                filteredMemos = filteredMemos,
                uiState = uiState,
                onTagClick = { tag ->
                    searchSelectedTags = if (tag in searchSelectedTags) {
                        searchSelectedTags - tag
                    } else {
                        searchSelectedTags + tag
                    }
                },
                onMemoClick = { memo ->
                    expanded = false
                    focusManager.clearFocus()
                    onMemoClick(memo)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchResultContent(
    query: String,
    selectedTags: Set<String>,
    availableTags: Map<String, Int>,
    filteredMemos: List<Memo>,
    uiState: org.example.memosm.viewmodel.MemosUiState,
    onTagClick: (String) -> Unit,
    onMemoClick: (Memo) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (uiState.isSearching && uiState.searchMemos.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        // Tag Cloud Section
        if (availableTags.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.profile_stats_tags),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().animateContentSize(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableTags.forEach { (tag, count) ->
                                val isSelected = tag in selectedTags
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onTagClick(tag) },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("#$tag")
                                            if (count > 0) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = count.toString(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    },
                                    trailingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (filteredMemos.isEmpty() && !uiState.isSearching) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (query.isBlank() && selectedTags.isEmpty()) 
                            stringResource(R.string.memo_search_hint) 
                        else stringResource(R.string.memo_search_no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredMemos, key = { it.name ?: it.content.hashCode() }) { memo ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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
}
