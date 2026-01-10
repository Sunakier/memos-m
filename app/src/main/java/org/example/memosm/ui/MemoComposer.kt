package org.example.memosm.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.example.memosm.R
import org.example.memosm.model.Attachment
import org.example.memosm.model.Location
import java.io.File

@Composable
fun getVisibilityLabel(visibility: String): String {
    return when (visibility.uppercase()) {
        "PUBLIC" -> stringResource(R.string.memo_visibility_public)
        "PROTECTED" -> stringResource(R.string.memo_visibility_protected)
        "PRIVATE" -> stringResource(R.string.memo_visibility_private)
        else -> visibility
    }
}

fun getVisibilityIcon(visibility: String, outlined: Boolean = false): ImageVector {
    return when (visibility.uppercase()) {
        "PUBLIC" -> if (outlined) Icons.Outlined.Public else Icons.Default.Public
        "PROTECTED" -> if (outlined) Icons.Outlined.Group else Icons.Default.Group
        "PRIVATE" -> if (outlined) Icons.Outlined.Lock else Icons.Default.Lock
        else -> if (outlined) Icons.Outlined.Lock else Icons.Default.Lock
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
    onDraftChanged: ((String, String, List<Attachment>, Location?) -> Unit)? = null,
    submitLabel: String? = null,
    resetToken: Any? = null
) {
    val context = LocalContext.current

    // We use resetToken to reset the internal state when necessary (e.g. after post or when changing which memo to edit)
    val contentState = remember(resetToken) { TextFieldState(initialContent) }
    var visibility by remember(resetToken) { mutableStateOf(initialVisibility) }
    var location by remember(resetToken) { mutableStateOf(initialLocation) }

    val draftAttachmentsState = remember(resetToken) {
        val initial: List<Pair<Uri, Attachment?>> =
            initialAttachments.map { Uri.EMPTY to (it as Attachment?) }
        mutableStateOf(initial)
    }
    var draftAttachments by draftAttachmentsState

    var expanded by remember { mutableStateOf(false) }
    var showLocationEditDialog by remember { mutableStateOf(false) }

    var isUploadingCount by remember { mutableIntStateOf(0) }
    var uploadingUris by remember { mutableStateOf(setOf<Uri>()) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Audio recording state
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var currentRecordFile by remember { mutableStateOf<File?>(null) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                draftAttachments = draftAttachments + (uri to null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchLocation() {
        isFetchingLocation = true
        val cancellationTokenSource = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationTokenSource.token
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
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchLocation()
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false

            currentRecordFile?.let { file ->
                val uri = file.toUri()
                draftAttachments = draftAttachments + (uri to null)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to stop recording", Toast.LENGTH_SHORT).show()
        }
    }

    fun startRecording() {
        try {
            val file = File(context.cacheDir, "record_${System.currentTimeMillis()}.aac")
            currentRecordFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION") MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to start recording: ${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecording()
        } else {
            Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) {
                try {
                    mediaRecorder?.stop()
                    mediaRecorder?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    // TRIGGER CACHE UPDATE when local changes occur
    LaunchedEffect(contentState.text, visibility, draftAttachments, location) {
        onDraftChanged?.invoke(
            contentState.text.toString(),
            visibility,
            draftAttachments.mapNotNull { it.second },
            location
        )
    }

    var componentWidth by remember { mutableStateOf(0.dp) }

    Column(
        modifier = modifier.onSizeChanged {
            componentWidth = with(density) { it.width.toDp() }
        }) {
        val showVisibilityLabel = componentWidth > 440.dp || componentWidth == 0.dp
        val showPublishLabel = componentWidth > 300.dp || componentWidth == 0.dp
        val isCompact = componentWidth < 380.dp && componentWidth != 0.dp

        val actionIconSize = if (isCompact) 20.dp else 24.dp
        val actionButtonSize = if (isCompact) 36.dp else 48.dp

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
                items(draftAttachments, key = { (uri, attachment) ->
                    if (uri != Uri.EMPTY) uri.toString()
                    else attachment?.name ?: attachment?.externalLink ?: "unknown"
                }) { (uri, attachment) ->
                    val mimeType = remember(uri, attachment) {
                        if (uri != Uri.EMPTY) {
                            val crType = context.contentResolver.getType(uri)
                            if (crType != null) crType
                            else {
                                val ext =
                                    MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase()
                                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: ""
                            }
                        } else {
                            attachment?.displayType ?: ""
                        }
                    }

                    val isImage = mimeType.startsWith(
                        "image/", ignoreCase = true
                    ) || mimeType.contains("image", ignoreCase = true)
                    val isAudio = mimeType.startsWith(
                        "audio/", ignoreCase = true
                    ) || mimeType.contains("audio", ignoreCase = true)
                    val isVideo = mimeType.startsWith(
                        "video/", ignoreCase = true
                    ) || mimeType.contains("video", ignoreCase = true)

                    val audioUrl = remember(uri, attachment) {
                        if (uri != Uri.EMPTY) uri.toString()
                        else attachment?.externalLink
                    }

                    val isUploading = uri in uploadingUris

                    Box(modifier = Modifier.size(80.dp, 80.dp)) {
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
                        } else if (isAudio && !audioUrl.isNullOrBlank()) {
                            MiniAudioPlayer(
                                url = audioUrl, token = token, modifier = Modifier.fillMaxSize()
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
                                    imageVector = when {
                                        isAudio -> Icons.Outlined.Audiotrack
                                        isVideo -> Icons.Outlined.Videocam
                                        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
                                    }, contentDescription = null
                                )
                            }
                        }

                        if (isUploading) {
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
            InputChip(selected = true, onClick = { showLocationEditDialog = true }, label = {
                Text(
                    text = loc.placeholder ?: "${loc.latitude}, ${loc.longitude}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }, trailingIcon = {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.memo_composer_remove_location),
                    modifier = Modifier
                        .size(18.dp)
                        .noRippleClickable { location = null })
            }, leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            })
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                            stopRecording()
                        } else {
                            val permission = Manifest.permission.RECORD_AUDIO
                            if (ContextCompat.checkSelfPermission(
                                    context, permission
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                startRecording()
                            } else {
                                audioPermissionLauncher.launch(permission)
                            }
                        }
                    }, enabled = !isPosting, modifier = Modifier.size(actionButtonSize)
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Mic else Icons.Outlined.MicNone,
                        contentDescription = "Record Audio",
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
                            fetchLocation()
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(
                        onClick = { expanded = true },
                        enabled = !isPosting,
                        contentPadding = if (isCompact) PaddingValues(horizontal = 8.dp) else ButtonDefaults.TextButtonContentPadding,
                        modifier = if (isCompact) Modifier.height(actionButtonSize) else Modifier
                    ) {
                        Icon(
                            imageVector = getVisibilityIcon(visibility, outlined = true),
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
                                        imageVector = getVisibilityIcon(option, outlined = true),
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

                val label = submitLabel ?: run {
                    val isEdit = initialContent.isNotEmpty() || initialAttachments.isNotEmpty()
                    if (isEdit) stringResource(R.string.memo_action_update)
                    else if (autoFocus) stringResource(R.string.memo_action_post)
                    else stringResource(R.string.memo_publish)
                }

                Button(
                    onClick = {
                        scope.launch {
                            val pendingUploads =
                                draftAttachments.filter { it.second == null && it.first != Uri.EMPTY }
                            val uploadedAttachments = mutableListOf<Attachment>()

                            for ((uri, _) in pendingUploads) {
                                isUploadingCount++
                                uploadingUris = uploadingUris + uri
                                val attachment = onUploadFile(uri, context)
                                uploadingUris = uploadingUris - uri
                                if (attachment != null) {
                                    uploadedAttachments.add(attachment)
                                    // Update local draft state immediately as we upload
                                    draftAttachments = draftAttachments.map {
                                        if (it.first == uri) uri to attachment else it
                                    }
                                }
                                isUploadingCount--
                            }

                            val existingAttachments = draftAttachments.mapNotNull { it.second }
                            val finalAttachments = existingAttachments + uploadedAttachments

                            onPublish(
                                contentState.text.toString(), visibility, finalAttachments, location
                            )
                        }
                    },
                    enabled = (contentState.text.isNotBlank() || draftAttachments.isNotEmpty()) && !isPosting && isUploadingCount == 0,
                    contentPadding = if (showPublishLabel) ButtonDefaults.ContentPadding else PaddingValues(
                        horizontal = if (isCompact) 8.dp else 12.dp
                    ),
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

    if (showLocationEditDialog && location != null) {
        var tempPlaceholder by remember { mutableStateOf(location?.placeholder ?: "") }
        var tempLatitude by remember { mutableStateOf(location?.latitude?.toString() ?: "") }
        var tempLongitude by remember { mutableStateOf(location?.longitude?.toString() ?: "") }

        AlertDialog(
            onDismissRequest = { showLocationEditDialog = false },
            title = { Text(stringResource(R.string.memo_composer_edit_location)) },
            text = {
                @Suppress("LocalVariableName") Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            })
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MiniAudioPlayer(url: String, token: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember {
        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(OkHttpClient.Builder().build())
                .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
        )
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory)).build()
    }

    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    exoPlayer.seekTo(0)
                    exoPlayer.pause()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = {
            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
        }) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
