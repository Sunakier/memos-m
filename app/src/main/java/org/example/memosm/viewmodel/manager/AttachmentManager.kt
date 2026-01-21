package org.example.memosm.viewmodel.manager

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import android.webkit.MimeTypeMap
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
        return withContext(Dispatchers.IO) {
            android.util.Log.d("AttachmentManager", "fetchFromApi: pageToken=$pageToken")
            val response = api.listAttachments(pageSize = ATTACHMENT_PAGE_SIZE, pageToken = pageToken)
            android.util.Log.d("AttachmentManager", "fetchFromApi: got ${response.attachments?.size ?: 0} attachments, nextToken=${response.nextPageToken}")
            Pair(response.attachments ?: emptyList(), response.nextPageToken)
        }
    }
    
    suspend fun uploadAttachment(uri: Uri, context: Context): Attachment? {
        try {
            android.util.Log.d("AttachmentManager", "uploadAttachment: starting upload for uri=$uri")
            
            val contentResolver = context.contentResolver
            val resolverMimeType = contentResolver.getType(uri)
            val mimeType = if (resolverMimeType == null || resolverMimeType == "application/octet-stream") {
                 val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase()
                 MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: resolverMimeType ?: "application/octet-stream"
            } else {
                resolverMimeType
            }
            val fileName = getFileName(uri, context) ?: "unknown_file"
            
            val base64Content = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                }
            } ?: return null

            val attachmentToCreate = Attachment(
                filename = fileName,
                type = mimeType,
                content = base64Content
            )
            
            android.util.Log.d("AttachmentManager", "uploadAttachment: sending createAttachment request for $fileName")
            val attachment = api.createAttachment(attachmentToCreate)
            android.util.Log.d("AttachmentManager", "uploadAttachment: success, id=${attachment.name}")
            
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
