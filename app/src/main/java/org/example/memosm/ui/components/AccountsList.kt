import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.example.memosm.R
import org.example.memosm.model.Account
import kotlin.collections.forEach

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DismissBackground(dismissState: SwipeToDismissBoxState) {
//    val color = when (dismissState.dismissDirection) {
//        SwipeToDismissBoxValue.StartToEnd -> Color
//        SwipeToDismissBoxValue.Settled -> Color.Transparent
//    }

    Row(
        modifier = Modifier
            .fillMaxSize()
//            .background(color)
            .padding(12.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            Icons.Outlined.Delete,
            contentDescription = "delete"
        )
        Spacer(modifier = Modifier)
        Icon(
            Icons.Outlined.Delete,
            contentDescription = "Archive"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsList(
    accounts: List<Account>,
    onSwitchAccount: (Account) -> Unit,
    onRemoveAccount: (Account) -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    var accountToRemove by remember { mutableStateOf<Account?>(null) }
    val scope = rememberCoroutineScope()

    if (accountToRemove != null) {
        AlertDialog(
            onDismissRequest = { accountToRemove = null },
            title = { Text(stringResource(R.string.profile_remove_account_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.profile_remove_account_confirm,
                        accountToRemove?.displayName ?: accountToRemove?.name ?: "Unknown"
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    accountToRemove?.let { onRemoveAccount(it) }
                    accountToRemove = null
                }) {
                    Text(
                        stringResource(R.string.common_remove),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToRemove = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            })
    }

    Column(modifier = modifier.padding(16.dp)) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.profile_accounts),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onAddAccount) {
                Icon(Icons.Outlined.Add, contentDescription = null)
            }
        }
        Column {
            accounts.forEachIndexed { index, account ->
                // Define the default shape based on position in the list
                val defaultShape = when {
                    accounts.size == 1 -> RoundedCornerShape(28.dp)
                    index == 0 -> RoundedCornerShape(
                        topStart = 28.dp,
                        topEnd = 28.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 4.dp
                    )
                    index == accounts.size - 1 -> RoundedCornerShape(
                        bottomStart = 28.dp,
                        bottomEnd = 28.dp,
                        topStart = 4.dp,
                        topEnd = 4.dp
                    )
                    else -> RoundedCornerShape(4.dp)
                }
                
                // Pill shape for when swiping
                val pillShape = RoundedCornerShape(28.dp)

                // Initialize state normally
                val dismissState = rememberSwipeToDismissBoxState()
                
                // Track if we've crossed the threshold and should trigger deletion
                var pendingDeletion by remember { mutableStateOf(false) }

                // Monitor for when the dismiss animation completes after crossing threshold
                // currentValue changes to EndToStart when the swipe is complete (user released past threshold)
                LaunchedEffect(dismissState.currentValue) {
                    if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                        // Row has "fallen" off to the left, now show the confirmation
                        pendingDeletion = true
                        accountToRemove = account
                    }
                }
                
                // Reset state when dialog is dismissed without confirming
                LaunchedEffect(accountToRemove) {
                    if (accountToRemove == null && pendingDeletion) {
                        // User cancelled the deletion, reset the swipe state
                        pendingDeletion = false
                        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                    }
                }

                // Has the swipe reached the threshold (middle or more)
                val isThresholdReached by remember {
                    derivedStateOf { dismissState.targetValue == SwipeToDismissBoxValue.EndToStart }
                }
                
                // Determine if there's ACTIVE swiping happening
                // When settled: currentValue == targetValue == Settled, progress == 1.0
                // When swiping: currentValue != targetValue OR progress < 1.0
                val isActivelySwiping by remember {
                    derivedStateOf { 
                        dismissState.currentValue != dismissState.targetValue ||
                        (dismissState.progress < 1f && dismissState.progress > 0f)
                    }
                }
                
                // Animate to pill shape ONLY when actively swiping
                val shouldMorphToPill = isActivelySwiping
                
                // Animate the min corner (4dp -> 28dp) for the edges that need to change
                val animatedMinCorner by animateDpAsState(
                    targetValue = if (shouldMorphToPill) 28.dp else 4.dp,
                    label = "corner_morph"
                )
                
                // Current animated shape for the card
                val currentShape = when {
                    accounts.size == 1 -> pillShape // Already a pill, no animation needed
                    index == 0 -> RoundedCornerShape(
                        topStart = 28.dp,
                        topEnd = 28.dp,
                        bottomStart = animatedMinCorner,
                        bottomEnd = animatedMinCorner
                    )
                    index == accounts.size - 1 -> RoundedCornerShape(
                        topStart = animatedMinCorner,
                        topEnd = animatedMinCorner,
                        bottomStart = 28.dp,
                        bottomEnd = 28.dp
                    )
                    else -> RoundedCornerShape(animatedMinCorner)
                }

                // Background animations
                val backgroundColor by animateColorAsState(
                    if (isThresholdReached) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
                    label = "bg_color"
                )
                val iconColor by animateColorAsState(
                    if (isThresholdReached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                    label = "icon_color"
                )
                val iconScale by animateFloatAsState(
                    if (isThresholdReached) 1.25f else 1.0f, 
                    label = "icon_scale"
                )

                Box(modifier = Modifier.padding(vertical = 1.dp)) {
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(currentShape)
                                    .background(backgroundColor)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                // Only show icon when there is active swipe progress
                                if (dismissState.progress > 0 || isThresholdReached) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.scale(iconScale)
                                    )
                                }
                            }
                        }
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = currentShape,
                            colors = CardDefaults.cardColors(
                                containerColor = if (account.isActive)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            onClick = { if (!account.isActive) onSwitchAccount(account) }
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        "@${account.name}",
                                        fontWeight = if (account.isActive) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                supportingContent = { Text(account.hostUrl) },
                                leadingContent = {
                                    AsyncImage(
                                        model = account.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface),
                                        contentScale = ContentScale.Crop
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}