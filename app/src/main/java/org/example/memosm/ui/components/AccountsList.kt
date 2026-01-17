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
    Column(modifier = modifier.padding(vertical = 16.dp)) {
        // Header Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp), // Increased horizontal padding
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.profile_accounts),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold, // More expressive weight
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onAddAccount) {
                Icon(Icons.Outlined.Add, contentDescription = null)
            }
        }

        // 1. Create the scope at the top of your Composable
        val scope = rememberCoroutineScope()

        accounts.forEach { account ->
            val dismissState = rememberSwipeToDismissBoxState(
                positionalThreshold = { it * 0.4f }
            )

            // 2. Watch the state. When it hits the "dismissed" value, trigger logic
            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                accountToRemove = account

                // 3. Launch a coroutine to snap the item back to the center
                LaunchedEffect(dismissState.currentValue) {
                    scope.launch {
                        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                    }
                }
            }

            // --- Expressive Shape Animation ---
            // When swiping, we make the corners sharper to create a "pushed" effect
            val cornerSize by animateDpAsState(
                targetValue = if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) 12.dp else 28.dp,
                label = "corner_animation"
            )
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerSize)

            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        val color by animateColorAsState(
                            when (dismissState.targetValue) {
                                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }, label = "bg_color"
                        )

                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(shape) // Shape morphs with the card
                                .background(color)
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                ) {
                    // --- The Main Expressive Card ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = shape, // Dynamic morphing shape
                        colors = CardDefaults.cardColors(
                            containerColor = if (account.isActive)
                                MaterialTheme.colorScheme.secondaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh // Subtle "pill" look
                        ),
                        onClick = { if (!account.isActive) onSwitchAccount(account) }
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "@" + (account.name ?: "Unknown"),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (account.isActive) FontWeight.Bold else FontWeight.Medium
                                    )
                                )
                            },
                            supportingContent = {
                                Text(
                                    account.hostUrl,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                Box(modifier = Modifier.padding(4.dp)) {
                                    AsyncImage(
                                        model = account.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(48.dp) // Slightly larger avatar
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceDim),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (account.isActive) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .align(Alignment.BottomEnd)
                                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                                .border(2.dp, MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}