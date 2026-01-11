package org.example.memosm.model

import com.google.gson.annotations.SerializedName

data class ListMemosResponse(
    @SerializedName("memos") val memos: List<Memo>?,
    @SerializedName("nextPageToken") val nextPageToken: String?
)

data class ListAttachmentsResponse(
    @SerializedName("attachments") val attachments: List<Attachment>?,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("totalSize") val totalSize: Int?
)

data class Memo(
    @SerializedName("name") val name: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("creator") val creator: String? = null,
    @SerializedName("createTime") val createTime: String? = null,
    @SerializedName("updateTime") val updateTime: String? = null,
    @SerializedName("displayTime") val displayTime: String? = null,
    @SerializedName("content") val content: String,
    @SerializedName("visibility") val visibility: String,
    @SerializedName("tags") val tags: List<String>? = null,
    @SerializedName("pinned") val pinned: Boolean? = null,
    @SerializedName("attachments") val attachments: List<Attachment>? = null,
    @SerializedName("relations") val relations: List<MemoRelation>? = null,
    @SerializedName("reactions") val reactions: List<Reaction>? = null,
    @SerializedName("property") val property: MemoProperty? = null,
    @SerializedName("parent") val parent: String? = null,
    @SerializedName("snippet") val snippet: String? = null,
    @SerializedName("location") val location: Location? = null
)

data class Attachment(
    @SerializedName("name") val name: String? = null,
    @SerializedName("createTime") val createTime: String? = null,
    @SerializedName("filename") val filename: String,
    @SerializedName("content") val content: String? = null,
    @SerializedName("externalLink") val externalLink: String? = null,
    @SerializedName("type") val type: String,
    @SerializedName("mimeType") val mimeType: String? = null,
    @SerializedName("size") val size: String? = null,
    @SerializedName("memo") val memo: String? = null
) {
    val displayType: String
        get() = mimeType ?: type
}

data class MemoRelation(
    @SerializedName("memo") val memo: MemoSnippet,
    @SerializedName("relatedMemo") val relatedMemo: MemoSnippet,
    @SerializedName("type") val type: String
)

data class MemoSnippet(
    @SerializedName("name") val name: String,
    @SerializedName("snippet") val snippet: String? = null
)

data class Reaction(
    @SerializedName("name") val name: String? = null,
    @SerializedName("creator") val creator: String? = null,
    @SerializedName("contentId") val contentId: String,
    @SerializedName("reactionType") val reactionType: String,
    @SerializedName("createTime") val createTime: String? = null
)

data class MemoProperty(
    @SerializedName("hasLink") val hasLink: Boolean? = null,
    @SerializedName("hasTaskList") val hasTaskList: Boolean? = null,
    @SerializedName("hasCode") val hasCode: Boolean? = null,
    @SerializedName("hasIncompleteTasks") val hasIncompleteTasks: Boolean? = null
)

data class Location(
    @SerializedName("placeholder") val placeholder: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null
)

// --- Activity Models ---

data class ListActivitiesResponse(
    @SerializedName("activities") val activities: List<Activity>?,
    @SerializedName("nextPageToken") val nextPageToken: String?
)

data class Activity(
    @SerializedName("name") val name: String? = null,
    @SerializedName("creator") val creator: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("level") val level: String? = null,
    @SerializedName("createTime") val createTime: String? = null,
    @SerializedName("payload") val payload: ActivityPayload? = null
)

data class ActivityPayload(
    @SerializedName("memoComment") val memoComment: ActivityMemoCommentPayload? = null
)

data class ActivityMemoCommentPayload(
    @SerializedName("memo") val memo: String? = null,
    @SerializedName("relatedMemo") val relatedMemo: String? = null
)

// --- Additional Memo Service Models ---

data class ListMemoAttachmentsResponse(
    @SerializedName("attachments") val attachments: List<Attachment>?,
    @SerializedName("nextPageToken") val nextPageToken: String?
)

data class SetMemoAttachmentsRequest(
    @SerializedName("name") val name: String,
    @SerializedName("attachments") val attachments: List<Attachment>
)

data class ListMemoCommentsResponse(
    @SerializedName("memos") val memos: List<Memo>?,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("totalSize") val totalSize: Int?
)

data class ListMemoReactionsResponse(
    @SerializedName("reactions") val reactions: List<Reaction>?,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("totalSize") val totalSize: Int?
)

data class UpsertMemoReactionRequest(
    @SerializedName("name") val name: String,
    @SerializedName("reaction") val reaction: Reaction
)

data class ListMemoRelationsResponse(
    @SerializedName("relations") val relations: List<MemoRelation>?,
    @SerializedName("nextPageToken") val nextPageToken: String?
)

data class SetMemoRelationsRequest(
    @SerializedName("name") val name: String,
    @SerializedName("relations") val relations: List<MemoRelation>
)
