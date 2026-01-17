package org.example.memosm.api

import android.os.Build
import com.connectrpc.ProtocolClientConfig
import com.connectrpc.ResponseMessage
import com.connectrpc.extensions.GoogleJavaProtobufStrategy
import com.connectrpc.impl.ProtocolClient
import com.connectrpc.okhttp.ConnectOkHttpClient
import com.connectrpc.protocols.NetworkProtocol
import memos.api.v1.AuthServiceClient
import memos.api.v1.AuthServiceOuterClass.CreateSessionRequest
import memos.api.v1.UserServiceClient
import memos.api.v1.UserServiceOuterClass.CreateUserAccessTokenRequest
import memos.api.v1.UserServiceOuterClass.UserAccessToken
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

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
    baseUrl: String,
    username: String,
    password: String
): String {
    // Create a CookieJar to persist cookies between requests
    val cookieJar = object : CookieJar {
        private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore.getOrPut(url.host) { mutableListOf() }.apply {
                // Remove existing cookies with same name before adding new ones
                cookies.forEach { newCookie ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        removeIf { it.name == newCookie.name }
                    } else {
                        removeAt(indexOfFirst { it.name == newCookie.name })
                    }
                }
                addAll(cookies)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    // Create OkHttp client with cookie jar
    val okHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .build()

    // Create Connect RPC client
    val protocolClient = ProtocolClient(
        httpClient = ConnectOkHttpClient(okHttpClient),
        config = ProtocolClientConfig(
            host = baseUrl,
            serializationStrategy = GoogleJavaProtobufStrategy(),
            networkProtocol = NetworkProtocol.GRPC_WEB,
        )
    )

    val authClient = AuthServiceClient(protocolClient)
    val userClient = UserServiceClient(protocolClient)

    // 1. Prepare the credentials message
    val credentials = CreateSessionRequest.PasswordCredentials.newBuilder()
        .setUsername(username)
        .setPassword(password)
        .build()

    // 2. Prepare the create session request
    val sessionRequest = CreateSessionRequest.newBuilder()
        .setPasswordCredentials(credentials)
        .build()

    // 3. Execute the session creation RPC call
    val sessionResponse = authClient.createSession(sessionRequest, emptyMap())

    val user = when (sessionResponse) {
        is ResponseMessage.Success -> {
            sessionResponse.message.user
        }
        is ResponseMessage.Failure -> {
            throw Exception("Login failed [${sessionResponse.cause.code}]: ${sessionResponse.cause.message}", sessionResponse.cause)
        }
    }

    // 4. Create a user access token (session cookie will be automatically sent)
    val timestamp = System.currentTimeMillis()
    val tokenDescription = "MemoM-$timestamp"

    val accessToken = UserAccessToken.newBuilder()
        .setDescription(tokenDescription)
        .build()

    val tokenRequest = CreateUserAccessTokenRequest.newBuilder()
        .setParent(user.name) // e.g., "users/1"
        .setAccessToken(accessToken)
        .build()

    val tokenResponse = userClient.createUserAccessToken(tokenRequest, emptyMap())

    return when (tokenResponse) {
        is ResponseMessage.Success -> {
            tokenResponse.message.accessToken
        }
        is ResponseMessage.Failure -> {
            throw Exception("Failed to create token [${tokenResponse.cause.code}]: ${tokenResponse.cause.message}", tokenResponse.cause)
        }
    }
}