package org.example.memosm.api

import org.example.memosm.model.CurrentSessionResponse
import org.example.memosm.model.ListUsersResponse
import org.example.memosm.model.SignInRequestV0260
import org.example.memosm.model.SignInResponse
import org.example.memosm.model.UseRole
import org.example.memosm.model.UseState
import org.example.memosm.model.User
import org.example.memosm.model.UserSnapshot
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MemosApiV0280 : MemosApiV0270 {
    @GET("api/v1/auth/me")
    suspend fun getCurrentUserV0280(): GetCurrentUserResponseDtoV0280

    @POST("api/v1/auth/signin")
    suspend fun signInV0280(@Body request: SignInRequestV0260): SignInResponseDtoV0280

    @GET("api/v1/users")
    suspend fun listUsersV0280(
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("filter") filter: String? = null,
        @Query("showDeleted") showDeleted: Boolean? = null
    ): ListUsersResponseV0280

    @POST("api/v1/users")
    suspend fun createUserV0280(
        @Body user: UserSnapshot,
        @Query("userId") userId: String? = null,
        @Query("validateOnly") validateOnly: Boolean? = null,
        @Query("requestId") requestId: String? = null
    ): UserV0280

    @GET("api/v1/{user}")
    suspend fun getUserV0280(
        @Path("user", encoded = true) user: String,
        @Query("readMask") readMask: String? = null
    ): UserV0280

    @PATCH("api/v1/{user}")
    suspend fun updateUserV0280(
        @Path("user", encoded = true) user: String,
        @Body userData: UserSnapshot,
        @Query("updateMask") updateMask: String,
        @Query("allowMissing") allowMissing: Boolean? = null
    ): UserV0280
}

data class UserV0280(
    override val name: String? = null,
    override val role: UseRole? = null,
    override val username: String? = null,
    override val email: String? = null,
    override val displayName: String? = null,
    override val avatarUrl: String? = null,
    override val description: String? = null,
    override val password: String? = null,
    override val state: UseState? = null,
    override val createTime: String? = null,
    override val updateTime: String? = null,
    override val token: String? = null
) : User

data class GetCurrentUserResponseDtoV0280(val user: UserV0280) {
    fun toModel(): CurrentSessionResponse = CurrentSessionResponse(user = user)
}

data class SignInResponseDtoV0280(
    val user: UserV0280,
    val accessToken: String,
    val accessTokenExpiresAt: String
) {
    fun toModel(): SignInResponse = SignInResponse(
        user = user,
        accessToken = accessToken,
        accessTokenExpiresAt = accessTokenExpiresAt
    )
}

data class ListUsersResponseV0280(
    val users: List<UserV0280>?,
    val nextPageToken: String? = null,
    val totalSize: Int? = null
) {
    fun toModel(): ListUsersResponse = ListUsersResponse(
        users = users,
        nextPageToken = nextPageToken,
        totalSize = totalSize
    )
}
