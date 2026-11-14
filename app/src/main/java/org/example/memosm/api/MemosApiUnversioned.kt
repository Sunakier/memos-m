package org.example.memosm.api

import org.example.memosm.model.ListActivitiesResponse
import org.example.memosm.model.ListAllUserStatsResponse
import org.example.memosm.model.PasswordCredentials
import org.example.memosm.model.Reaction
import org.example.memosm.model.SignInRequest
import org.example.memosm.model.SignInResponse
import org.example.memosm.model.UpsertMemoReactionRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** REST schema introduced when Memos removed the /api/v1 version marker. */
interface MemosApiUnversioned : MemosApiV0300 {
    @GET("users/-/stats")
    override suspend fun listAllUserStats(): ListAllUserStatsResponse

    @POST("auth/signin")
    suspend fun signInUnversioned(@Body request: SignInRequestUnversioned): SignInResponseDtoV0280

    @POST("{memo}/reactions")
    suspend fun upsertMemoReactionUnversioned(
        @Path("memo", encoded = true) memo: String,
        @Body request: UpsertMemoReactionRequestUnversioned
    ): Reaction
}

data class UpsertMemoReactionRequestUnversioned(
    val parent: String,
    val reaction: ReactionInputUnversioned
)

data class ReactionInputUnversioned(val reactionType: String)

data class SignInRequestUnversioned(
    val passwordCredentials: PasswordCredentials?,
    val ssoCredentials: SsoCredentialsUnversioned?
)

data class SsoCredentialsUnversioned(
    val identityProvider: String,
    val code: String,
    val redirectUri: String,
    val codeVerifier: String
)

class MemosApiUnversionedImpl(
    private val api: MemosApiUnversioned,
    private val delegate: MemosApi = MemosApiV0300Impl(api)
) : MemosApi by delegate {
    override val constants = delegate.constants.copy(apiPath = "api")

    // Memos 26.10 removed the activity service together with the /api/v1
    // marker. Answer with an empty page instead of a 404 so existing callers
    // keep working against servers without the service.
    override suspend fun listActivities(
        pageSize: Int?,
        pageToken: String?
    ): ListActivitiesResponse = ListActivitiesResponse(
        activities = emptyList(),
        nextPageToken = null
    )

    override suspend fun upsertMemoReaction(memo: String, request: UpsertMemoReactionRequest): Reaction =
        api.upsertMemoReactionUnversioned(
            memo,
            UpsertMemoReactionRequestUnversioned(memo, ReactionInputUnversioned(request.reaction.reactionType))
        )

    override suspend fun signIn(request: SignInRequest): SignInResponse =
        api.signInUnversioned(SignInRequestUnversioned(
            request.passwordCredentials,
            request.ssoCredentials?.let {
                SsoCredentialsUnversioned(it.idpName, it.code, it.redirectUri, it.codeVerifier)
            }
        )).toModel()
}
