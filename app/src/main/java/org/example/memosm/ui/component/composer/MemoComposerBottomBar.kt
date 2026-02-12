
package org.example.memosm.ui.component.composer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDropUp
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import org.example.memosm.R
import org.example.memosm.model.Attachment
import org.example.memosm.model.Location
import org.example.memosm.model.Visibility
import org.example.memosm.ui.VisibilityIcon
import org.example.memosm.ui.component.item.AttachmentCard
import org.example.memosm.ui.component.item.AttachmentCompactMode
import org.example.memosm.ui.getVisibilityLabel

@Composable
fun MemoComposerBottomBar(
    modifier: Modifier = Modifier,
    draftAttachments: List<Pair<Uri, Attachment?>>,
    uploadingUris: Set<Uri>,
    location: Location?,
    isPosting: Boolean,
    isFetchingLocation: Boolean,
    isRecording: Boolean,
    visibility: Visibility,
    mode: ComposerMode,
    componentWidth: Dp,
    pickerLauncher: ManagedActivityResultLauncher<String, List<Uri>>,
    audioPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    locationPermissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    token: String,
    hostUrl: String,
    contentStateText: String,
    isUploadingCount: Int,
    onFetchLocation: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onRemoveLocation: () -> Unit,
    onLocationClick: () -> Unit,
    onVisibilityChange: (Visibility) -> Unit,
    onPublishClick: () -> Unit,
    onRemoveAttachment: (Uri, Attachment?) -> Unit,
) {
    val context = LocalContext.current
    var showActionOverflowMenu by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val showVisibilityLabel = componentWidth > 480.dp || componentWidth == 0.dp
    val showPublishLabel = componentWidth > 410.dp || componentWidth == 0.dp
    val isCompact = componentWidth < 380.dp && componentWidth != 0.dp

    val actionIconSize = if (isCompact) 20.dp else 24.dp
    val actionButtonSize = if (isCompact) 36.dp else 48.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom // Align bottom to keep inputs aligned? Or center? usually bottom for chat bars.
    ) {
        // "Everything else" Card
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Attachments List
                if (draftAttachments.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        contentPadding = PaddingValues(top = 8.dp, end = 8.dp, bottom = 8.dp)
                    ) {
                        items(draftAttachments, key = { (uri, attachment) ->
                            if (uri != Uri.EMPTY) uri.toString()
                            else "${attachment?.name ?: "unknown"}_${attachment?.filename ?: "unknown"}_${attachment?.createTime ?: System.currentTimeMillis()}"
                        }) { (uri, attachment) ->
                            val isUploading = uri in uploadingUris

                            Box(modifier = Modifier.size(80.dp, 80.dp)) {
                                AttachmentCard(
                                    attachment = attachment,
                                    token = token,
                                    hostUrl = hostUrl,
                                    uri = uri,
                                    modifier = Modifier.fillMaxSize(),
                                    showInfo = false,
                                    compactMode = AttachmentCompactMode.Always
                                )

                                if (isUploading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Color.Black.copy(alpha = 0.3f),
                                                RoundedCornerShape(8.dp)
                                            ), contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp), color = Color.White
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onRemoveAttachment(uri, attachment) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .size(24.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .zIndex(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.memo_composer_remove_attachment),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Location Chip
                location?.let { loc ->
                    Spacer(modifier = Modifier.height(8.dp))
                    InputChip(
                        selected = true,
                        onClick = onLocationClick,
                        label = {
                            Text(
                                text = loc.placeholder ?: "${loc.latitude}, ${loc.longitude}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.memo_composer_remove_location),
                                modifier = Modifier
                                    .size(18.dp)
                                    .noRippleClickable { onRemoveLocation() })
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Place,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        })
                }
                
                if (draftAttachments.isNotEmpty() || location != null) {
                     Spacer(modifier = Modifier.height(12.dp))
                }

                // Actions Row
                // Determine if we should use overflow menu based on width
                val useOverflowMenu = componentWidth < 320.dp && componentWidth != 0.dp

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (useOverflowMenu) {
                             // Show overflow menu button
                            Box {
                                IconButton(
                                    onClick = { showActionOverflowMenu = true },
                                    enabled = !isPosting,
                                    modifier = Modifier.size(actionButtonSize)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreVert,
                                        contentDescription = stringResource(R.string.memo_action_more),
                                        modifier = Modifier.size(actionIconSize)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showActionOverflowMenu,
                                    onDismissRequest = { showActionOverflowMenu = false }) {
                                    DropdownMenuItem(
                                        text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Outlined.AttachFile,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(stringResource(R.string.memo_composer_attach_file))
                                        }
                                    }, onClick = {
                                        showActionOverflowMenu = false
                                        pickerLauncher.launch("*/*")
                                    }, enabled = !isPosting
                                    )
                                    DropdownMenuItem(
                                        text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Outlined.Image,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(stringResource(R.string.memo_composer_add_image))
                                        }
                                    }, onClick = {
                                        showActionOverflowMenu = false
                                        pickerLauncher.launch("image/*")
                                    }, enabled = !isPosting
                                    )
                                    DropdownMenuItem(
                                        text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isRecording) Icons.Default.Mic else Icons.Outlined.MicNone,
                                                contentDescription = null,
                                                tint = if (isRecording) MaterialTheme.colorScheme.error else LocalContentColor.current,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                if (isRecording) "Stop Recording" else "Record Audio"
                                            )
                                        }
                                    }, onClick = {
                                        showActionOverflowMenu = false
                                        if (isRecording) {
                                            onStopRecording()
                                        } else {
                                            val permission = Manifest.permission.RECORD_AUDIO
                                            if (ContextCompat.checkSelfPermission(
                                                    context, permission
                                                ) == PackageManager.PERMISSION_GRANTED
                                            ) {
                                                onStartRecording()
                                            } else {
                                                audioPermissionLauncher.launch(permission)
                                            }
                                        }
                                    }, enabled = !isPosting
                                    )
                                    DropdownMenuItem(
                                        text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isFetchingLocation) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = if (location != null) Icons.Default.Place else Icons.Outlined.Place,
                                                    contentDescription = null,
                                                    tint = if (location != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(stringResource(R.string.memo_composer_add_location))
                                        }
                                    }, onClick = {
                                        showActionOverflowMenu = false
                                        val hasCoarse = ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.ACCESS_COARSE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                        val hasFine = ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED

                                        if (hasCoarse || hasFine) {
                                            onFetchLocation()
                                        } else {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                    }, enabled = !isPosting && !isFetchingLocation
                                    )
                                }
                            }
                        } else {
                            // Show individual icon buttons
                            IconButton(
                                onClick = { pickerLauncher.launch("*/*") },
                                enabled = !isPosting,
                                modifier = Modifier.size(actionButtonSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AttachFile,
                                    contentDescription = stringResource(R.string.memo_composer_attach_file),
                                    modifier = Modifier.size(actionIconSize)
                                )
                            }
                            IconButton(
                                onClick = { pickerLauncher.launch("image/*") },
                                enabled = !isPosting,
                                modifier = Modifier.size(actionButtonSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = stringResource(R.string.memo_composer_add_image),
                                    modifier = Modifier.size(actionIconSize)
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (isRecording) {
                                        onStopRecording()
                                    } else {
                                        val permission = Manifest.permission.RECORD_AUDIO
                                        if (ContextCompat.checkSelfPermission(
                                                context, permission
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            onStartRecording()
                                        } else {
                                            audioPermissionLauncher.launch(permission)
                                        }
                                    }
                                }, enabled = !isPosting, modifier = Modifier.size(actionButtonSize)
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Mic else Icons.Outlined.MicNone,
                                    contentDescription = stringResource(R.string.memo_composer_error_microphone_permission).removeSuffix(
                                        " required"
                                    ), // Best effort if no specific string
                                    tint = if (isRecording) MaterialTheme.colorScheme.error else LocalContentColor.current,
                                    modifier = Modifier.size(actionIconSize)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val hasCoarse = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.ACCESS_COARSE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    val hasFine = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasCoarse || hasFine) {
                                        onFetchLocation()
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                },
                                enabled = !isPosting && !isFetchingLocation,
                                modifier = Modifier.size(actionButtonSize)
                            ) {
                                if (isFetchingLocation) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(actionIconSize), strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (location != null) Icons.Default.Place else Icons.Outlined.Place,
                                        contentDescription = stringResource(R.string.memo_composer_add_location),
                                        tint = if (location != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                        modifier = Modifier.size(actionIconSize)
                                    )
                                }
                            }
                        }
                    } // End Left Actions Row

                    // Visibility Dropdown
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            TextButton(
                                onClick = { expanded = true },
                                enabled = !isPosting,
                                contentPadding = if (isCompact) PaddingValues(horizontal = 8.dp) else ButtonDefaults.TextButtonContentPadding,
                                modifier = if (isCompact) Modifier.height(actionButtonSize) else Modifier
                            ) {
                                VisibilityIcon(
                                    visibility = visibility, modifier = Modifier.size(18.dp)
                                )
                                if (showVisibilityLabel) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(getVisibilityLabel(visibility))
                                    Icon(
                                        imageVector = if (expanded) Icons.Outlined.ArrowDropUp else Icons.Outlined.ArrowDropDown,
                                        contentDescription = null
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = expanded, onDismissRequest = { expanded = false }) {
                                Visibility.entries.forEach { option ->
                                    DropdownMenuItem(text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            VisibilityIcon(
                                                visibility = option,
                                                modifier = Modifier.size(18.dp),
                                                outlined = true
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = getVisibilityLabel(option))
                                        }
                                    }, onClick = {
                                        onVisibilityChange(option)
                                        expanded = false
                                    })
                                }
                            }
                        }
                    }
                }
            } // End Column in Tools Card
        } // End Tools Card

        // Publish Button Card
        Card(
            shape = RoundedCornerShape(24.dp), // Match the tools card radius or make it circle? "publish button will be on a different card".
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer // Distinct color?
            ),
             elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
             val label = when (mode) {
                ComposerMode.PUBLISH -> stringResource(R.string.memo_publish)
                ComposerMode.UPDATE -> stringResource(R.string.memo_action_update)
                ComposerMode.COMMENT -> stringResource(R.string.memo_action_post)
            }

            // Using TextButton or Button inside the Card?
            // Actually, if the Card is the container, I can just put the content (Icon + Text) inside a clickable layout or use a Button with transparent background.
            // Or just put the Button itself here without a card wrapper if the Button styles match.
            // But Button has its own elevation/shape. The user asked for a "different card".
            // If I put a Button inside a Card, I get double elevation/background.
            // I'll make the Card clickable or use a filled Button that *looks* like a card.
            // But "Publish button ON a different card" implies Card > Button.
            // I will use a Box inside the Card that is clickable.
            
            // Wait, existing code uses a Button. Button IS a Surface/Card conceptually.
            // If I just place the Button outside the main card, it is "on a different card" (its own surface).
            // But maybe the user wants a visual "card" container.
            // I'll stick to a Button but maybe style it to look "more round"? Default material 3 button is fully rounded (StadiumShape).
            // "Publish button will be on a different card" might imply a container for the button?
            // "everything else will be in a more round card" implies the tools are grouped.
            // So:
            // [ (Tools) ]  [ (Publish) ]
            // Tools in Card. Publish in... Card? Or just Button?
            // If checking the image description "like this", it usually means a small floating toolbar.
            // I'll put the Button in a Box or just use the Button directly.
            // BUT, `Button` has `shape`.
            // I'll use `Button` directly but ensure it has the same height/alignment.
            
            Button(
                onClick = onPublishClick,
                enabled = (contentStateText.isNotBlank() || draftAttachments.isNotEmpty()) && !isPosting && isUploadingCount == 0,
                contentPadding = if (showPublishLabel) ButtonDefaults.ContentPadding else PaddingValues(
                    horizontal = if (isCompact) 8.dp else 12.dp
                ),
                shape = RoundedCornerShape(24.dp), // "More round"
                modifier = if (isCompact) Modifier.height(actionButtonSize) else Modifier
            ) {
                if (isPosting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(actionIconSize),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.memo_publish),
                        modifier = Modifier.size(actionIconSize)
                    )
                }
                if (showPublishLabel) {
                    Spacer(modifier = Modifier.width(if (isCompact) 4.dp else 8.dp))
                    Text(label)
                }
            }
        }
    }
}
