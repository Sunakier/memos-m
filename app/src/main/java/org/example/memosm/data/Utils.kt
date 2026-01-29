package org.example.memosm.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.memosm.model.Attachment
import java.io.File


/**
 * Converts a local Uri to an Attachment with base64-encoded content for draft caching.
 * Returns null if the file cannot be read.
 */
suspend fun uriToBase64Attachment(uri: Uri, context: Context): Attachment? =
    withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver

            // Get filename
            val fileName = run {
                var name: String? = null
                if (uri.scheme == "content") {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (index != -1) name = cursor.getString(index)
                        }
                    }
                }
                name ?: uri.path?.substringAfterLast('/')
                ?: "attachment_${System.currentTimeMillis()}"
            }

            // Get MIME type
            val resolverMimeType = contentResolver.getType(uri)
            val mimeType =
                if (resolverMimeType == null || resolverMimeType == "application/octet-stream") {
                    val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase()
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: resolverMimeType
                    ?: "application/octet-stream"
                } else {
                    resolverMimeType
                }

            // Read and encode content
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
            val bytes = inputStream.use { it.readBytes() }
            val base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)

            Attachment(
                filename = fileName, type = mimeType, content = base64Content
            )
        } catch (e: Exception) {
            Log.e("MemoComposer", "Error converting Uri to base64 Attachment", e)
            null
        }
    }

/**
 * Converts a base64 string (from a draft Attachment) back to a temporary file Uri for uploading.
 */
suspend fun base64ToTempUri(
    base64: String, filename: String, mimeType: String, context: Context
): Uri? = withContext(Dispatchers.IO) {
    try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val file = File(context.cacheDir, "temp_upload_${System.currentTimeMillis()}_$filename")
        file.writeBytes(bytes)
        file.toUri()
    } catch (e: Exception) {
        Log.e("MemoComposer", "Error converting base64 to temp Uri", e)
        null
    }
}
