package org.example.memosm.api

import memos.api.v1.SignInRequest
import memos.api.v1.SignInResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface MemosApi {
    @POST("memos.api.v1.AuthService/CreateSession")
    suspend fun createSession(@Body request: SignInRequest): SignInResponse

    @POST("api/v1/auth/signin")
    suspend fun signin(@Body request: SignInRequest): SignInResponse
}