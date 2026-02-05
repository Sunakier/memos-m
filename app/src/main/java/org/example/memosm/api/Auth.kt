package org.example.memosm.api

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.example.memosm.model.CreatePersonalAccessTokenRequest
import org.example.memosm.model.PasswordCredentials
import org.example.memosm.model.SignInRequest

const val TAG = "MemosLogin"

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
        Log.d(TAG, message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    try {
        val logInRes = api.signIn(
            SignInRequest(
                passwordCredentials = PasswordCredentials(
                    username = username.trim(), password = password
                )
            )
        )

        Log.d(TAG, "Login successful! Response: $logInRes")

// Verify token validity by fetching current user
        val authClient = OkHttpClient.Builder().addInterceptor(logging).addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer ${logInRes.accessToken}").build()
            chain.proceed(request)
        }.build()

        val authApi = MemosApiFactory.create(baseUrl, authClient)

        val userRes = authApi.getCurrentSession()

        Log.d(TAG, "Current user: ${userRes.user}")

        // Token name generated with the current time
        val tokenName = "MemosM" + System.currentTimeMillis()

        val userId = userRes.user!!.name!!

        val accessTokenRes = authApi.createPersonalAccessToken(
            userId, CreatePersonalAccessTokenRequest(
                parent = userId, description = tokenName
            )
        )
        Log.d(TAG, "Access token Res: $accessTokenRes")
        return accessTokenRes.token
    } catch (e: retrofit2.HttpException) {
        val errorBody = e.response()?.errorBody()?.string()
        Log.e(TAG, "Login failed: $errorBody", e)
        throw Exception("Failed to login: $errorBody", e)
    } catch (e: Exception) {
        throw Exception("Failed to create personal access token: ${e.message}", e)
    }
}