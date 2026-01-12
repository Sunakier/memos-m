package org.example.memosm.model

import com.google.gson.annotations.SerializedName

data class UserResponse(
    @SerializedName("user") val user: User?
)

data class CurrentSessionResponse(
    @SerializedName("user") val user: User?
)

data class User(
    @SerializedName("name") val name: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("createTime") val createTime: String? = null,
    @SerializedName("updateTime") val updateTime: String? = null
)

data class UserStats(
    @SerializedName("name") val name: String? = null,
    @SerializedName("memoDisplayTimestamps") val memoDisplayTimestamps: List<String>? = null,
    @SerializedName("memoTypeStats") val memoTypeStats: MemoTypeStats? = null,
    @SerializedName("tagCount") val tagCount: Map<String, Int>? = null,
    @SerializedName("pinnedMemos") val pinnedMemos: List<String>? = null,
    @SerializedName("totalMemoCount") val totalMemoCount: Int? = null
)

data class MemoTypeStats(
    @SerializedName("linkCount") val linkCount: Int? = null,
    @SerializedName("codeCount") val codeCount: Int? = null,
    @SerializedName("todoCount") val todoCount: Int? = null,
    @SerializedName("undoCount") val undoCount: Int? = null
)

data class ShortcutResponse(
    @SerializedName("shortcuts") val shortcuts: List<Shortcut>? = null
)

data class Shortcut(
    @SerializedName("name") val name: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("filter") val filter: String? = null
)

data class InstanceProfile(
    @SerializedName("owner") val owner: String? = null,
    @SerializedName("version") val version: String? = null,
    @SerializedName("mode") val mode: String? = null,
    @SerializedName("instanceUrl") val instanceUrl: String? = null
)

// --- Auth Models ---

data class RefreshTokenRequest(
    @SerializedName("dummy") val dummy: String? = null // Usually empty
)

data class RefreshTokenResponse(
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("expiresAt") val expiresAt: String
)

data class SignInRequest(
    @SerializedName("passwordCredentials") val passwordCredentials: PasswordCredentials? = null,
    @SerializedName("ssoCredentials") val ssoCredentials: SSOCredentials? = null
)

data class PasswordCredentials(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class SSOCredentials(
    @SerializedName("idpId") val idpId: Int,
    @SerializedName("code") val code: String,
    @SerializedName("redirectUri") val redirectUri: String,
    @SerializedName("codeVerifier") val codeVerifier: String? = null
)

data class SignInResponse(
    @SerializedName("user") val user: User,
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("accessTokenExpiresAt") val accessTokenExpiresAt: String
)

data class GetCurrentUserResponse(
    @SerializedName("user") val user: User
)

// --- Identity Provider Models ---

data class ListIdentityProvidersResponse(
    @SerializedName("identityProviders") val identityProviders: List<IdentityProvider>?
)

data class IdentityProvider(
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: String,
    @SerializedName("title") val title: String,
    @SerializedName("identifierFilter") val identifierFilter: String? = null,
    @SerializedName("config") val config: IdentityProviderConfig
)

data class IdentityProviderConfig(
    @SerializedName("oauth2Config") val oauth2Config: OAuth2Config? = null
)

data class OAuth2Config(
    @SerializedName("clientId") val clientId: String,
    @SerializedName("clientSecret") val clientSecret: String,
    @SerializedName("authUrl") val authUrl: String,
    @SerializedName("tokenUrl") val tokenUrl: String,
    @SerializedName("userInfoUrl") val userInfoUrl: String,
    @SerializedName("scopes") val scopes: List<String>,
    @SerializedName("fieldMapping") val fieldMapping: FieldMapping
)

data class FieldMapping(
    @SerializedName("identifier") val identifier: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("email") val email: String,
    @SerializedName("avatarUrl") val avatarUrl: String
)

// --- Instance Models ---

data class InstanceSetting(
    @SerializedName("name") val name: String? = null,
    @SerializedName("generalSetting") val generalSetting: GeneralSetting? = null,
    @SerializedName("storageSetting") val storageSetting: StorageSetting? = null,
    @SerializedName("memoRelatedSetting") val memoRelatedSetting: MemoRelatedSetting? = null
)

data class GeneralSetting(
    @SerializedName("disallowUserRegistration") val disallowUserRegistration: Boolean? = null,
    @SerializedName("disallowPasswordAuth") val disallowPasswordAuth: Boolean? = null,
    @SerializedName("additionalScript") val additionalScript: String? = null,
    @SerializedName("additionalStyle") val additionalStyle: String? = null,
    @SerializedName("customProfile") val customProfile: CustomProfile? = null,
    @SerializedName("weekStartDayOffset") val weekStartDayOffset: Int? = null,
    @SerializedName("disallowChangeUsername") val disallowChangeUsername: Boolean? = null,
    @SerializedName("disallowChangeNickname") val disallowChangeNickname: Boolean? = null
)

data class CustomProfile(
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("logoUrl") val logoUrl: String? = null
)

data class MemoRelatedSetting(
    @SerializedName("disallowPublicVisibility") val disallowPublicVisibility: Boolean? = null,
    @SerializedName("displayWithUpdateTime") val displayWithUpdateTime: Boolean? = null,
    @SerializedName("contentLengthLimit") val contentLengthLimit: Int? = null,
    @SerializedName("enableDoubleClickEdit") val enableDoubleClickEdit: Boolean? = null,
    @SerializedName("reactions") val reactions: List<String>? = null
)

data class StorageSetting(
    @SerializedName("storageType") val storageType: String? = null,
    @SerializedName("filepathTemplate") val filepathTemplate: String? = null,
    @SerializedName("uploadSizeLimitMb") val uploadSizeLimitMb: String? = null,
    @SerializedName("s3Config") val s3Config: S3Config? = null
)

data class S3Config(
    @SerializedName("accessKeyId") val accessKeyId: String,
    @SerializedName("accessKeySecret") val accessKeySecret: String,
    @SerializedName("endpoint") val endpoint: String,
    @SerializedName("region") val region: String,
    @SerializedName("bucket") val bucket: String,
    @SerializedName("usePathStyle") val usePathStyle: Boolean
)

// --- User Expansion Models ---

data class ListUsersResponse(
    @SerializedName("users") val users: List<User>?,
    @SerializedName("nextPageToken") val nextPageToken: String? = null,
    @SerializedName("totalSize") val totalSize: Int? = null
)

data class ListUserNotificationsResponse(
    @SerializedName("notifications") val notifications: List<UserNotification>?,
    @SerializedName("nextPageToken") val nextPageToken: String?
)

data class UserNotification(
    @SerializedName("name") val name: String? = null,
    @SerializedName("sender") val sender: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("createTime") val createTime: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("activityId") val activityId: Int? = null
)

data class ListPersonalAccessTokensResponse(
    @SerializedName("personalAccessTokens") val personalAccessTokens: List<PersonalAccessToken>?,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("totalSize") val totalSize: Int?
)

data class PersonalAccessToken(
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("expiresAt") val expiresAt: String? = null,
    @SerializedName("lastUsedAt") val lastUsedAt: String? = null
)

data class CreatePersonalAccessTokenRequest(
    @SerializedName("parent") val parent: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("expiresInDays") val expiresInDays: Int? = null
)

data class CreatePersonalAccessTokenResponse(
    @SerializedName("personalAccessToken") val personalAccessToken: PersonalAccessToken,
    @SerializedName("token") val token: String
)

data class ListUserSettingsResponse(
    @SerializedName("settings") val settings: List<UserSetting>?,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("totalSize") val totalSize: Int?
)

data class UserSetting(
    @SerializedName("name") val name: String? = null,
    @SerializedName("general_setting", alternate = ["generalSetting"])
    val generalSetting: UserGeneralSetting? = null,
    @SerializedName("webhooks_setting", alternate = ["webhooksSetting"])
    val webhooksSetting: UserWebhooksSetting? = null
)

data class UserGeneralSetting(
    @SerializedName("locale") val locale: String? = null,
    @SerializedName("memo_visibility", alternate = ["memoVisibility"])
    val memoVisibility: String? = null,
    @SerializedName("theme") val theme: String? = null
)

data class UserWebhooksSetting(
    @SerializedName("webhooks") val webhooks: List<UserWebhook>? = null
)

data class ListUserWebhooksResponse(
    @SerializedName("webhooks") val webhooks: List<UserWebhook>?
)

data class UserWebhook(
    @SerializedName("name") val name: String? = null,
    @SerializedName("url") val url: String,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("createTime") val createTime: String? = null,
    @SerializedName("updateTime") val updateTime: String? = null
)

data class ListAllUserStatsResponse(
    @SerializedName("stats") val stats: List<UserStats>?
)
