package org.example.memosm.api

import org.example.memosm.model.CurrentSessionResponse
import org.example.memosm.model.SignInRequest
import org.example.memosm.model.SignInResponse
import org.example.memosm.model.SignInRequestV0260
import org.example.memosm.model.PasswordCredentials

class MemosApiV0260Impl(
    private val apiV0260: MemosApiV0260
) : MemosApiImpl(apiV0260) {

    override suspend fun getCurrentSession(): CurrentSessionResponse {
        // v0.26.0+ uses auth/me instead of auth/sessions/current
        val userResponse = apiV0260.getCurrentUser()
        return CurrentSessionResponse(user = userResponse.user)
    }

    override suspend fun signIn(request: SignInRequest): SignInResponse {
        // v0.26.0+ uses different structure for signin
        val v0260Request = SignInRequestV0260(
            passwordCredentials = PasswordCredentials(
                username = request.username,
                password = request.password
            )
        )
        return apiV0260.signIn(v0260Request)
    }
}
