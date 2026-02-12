package org.example.memosm.ui.component.composer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.draganddrop.dragAndDropTarget
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.memosm.R
import org.example.memosm.data.base64ToTempUri
import org.example.memosm.data.uriToBase64Attachment
import org.example.memosm.model.Attachment
import org.example.memosm.model.Location
import org.example.memosm.model.Visibility
import org.example.memosm.ui.VisibilityIcon
import org.example.memosm.ui.component.item.AttachmentCard
import org.example.memosm.ui.component.item.AttachmentCompactMode
import org.example.memosm.ui.findActivity
import org.example.memosm.ui.getFileSize
import org.example.memosm.ui.getVisibilityLabel
import java.io.File


/**
 * Mode for the memo composer, determines the submit button label.
 * All callers MUST specify an explicit mode.
 */
enum class ComposerMode {
    /** Creating a new memo or publishing a draft */
    PUBLISH,

    /** Editing an existing memo */
    UPDATE,

    /** Adding a comment to a memo */
    COMMENT
}

@Composable
fun MemoComposer(
    modifier: Modifier = Modifier,
    onPublish: (String, Visibility, List<Attachment>, Location?) -> Unit,
    onUploadFile: suspend (Uri, Context) -> Attachment?,
    onGetLocationName: (suspend (Double, Double) -> String?)? = null,
    availableTags: Set<String>,
    token: String,
    hostUrl: String,
    mode: ComposerMode,
    isPosting: Boolean = false,
    initialContent: String = "",
    initialVisibility: Visibility = Visibility.PRIVATE,
    initialAttachments: List<Attachment> = emptyList(),
    initialUris: List<Uri> = emptyList(),
    initialLocation: Location? = null,
    placeholder: String = stringResource(R.string.memo_composer_placeholder),
    autoFocus: Boolean = false,
    onDraftChanged: ((String, Visibility, List<Attachment>, Location?) -> Unit)? = null
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    // Changed to TextFieldValue for VisualTransformation support
    var contentState by remember {
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                initialContent
            )
        )
    }
    var visibility by remember { mutableStateOf(initialVisibility) }
    var location by remember { mutableStateOf(initialLocation) }

    val draftAttachmentsState = remember {
        // Combine existing attachments (from editing) with new URIs (from share intent)
        val fromAttachments: List<Pair<Uri, Attachment?>> =
            initialAttachments.map { Uri.EMPTY to (it as Attachment?) }


        val fromUris: List<Pair<Uri, Attachment?>> = initialUris.map { it to null }
        mutableStateOf(fromAttachments + fromUris)
    }

    var draftAttachments by draftAttachmentsState

    var expanded by remember { mutableStateOf(false) }
    var showLocationEditDialog by remember { mutableStateOf(false) }

    var isUploadingCount by remember { mutableIntStateOf(0) }
    var uploadingUris by remember { mutableStateOf(setOf<Uri>()) }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var showActionOverflowMenu by remember { mutableStateOf(false) }

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
            // Add URIs immediately for display, then convert to base64 in background
            val newUris = uris.map { it to null as Attachment? }
            draftAttachments = draftAttachments + newUris

            // Convert to base64 in background for each URI
            uris.forEach { uri ->
                scope.launch {
                    val attachment = uriToBase64Attachment(uri, context)
                    if (attachment != null) {
                        // Update the draft attachments with the converted attachment
                        draftAttachments = draftAttachments.map { (u, a) ->
                            if (u == uri && a == null) uri to attachment else u to a
                        }
                    }
                }
            }
        }
    }

    val locationPlaceHolder = stringResource(R.string.memo_composer_location_default_placeholder)

    @SuppressLint("MissingPermission")
    fun fetchLocation() {
        isFetchingLocation = true

        fun handleAndroidLocation(androidLoc: android.location.Location?) {
            if (androidLoc != null) {
                var loc = Location(
                    latitude = androidLoc.latitude,
                    longitude = androidLoc.longitude,
                    placeholder = locationPlaceHolder
                )

                // Fetch address name
                val fetcher = onGetLocationName
                if (fetcher != null) {
                    scope.launch {
                        try {
                            val name = fetcher(androidLoc.latitude, androidLoc.longitude)
                            if (name != null) {
                                loc = loc.copy(placeholder = name)
                            }
                        } catch (e: Exception) {
                            Log.e("MemoComposer", "Error in reverse geocoding callback", e)
                        } finally {
                            location = loc
                            isFetchingLocation = false
                        }
                    }
                } else {
                    location = loc
                    isFetchingLocation = false
                }
            } else {
                isFetchingLocation = false
            }
        }

        fun fetchFallback() {
            try {
                val locationManager =
                    context.getSystemService(Context.LOCATION_SERVICE) as LocationManager


                // Select provider explicitly instead of using Criteria
                val provider = when {
                    // 1. Try Fused: Only valid on Android 12 (S) + and if enabled
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && locationManager.isProviderEnabled(
                        LocationManager.FUSED_PROVIDER
                    ) -> {
                        LocationManager.FUSED_PROVIDER
                    }

                    // 2. Fallback to Network (Faster, battery efficient)
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> {
                        LocationManager.NETWORK_PROVIDER
                    }

                    // 3. Fallback to GPS (Higher accuracy, slower)
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> {
                        LocationManager.GPS_PROVIDER
                    }

                    else -> null
                }

                if (provider != null) {
                    // Try last known location first as a quick fallback
                    val lastKnown = locationManager.getLastKnownLocation(provider)
                    if (lastKnown != null) {
                        handleAndroidLocation(lastKnown)
                        return
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        locationManager.getCurrentLocation(
                            provider, null, ContextCompat.getMainExecutor(context)
                        ) { loc -> handleAndroidLocation(loc) }
                    } else {
                        @Suppress("DEPRECATION") locationManager.requestSingleUpdate(
                            provider, object : LocationListener {
                                override fun onLocationChanged(l: android.location.Location) {
                                    handleAndroidLocation(l)
                                }

                                @Deprecated("Deprecated in Java")
                                override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {
                                }

                                override fun onProviderEnabled(p: String) {}
                                override fun onProviderDisabled(p: String) {}
                            }, context.mainLooper
                        )
                    }
                } else {
                    isFetchingLocation = false
                }
            } catch (e: Exception) {
                Log.e("MemoComposer", "Error in fallback location fetch", e)
                isFetchingLocation = false
            }
        }

        val cancellationTokenSource = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationTokenSource.token
        ).addOnSuccessListener { androidLoc ->
            if (androidLoc != null) {
                handleAndroidLocation(androidLoc)
            } else {
                fetchFallback()
            }
        }.addOnFailureListener {
            fetchFallback()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchLocation()
        }
    }


    val recordErrorText = stringResource(R.string.memo_composer_error_stop_recording)

    fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false

            currentRecordFile?.let { file ->
                val uri = file.toUri()
                // Add immediately for display
                draftAttachments = draftAttachments + (uri to null)
                // Convert to base64 in background
                scope.launch {
                    val attachment = uriToBase64Attachment(uri, context)
                    if (attachment != null) {
                        draftAttachments = draftAttachments.map { (u, a) ->
                            if (u == uri && a == null) uri to attachment else u to a
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MemoComposer", "Error stopping recording", e)
            Toast.makeText(
                context, recordErrorText, Toast.LENGTH_SHORT
            ).show()
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
            val message =
                resources.getString(R.string.memo_composer_error_start_recording, e.message ?: "")
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecording()
        } else {
            Toast.makeText(
                context,
                resources.getString(R.string.memo_composer_error_microphone_permission),
                Toast.LENGTH_SHORT
            ).show()
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
    // Debounced to avoid blocking UI on every keystroke
    // Only saves attachments that have already been converted to base64 (non-null)
    LaunchedEffect(contentState.text, visibility, draftAttachments, location) {
        if (onDraftChanged == null) return@LaunchedEffect

        // Debounce: wait 500ms before saving draft
        delay(500)

        // Only save attachments that have been converted (non-null Attachment)
        // Attachments still being converted in background will be saved on next trigger
        val convertedAttachments = draftAttachments.mapNotNull { (_, attachment) -> attachment }

        onDraftChanged.invoke(
            contentState.text, visibility, convertedAttachments, location
        )
    }

    // Drag and Drop state
    var isDragging by remember { mutableStateOf(false) }
    val dndTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragging = false
                val dragEvent = event.toAndroidDragEvent()

                // Request drag and drop permissions for cross-app access
                context.findActivity()?.requestDragAndDropPermissions(dragEvent)

                val clipData = dragEvent.clipData
                if (clipData != null) {
                    val uris = mutableListOf<Uri>()
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i).uri?.let { uris.add(it) }
                    }
                    if (uris.isNotEmpty()) {
                        scope.launch {
                            val validUris = uris.filter { uri ->
                                val size = getFileSize(context, uri)
                                if (size > 10 * 1024 * 1024) {
                                    Toast.makeText(
                                        context, "File size exceeds 10MB limit", Toast.LENGTH_SHORT
                                    ).show()
                                    false
                                } else {
                                    true
                                }
                            }

                            val newAttachments = validUris.map { uri ->
                                val base64Attachment = uriToBase64Attachment(uri, context)
                                uri to base64Attachment
                            }
                            draftAttachments = draftAttachments + newAttachments
                        }
                        return true
                    }
                }
                return false
            }

            override fun onEntered(event: DragAndDropEvent) {
                isDragging = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isDragging = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isDragging = false
            }
        }
    }

    var componentWidth by remember { mutableStateOf(0.dp) }

    Column(
        modifier = modifier
            .onSizeChanged {
                componentWidth = with(density) { it.width.toDp() }
            }
            .dragAndDropTarget(
                shouldStartDragAndDrop = { true }, target = dndTarget
            )
            .background(
                if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent, RoundedCornerShape(8.dp)
            )
    ) {
        val showVisibilityLabel = componentWidth > 480.dp || componentWidth == 0.dp
        val showPublishLabel = componentWidth > 410.dp || componentWidth == 0.dp
        val isCompact = componentWidth < 380.dp && componentWidth != 0.dp

        val actionIconSize = if (isCompact) 20.dp else 24.dp
        val actionButtonSize = if (isCompact) 36.dp else 48.dp

        MemoInput(
            contentState = contentState,
            onContentChange = { contentState = it },
            placeholder = placeholder,
            availableTags = availableTags,
            enabled = !isPosting,
            autoFocus = autoFocus,
            modifier = Modifier.weight(1f),
        )

        MemoComposerBottomBar(
            modifier = Modifier,
            draftAttachments = draftAttachments,
            uploadingUris = uploadingUris,
            location = location,
            isPosting = isPosting,
            isFetchingLocation = isFetchingLocation,
            isRecording = isRecording,
            visibility = visibility,
            mode = mode,
            componentWidth = componentWidth,
            pickerLauncher = pickerLauncher,
            audioPermissionLauncher = audioPermissionLauncher,
            locationPermissionLauncher = locationPermissionLauncher,
            token = token,
            hostUrl = hostUrl,
            contentStateText = contentState.text,
            isUploadingCount = isUploadingCount,
            onFetchLocation = { fetchLocation() },
            onStartRecording = { startRecording() },
            onStopRecording = { stopRecording() },
            onRemoveLocation = { location = null },
            onLocationClick = { showLocationEditDialog = true },
            onVisibilityChange = { visibility = it },
            onRemoveAttachment = { uri, attachment ->
                val updated =
                    draftAttachments.filter { it.second != attachment || (it.first != uri && uri != Uri.EMPTY) }
                draftAttachments = updated
            },
            onPublishClick = {
                scope.launch {
                    val pendingUploads = draftAttachments.filter {
                        // Case 1: New local file (Uri is not EMPTY, Attachment is null)
                        (it.second == null && it.first != Uri.EMPTY) ||
                                // Case 2: Restored draft (Uri is EMPTY, Attachment has content but no name on server)
                                (it.second != null && it.second!!.name == null && it.second!!.content != null)
                    }
                    val uploadedAttachments = mutableListOf<Attachment>()

                    for ((uri, attachment) in pendingUploads) {
                        isUploadingCount++

                        // If it's a restored draft, we first need to convert it to a temp Uri
                        val uploadUri =
                            if (uri == Uri.EMPTY && attachment != null) {
                                base64ToTempUri(
                                    attachment.content ?: "",
                                    attachment.filename,
                                    attachment.type,
                                    context
                                )
                            } else {
                                uri
                            }

                        if (uploadUri != null) {
                            uploadingUris = uploadingUris + uploadUri
                            val uploaded = onUploadFile(uploadUri, context)
                            uploadingUris = uploadingUris - uploadUri

                            if (uploaded != null) {
                                uploadedAttachments.add(uploaded)
                                // Update local draft state immediately as we upload
                                // We match by the ORIGINAL pair to replace it
                                draftAttachments = draftAttachments.map {
                                    if (it == (uri to attachment)) uploadUri to uploaded else it
                                }
                            }
                        }
                        isUploadingCount--
                    }

                    // Now collect all valid attachments:
                    // 1. Items that were already valid (non-null attachment with name)
                    // 2. Newly uploaded items are already swapped into draftAttachments by the loop above,
                    //    OR added to uploadedAttachments if we want to be safe, but the loop upates draftAttachments.
                    // Let's just grab everything from draftAttachments that has a valid server-side attachment (name != null)
                    val finalAttachments = draftAttachments.mapNotNull { it.second }
                        .filter { it.name != null }

                    onPublish(
                        contentState.text, visibility, finalAttachments, location
                    )
                }
            }
        )
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
            })
    }
}
