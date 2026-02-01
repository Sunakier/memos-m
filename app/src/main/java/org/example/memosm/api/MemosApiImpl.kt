package org.example.memosm.api

import org.example.memosm.model.*

open class MemosApiImpl(
    protected val api: MemosApiV0353
) : MemosApi {

    override suspend fun listActivities(pageSize: Int?, pageToken: String?): ListActivitiesResponse {
        return api.listActivities(pageSize, pageToken)
    }

    override suspend fun getActivity(activity: String): Activity {
        return api.getActivity(activity)
    }

    override suspend fun listAttachments(
        pageSize: Int?,
        pageToken: String?,
        filter: String?,
        orderBy: String?
    ): ListAttachmentsResponse {
        return api.listAttachments(pageSize, pageToken, filter, orderBy)
    }

    override suspend fun createAttachment(attachment: Attachment, attachmentId: String?): Attachment {
        return api.createAttachment(attachment, attachmentId)
    }

    override suspend fun getAttachment(attachment: String): Attachment {
        return api.getAttachment(attachment)
    }

    override suspend fun deleteAttachment(attachment: String) {
        return api.deleteAttachment(attachment)
    }

    override suspend fun updateAttachment(
        attachment: String,
        attachmentData: Attachment,
        updateMask: String
    ): Attachment {
        return api.updateAttachment(attachment, attachmentData, updateMask)
    }

    override suspend fun getCurrentSession(): CurrentSessionResponse {
        return api.getCurrentSession()
    }

    override suspend fun refreshToken(request: RefreshTokenRequest): RefreshTokenResponse {
        return api.refreshToken(request)
    }

    override suspend fun signIn(request: SignInRequest): SignInResponse {
        return api.signIn(request)
    }

    override suspend fun signOut() {
        return api.signOut()
    }

    override suspend fun listIdentityProviders(): ListIdentityProvidersResponse {
        return api.listIdentityProviders()
    }

    override suspend fun createIdentityProvider(
        identityProvider: IdentityProvider,
        identityProviderId: String?
    ): IdentityProvider {
        return api.createIdentityProvider(identityProvider, identityProviderId)
    }

    override suspend fun getIdentityProvider(identityProvider: String): IdentityProvider {
        return api.getIdentityProvider(identityProvider)
    }

    override suspend fun deleteIdentityProvider(identityProvider: String) {
        return api.deleteIdentityProvider(identityProvider)
    }

    override suspend fun updateIdentityProvider(
        identityProvider: String,
        identityProviderData: IdentityProvider,
        updateMask: String
    ): IdentityProvider {
        return api.updateIdentityProvider(identityProvider, identityProviderData, updateMask)
    }

    override suspend fun getInstanceProfile(): InstanceProfile {
        return api.getInstanceProfile()
    }

    override suspend fun getInstanceSetting(instance: String): InstanceSetting {
        return api.getInstanceSetting(instance)
    }

    override suspend fun updateInstanceSetting(
        instance: String,
        setting: InstanceSetting,
        updateMask: String
    ): InstanceSetting {
        return api.updateInstanceSetting(instance, setting, updateMask)
    }

    override suspend fun listMemos(
        pageSize: Int?,
        pageToken: String?,
        state: String?,
        orderBy: String?,
        filter: String?,
        showDeleted: Boolean?
    ): ListMemosResponse {
        return api.listMemos(pageSize, pageToken, state, orderBy, filter, showDeleted)
    }

    override suspend fun createMemo(memo: Memo, memoId: String?): Memo {
        return api.createMemo(memo, memoId)
    }

    override suspend fun getMemo(memo: String): Memo {
        return api.getMemo(memo)
    }

    override suspend fun deleteMemo(memo: String, force: Boolean?) {
        return api.deleteMemo(memo, force)
    }

    override suspend fun updateMemo(memo: String, memoData: Memo, updateMask: String): Memo {
        return api.updateMemo(memo, memoData, updateMask)
    }

    override suspend fun listMemoAttachments(
        memo: String,
        pageSize: Int?,
        pageToken: String?
    ): ListMemoAttachmentsResponse {
        return api.listMemoAttachments(memo, pageSize, pageToken)
    }

    override suspend fun setMemoAttachments(memo: String, request: SetMemoAttachmentsRequest) {
        return api.setMemoAttachments(memo, request)
    }

    override suspend fun listMemoComments(
        memo: String,
        pageSize: Int?,
        pageToken: String?,
        orderBy: String?
    ): ListMemoCommentsResponse {
        return api.listMemoComments(memo, pageSize, pageToken, orderBy)
    }

    override suspend fun createMemoComment(memo: String, comment: Memo, commentId: String?): Memo {
        return api.createMemoComment(memo, comment, commentId)
    }

    override suspend fun listMemoReactions(
        memo: String,
        pageSize: Int?,
        pageToken: String?
    ): ListMemoReactionsResponse {
        return api.listMemoReactions(memo, pageSize, pageToken)
    }

    override suspend fun upsertMemoReaction(
        memo: String,
        request: UpsertMemoReactionRequest
    ): Reaction {
        return api.upsertMemoReaction(memo, request)
    }

    override suspend fun deleteMemoReaction(reaction: String) {
        return api.deleteMemoReaction(reaction)
    }

    override suspend fun listMemoRelations(
        memo: String,
        pageSize: Int?,
        pageToken: String?
    ): ListMemoRelationsResponse {
        return api.listMemoRelations(memo, pageSize, pageToken)
    }

    override suspend fun setMemoRelations(memo: String, request: SetMemoRelationsRequest) {
        return api.setMemoRelations(memo, request)
    }

    override suspend fun listUsers(
        pageSize: Int?,
        pageToken: String?,
        filter: String?,
        showDeleted: Boolean?
    ): ListUsersResponse {
        return api.listUsers(pageSize, pageToken, filter, showDeleted)
    }

    override suspend fun createUser(
        user: User,
        userId: String?,
        validateOnly: Boolean?,
        requestId: String?
    ): User {
        return api.createUser(user, userId, validateOnly, requestId)
    }

    override suspend fun getUser(user: String, readMask: String?): User {
        return api.getUser(user, readMask)
    }

    override suspend fun deleteUser(user: String, force: Boolean?) {
        return api.deleteUser(user, force)
    }

    override suspend fun updateUser(
        user: String,
        userData: User,
        updateMask: String,
        allowMissing: Boolean?
    ): User {
        return api.updateUser(user, userData, updateMask, allowMissing)
    }

    override suspend fun listUserNotifications(
        user: String,
        pageSize: Int?,
        pageToken: String?,
        filter: String?
    ): ListUserNotificationsResponse {
        return api.listUserNotifications(user, pageSize, pageToken, filter)
    }

    override suspend fun deleteUserNotification(user: String, notification: String) {
        return api.deleteUserNotification(user, notification)
    }

    override suspend fun updateUserNotification(
        user: String,
        notification: String,
        notificationData: UserNotification,
        updateMask: String
    ): UserNotification {
        return api.updateUserNotification(user, notification, notificationData, updateMask)
    }

    override suspend fun listPersonalAccessTokens(
        user: String,
        pageSize: Int?,
        pageToken: String?
    ): ListPersonalAccessTokensResponse {
        return api.listPersonalAccessTokens(user, pageSize, pageToken)
    }

    override suspend fun createPersonalAccessToken(
        user: String,
        request: CreatePersonalAccessTokenRequest
    ): CreatePersonalAccessTokenResponse {
        return api.createPersonalAccessToken(user, request)
    }

    override suspend fun deletePersonalAccessToken(user: String, personalAccessToken: String) {
        return api.deletePersonalAccessToken(user, personalAccessToken)
    }

    override suspend fun listUserSettings(
        user: String,
        pageSize: Int?,
        pageToken: String?
    ): ListUserSettingsResponse {
        return api.listUserSettings(user, pageSize, pageToken)
    }

    override suspend fun getUserSetting(user: String, setting: String): UserSetting {
        return api.getUserSetting(user, setting)
    }

    override suspend fun updateUserSetting(
        user: String,
        setting: String,
        settingData: UserSetting,
        updateMask: String
    ): UserSetting {
        return api.updateUserSetting(user, setting, settingData, updateMask)
    }

    override suspend fun getShortcuts(user: String): ShortcutResponse {
        return api.getShortcuts(user)
    }

    override suspend fun createShortcut(
        user: String,
        shortcut: Shortcut,
        validateOnly: Boolean?
    ): Shortcut {
        return api.createShortcut(user, shortcut, validateOnly)
    }

    override suspend fun deleteShortcut(user: String, shortcut: String) {
        return api.deleteShortcut(user, shortcut)
    }

    override suspend fun updateShortcut(
        user: String,
        shortcut: String,
        shortcutData: Shortcut,
        updateMask: String?
    ): Shortcut {
        return api.updateShortcut(user, shortcut, shortcutData, updateMask)
    }

    override suspend fun listUserWebhooks(user: String): ListUserWebhooksResponse {
        return api.listUserWebhooks(user)
    }

    override suspend fun createUserWebhook(user: String, webhook: UserWebhook): UserWebhook {
        return api.createUserWebhook(user, webhook)
    }

    override suspend fun deleteUserWebhook(user: String, webhook: String) {
        return api.deleteUserWebhook(user, webhook)
    }

    override suspend fun updateUserWebhook(
        user: String,
        webhook: String,
        webhookData: UserWebhook,
        updateMask: String
    ): UserWebhook {
        return api.updateUserWebhook(user, webhook, webhookData, updateMask)
    }

    override suspend fun getUserStats(user: String): UserStats {
        return api.getUserStats(user)
    }

    override suspend fun listAllUserStats(): ListAllUserStatsResponse {
        return api.listAllUserStats()
    }
}
