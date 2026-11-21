package org.example.memosm.ui.component.composer

import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.example.memosm.MemosApplication
import org.example.memosm.api.GsonProvider
import org.example.memosm.data.DraftManager
import org.example.memosm.model.Attachment
import org.example.memosm.model.Draft
import org.example.memosm.model.Location
import org.example.memosm.model.Memo
import org.example.memosm.model.Visibility
import org.example.memosm.viewmodel.delegates.EditorMemoCommit
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Only the session id crosses Binder; memo and attachment snapshots stay in private storage. */
data class EditorRequest(
    val accountId: String,
    val mode: ComposerMode = ComposerMode.PUBLISH,
    val titleRes: Int,
    val draft: Draft? = null,
    val memo: Memo? = null,
    val parentMemo: Memo? = null,
    val content: String = "",
    val uris: List<String> = emptyList(),
    val visibility: Visibility = Visibility.PRIVATE,
    val location: Location? = null,
    val draftId: String = draft?.id ?: UUID.randomUUID().toString(),
    val createdAt: Long = draft?.createdAt ?: System.currentTimeMillis()
)

data class EditorAttachment(val uri: String, val attachment: Attachment?)
data class EditorSnapshot(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val visibility: Visibility,
    val location: Location?,
    val attachments: List<EditorAttachment>,
) {
    fun sameContentAs(other: EditorSnapshot): Boolean = text == other.text && visibility == other.visibility &&
        location == other.location && attachments == other.attachments

    fun hasContent(): Boolean = text.isNotBlank() || attachments.isNotEmpty() || location != null

    fun matches(memo: Memo): Boolean = text == memo.content && visibility == memo.visibility &&
        location == memo.location && attachments.none { it.attachment == null } &&
        attachments.mapNotNull { it.attachment } == memo.attachments.orEmpty()
}

data class StoredEditorSession(
    val request: EditorRequest,
    val snapshot: EditorSnapshot,
    val savedAt: Long? = null,
    val lastCommit: EditorMemoCommit? = null,
)

/** The IME's full value is retained, including selection and composing range. */
class ComposerState(snapshot: EditorSnapshot) {
    val content = mutableStateOf(TextFieldValue(snapshot.text, TextRange(
        snapshot.selectionStart.coerceIn(0, snapshot.text.length),
        snapshot.selectionEnd.coerceIn(0, snapshot.text.length)
    )))
    val visibility = mutableStateOf(snapshot.visibility)
    val location = mutableStateOf(snapshot.location)
    val attachments = mutableStateOf(snapshot.attachments.map { Uri.parse(it.uri) to it.attachment })

    fun snapshot() = EditorSnapshot(content.value.text, content.value.selection.start,
        content.value.selection.end, visibility.value, location.value,
        attachments.value.map { EditorAttachment(it.first.toString(), it.second) })
}

/** Saves outlive the activity, so system Back can finish without intercepting its gesture. */
object EditorSessionStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = ConcurrentHashMap<String, Job>()
    private val mutex = Mutex()
    private val directory get() = File(MemosApplication.instance.filesDir, "editor_sessions")

    private fun file(id: String): AtomicFile {
        require(UUID.fromString(id).toString() == id)
        val target = directory
        check(target.isDirectory || target.mkdirs() || target.isDirectory)
        return AtomicFile(File(target, "$id.json"))
    }

    suspend fun create(request: EditorRequest): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val text = request.memo?.content ?: request.draft?.content ?: request.content
        val attachments = (request.memo?.attachments ?: request.draft?.attachments).orEmpty()
            .map { EditorAttachment("", it) } + request.uris.map { EditorAttachment(it, null) }
        write(id, StoredEditorSession(request, EditorSnapshot(text, text.length, text.length,
            request.memo?.visibility ?: request.draft?.visibility ?: request.parentMemo?.visibility ?: request.visibility,
            request.memo?.location ?: request.draft?.location ?: request.location, attachments)))
        id
    }

    suspend fun read(id: String): StoredEditorSession = withContext(Dispatchers.IO) {
        mutex.withLock { read(file(id)) }
    }

    private fun read(file: AtomicFile): StoredEditorSession =
        file.openRead().bufferedReader().use { GsonProvider.gson.fromJson(it, StoredEditorSession::class.java) }

    suspend fun referencedUploadIds(): Set<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            buildSet {
                for (file in directory.listFiles().orEmpty()) {
                    if (file.extension != "json") continue
                    val stored = read(AtomicFile(file))
                    stored.snapshot.attachments.mapNotNullTo(this) { it.attachment?.clientId }
                    stored.lastCommit?.memo?.attachments.orEmpty().mapNotNullTo(this) { it.clientId }
                }
            }
        }
    }

    private fun write(id: String, session: StoredEditorSession) {
        val target = file(id)
        val output = target.startWrite()
        try {
            val writer = output.writer()
            GsonProvider.gson.toJson(session, writer)
            writer.flush()
            target.finishWrite(output)
        } catch (error: Throwable) {
            target.failWrite(output)
            throw error
        }
    }

    fun save(session: EditorSession, snapshot: EditorSnapshot, debounce: Boolean, remove: Boolean = false): Job {
        return scope.launch {
            if (debounce) delay(500)
            try {
                mutex.withLock {
                    // Once a write starts, finish it before a newer snapshot or submission.
                    withContext(NonCancellable) {
                        if (!session.completed) {
                            val commit = session.lastCommit
                            val contentChanged = !snapshot.sameContentAs(session.savedSnapshot)
                            val savedAt = when {
                                commit != null && snapshot.matches(commit.memo) -> commit.savedAt
                                contentChanged -> System.currentTimeMillis()
                                else -> session.savedAt
                            }
                            write(session.id, StoredEditorSession(session.request, snapshot, savedAt, commit))
                            if (session.request.mode == ComposerMode.PUBLISH && commit == null && !session.submitting) {
                                val draft = Draft(id = session.request.draftId, content = snapshot.text,
                                    visibility = snapshot.visibility, location = snapshot.location,
                                    attachments = snapshot.attachments.mapNotNull { it.attachment },
                                    createdAt = session.createdAt)
                                val drafts = GlobalContext.get().get<DraftManager>()
                                if (draft.hasContent()) {
                                    drafts.saveDraft(session.request.accountId, draft, strict = true)
                                } else if (!snapshot.hasContent() && contentChanged) {
                                    drafts.deleteDraft(session.request.accountId, draft.id)
                                }
                            }
                            session.savedSnapshot = snapshot
                            session.savedAt = savedAt
                        }
                        if (remove || session.completed) file(session.id).delete()
                        session.saveFailed = false
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                session.saveFailed = true
                Log.e("EditorSession", "Could not save editor session", error)
            }
        }.also { job ->
            writes[session.id] = job
            if (remove) job.invokeOnCompletion { writes.remove(session.id, job) }
        }
    }

    suspend fun awaitWrites(id: String?) {
        if (id == null) return
        while (true) {
            val write = writes[id] ?: return
            write.join()
            if (writes.remove(id, write)) return
        }
    }
}

class EditorSession(val id: String, stored: StoredEditorSession) {
    val request = stored.request
    val state = ComposerState(stored.snapshot)
    private val openingDraft = request.draft?.toMemo()
    var lastCommit by mutableStateOf(stored.lastCommit)
        private set
    var savedSnapshot by mutableStateOf(stored.snapshot)
        internal set
    var savedAt by mutableStateOf(stored.savedAt ?: stored.lastCommit?.savedAt ?: request.draft?.updatedAt
        ?: request.memo?.updateTime?.toEpochMilliseconds())
        internal set

    fun needsPublication(snapshot: EditorSnapshot): Boolean {
        if (request.mode == ComposerMode.COMMENT) return lastCommit == null && snapshot.text.isNotBlank()
        val base = lastCommit?.memo ?: request.memo
        return if (base == null) snapshot.hasContent() else !snapshot.matches(base)
    }

    fun canAutoPublish(snapshot: EditorSnapshot): Boolean = needsPublication(snapshot) &&
        (lastCommit != null || openingDraft == null || !snapshot.matches(openingDraft))

    val createdAt = request.createdAt
    @Volatile var completed = false
        private set
    @Volatile var submitting = false
        private set
    var saveFailed by mutableStateOf(false)
        internal set
    var submitFailed by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var finishAfterSave by mutableStateOf(false)
        private set
    private var closing = false
    private var pendingSave: Job? = null
    private var failedSnapshot: EditorSnapshot? = null

    fun changed() {
        if (completed || busy || closing) return
        val snapshot = state.snapshot()
        if (failedSnapshot?.sameContentAs(snapshot) == false) {
            failedSnapshot = null
            submitFailed = false
        }
        pendingSave?.cancel()
        pendingSave = EditorSessionStore.save(this, snapshot, debounce = true)
    }

    fun requestFinishAfterSave() {
        submitFailed = false
        finishAfterSave = true
    }

    fun flush(remove: Boolean = false): Job {
        if (remove) closing = true
        pendingSave?.cancel()
        return EditorSessionStore.save(this, state.snapshot(), debounce = false, remove = remove)
            .also { pendingSave = it }
    }

    fun beginSubmission(): Boolean {
        if (busy || completed || closing) return false
        busy = true
        submitFailed = false
        failedSnapshot = null
        return true
    }

    suspend fun prepareSubmission(): Boolean {
        flush()
        EditorSessionStore.awaitWrites(id)
        if (saveFailed) return false
        submitting = true
        return true
    }

    fun stageAttachmentInEditor(index: Int, sourceUri: String, attachment: Attachment) {
        val current = state.attachments.value
        val item = current.getOrNull(index) ?: return
        if (item.first.toString() != sourceUri || item.second != null) return
        state.attachments.value = current.toMutableList().apply { set(index, item.first to attachment) }
    }

    fun recordCommit(snapshot: EditorSnapshot, commit: EditorMemoCommit) {
        lastCommit = commit
        if (request.mode == ComposerMode.COMMENT) return

        // Only replace sources still in the editor. A newer selection or edit can arrive during staging.
        val prepared = snapshot.attachments.zip(commit.memo.attachments.orEmpty()).toMutableList()
        state.attachments.value = state.attachments.value.map { (uri, attachment) ->
            val index = prepared.indexOfFirst { (source, _) ->
                source.uri == uri.toString() && (source.attachment == attachment ||
                    source.attachment == null && attachment?.name == null && attachment?.clientId == null)
            }
            if (index == -1) uri to attachment else uri to prepared.removeAt(index).second
        }
    }

    fun endSubmission(failed: Boolean) {
        submitFailed = failed
        if (failed) {
            failedSnapshot = state.snapshot()
            finishAfterSave = false
        }
        busy = false
        submitting = false
        if (!closing && !completed) flush()
    }

    fun close() {
        if (!closing) flush(remove = true)
    }

    fun submitted() {
        completed = true
        pendingSave?.cancel()
        flush(remove = true)
    }
}
