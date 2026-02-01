package org.example.memosm.api

import org.example.memosm.model.GetCurrentUserResponse
import org.example.memosm.model.SignInRequestV0260
import retrofit2.http.GET

interface MemosApiV0260 : MemosApiV0353 {

    // Replaces getCurrentSession
    // https://demo.usememos.com/api/v1/auth/me
    @GET("api/v1/auth/me")
    suspend fun getCurrentUser(): GetCurrentUserResponse

    @retrofit2.http.POST("api/v1/auth/signin")
    suspend fun signIn(@retrofit2.http.Body request: SignInRequestV0260): org.example.memosm.model.SignInResponse
}
