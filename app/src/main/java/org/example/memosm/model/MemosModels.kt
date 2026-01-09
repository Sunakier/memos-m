package org.example.memosm.model

import com.google.gson.annotations.SerializedName

data class ListMemosResponse(
    val memos: List<Memo>?,
    val nextPageToken: String?
)

data class ListAttachmentsResponse(
    val attachments: List<Attachment>?,
    val nextPageToken: String?,
    val totalSize: Long?
)

data class Memo(
    val name: String,
    val state: String,
    val creator: String,
    val createTime: String,
    val updateTime: String,
    val displayTime: String,
    val content: String,
    val visibility: String,
    val tags: List<String>,
    val pinned: Boolean,
    val attachments: List<Attachment>?,
    val relations: List<MemoRelation>?,
    val reactions: List<Reaction>?,
    val property: MemoProperty?,
    val parent: String?,
    val snippet: String?,
    val location: Location?
)

data class Attachment(
    val name: String,
    val createTime: String,
    val filename: String,
    val content: String?,
    val externalLink: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("mimeType") val mimeType: String?,
    val size: String?,
    val memo: String?
) {
    val displayType: String
        get() = mimeType ?: type ?: ""
}

data class MemoRelation(
    val memo: MemoSnippet,
    val relatedMemo: MemoSnippet,
    val type: String
)

data class MemoSnippet(
    val name: String,
    val snippet: String
)

data class Reaction(
    val name: String,
    val creator: String,
    val contentId: String,
    val reactionType: String,
    val createTime: String
)

data class MemoProperty(
    val hasLink: Boolean,
    val hasTaskList: Boolean,
    val hasCode: Boolean,
    val hasIncompleteTasks: Boolean
)

data class Location(
    val placeholder: String,
    val latitude: Double,
    val longitude: Double
)
