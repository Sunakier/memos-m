package org.example.memosm.api

import org.example.memosm.model.CurrentSessionResponse
import org.example.memosm.model.SignInRequest
import org.example.memosm.model.SignInRequestV0260
import org.example.memosm.model.SignInResponse

open class MemosApiV0260Impl(
    private val apiV0260: MemosApiV0260
) : MemosApiImpl(apiV0260) {

    override val constants = super.constants.copy(
        memoCreatorFilterStyle = MemoCreatorFilterStyle.LEGACY_ID,
        memoOrderByPinnedDesc = "pinned desc, display_time desc",
        memoOrderByNewest = "display_time desc",
        memoOrderByOldest = "display_time asc"
    )

    override suspend fun getCurrentSession(): CurrentSessionResponse {
        // v0.26.0+ uses auth/me instead of auth/sessions/current
        val userResponse = apiV0260.getCurrentUser()
        return CurrentSessionResponse(user = userResponse.user)
    }

    override suspend fun signIn(request: SignInRequest): SignInResponse {
        // v0.26.0+ uses different structure for signin
        val v0260Request = SignInRequestV0260(
            passwordCredentials = request.passwordCredentials
        )
        return apiV0260.signIn(v0260Request)
    }
}
