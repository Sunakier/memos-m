package org.example.memosm.viewmodel.delegates

import org.example.memosm.data.media.AttachmentUpload

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.example.memosm.R
import org.example.memosm.api.GsonProvider
import org.example.memosm.data.DraftManager
import org.example.memosm.data.backup.BackupCoordinator
import org.example.memosm.data.cache.CacheListType
import org.example.memosm.data.cache.MemoCacheRepository
import org.example.memosm.data.sync.PendingOp
import org.example.memosm.data.sync.PendingOpType
import org.example.memosm.data.sync.ReactionOpPayload
import org.example.memosm.data.sync.SyncWorkScheduler
import org.example.memosm.data.sync.applyMemoPatch
import org.example.memosm.model.Attachment
import org.example.memosm.model.Location
import org.example.memosm.model.Memo
import org.example.memosm.model.MemoState
import org.example.memosm.model.Reaction
import org.example.memosm.model.Visibility
import org.example.memosm.viewmodel.AccountContext
import org.example.memosm.viewmodel.AccountSession
import org.example.memosm.viewmodel.MemosUiState
import org.example.memosm.viewmodel.UiMessage
import org.example.memosm.viewmodel.manager.AttachmentManager
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

data class EditorMemoCommit(val memo: Memo, val operationId: String?, val savedAt: Long)

interface MemoActionDelegate {
    fun selectMemo(memo: Memo?)

    fun clearSelectedMemo()

    fun createMemo(
        content: String,
        visibility: Visibility,
        attachments: List<Attachment>? = null,
        location: Location? = null,
        memoId: String? = null,
        onError: () -> Unit = {},
        onSuccess: () -> Unit = {},
    )

    fun updateMemo(
        memo: Memo,
        content: String,
        visibility: Visibility,
        attachments: List<Attachment>,
        location: Location? = memo.location,
        state: MemoState? = null,
        onError: () -> Unit = {},
        onSuccess: () -> Unit = {},
    )

    fun deleteMemo(
        memo: Memo,
        onSuccess: () -> Unit = {},
    )

    fun updateMemoPinned(
        memo: Memo,
        pinned: Boolean,
        onSuccess: () -> Unit = {},
    )

    fun createComment(
        parentMemo: Memo,
        content: String,
        commentId: String? = null,
        onError: () -> Unit = {},
        onSuccess: () -> Unit = {},
    )

    suspend fun saveEditorMemo(
        accountId: String,
        draftId: String,
        edited: Memo,
        original: Memo?,
        parent: Memo?,
        previous: EditorMemoCommit?,
    ): EditorMemoCommit

    suspend fun uploadAttachment(
        uri: Uri,
        context: Context,
        accountId: String,
    ): Attachment?

    /** Discard a queued offline upload once nothing references its placeholder anymore. */
    fun discardQueuedUploadIfOrphaned(clientId: String)

    fun upsertMemoReaction(
        memo: Memo,
        reactionType: String,
    )

    fun deleteMemoReaction(
        memo: Memo,
        reaction: Reaction,
    )
}

/** User actions only commit local state. WorkManager owns every network mutation. */
class MemoActionDelegateImpl(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<MemosUiState>,
    private val draftDelegate: DraftDelegate,
    private val attachmentManagerProvider: () -> AttachmentManager?,
    private val memoCacheRepository: MemoCacheRepository,
    private val accountSession: AccountSession,
    private val draftManager: DraftManager,
    private val workScheduler: SyncWorkScheduler,
    private val accountExists: suspend (String) -> Boolean = { true },
) : MemoActionDelegate {
    private val gson = GsonProvider.gson

    override fun selectMemo(memo: Memo?) {
        val context = accountSession.current ?: return
        accountSession.update(uiState, context) {
            it.copy(detailPane = it.detailPane.copy(selectedMemo = memo))
        }
    }

    override fun clearSelectedMemo() = selectMemo(null)

    private fun mutate(
        context: AccountContext,
        onError: () -> Unit = {},
        onSuccess: () -> Unit = {},
        afterCommit: suspend () -> Unit = {},
        attachments: List<Attachment>? = null,
        prepare: suspend (List<Attachment>?) -> Pair<PendingOp, Memo?>?,
    ) {
        val generation = BackupCoordinator.generation
        scope.launch {
            accountSession.update(uiState, context) { it.copy(isPosting = true) }
            try {
                commitMutation(context, attachments, generation, prepare)
                afterCommit()
                if (accountSession.isCurrent(context)) onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("MemoActions", "Could not save local mutation", error)
                accountSession.update(uiState, context) { it.copy(error = UiMessage(R.string.common_operation_failed)) }
                if (accountSession.isCurrent(context)) onError()
            } finally {
                accountSession.update(uiState, context) { it.copy(isPosting = false) }
            }
        }
    }

    private suspend fun commitMutation(
        context: AccountContext,
        attachments: List<Attachment>?,
        generation: Long = BackupCoordinator.generation,
        prepare: suspend (List<Attachment>?) -> Pair<PendingOp, Memo?>?,
    ): Pair<PendingOp, Memo?>? {
        val accountId = context.account.id
        val commit: suspend (List<Attachment>?, List<AttachmentUpload>) -> Pair<PendingOp, Memo?>? = { prepared, uploads ->
            BackupCoordinator.withStorageLock {
                check(BackupCoordinator.generation == generation && !BackupCoordinator.restoring.value) { "Storage was restored" }
                check(accountExists(accountId)) { "Account was removed" }
                prepare(prepared)?.also { (op, memo) ->
                    memoCacheRepository.commitMutation(
                        op,
                        memo,
                        when {
                            op.parentName != null || memo?.parent != null -> CacheListType.COMMENT
                            memo?.state == MemoState.ARCHIVED -> CacheListType.ARCHIVED
                            else -> CacheListType.USER
                        },
                        op.parentName ?: memo?.parent,
                        uploads,
                    )
                }
            }
        }
        val manager = attachmentManagerProvider()
        val mutation = if (attachments != null && manager != null) {
            manager.withPreparedAttachments(context, attachments, commit)
        } else {
            require(attachments.orEmpty().all { !it.name.isNullOrBlank() || it.clientId != null }) { "Uploads are unavailable" }
            commit(attachments, emptyList())
        }
        if (mutation != null) {
            try {
                workScheduler.schedule(accountId)
            } catch (error: Exception) {
                Log.e("MemoActions", "Could not schedule committed mutation", error)
            }
        }
        return mutation
    }

    override fun createMemo(
        content: String,
        visibility: Visibility,
        attachments: List<Attachment>?,
        location: Location?,
        memoId: String?,
        onError: () -> Unit,
        onSuccess: () -> Unit,
    ) {
        val context = accountSession.current ?: return
        val draftId = uiState.value.draft.currentEditingDraftId
        val id =
            memoId ?: draftId?.let { UUID.nameUUIDFromBytes(it.toByteArray(Charsets.UTF_8)).toString() }
                ?: UUID.randomUUID().toString()
        val creator = context.account.user?.name ?: uiState.value.session.currUser?.name
        mutate(context, onError, onSuccess, attachments = attachments.orEmpty(), afterCommit = {
            // Await originating-account draft cleanup before the editor can finish.
            draftId?.let { draftManager.deleteDraft(context.account.id, it) }
            if (accountSession.isCurrent(context) && uiState.value.draft.currentEditingDraftId == draftId) {
                draftDelegate.setCurrentEditingDraft(null)
            }
        }) { prepared ->
            createMutation(
                context, id,
                Memo(content = content, visibility = visibility, attachments = prepared, location = location, creator = creator),
            )
        }
    }

    override fun updateMemo(
        memo: Memo,
        content: String,
        visibility: Visibility,
        attachments: List<Attachment>,
        location: Location?,
        state: MemoState?,
        onError: () -> Unit,
        onSuccess: () -> Unit,
    ) {
        val context = accountSession.current ?: return
        if (memo.name == null) return
        mutate(context, onError, onSuccess, attachments = attachments) { prepared ->
            updateMutation(
                context,
                memo,
                memo.copy(
                    content = content,
                    visibility = visibility,
                    attachments = prepared,
                    location = location,
                    state = state ?: memo.state,
                ),
            )
        }
    }

    private fun createMutation(context: AccountContext, id: String, edited: Memo, parent: String? = null): Pair<PendingOp, Memo> {
        val now = Clock.System.now()
        val type = if (parent == null) PendingOpType.CREATE else PendingOpType.COMMENT_CREATE
        val memo = edited.copy(
            name = if (parent == null) "offline-$id" else "offline-comment-$id",
            parent = parent,
            state = MemoState.NORMAL,
            createTime = now,
            updateTime = now,
            displayTime = now,
        )
        return PendingOp.new(context.account.id, type, memo.name, parent, gson.toJson(memo), id = id) to memo
    }

    private fun updateMutation(context: AccountContext, memo: Memo, update: Memo): Pair<PendingOp, Memo>? {
        val mask = buildList {
            if (update.content != memo.content) add("content")
            if (update.visibility != memo.visibility) add("visibility")
            if (update.attachments.orEmpty() != memo.attachments.orEmpty()) add("attachments")
            if (update.location != memo.location) add("location")
            if (update.state != memo.state) add("state")
        }.joinToString(",")
        if (mask.isEmpty()) return null
        return PendingOp.new(
            context.account.id,
            PendingOpType.UPDATE,
            requireNotNull(memo.name),
            parentName = memo.parent,
            payloadJson = gson.toJson(update),
            updateMask = mask,
            baseUpdateTime = memo.updateTime?.toString(),
        ) to update
    }

    override suspend fun saveEditorMemo(
        accountId: String,
        draftId: String,
        edited: Memo,
        original: Memo?,
        parent: Memo?,
        previous: EditorMemoCommit?,
    ): EditorMemoCommit {
        val context = checkNotNull(accountSession.current?.takeIf { it.account.id == accountId }) { "Editor account is unavailable" }
        val creator = context.account.user?.name ?: uiState.value.session.currUser?.name
        val id = UUID.nameUUIDFromBytes(draftId.toByteArray(Charsets.UTF_8)).toString()
        var base = previous?.memo ?: original
        val previousAttachments = base?.attachments.orEmpty()
        val attachments = edited.attachments.orEmpty().takeIf { parent == null && (base == null || it != previousAttachments) }
        val mutation = commitMutation(context, attachments) { prepared ->
            if (parent != null) {
                val name = requireNotNull(parent.name)
                val comment = memoCacheRepository.getCachedMemo(accountId, "offline-comment-$id").also { base = it }
                return@commitMutation if (comment == null) {
                    createMutation(context, id, Memo(content = edited.content, visibility = parent.visibility, creator = creator), name)
                } else updateMutation(context, comment, comment.copy(content = edited.content))
            }
            // The create may already be durable if the process died before saving the editor checkpoint.
            val memo = base ?: memoCacheRepository.getCachedMemo(accountId, "offline-$id").also { base = it }
                ?: return@commitMutation createMutation(context, id, edited.copy(attachments = prepared, creator = creator))
            val mutation = updateMutation(
                context,
                memo,
                memo.copy(
                    content = edited.content,
                    visibility = edited.visibility,
                    attachments = prepared ?: memo.attachments,
                    location = edited.location,
                ),
            ) ?: return@commitMutation null
            val (op, update) = mutation
            // A note opened from its optimistic local identity can be acknowledged before the first edit.
            if (previous == null && !op.memoName.orEmpty().startsWith("offline-")) return@commitMutation mutation
            val guard = editorUpdateBase(accountId, memo, previous?.operationId, requireNotNull(op.updateMask))
            op.copy(baseUpdateTime = guard?.toString()) to update.copy(updateTime = guard ?: memo.updateTime)
        }
        if (original == null && parent == null && previous == null) draftManager.deleteDraft(accountId, draftId)
        val memo = requireNotNull(mutation?.second ?: base)
        return EditorMemoCommit(memo, mutation?.first?.id ?: previous?.operationId, System.currentTimeMillis())
    }

    private suspend fun editorUpdateBase(accountId: String, memo: Memo, predecessorId: String?, mask: String): Instant? {
        // Acknowledgements can advance the predecessor's guard while it is still queued.
        predecessorId?.let { id ->
            memoCacheRepository.getMutation(id)?.takeIf { it.accountId == accountId }?.let { op ->
                return op.baseUpdateTime?.let(Instant::parse)
            }
        }
        val server = memoCacheRepository.getServerMemo(accountId, requireNotNull(memo.name)) ?: return memo.updateTime
        val fields = mask.split(',')
        val sameOtherFields = applyMemoPatch(server, memo, fields.filterNot { it == "attachments" }.joinToString(",")) == server
        if (!sameOtherFields) return memo.updateTime
        if ("attachments" in fields) {
            val manager = attachmentManagerProvider()
            val savedNames = memo.attachments.orEmpty().map { it.name ?: manager?.resolvedAttachmentName(it) }
            if (savedNames != server.attachments.orEmpty().map { it.name }) return memo.updateTime
        }
        return server.updateTime
    }

    override fun deleteMemo(
        memo: Memo,
        onSuccess: () -> Unit,
    ) {
        val context = accountSession.current ?: return
        val name = memo.name ?: return
        mutate(context, onSuccess = onSuccess) {
            PendingOp.new(context.account.id, PendingOpType.DELETE, name, parentName = memo.parent) to memo
        }
    }

    override fun updateMemoPinned(
        memo: Memo,
        pinned: Boolean,
        onSuccess: () -> Unit,
    ) {
        val context = accountSession.current ?: return
        val name = memo.name ?: return
        mutate(context, onSuccess = onSuccess) {
            val update = memo.copy(pinned = pinned)
            PendingOp.new(
                context.account.id,
                PendingOpType.UPDATE,
                name,
                parentName = memo.parent,
                payloadJson = gson.toJson(update),
                updateMask = "pinned",
                baseUpdateTime = memo.updateTime?.toString(),
            ) to update
        }
    }

    override fun createComment(
        parentMemo: Memo,
        content: String,
        commentId: String?,
        onError: () -> Unit,
        onSuccess: () -> Unit,
    ) {
        val context = accountSession.current ?: return
        val parent = parentMemo.name ?: return
        val id = commentId ?: UUID.randomUUID().toString()
        val creator = context.account.user?.name ?: uiState.value.session.currUser?.name
        mutate(context, onError, onSuccess) {
            createMutation(context, id, Memo(content = content, visibility = parentMemo.visibility, creator = creator), parent)
        }
    }

    override fun upsertMemoReaction(
        memo: Memo,
        reactionType: String,
    ) = reaction(memo, reactionType, add = true)

    override fun deleteMemoReaction(
        memo: Memo,
        reaction: Reaction,
    ) = reaction(memo, reaction.reactionType, add = false)

    private fun reaction(
        memo: Memo,
        type: String,
        add: Boolean,
    ) {
        val context = accountSession.current ?: return
        val name = memo.name ?: return
        val mine =
            context.account.user?.name ?: uiState.value.session.currUser
                ?.name ?: return
        mutate(context) {
            PendingOp.new(
                context.account.id,
                if (add) PendingOpType.REACTION_UPSERT else PendingOpType.REACTION_DELETE,
                name,
                parentName = memo.parent,
                payloadJson = gson.toJson(ReactionOpPayload(type, mine)),
            ) to memo
        }
    }

    override suspend fun uploadAttachment(
        uri: Uri,
        context: Context,
        accountId: String,
    ): Attachment? {
        val account = accountSession.current?.takeIf { it.account.id == accountId } ?: return null
        return attachmentManagerProvider()?.uploadAttachment(uri, context, account).takeIf { accountSession.isCurrent(account) }
    }

    override fun discardQueuedUploadIfOrphaned(clientId: String) {
        scope.launch { attachmentManagerProvider()?.discardQueuedUploadIfOrphaned(clientId) }
    }
}
