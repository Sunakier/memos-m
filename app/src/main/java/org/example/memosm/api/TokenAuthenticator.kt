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
    private val onTokenRefreshed: (String) -> Unit,
    private val onSessionInvalidated: (() -> Unit)? = null
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        Log.d("TokenAuthenticator", "Received 401 from ${response.request.url}, attempting refresh")

        // Avoid infinite loops if the refresh endpoint itself returns 401
        if (response.request.url.pathSegments.contains("auth") &&
            response.request.url.pathSegments.contains("refresh")
        ) {
            Log.w("TokenAuthenticator", "Refresh token request failed with 401, aborting")
            // Notify that session is invalid and user needs to re-login
            onSessionInvalidated?.invoke()
            return null
        }

        // Also check if this is a refresh path in the segments
        if (response.request.url.encodedPath.contains("/auth/refresh")) {
            Log.w("TokenAuthenticator", "Refresh endpoint returned 401, session invalidated")
            onSessionInvalidated?.invoke()
            return null
        }

        // Use a separate client for the refresh request to avoid interceptor recursion
        // but verify it shares the cookie jar.
        // IMPORTANT: Add GrpcMetadataCookieInterceptor to copy Cookie -> Grpc-Metadata-Cookie
        // for the gRPC-Gateway to read it.
        // It must be a Network Interceptor to see the cookies added by the CookieJar (BridgeInterceptor)
        val refreshClient = OkHttpClient.Builder()
            .addNetworkInterceptor(GrpcMetadataCookieInterceptor())
            .addNetworkInterceptor(GrpcCookieInterceptor())
            .cookieJar(cookieJar)
            .build()

        val refreshUrl =
            if (baseUrl.endsWith("/")) "${baseUrl}api/v1/auth/refresh" else "$baseUrl/api/v1/auth/refresh"

        // Empty body as per user spec
        val requestBody = "{}".toRequestBody("application/json".toMediaType())

        val refreshRequest = Request.Builder()
            .url(refreshUrl)
            .post(requestBody)
            .build()

        return try {
            val refreshResponse = refreshClient.newCall(refreshRequest).execute()
            Log.d("TokenAuthenticator", "Refresh response code: ${refreshResponse.code}")

            if (refreshResponse.isSuccessful) {
                val bodyString = refreshResponse.body.string()
                Log.d("TokenAuthenticator", "Refresh response body: $bodyString")
                run {
                    val tokenResponse =
                        Gson().fromJson(bodyString, RefreshTokenResponse::class.java)
                    val newToken = tokenResponse.accessToken

                    if (newToken.isNullOrBlank()) {
                        Log.e("TokenAuthenticator", "Parsed accessToken is null or blank!")
                        return null
                    }

                    Log.i(
                        "TokenAuthenticator",
                        "Token refresh successful, new token: ${newToken.take(20)}..."
                    )

                    // Notify the app to update storage and interceptor
                    onTokenRefreshed(newToken)

                    // Retry original request with new token
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                }
            } else {
                val errorBody = refreshResponse.body.string()
                Log.e(
                    "TokenAuthenticator",
                    "Refresh failed with code ${refreshResponse.code}, body: $errorBody"
                )
                // If refresh fails with 401, the session is invalid
                if (refreshResponse.code == 401) {
                    Log.w("TokenAuthenticator", "Refresh token rejected, session invalidated")
                    onSessionInvalidated?.invoke()
                }
                null
            }
        } catch (e: Exception) {
            Log.e("TokenAuthenticator", "Exception during token refresh", e)
            null
        }
    }
}
