package org.example.memosm.model

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class ListMemosResponse(
    val memos: List<Memo>?, val nextPageToken: String?
)

data class ListAttachmentsResponse(
    val attachments: List<Attachment>?, val nextPageToken: String?, val totalSize: Int?
)

@Serializable
enum class Visibility {
    @SerialName("PUBLIC")
    PUBLIC,

    @SerialName("PROTECTED")
    PROTECTED,

    @SerialName("PRIVATE")
    PRIVATE,

    @SerializedName("VISIBILITY_UNSPECIFIED")
    VISIBILITY_UNSPECIFIED
}

@Serializable
enum class MemoState {
    @SerialName("NORMAL")
    NORMAL,

    @SerialName("ARCHIVED")
    ARCHIVED,

    @SerialName("STATE_UNSPECIFIED")
    STATE_UNSPECIFIED
}

data class Memo(
    val name: String? = null,
    val state: MemoState? = null,
    val creator: String? = null,
    val createTime: String? = null,
    val updateTime: String? = null,
    val displayTime: String? = null,
    val content: String,
    val visibility: Visibility = Visibility.PRIVATE,
    val tags: List<String>? = null,
    val pinned: Boolean? = null,
    val attachments: List<Attachment>? = null,
    val relations: List<MemoRelation>? = null,
    val reactions: List<Reaction>? = null,
    val property: MemoProperty? = null,
    val parent: String? = null,
    val snippet: String? = null,
    val location: Location? = null
)

data class Attachment(
    val name: String? = null,
    val createTime: String? = null,
    val filename: String,
    val content: String? = null,
    val externalLink: String? = null,
    val type: String,
    val mimeType: String? = null,
    val size: String? = null,
    val memo: String? = null
) {
    val displayType: String
        get() = mimeType ?: type
}

data class MemoRelation(
    val memo: MemoSnippet, val relatedMemo: MemoSnippet, val type: String
)

data class MemoSnippet(
    val name: String, val snippet: String? = null
)

data class Reaction(
    val name: String? = null,
    val creator: String? = null,
    val contentId: String,
    val reactionType: String,
    val createTime: String? = null
)

data class MemoProperty(
    val hasLink: Boolean? = null,
    val hasTaskList: Boolean? = null,
    val hasCode: Boolean? = null,
    val hasIncompleteTasks: Boolean? = null
)

data class Location(
    val placeholder: String? = null, val latitude: Double? = null, val longitude: Double? = null
)

// --- Activity Models ---

data class ListActivitiesResponse(
    val activities: List<Activity>?, val nextPageToken: String?
)

data class Activity(
    val name: String? = null,
    val creator: String? = null,
    val type: String? = null,
    val level: String? = null,
    val createTime: String? = null,
    val payload: ActivityPayload? = null
)

data class ActivityPayload(
    val memoComment: ActivityMemoCommentPayload? = null
)

data class ActivityMemoCommentPayload(
    val memo: String? = null, val relatedMemo: String? = null
)

// --- Additional Memo Service Models ---

data class ListMemoAttachmentsResponse(
    val attachments: List<Attachment>?, val nextPageToken: String?
)

data class SetMemoAttachmentsRequest(
    val name: String, val attachments: List<Attachment>
)

data class ListMemoCommentsResponse(
    val memos: List<Memo>?, val nextPageToken: String?, val totalSize: Int?
)

data class ListMemoReactionsResponse(
    val reactions: List<Reaction>?, val nextPageToken: String?, val totalSize: Int?
)

data class UpsertMemoReactionRequest(
    val name: String, val reaction: Reaction
)

data class ListMemoRelationsResponse(
    val relations: List<MemoRelation>?, val nextPageToken: String?
)

data class SetMemoRelationsRequest(
    val name: String, val relations: List<MemoRelation>
)
