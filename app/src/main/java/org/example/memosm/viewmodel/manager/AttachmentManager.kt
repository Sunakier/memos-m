package org.example.memosm.viewmodel.manager

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.example.memosm.api.MemosApiV0353
import org.example.memosm.model.Attachment
import java.io.File
import java.io.FileOutputStream

private const val ATTACHMENT_PAGE_SIZE = 20

class AttachmentManager(
    private val scope: CoroutineScope,
    private val api: MemosApiV0353,
    initialCellWidth: Float = 120f
) : BaseListManager<Attachment>(scope) {

    private val _cellWidth = MutableStateFlow(initialCellWidth)
    val cellWidth = _cellWidth.asStateFlow()

    fun updateCellWidth(width: Float) {
        _cellWidth.value = width
    }

    override suspend fun fetchFromApi(pageToken: String?): Pair<List<Attachment>, String?> {
        val response = api.listAttachments(pageSize = ATTACHMENT_PAGE_SIZE, pageToken = pageToken)
        return Pair(response.attachments ?: emptyList(), response.nextPageToken)
    }
    
    suspend fun uploadAttachment(uri: Uri, context: Context): Attachment? {
        try {
             val rawFilesDir = File(context.cacheDir, "raw_files")
            if (!rawFilesDir.exists()) rawFilesDir.mkdirs()

            val fileName = getFileName(uri, context) ?: "unknown_file"
            // Simple sanitization
            val safeFileName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val file = File(rawFilesDir, safeFileName)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            
            val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", fileName, requestFile)
            
            val attachment = api.uploadAttachment(body)
            
            // Prepend to list locally
            updateState { state ->
                state.copy(items = listOf(attachment) + state.items)
            }
            
            return attachment
        } catch (e: Exception) {
            Log.e("AttachmentManager", "Upload failed", e)
            return null
        }
    }
    
    private fun getFileName(uri: Uri, context: Context): String? {
        // ... (implementation same as before)
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) name = it.getString(index)
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                name = name.substring(cut + 1)
            }
        }
        return name
    }

    companion object {
        fun resolveResourceUrl(hostUrl: String, relativeUrl: String?): String? {
            if (relativeUrl.isNullOrBlank()) return null
            if (relativeUrl.startsWith("http")) return relativeUrl
            
            val cleanHost = hostUrl.trimEnd('/')
            val cleanRelative = relativeUrl.trimStart('/')
            
            val result = "$cleanHost/$cleanRelative"
            android.util.Log.d("MemosDebug", "AttachmentManager.resolve: host=$hostUrl, relative=$relativeUrl -> $result")
            return result
        }

        fun getAttachmentUrl(hostUrl: String, attachment: Attachment?): String? {
            if (attachment == null) return null
            
            val url = if (!attachment.externalLink.isNullOrBlank()) {
                resolveResourceUrl(hostUrl, attachment.externalLink)
            } else if (!attachment.name.isNullOrBlank()) {
                resolveResourceUrl(hostUrl, "file/${attachment.name}/${attachment.filename}")
            } else {
                null
            }
            android.util.Log.d("MemosDebug", "AttachmentManager.getUrl: name=${attachment.name}, ext=${attachment.externalLink} -> $url")
            return url
        }
    }
}
