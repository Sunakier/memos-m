package org.example.memosm.api

import org.example.memosm.model.*

interface MemosApi {

    // --- ActivityService ---
    suspend fun listActivities(
        pageSize: Int? = null, pageToken: String? = null
    ): ListActivitiesResponse

    suspend fun getActivity(activity: String): Activity

    // --- AttachmentService ---
    suspend fun listAttachments(
        pageSize: Int? = null,
        pageToken: String? = null,
        filter: String? = null,
        orderBy: String? = null
    ): ListAttachmentsResponse

    suspend fun createAttachment(
        attachment: Attachment, attachmentId: String? = null
    ): Attachment

    suspend fun getAttachment(attachment: String): Attachment

    suspend fun deleteAttachment(attachment: String)

    suspend fun updateAttachment(
        attachment: String,
        attachmentData: Attachment,
        updateMask: String
    ): Attachment

    // --- AuthService ---
    suspend fun getCurrentSession(): CurrentSessionResponse

    suspend fun refreshToken(request: RefreshTokenRequest): RefreshTokenResponse

    suspend fun signIn(request: SignInRequest): SignInResponse

    suspend fun signOut()

    // --- IdentityProviderService ---
    suspend fun listIdentityProviders(): ListIdentityProvidersResponse

    suspend fun createIdentityProvider(
        identityProvider: IdentityProvider,
        identityProviderId: String? = null
    ): IdentityProvider

    suspend fun getIdentityProvider(
        identityProvider: String
    ): IdentityProvider

    suspend fun deleteIdentityProvider(
        identityProvider: String
    )

    suspend fun updateIdentityProvider(
        identityProvider: String,
        identityProviderData: IdentityProvider,
        updateMask: String
    ): IdentityProvider

    // --- InstanceService ---
    suspend fun getInstanceProfile(): InstanceProfile

    suspend fun getInstanceSetting(instance: String): InstanceSetting

    suspend fun updateInstanceSetting(
        instance: String,
        setting: InstanceSetting,
        updateMask: String
    ): InstanceSetting

    // --- MemoService ---
    suspend fun listMemos(
        pageSize: Int? = null,
        pageToken: String? = null,
        state: String? = null,
        orderBy: String? = null,
        filter: String? = null,
        showDeleted: Boolean? = null
    ): ListMemosResponse

    suspend fun createMemo(
        memo: Memo, memoId: String? = null
    ): Memo

    suspend fun getMemo(memo: String): Memo

    suspend fun deleteMemo(
        memo: String, force: Boolean? = null
    )

    suspend fun updateMemo(
        memo: String,
        memoData: Memo,
        updateMask: String
    ): Memo

    suspend fun listMemoAttachments(
        memo: String,
        pageSize: Int? = null,
        pageToken: String? = null
    ): ListMemoAttachmentsResponse

    suspend fun setMemoAttachments(
        memo: String, request: SetMemoAttachmentsRequest
    )

    suspend fun listMemoComments(
        memo: String,
        pageSize: Int? = null,
        pageToken: String? = null,
        orderBy: String? = null
    ): ListMemoCommentsResponse

    suspend fun createMemoComment(
        memo: String,
        comment: Memo,
        commentId: String? = null
    ): Memo

    suspend fun listMemoReactions(
        memo: String,
        pageSize: Int? = null,
        pageToken: String? = null
    ): ListMemoReactionsResponse

    suspend fun upsertMemoReaction(
        memo: String, request: UpsertMemoReactionRequest
    ): Reaction

    suspend fun deleteMemoReaction(
        reaction: String
    )

    suspend fun listMemoRelations(
        memo: String,
        pageSize: Int? = null,
        pageToken: String? = null
    ): ListMemoRelationsResponse

    suspend fun setMemoRelations(
        memo: String, request: SetMemoRelationsRequest
    )

    // --- UserService ---
    suspend fun listUsers(
        pageSize: Int? = null,
        pageToken: String? = null,
        filter: String? = null,
        showDeleted: Boolean? = null
    ): ListUsersResponse

    suspend fun createUser(
        user: User,
        userId: String? = null,
        validateOnly: Boolean? = null,
        requestId: String? = null
    ): User

    suspend fun getUser(
        user: String, readMask: String? = null
    ): User

    suspend fun deleteUser(
        user: String, force: Boolean? = null
    )

    suspend fun updateUser(
        user: String,
        userData: User,
        updateMask: String,
        allowMissing: Boolean? = null
    ): User

    suspend fun listUserNotifications(
        user: String,
        pageSize: Int? = null,
        pageToken: String? = null,
        filter: String? = null
    ): ListUserNotificationsResponse

    suspend fun deleteUserNotification(
        user: String,
        notification: String
    )

    suspend fun updateUserNotification(
        user: String,
        notification: String,
        notificationData: UserNotification,
        updateMask: String
    ): UserNotification

    suspend fun listPersonalAccessTokens(
        user: String,
        pageSize: Int? = null,
        pageToken: String? = null
    ): ListPersonalAccessTokensResponse

    suspend fun createPersonalAccessToken(
        user: String, request: CreatePersonalAccessTokenRequest
    ): CreatePersonalAccessTokenResponse

    suspend fun deletePersonalAccessToken(
        user: String,
        personalAccessToken: String
    )

    suspend fun listUserSettings(
        user: String,
        pageSize: Int? = null,
        pageToken: String? = null
    ): ListUserSettingsResponse

    suspend fun getUserSetting(
        user: String, setting: String
    ): UserSetting

    suspend fun updateUserSetting(
        user: String,
        setting: String,
        settingData: UserSetting,
        updateMask: String
    ): UserSetting

    suspend fun getShortcuts(user: String): ShortcutResponse

    suspend fun createShortcut(
        user: String,
        shortcut: Shortcut,
        validateOnly: Boolean? = null
    ): Shortcut

    suspend fun deleteShortcut(
        user: String,
        shortcut: String
    )

    suspend fun updateShortcut(
        user: String,
        shortcut: String,
        shortcutData: Shortcut,
        updateMask: String? = null
    ): Shortcut

    suspend fun listUserWebhooks(
        user: String
    ): ListUserWebhooksResponse

    suspend fun createUserWebhook(
        user: String, webhook: UserWebhook
    ): UserWebhook

    suspend fun deleteUserWebhook(
        user: String, webhook: String
    )

    suspend fun updateUserWebhook(
        user: String,
        webhook: String,
        webhookData: UserWebhook,
        updateMask: String
    ): UserWebhook

    suspend fun getUserStats(user: String): UserStats

    suspend fun listAllUserStats(): ListAllUserStatsResponse
}
