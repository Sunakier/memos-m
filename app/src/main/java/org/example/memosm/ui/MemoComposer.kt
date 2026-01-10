package org.example.memosm.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import org.example.memosm.R
import org.example.memosm.model.Attachment
import org.example.memosm.model.Location

@Composable
fun getVisibilityLabel(visibility: String): String {
    return when (visibility.uppercase()) {
        "PUBLIC" -> stringResource(R.string.memo_visibility_public)
        "PROTECTED" -> stringResource(R.string.memo_visibility_protected)
        "PRIVATE" -> stringResource(R.string.memo_visibility_private)
        else -> visibility
    }
}

@Composable
fun MemoComposer(
    onPublish: (String, String, List<Attachment>, Location?) -> Unit,
    onUploadFile: suspend (Uri, Context) -> Attachment?,
    availableTags: Set<String>,
    token: String,
    modifier: Modifier = Modifier,
    isPosting: Boolean = false,
    initialContent: String = "",
    initialVisibility: String = "PRIVATE",
    initialAttachments: List<Attachment> = emptyList(),
    initialLocation: Location? = null,
    placeholder: String = stringResource(R.string.memo_composer_placeholder),
    autoFocus: Boolean = false,
    onCancel: (() -> Unit)? = null,
    onDraftChanged: ((String, String, List<Attachment>, Location?) -> Unit)? = null,
    submitLabel: String? = null
) {
    val contentState = rememberTextFieldState(initialContent)
    var visibility by remember(initialVisibility) { mutableStateOf(initialVisibility) }
    var expanded by remember { mutableStateOf(false) }

    val draftAttachmentsState = remember(initialAttachments) {
        val initial: List<Pair<Uri, Attachment?>> =
            initialAttachments.map { Uri.EMPTY to (it as Attachment?) }
        mutableStateOf(initial)
    }
    var draftAttachments by draftAttachmentsState
    var location by remember(initialLocation) { mutableStateOf(initialLocation) }
    var showLocationEditDialog by remember { mutableStateOf(false) }

    var isUploadingCount by remember { mutableIntStateOf(0) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                draftAttachments = draftAttachments + (uri to null)
                isUploadingCount++
                scope.launch {
                    val attachment = onUploadFile(uri, context)
                    if (attachment != null) {
                        val updated = draftAttachments.map {
                            if (it.first == uri) uri to attachment else it
                        }
                        draftAttachments = updated
                        onDraftChanged?.invoke(
                            contentState.text.toString(),
                            visibility,
                            updated.mapNotNull { it.second },
                            location)
                    } else {
                        val updated = draftAttachments.filter { it.first != uri }
                        draftAttachments = updated
                        onDraftChanged?.invoke(
                            contentState.text.toString(),
                            visibility,
                            updated.mapNotNull { it.second },
                            location)
                    }
                    isUploadingCount--
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchLocation() {
        isFetchingLocation = true
        val cancellationTokenSource = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { androidLoc ->
            if (androidLoc != null) {
                location = Location(
                    latitude = androidLoc.latitude,
                    longitude = androidLoc.longitude,
                    placeholder = "Current Location"
                )
            }
            isFetchingLocation = false
        }.addOnFailureListener {
            isFetchingLocation = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            fetchLocation()
        }
    }

    LaunchedEffect(contentState.text) {
        onDraftChanged?.invoke(
            contentState.text.toString(), visibility, draftAttachments.mapNotNull { it.second }, location)
    }

    LaunchedEffect(visibility) {
        onDraftChanged?.invoke(
            contentState.text.toString(), visibility, draftAttachments.mapNotNull { it.second }, location)
    }

    LaunchedEffect(location) {
        onDraftChanged?.invoke(
            contentState.text.toString(), visibility, draftAttachments.mapNotNull { it.second }, location)
    }

    var componentWidth by remember { mutableStateOf(0.dp) }

    Column(
        modifier = modifier.onSizeChanged {
            componentWidth = with(density) { it.width.toDp() }
        }) {
        // Breakpoints for hiding text based on measured width.
        // We use componentWidth == 0.dp to default to true before first measurement.
        val showVisibilityLabel = componentWidth > 440.dp || componentWidth == 0.dp
        val showPublishLabel = componentWidth > 300.dp || componentWidth == 0.dp

        MemoInput(
            contentState = contentState,
            placeholder = placeholder,
            availableTags = availableTags,
            enabled = !isPosting,
            autoFocus = autoFocus,
            minHeightInLines = if (autoFocus) 5 else 3
        )

        if (draftAttachments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(top = 8.dp, end = 8.dp)
            ) {
                items(draftAttachments) { (uri, attachment) ->
                    val isImage = remember(uri, attachment) {
                        if (uri != Uri.EMPTY) {
                            context.contentResolver.getType(uri)?.startsWith("image/") == true
                        } else {
                            attachment?.displayType?.startsWith(
                                "image/", ignoreCase = true
                            ) == true || attachment?.displayType?.contains(
                                "image", ignoreCase = true
                            ) == true
                        }
                    }

                    Box(modifier = Modifier.size(80.dp)) {
                        if (isImage) {
                            val model = if (uri != Uri.EMPTY) uri else attachment?.externalLink
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(model)
                                    .addHeader("Authorization", "Bearer $token").crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(4.dp), contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                                    contentDescription = null
                                )
                            }
                        }

                        if (attachment == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp)
                                    ), contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp), color = Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val updated =
                                    draftAttachments.filter { it.second != attachment || (it.first != uri && uri != Uri.EMPTY) }
                                draftAttachments = updated
                                onDraftChanged?.invoke(
                                    contentState.text.toString(),
                                    visibility,
                                    updated.mapNotNull { it.second },
                                    location)
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .zIndex(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.memo_composer_remove_attachment),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        location?.let { loc ->
            Spacer(modifier = Modifier.height(8.dp))
            InputChip(
                selected = true,
                onClick = { showLocationEditDialog = true },
                label = {
                    Text(
                        text = loc.placeholder ?: "${loc.latitude}, ${loc.longitude}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.memo_composer_remove_location),
                        modifier = Modifier
                            .size(18.dp)
                            .noRippleClickable { location = null }
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { pickerLauncher.launch("*/*") }, enabled = !isPosting
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AttachFile, contentDescription = stringResource(R.string.memo_composer_attach_file)
                    )
                }
                IconButton(
                    onClick = { pickerLauncher.launch("image/*") }, enabled = !isPosting
                ) {
                    Icon(imageVector = Icons.Outlined.Image, contentDescription = stringResource(R.string.memo_composer_add_image))
                }
                IconButton(
                    onClick = {
                        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        
                        if (hasCoarse || hasFine) {
                            fetchLocation()
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        }
                    },
                    enabled = !isPosting && !isFetchingLocation
                ) {
                    if (isFetchingLocation) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = if (location != null) Icons.Default.Place else Icons.Outlined.Place,
                            contentDescription = stringResource(R.string.memo_composer_add_location),
                            tint = if (location != null) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onCancel != null) {
                    TextButton(onClick = onCancel, enabled = !isPosting) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Spacer(modifier = Modifier.width(if (showPublishLabel) 8.dp else 4.dp))
                }

                Box {
                    TextButton(onClick = { expanded = true }, enabled = !isPosting) {
                        Icon(
                            imageVector = getVisibilityIcon(visibility),
                            contentDescription = visibility,
                            modifier = Modifier.size(18.dp)
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
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("PRIVATE", "PROTECTED", "PUBLIC").forEach { option ->
                            DropdownMenuItem(text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = getVisibilityIcon(option),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(getVisibilityLabel(option))
                                }
                            }, onClick = {
                                visibility = option
                                expanded = false
                            })
                        }
                    }
                }

                Button(
                    onClick = {
                        val finalAttachments = draftAttachments.mapNotNull { it.second }
                        onPublish(contentState.text.toString(), visibility, finalAttachments, location)
                    },
                    enabled = (contentState.text.isNotBlank() || draftAttachments.isNotEmpty()) && !isPosting && isUploadingCount == 0,
                    contentPadding = if (showPublishLabel) ButtonDefaults.ContentPadding else PaddingValues(
                        horizontal = 12.dp
                    )
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.memo_publish)
                        )
                        if (showPublishLabel) {
                            Spacer(modifier = Modifier.width(8.dp))
                            val label = submitLabel ?: run {
                                val isEdit =
                                    initialContent.isNotEmpty() || initialAttachments.isNotEmpty()
                                if (isEdit) stringResource(R.string.memo_action_update) else if (autoFocus) stringResource(R.string.memo_action_post) else stringResource(R.string.memo_publish)
                            }
                            Text(label)
                        }
                    }
                }
            }
        }
    }

    if (showLocationEditDialog && location != null) {
        var tempPlaceholder by remember { mutableStateOf(location?.placeholder ?: "") }
        var tempLatitude by remember { mutableStateOf(location?.latitude?.toString() ?: "") }
        var tempLongitude by remember { mutableStateOf(location?.longitude?.toString() ?: "") }

        AlertDialog(
            onDismissRequest = { showLocationEditDialog = false },
            title = { Text(stringResource(R.string.memo_composer_edit_location)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tempPlaceholder,
                        onValueChange = { tempPlaceholder = it },
                        label = { Text(stringResource(R.string.memo_composer_location_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempLatitude,
                        onValueChange = { tempLatitude = it },
                        label = { Text(stringResource(R.string.memo_composer_location_latitude)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempLongitude,
                        onValueChange = { tempLongitude = it },
                        label = { Text(stringResource(R.string.memo_composer_location_longitude)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    location = location?.copy(
                        placeholder = tempPlaceholder,
                        latitude = tempLatitude.toDoubleOrNull(),
                        longitude = tempLongitude.toDoubleOrNull()
                    )
                    showLocationEditDialog = false
                }) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationEditDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
