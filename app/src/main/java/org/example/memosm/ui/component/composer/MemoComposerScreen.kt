package org.example.memosm.ui.component.composer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.example.memosm.R
import org.example.memosm.ui.component.ProfileBackButton
import org.example.memosm.ui.theme.LocalPaperTone
import org.example.memosm.ui.theme.PaperToneEditorHeader
import org.example.memosm.viewmodel.MemosViewModel
import org.example.memosm.viewmodel.isOffline
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val AUTO_PUBLISH_DELAY_MS = 1_500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoComposerScreen(
    onDismiss: () -> Unit,
    viewModel: MemosViewModel,
    hostUrl: String,
    title: String,
    editorSession: EditorSession,
    autoPublish: Boolean,
    onPublish: () -> Unit,
    onSubmitted: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val paperTone = LocalPaperTone.current
    val immersive = uiState.appSettings.immersiveEditing
    val publish by rememberUpdatedState(onPublish)

    LaunchedEffect(editorSession, autoPublish) {
        if (!autoPublish) return@LaunchedEffect
        snapshotFlow {
            val snapshot = editorSession.state.snapshot()
            if (editorSession.busy || editorSession.finishAfterSave || editorSession.submitFailed ||
                editorSession.state.content.value.composition != null || !editorSession.canAutoPublish(snapshot)
            ) null else snapshot.copy(selectionStart = 0, selectionEnd = 0)
        }.collectLatest { snapshot ->
            if (snapshot != null) {
                delay(AUTO_PUBLISH_DELAY_MS)
                publish()
            }
        }
    }

    LaunchedEffect(editorSession.finishAfterSave, editorSession.busy) {
        if (editorSession.finishAfterSave && !editorSession.busy) {
            when {
                editorSession.needsPublication(editorSession.state.snapshot()) -> publish()
                editorSession.lastCommit != null -> onSubmitted()
                else -> onDismiss()
            }
        }
    }
    val exit: () -> Unit = {
        if (autoPublish || editorSession.busy) editorSession.requestFinishAfterSave() else onDismiss()
    }
    BackHandler(onBack = exit)

    val commit = editorSession.lastCommit
    val snapshot = editorSession.state.snapshot()
    val memoName = commit?.memo?.name ?: editorSession.request.memo?.name
    val statusFlow = remember(viewModel, memoName, editorSession.request.accountId, commit?.operationId) {
        memoName?.let { viewModel.observeMemoWriteStatus(editorSession.request.accountId, it) }
    }
    // Wait for a fresh Room read after each commit before displaying acknowledgement.
    val writeStatus = key(memoName, commit?.operationId) { statusFlow?.collectAsStateWithLifecycle(initialValue = null)?.value }
    val failed = editorSession.saveFailed || editorSession.submitFailed || writeStatus?.blocked == true
    val saving = editorSession.busy || !snapshot.sameContentAs(editorSession.savedSnapshot)
    val status = when {
        editorSession.saveFailed -> R.string.memo_editor_save_failed
        editorSession.submitFailed -> R.string.memo_editor_publish_failed
        saving -> R.string.memo_editor_saving
        writeStatus?.blocked == true -> R.string.memo_editor_sync_failed
        editorSession.needsPublication(snapshot) -> R.string.memo_editor_saved_locally
        writeStatus != null && writeStatus.pendingCount > 0 -> when {
            uiState.connectionState.isOffline -> R.string.memo_editor_offline_pending
            uiState.isSyncing -> R.string.sync_status_syncing
            else -> R.string.memo_editor_pending
        }
        writeStatus?.serverVerified == true && writeStatus.content == snapshot.text &&
            writeStatus.visibility == snapshot.visibility.name -> R.string.memo_editor_synced
        editorSession.savedAt != null -> R.string.memo_editor_saved_locally
        else -> null
    }
    val metadata: @Composable () -> Unit = {
        MemoEditorMetadata(
            text = snapshot.text,
            status = status?.let { stringResource(it) },
            savedAt = editorSession.savedAt.takeUnless { saving || failed },
            failed = failed,
        )
    }

    Scaffold(
        topBar = {
            if (paperTone || immersive) {
                PaperToneEditorHeader(onBack = exit.takeUnless { immersive }, content = metadata)
            } else {
                Column {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = { ProfileBackButton(onClick = exit) },
                    )
                    metadata()
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding().imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            MemoComposer(
                onPublish = editorSession::requestFinishAfterSave,
                onDiscardQueuedUpload = viewModel.memoActionDelegate::discardQueuedUploadIfOrphaned,
                availableTags = uiState.session.userStats?.tagCount.orEmpty(),
                token = uiState.session.token,
                hostUrl = hostUrl,
                isPosting = editorSession.busy || editorSession.finishAfterSave,
                isFinishing = editorSession.finishAfterSave,
                editorState = editorSession.state,
                mode = editorSession.request.mode,
                modifier = Modifier.widthIn(max = 800.dp).fillMaxSize().padding(horizontal = if (paperTone) 0.dp else 8.dp),
            )
        }
    }
}

@Composable
private fun MemoEditorMetadata(text: String, status: String?, savedAt: Long?, failed: Boolean) {
    val count = remember(text) {
        text.codePoints().filter { !Character.isWhitespace(it) && !Character.isSpaceChar(it) }.count().toInt()
    }
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val timestamp = remember(savedAt, locale) {
        savedAt?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(it)) }
    }
    val label = if (status != null && timestamp != null) {
        stringResource(R.string.memo_editor_status_with_time, status, timestamp)
    } else status
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.appearance_papertone_characters, count, count),
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.testTag("paperToneEditorCount"),
        )
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error else color,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
