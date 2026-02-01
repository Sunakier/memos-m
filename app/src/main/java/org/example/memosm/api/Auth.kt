package org.example.memosm.api

import android.util.Log
import org.example.memosm.model.CreatePersonalAccessTokenRequest
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.example.memosm.model.SignInRequest

/**
 * Authenticates a user and creates an access token for the app.
 * Uses a CookieJar to persist session cookies between CreateSession and CreateUserAccessToken calls.
 *
 * @param baseUrl The base URL of the Memos instance.
 * @param username The username provided by the user.
 * @param password The password provided by the user.
 * @return The access token string.
 * @throws Exception if the login fails or a network error occurs.
 */
suspend fun loginAndCreateToken(
    api: MemosApi, baseUrl: String, username: String, password: String
): String {

    val logging = HttpLoggingInterceptor { message ->
        Log.d("MemosApi", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }



    try {
        val logInRes = api.signIn(SignInRequest(username = username.trim(), password = password))

        Log.d("MemosLogin", "Login successful! Response: $logInRes")

// Verify token validity by fetching current user
        val authClient = OkHttpClient.Builder().addInterceptor(logging).addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer ${logInRes.accessToken}").build()
            chain.proceed(request)
        }.build()

        val authApi = MemosApiFactory.create(baseUrl, authClient)

        val userRes = authApi.getCurrentSession()

        Log.d("MemosLogin", "Current user: ${userRes.user}")

        // Token name generated with the current time
        val tokenName = "MemosM" + System.currentTimeMillis()

        val userId = userRes.user!!.name!!

        val accessTokenRes = authApi.createPersonalAccessToken(
            userId, CreatePersonalAccessTokenRequest(
                userId, tokenName, 0
            )
        )
        Log.d("MemosLogin", "Access token Res: $accessTokenRes")
        return accessTokenRes.token
    } catch (e: Exception) {
        throw Exception("Failed to create personal access token: ${e.message}", e)
    }
}