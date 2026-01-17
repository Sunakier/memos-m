package org.example.memosm.api

import com.connectrpc.ResponseMessage
import memos.api.v1.AuthServiceClient
import memos.api.v1.AuthServiceOuterClass.CreateSessionRequest

/**
 * Authenticates a user and creates a new session.
 *
 * @param authClient The generated AuthServiceClient instance.
 * @param username The username provided by the user.
 * @param password The password provided by the user.
 * @return The authenticated User object (session is managed via cookies by the server).
 * @throws Exception if the login fails or a network error occurs.
 */
suspend fun loginAndCreateSession(
    authClient: AuthServiceClient,
    username: String,
    password: String
): memos.api.v1.UserServiceOuterClass.User {
    // 1. Prepare the credentials message
    val credentials = CreateSessionRequest.PasswordCredentials.newBuilder()
        .setUsername(username)
        .setPassword(password)
        .build()

    // 2. Prepare the create session request
    val request = CreateSessionRequest.newBuilder()
        .setPasswordCredentials(credentials)
        .build()

    // 3. Execute the RPC call (requires headers parameter)
    val response = authClient.createSession(request, emptyMap())

    // 4. Handle result and return the user or throw
    return when (response) {
        is ResponseMessage.Success -> {
            // response.message is the CreateSessionResponse object
            response.message.user
        }
        is ResponseMessage.Failure -> {
            // Throw an exception with the Connect Error code/cause
            throw Exception("Login failed [${response.cause.code}]: ${response.cause.message}", response.cause)
        }
    }
}