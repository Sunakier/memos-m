package org.example.memosm.api

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.example.memosm.data.StreamingBase64
import org.example.memosm.model.Attachment
import java.io.File
import java.io.InputStream

/**
 * Specialized API client for streaming attachment uploads.
 * Uses OkHttp directly (bypassing Retrofit) to stream large files
 * without loading the entire content into memory.
 */
class StreamingAttachmentApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val apiPath: String = "api/v1"
) {
    companion object {
        private const val TAG = "StreamingAttachmentApi"
    }

    private val gson = GsonProvider.gson

    suspend fun createAttachmentStreaming(
        filename: String,
        mimeType: String,
        contentUri: Uri,
        context: Context
    ): Attachment? = upload(filename, mimeType) {
        requireNotNull(context.contentResolver.openInputStream(contentUri))
    }

    suspend fun createAttachmentFromFile(
        file: File,
        mimeType: String,
        attachmentId: String? = null
    ): Attachment? = upload(file.name, mimeType, attachmentId) { file.inputStream() }

    private suspend fun upload(
        filename: String,
        mimeType: String,
        attachmentId: String? = null,
        openStream: () -> InputStream
    ): Attachment? = withContext(Dispatchers.IO) {
        try {
            val serverUrl = requireNotNull(normalizeLoginServerUrl(baseUrl))
            val url = "$serverUrl$apiPath/attachments".toHttpUrl().newBuilder()
                .apply { attachmentId?.let { addQueryParameter("attachmentId", it) } }
                .build()
            val request = Request.Builder().url(url)
                .post(StreamingAttachmentRequestBody(filename, mimeType, openStream)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Upload failed with code ${response.code}: ${response.message}")
                    return@withContext null
                }
                gson.fromJson(response.body.string(), Attachment::class.java)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Streaming upload failed", e)
            null
        }
    }

    /** Streams the JSON content field without retaining the file bytes in memory. */
    private class StreamingAttachmentRequestBody(
        private val filename: String,
        private val mimeType: String,
        private val openStream: () -> InputStream
    ) : RequestBody() {
        override fun contentType() = "application/json".toMediaType()

        override fun writeTo(sink: BufferedSink) {
            sink.writeUtf8("{\"filename\":")
            sink.writeUtf8(GsonProvider.gson.toJson(filename))
            sink.writeUtf8(",\"type\":")
            sink.writeUtf8(GsonProvider.gson.toJson(mimeType))
            sink.writeUtf8(",\"content\":\"")
            openStream().use { StreamingBase64.encodeToStream(it, sink.outputStream()) }
            sink.writeUtf8("\"}")
        }
    }
}
