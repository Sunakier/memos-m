package org.example.memosm.model

import android.net.Uri

/**
 * Holds data extracted from a share intent (ACTION_SEND or ACTION_SEND_MULTIPLE).
 *
 * @param text The shared text content (from EXTRA_TEXT or EXTRA_SUBJECT)
 * @param uris The list of shared file/image URIs
 */
data class ShareIntentData(
    val text: String? = null,
    val uris: List<Uri> = emptyList()
) {
    val isEmpty: Boolean
        get() = text.isNullOrBlank() && uris.isEmpty()
}
