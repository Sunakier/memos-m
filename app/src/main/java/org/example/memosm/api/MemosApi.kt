package org.example.memosm.api

import okhttp3.MultipartBody
import org.example.memosm.model.*
import retrofit2.http.*

interface MemosApi {

    // --- ActivityService ---
    @GET("api/v1/activities")
    suspend fun listActivities(
        @Query("pageSize") pageSize: Int? = null, @Query("pageToken") pageToken: String? = null
    ): ListActivitiesResponse

    @GET("api/v1/activities/{activity}")
    suspend fun getActivity(@Path("activity") activity: String): Activity

    // --- AttachmentService ---
    @GET("api/v1/attachments")
    suspend fun listAttachments(
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("filter") filter: String? = null,
        @Query("orderBy") orderBy: String? = null
    ): ListAttachmentsResponse

    @POST("api/v1/attachments")
    suspend fun createAttachment(
        @Body attachment: Attachment, @Query("attachmentId") attachmentId: String? = null
    ): Attachment

    @GET("api/v1/attachments/{attachment}")
    suspend fun getAttachment(@Path("attachment") attachment: String): Attachment

    @DELETE("api/v1/attachments/{attachment}")
    suspend fun deleteAttachment(@Path("attachment") attachment: String)

    @PATCH("api/v1/attachments/{attachment}")
    suspend fun updateAttachment(
        @Path("attachment") attachment: String,
        @Body attachmentData: Attachment,
        @Query("updateMask") updateMask: String
    ): Attachment

    @Multipart
    @POST("api/v1/attachments:upload")
    suspend fun uploadAttachment(
        @Part file: MultipartBody.Part
    ): Attachment

    // --- AuthService ---
    @GET("api/v1/auth/me")
    suspend fun getCurrentUserAuth(): UserResponse

    @GET("api/v1/auth/sessions/current")
    suspend fun getCurrentSession(): CurrentSessionResponse

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): RefreshTokenResponse

    @POST("api/v1/auth/signin")
    suspend fun signIn(@Body request: SignInRequest): SignInResponse

    @POST("api/v1/auth/signout")
    suspend fun signOut()

    // --- IdentityProviderService ---
    @GET("api/v1/identity-providers")
    suspend fun listIdentityProviders(): ListIdentityProvidersResponse

    @POST("api/v1/identity-providers")
    suspend fun createIdentityProvider(
        @Body identityProvider: IdentityProvider,
        @Query("identityProviderId") identityProviderId: String? = null
    ): IdentityProvider

    @GET("api/v1/identity-providers/{identityProvider}")
    suspend fun getIdentityProvider(@Path("identityProvider") identityProvider: String): IdentityProvider

    @DELETE("api/v1/identity-providers/{identityProvider}")
    suspend fun deleteIdentityProvider(@Path("identityProvider") identityProvider: String)

    @PATCH("api/v1/identity-providers/{identityProvider}")
    suspend fun updateIdentityProvider(
        @Path("identityProvider") identityProvider: String,
        @Body identityProviderData: IdentityProvider,
        @Query("updateMask") updateMask: String
    ): IdentityProvider

    // --- InstanceService ---
    @GET("api/v1/instance/profile")
    suspend fun getInstanceProfile(): InstanceProfile

    @GET("api/v1/instance/{instance}/*")
    suspend fun getInstanceSetting(@Path("instance") instance: String): InstanceSetting

    @PATCH("api/v1/instance/{instance}/*")
    suspend fun updateInstanceSetting(
        @Path("instance") instance: String,
        @Body setting: InstanceSetting,
        @Query("updateMask") updateMask: String
    ): InstanceSetting

    // --- MemoService ---
    @GET("api/v1/memos")
    suspend fun listMemos(
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("state") state: String? = null,
        @Query("orderBy") orderBy: String? = null,
        @Query("filter") filter: String? = null,
        @Query("showDeleted") showDeleted: Boolean? = null
    ): ListMemosResponse

    @POST("api/v1/memos")
    suspend fun createMemo(
        @Body memo: Memo, @Query("memoId") memoId: String? = null
    ): Memo

    @GET("api/v1/memos/{memo}")
    suspend fun getMemo(@Path("memo") memo: String): Memo

    @DELETE("api/v1/memos/{memo}")
    suspend fun deleteMemo(
        @Path("memo") memo: String, @Query("force") force: Boolean? = null
    )

    @PATCH("api/v1/memos/{memo}")
    suspend fun updateMemo(
        @Path("memo") memo: String, @Body memoData: Memo, @Query("updateMask") updateMask: String
    ): Memo

    @GET("api/v1/memos/{memo}/attachments")
    suspend fun listMemoAttachments(
        @Path("memo") memo: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListMemoAttachmentsResponse

    @PATCH("api/v1/memos/{memo}/attachments")
    suspend fun setMemoAttachments(
        @Path("memo") memo: String, @Body request: SetMemoAttachmentsRequest
    )

    @GET("api/v1/memos/{memo}/comments")
    suspend fun listMemoComments(
        @Path("memo") memo: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("orderBy") orderBy: String? = null
    ): ListMemoCommentsResponse

    @POST("api/v1/memos/{memo}/comments")
    suspend fun createMemoComment(
        @Path("memo") memo: String,
        @Body comment: Memo,
        @Query("commentId") commentId: String? = null
    ): Memo

    @GET("api/v1/memos/{memo}/reactions")
    suspend fun listMemoReactions(
        @Path("memo") memo: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListMemoReactionsResponse

    @POST("api/v1/memos/{memo}/reactions")
    suspend fun upsertMemoReaction(
        @Path("memo") memo: String, @Body request: UpsertMemoReactionRequest
    ): Reaction

    @DELETE("api/v1/memos/{memo}/reactions/{reaction}")
    suspend fun deleteMemoReaction(
        @Path("memo") memo: String, @Path("reaction") reaction: String
    )

    @GET("api/v1/memos/{memo}/relations")
    suspend fun listMemoRelations(
        @Path("memo") memo: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListMemoRelationsResponse

    @PATCH("api/v1/memos/{memo}/relations")
    suspend fun setMemoRelations(
        @Path("memo") memo: String, @Body request: SetMemoRelationsRequest
    )

    // --- UserService ---
    @GET("api/v1/users")
    suspend fun listUsers(
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("filter") filter: String? = null,
        @Query("showDeleted") showDeleted: Boolean? = null
    ): ListUsersResponse

    @POST("api/v1/users")
    suspend fun createUser(
        @Body user: User,
        @Query("userId") userId: String? = null,
        @Query("validateOnly") validateOnly: Boolean? = null,
        @Query("requestId") requestId: String? = null
    ): User

    @GET("api/v1/users/{user}")
    suspend fun getUser(
        @Path("user") user: String, @Query("readMask") readMask: String? = null
    ): User

    @DELETE("api/v1/users/{user}")
    suspend fun deleteUser(
        @Path("user") user: String, @Query("force") force: Boolean? = null
    )

    @PATCH("api/v1/users/{user}")
    suspend fun updateUser(
        @Path("user") user: String,
        @Body userData: User,
        @Query("updateMask") updateMask: String,
        @Query("allowMissing") allowMissing: Boolean? = null
    ): User

    @GET("api/v1/users/{user}/notifications")
    suspend fun listUserNotifications(
        @Path("user") user: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("filter") filter: String? = null
    ): ListUserNotificationsResponse

    @DELETE("api/v1/users/{user}/notifications/{notification}")
    suspend fun deleteUserNotification(
        @Path("user") user: String, @Path("notification") notification: String
    )

    @PATCH("api/v1/users/{user}/notifications/{notification}")
    suspend fun updateUserNotification(
        @Path("user") user: String,
        @Path("notification") notification: String,
        @Body notificationData: UserNotification,
        @Query("updateMask") updateMask: String
    ): UserNotification

    @GET("api/v1/users/{user}/personalAccessTokens")
    suspend fun listPersonalAccessTokens(
        @Path("user") user: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListPersonalAccessTokensResponse

    @POST("api/v1/users/{user}/personalAccessTokens")
    suspend fun createPersonalAccessToken(
        @Path("user") user: String, @Body request: CreatePersonalAccessTokenRequest
    ): CreatePersonalAccessTokenResponse

    @DELETE("api/v1/users/{user}/personalAccessTokens/{personalAccessToken}")
    suspend fun deletePersonalAccessToken(
        @Path("user") user: String, @Path("personalAccessToken") personalAccessToken: String
    )

    @GET("api/v1/users/{user}/settings")
    suspend fun listUserSettings(
        @Path("user") user: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListUserSettingsResponse

    @GET("api/v1/users/{user}/settings/{setting}")
    suspend fun getUserSetting(
        @Path("user") user: String, @Path("setting") setting: String
    ): UserSetting

    @PATCH("api/v1/users/{user}/settings/{setting}")
    suspend fun updateUserSetting(
        @Path("user") user: String,
        @Path("setting") setting: String,
        @Body settingData: UserSetting,
        @Query("updateMask") updateMask: String
    ): UserSetting

    @GET("api/v1/users/{user}/shortcuts")
    suspend fun getShortcuts(@Path("user") user: String): ShortcutResponse

    @GET("api/v1/users/{user}/webhooks")
    suspend fun listUserWebhooks(@Path("user") user: String): ListUserWebhooksResponse

    @POST("api/v1/users/{user}/webhooks")
    suspend fun createUserWebhook(
        @Path("user") user: String, @Body webhook: UserWebhook
    ): UserWebhook

    @DELETE("api/v1/users/{user}/webhooks/{webhook}")
    suspend fun deleteUserWebhook(
        @Path("user") user: String, @Path("webhook") webhook: String
    )

    @PATCH("api/v1/users/{user}/webhooks/{webhook}")
    suspend fun updateUserWebhook(
        @Path("user") user: String,
        @Path("webhook") webhook: String,
        @Body webhookData: UserWebhook,
        @Query("updateMask") updateMask: String
    ): UserWebhook

    @GET("api/v1/users/{user}:getStats")
    suspend fun getUserStats(@Path("user") user: String): UserStats

    @GET("api/v1/users:stats")
    suspend fun listAllUserStats(): ListAllUserStatsResponse
}
