package org.example.memosm.model

import java.util.UUID

/**
 * Represents a local draft memo that hasn't been published yet.
 * Stored in the app's cache directory as JSON files, keyed by account ID.
 */
data class Draft(
    val id: String = UUID.randomUUID().toString(),
    val content: String = "",
    val visibility: String = "PRIVATE",
    val attachments: List<Attachment> = emptyList(),
    val location: Location? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Returns true if the draft has meaningful content worth saving.
     */
    fun hasContent(): Boolean = content.isNotBlank() || attachments.isNotEmpty() || location != null
}
