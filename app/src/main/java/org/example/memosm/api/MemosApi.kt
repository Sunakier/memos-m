package org.example.memosm.api

import org.example.memosm.model.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

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
}

data class MemoRequest(
    val content: String, val state: String = "NORMAL", val visibility: String = "PRIVATE"
)
