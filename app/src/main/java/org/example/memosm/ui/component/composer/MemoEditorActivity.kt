package org.example.memosm.ui.component.composer

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.memosm.R
import org.example.memosm.data.DataStoreManager
import org.example.memosm.model.Memo
import org.example.memosm.ui.profile.ProfileActivity
import org.example.memosm.viewmodel.MemosViewModel
import org.example.memosm.viewmodel.delegates.MemoActionDelegate
import org.koin.android.ext.android.inject

private const val EXTRA_SESSION = "editor_session"
private const val EXTRA_ACCOUNT = "editor_account"

class MemoEditorActivity : ProfileActivity() {
    private val editor: MemoEditorViewModel by viewModels()
    private val settings: DataStoreManager by inject()
    override val requiredAccountId: String? get() = intent.getStringExtra(EXTRA_ACCOUNT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Return the session id even for system Back, so the caller can await the final save.
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_SESSION, intent.getStringExtra(EXTRA_SESSION)))
        editor.load(intent.getStringExtra(EXTRA_SESSION))
    }

    @Composable
    override fun Destination(viewModel: MemosViewModel, onBack: () -> Unit) {
        val uiState by viewModel.uiState.collectAsState()
        androidx.compose.runtime.DisposableEffect(uiState.appSettings.immersiveEditing) {
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            val bars = androidx.core.view.WindowInsetsCompat.Type.systemBars()
            if (uiState.appSettings.immersiveEditing) {
                controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(bars)
            }
            onDispose { controller.show(bars) }
        }
        val session = editor.session
        if (editor.failed) {
            Text(stringResource(R.string.common_operation_failed))
            return
        }
        if (session == null) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            return
        }
        LaunchedEffect(session) {
            snapshotFlow { session.state.snapshot() }.collect { session.changed() }
        }
        val autoPublish by settings.quickEditAutoPublish.collectAsState(initial = null)
        MemoComposerScreen(viewModel = viewModel, hostUrl = uiState.session.hostUrl,
            title = stringResource(session.request.titleRes),
            editorSession = session,
            autoPublish = autoPublish == true && session.request.mode != ComposerMode.COMMENT,
            onPublish = { editor.publish(viewModel.memoActionDelegate, applicationContext) },
            onDismiss = { finish() },
            onSubmitted = {
                session.submitted()
                setResult(RESULT_OK, Intent().putExtra(EXTRA_SESSION, session.id))
                finish()
            })
    }

    companion object {
        suspend fun createIntent(context: Context, request: EditorRequest): Intent =
            Intent(context, MemoEditorActivity::class.java)
                .putExtra(EXTRA_SESSION, EditorSessionStore.create(request))
                .putExtra(EXTRA_ACCOUNT, request.accountId)
    }

    override fun onPause() {
        // Activity results can reach the caller before onStop, so start the final save now.
        editor.session?.flush()
        super.onPause()
    }

    override fun onStop() {
        editor.session?.flush(remove = isFinishing)
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) editor.session?.close()
        super.onDestroy()
    }
}

class MemoEditorViewModel : ViewModel() {
    var session by mutableStateOf<EditorSession?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    private var loading = false

    fun publish(actions: MemoActionDelegate, context: Context) {
        val session = session ?: return
        val snapshot = session.state.snapshot()
        if (!session.needsPublication(snapshot) || !session.beginSubmission()) return
        viewModelScope.launch {
            var failed = true
            try {
                check(session.prepareSubmission()) { "Could not save editor recovery snapshot" }
                val request = session.request
                val attachments = if (request.mode == ComposerMode.COMMENT) emptyList() else collectComposerAttachments(
                    snapshot.attachments.map { Uri.parse(it.uri) to it.attachment },
                    stage = { uri ->
                        requireNotNull(actions.uploadAttachment(uri, context, request.accountId)) { "Could not save attachment" }
                    },
                    onPrepared = { index, attachment ->
                        session.stageAttachmentInEditor(index, snapshot.attachments[index].uri, attachment)
                    },
                )
                withContext(NonCancellable) {
                    val commit = actions.saveEditorMemo(
                        accountId = request.accountId,
                        draftId = request.draftId,
                        edited = Memo(content = snapshot.text, visibility = snapshot.visibility, attachments = attachments, location = snapshot.location),
                        original = request.memo,
                        parent = request.parentMemo,
                        previous = session.lastCommit,
                    )
                    session.recordCommit(snapshot, commit)
                    failed = false
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("MemoEditor", "Could not publish editor snapshot", error)
            } finally {
                session.endSubmission(failed)
            }
        }
    }

    fun load(id: String?) {
        if (session != null || loading) return
        loading = true
        viewModelScope.launch {
            try {
                requireNotNull(id)
                session = EditorSession(id, EditorSessionStore.read(id))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failed = true
            }
        }
    }
}

/** Capture a launch snapshot once; parent repositories refresh only after the final save. */
@Composable
fun rememberMemoEditorLauncher(viewModel: MemosViewModel): (EditorRequest) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var launched by androidx.compose.runtime.remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        launched = false
        scope.launch {
            EditorSessionStore.awaitWrites(result.data?.getStringExtra(EXTRA_SESSION))
            viewModel.refreshAfterEditor()
        }
    }
    return open@{ request ->
        if (launched) return@open
        launched = true
        scope.launch {
            try {
                launcher.launch(MemoEditorActivity.createIntent(context, request))
            } catch (error: CancellationException) {
                launched = false
                throw error
            } catch (error: Exception) {
                launched = false
                Log.e("MemoEditor", "Could not open editor session", error)
                Toast.makeText(context, R.string.common_operation_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
