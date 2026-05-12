package org.example.memosm.api

import org.example.memosm.model.CurrentSessionResponse
import org.example.memosm.model.ListUsersResponse
import org.example.memosm.model.SignInRequest
import org.example.memosm.model.SignInRequestV0260
import org.example.memosm.model.SignInResponse
import org.example.memosm.model.User
import org.example.memosm.model.toUserSnapshot

class MemosApiV0280Impl(
    private val apiV0280: MemosApiV0280
) : MemosApiV0270Impl(apiV0280) {
    override val constants = super.constants.copy(
        memoOrderByPinnedDesc = "pinned desc, create_time desc",
        memoOrderByNewest = "create_time desc",
        memoOrderByOldest = "create_time asc"
    )

    override suspend fun getCurrentSession(): CurrentSessionResponse {
        return apiV0280.getCurrentUserV0280().toModel()
    }

    override suspend fun signIn(request: SignInRequest): SignInResponse {
        val v0260Request = SignInRequestV0260(
            passwordCredentials = request.passwordCredentials
        )
        return apiV0280.signInV0280(v0260Request).toModel()
    }

    override suspend fun listUsers(
        pageSize: Int?,
        pageToken: String?,
        filter: String?,
        showDeleted: Boolean?
    ): ListUsersResponse {
        return apiV0280.listUsersV0280(pageSize, pageToken, filter, showDeleted).toModel()
    }

    override suspend fun createUser(
        user: User,
        userId: String?,
        validateOnly: Boolean?,
        requestId: String?
    ): User {
        return apiV0280.createUserV0280(user.toUserSnapshot(), userId, validateOnly, requestId)
    }

    override suspend fun getUser(user: String, readMask: String?): User {
        return apiV0280.getUserV0280(user, readMask)
    }

    override suspend fun updateUser(
        user: String,
        userData: User,
        updateMask: String,
        allowMissing: Boolean?
    ): User {
        return apiV0280.updateUserV0280(user, userData.toUserSnapshot(), updateMask, allowMissing)
    }
}
