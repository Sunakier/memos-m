package org.example.memosm.api

import okhttp3.MultipartBody
import org.example.memosm.model.*
import retrofit2.http.*

interface MemosApi {

    @GET("api/v1/memos")
    suspend fun listMemos(
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("state") state: String? = null,
        @Query("orderBy") orderBy: String? = null,
        @Query("filter") filter: String? = null,
        @Query("showDeleted") showDeleted: Boolean? = null,
    ): ListMemosResponse

    @POST("api/v1/memos")
    suspend fun createMemo(@Body memo: MemoRequest): Memo

    @GET("api/v1/auth/me")
    suspend fun getCurrentUserAuth(): UserResponse

    @GET("api/v1/users/{user}")
    suspend fun getUser(@Path("user") user: String): User

    @GET("api/v1/users/{user}:getStats")
    suspend fun getUserStats(@Path("user") user: String): UserStats

    @GET("api/v1/users/{user}/shortcuts")
    suspend fun getShortcuts(@Path("user") user: String): ShortcutResponse

    @GET("api/v1/instance/profile")
    suspend fun getInstanceProfile(): InstanceProfile

    @GET("api/v1/attachments")
    suspend fun listAttachments(
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("filter") filter: String? = null,
        @Query("orderBy") orderBy: String? = null,
    ): ListAttachmentsResponse

    @GET("api/v1/attachments/{attachment}")
    suspend fun getAttachment(@Path("attachment") attachment: String): Attachment

    @POST("api/v1/attachments")
    suspend fun createAttachment(@Body attachment: AttachmentRequest): Attachment

    @Multipart
    @POST("api/v1/attachments")
    suspend fun uploadAttachment(
        @Part file: MultipartBody.Part
    ): Attachment

    @PATCH("api/v1/attachments/{attachment}")
    suspend fun updateAttachment(
        @Path("attachment") attachment: String,
        @Body request: AttachmentRequest,
        @Query("updateMask") updateMask: String
    ): Attachment

    @DELETE("api/v1/attachments/{attachment}")
    suspend fun deleteAttachment(@Path("attachment") attachment: String)
}

data class MemoRequest(
    val content: String,
    val state: String = "NORMAL",
    val visibility: String = "PRIVATE",
    val attachments: List<Attachment>? = null
)

data class AttachmentRequest(
    val name: String? = null,
    val filename: String,
    val content: String? = null,
    val externalLink: String? = null,
    val type: String,
    val memo: String? = null
)
