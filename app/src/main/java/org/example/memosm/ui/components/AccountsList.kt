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
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
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
                val shape = when {
                    accounts.size == 1 -> RoundedCornerShape(28.dp)
                    index == 0 -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                    index == accounts.size - 1 -> RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp, topStart = 4.dp, topEnd = 4.dp)
                    else -> RoundedCornerShape(4.dp)
                }

                // 1. Initialize state normally
                val dismissState = rememberSwipeToDismissBoxState()

                // 2. TRIGGER LOGIC: Monitor when the state has settled on "Dismissed"
                // This ensures the user has released the swipe and the animation has finished.
                LaunchedEffect(dismissState.currentValue) {
                    if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                        accountToRemove = account
                        // Snap back immediately so the UI is ready for the next action
                        // once the dialog is dismissed.
                        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                    }
                }

                Box(modifier = Modifier.padding(vertical = 1.dp)) {
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            // 3. Re-implementing the Visual Feedback (The "Red" phase)
                            // We use targetValue here because it reacts while the user is still dragging
                            val isReached = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart

                            val backgroundColor by animateColorAsState(
                                if (isReached) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
                                label = "bg_color"
                            )
                            val iconColor by animateColorAsState(
                                if (isReached) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.outline,
                                label = "icon_color"
                            )
                            val scale by animateFloatAsState(
                                if (isReached) 1.25f else 1.0f, label = "icon_scale"
                            )

                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(shape)
                                    .background(backgroundColor)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                // Only show icon when there is active swipe progress
                                if (dismissState.progress > 0 || isReached) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.scale(scale)
                                    )
                                }
                            }
                        }
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = shape,
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
                                    Text("@${account.name}", fontWeight = if (account.isActive) FontWeight.Bold else FontWeight.Normal)
                                },
                                supportingContent = { Text(account.hostUrl) },
                                leadingContent = {
                                    AsyncImage(
                                        model = account.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
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