package org.example.memosm.api

import android.util.Log
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.example.memosm.model.RefreshTokenResponse

class TokenAuthenticator(
    private val baseUrl: String,
    private val cookieJar: CookieJar,
    private val onTokenRefreshed: (String) -> Unit
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        Log.d("TokenAuthenticator", "Received 401 from ${response.request.url}, attempting refresh")

        // Avoid infinite loops if the refresh endpoint itself returns 401
        if (response.request.url.pathSegments.contains("auth") && 
            response.request.url.pathSegments.contains("refresh")) {
            Log.w("TokenAuthenticator", "Refresh token request failed with 401, aborting")
            return null
        }
        
        // Use a separate client for the refresh request to avoid interceptor recursion
        // but verify it shares the cookie jar
        val refreshClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .build()
            
        val refreshUrl = if (baseUrl.endsWith("/")) "${baseUrl}api/v1/auth/refresh" else "$baseUrl/api/v1/auth/refresh"
        
        // Empty body as per user spec
        val requestBody = "{}".toRequestBody("application/json".toMediaType())
        
        val refreshRequest = Request.Builder()
            .url(refreshUrl)
            .post(requestBody)
            .build()

        // Debug: check cookies for this URL
        val cookies = cookieJar.loadForRequest(refreshRequest.url)
        Log.d("TokenAuthenticator", "Attempting refresh at $refreshUrl with ${cookies.size} cookies")
            
        return try {
            val refreshResponse = refreshClient.newCall(refreshRequest).execute()
            Log.d("TokenAuthenticator", "Refresh response code: ${refreshResponse.code}")
            
            if (refreshResponse.isSuccessful) {
                val bodyString = refreshResponse.body.string()
                run {
                    val tokenResponse = Gson().fromJson(bodyString, RefreshTokenResponse::class.java)
                    val newToken = tokenResponse.accessToken

                    Log.i("TokenAuthenticator", "Token refresh successful")

                    // Notify the app to update storage and interceptor
                    onTokenRefreshed(newToken)

                    // Retry original request with new token
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                }
            } else {
                val errorBody = refreshResponse.body.string()
                Log.e("TokenAuthenticator", "Refresh failed with code ${refreshResponse.code}, body: $errorBody")
                null
            }
        } catch (e: Exception) {
            Log.e("TokenAuthenticator", "Exception during token refresh", e)
            null
        }
    }
}
