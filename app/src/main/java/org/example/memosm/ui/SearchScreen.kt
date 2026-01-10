package org.example.memosm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.example.memosm.R
import org.example.memosm.model.Memo
import org.example.memosm.viewmodel.MemosViewModel
import java.text.SimpleDateFormat
import java.util.*

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
    var startDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var endDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }

    // Aggregate tags from the search pool to be context-accurate
    val availableTags = remember(uiState.searchMemos, uiState.userStats, isExplore) {
        if (isExplore) {
            val tags = mutableMapOf<String, Int>()
            uiState.searchMemos.forEach { memo ->
                val regex = "#(\\w+)".toRegex()
                regex.findAll(memo.content).forEach { match ->
                    val tag = match.groupValues[1]
                    tags[tag] = (tags[tag] ?: 0) + 1
                }
            }
            tags.toList().sortedByDescending { it.second }.toMap()
        } else {
            uiState.userStats?.tagCount ?: emptyMap()
        }
    }

    // Effect to trigger server-side search whenever filters change
    LaunchedEffect(query, searchSelectedTags, startDateMillis, endDateMillis, expanded) {
        if (expanded) {
            val filters = mutableListOf<String>()

            if (query.isNotBlank()) {
                filters.add("content.contains(\"$query\")")
            }

            searchSelectedTags.forEach { tag ->
                filters.add("tag in [\"$tag\"]")
            }

            if (startDateMillis != null) {
                filters.add("created_ts >= ${startDateMillis!! / 1000}")
            }

            if (endDateMillis != null) {
                // End date inclusive: add one day minus one second
                filters.add("created_ts < ${(endDateMillis!! + 86400000L) / 1000}")
            }

            val filterString = if (filters.isEmpty()) null else filters.joinToString(" && ")
            viewModel.prepareSearch(isExplore, filterString)
        }
    }

    Box(modifier = modifier
        .fillMaxWidth()
        .semantics { isTraversalGroup = true }
        .zIndex(1f)) {
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
                        if (query.isNotEmpty() || searchSelectedTags.isNotEmpty() || startDateMillis != null || endDateMillis != null) {
                            IconButton(onClick = {
                                query = ""
                                searchSelectedTags = emptySet()
                                startDateMillis = null
                                endDateMillis = null
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
                startDateMillis = startDateMillis,
                endDateMillis = endDateMillis,
                availableTags = availableTags,
                filteredMemos = uiState.searchMemos,
                uiState = uiState,
                viewModel = viewModel,
                onTagClick = { tag ->
                    searchSelectedTags = if (tag in searchSelectedTags) {
                        searchSelectedTags - tag
                    } else {
                        searchSelectedTags + tag
                    }
                },
                onStartDateSelected = { startDateMillis = it },
                onEndDateSelected = { endDateMillis = it },
                onMemoClick = { memo ->
                    // Expanded state is preserved, we'll show details in a dialog
                    // We call onMemoClick just in case parent needs to know, but we handle showing detail locally
                    onMemoClick(memo)
                })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SearchResultContent(
    query: String,
    selectedTags: Set<String>,
    startDateMillis: Long?,
    endDateMillis: Long?,
    availableTags: Map<String, Int>,
    filteredMemos: List<Memo>,
    uiState: org.example.memosm.viewmodel.MemosUiState,
    viewModel: MemosViewModel,
    onTagClick: (String) -> Unit,
    onStartDateSelected: (Long?) -> Unit,
    onEndDateSelected: (Long?) -> Unit,
    onMemoClick: (Memo) -> Unit
) {
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var detailMemo by remember { mutableStateOf<Memo?>(null) }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
        DatePickerDialog(onDismissRequest = { showStartDatePicker = false }, confirmButton = {
            TextButton(onClick = {
                onStartDateSelected(datePickerState.selectedDateMillis)
                showStartDatePicker = false
            }) {
                Text(stringResource(android.R.string.ok))
            }
        }, dismissButton = {
            TextButton(onClick = {
                onStartDateSelected(null)
                showStartDatePicker = false
            }) {
                Text(stringResource(R.string.common_cancel))
            }
        }) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDateMillis)
        DatePickerDialog(onDismissRequest = { showEndDatePicker = false }, confirmButton = {
            TextButton(onClick = {
                onEndDateSelected(datePickerState.selectedDateMillis)
                showEndDatePicker = false
            }) {
                Text(stringResource(android.R.string.ok))
            }
        }, dismissButton = {
            TextButton(onClick = {
                onEndDateSelected(null)
                showEndDatePicker = false
            }) {
                Text(stringResource(R.string.common_cancel))
            }
        }) {
            DatePicker(state = datePickerState)
        }
    }

    detailMemo?.let { memo ->
        // Use a full-screen dialog to show memo details without closing search
        Dialog(
            onDismissRequest = { detailMemo = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            // Need to fetch comments for the selected memo
            LaunchedEffect(memo) {
                viewModel.selectMemo(memo)
            }

            val detailUiState by viewModel.uiState.collectAsState()

            MemoDetailPane(
                memo = memo,
                comments = detailUiState.selectedMemoComments,
                isLoadingComments = detailUiState.isLoadingComments,
                token = detailUiState.token,
                showBackButton = true,
                onBack = {
                    viewModel.clearSelectedMemo()
                    detailMemo = null
                },
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Date Selector Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DateSelectorCard(
                    label = "Start",
                    dateMillis = startDateMillis,
                    onClick = { showStartDatePicker = true },
                    onClear = { onStartDateSelected(null) },
                    modifier = Modifier.weight(1f)
                )
                DateSelectorCard(
                    label = "End",
                    dateMillis = endDateMillis,
                    onClick = { showEndDatePicker = true },
                    onClear = { onEndDateSelected(null) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Tag Cloud Section
        if (availableTags.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 4.dp),
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
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides 0.dp
                        ) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                availableTags.forEach { (tag, count) ->
                                    val isSelected = tag in selectedTags
                                    FilterChip(
                                        modifier = Modifier.height(28.dp),
                                        selected = isSelected,
                                        onClick = { onTagClick(tag) },
                                        label = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "#$tag",
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                                if (count > 0) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = count.toString(),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                                            alpha = 0.7f
                                                        )
                                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.7f
                                                        )
                                                    )
                                                }
                                            }
                                        },
                                        trailingIcon = {
                                            AnimatedVisibility(
                                                visible = isSelected,
                                                enter = fadeIn() + expandHorizontally(),
                                                exit = fadeOut() + shrinkHorizontally()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = MaterialTheme.colorScheme.outline.copy(
                                                alpha = 0.5f
                                            ),
                                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(
                                                alpha = 0.5f
                                            ),
                                            borderWidth = 0.5.dp,
                                            selectedBorderWidth = 0.5.dp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (uiState.isSearching) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
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
                        text = if (query.isBlank() && selectedTags.isEmpty() && startDateMillis == null && endDateMillis == null) stringResource(
                            R.string.memo_search_hint
                        )
                        else stringResource(R.string.memo_search_no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredMemos, key = { it.name ?: it.content.hashCode() }) { memo ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                    MemoItem(
                        memo = memo,
                        user = uiState.users[memo.creator],
                        currentUser = uiState.user,
                        token = uiState.token,
                        onClick = {
                            detailMemo = memo
                            onMemoClick(memo)
                        })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectorCard(
    label: String,
    dateMillis: Long?,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier, colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ), shape = RoundedCornerShape(12.dp), onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                val dateText = remember(dateMillis) {
                    if (dateMillis != null) {
                        SimpleDateFormat(
                            "MMM dd, yyyy", Locale.getDefault()
                        ).format(Date(dateMillis))
                    } else {
                        "Any"
                    }
                }
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            if (dateMillis != null) {
                IconButton(
                    onClick = onClear, modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
