package org.example.memosm.ui.components

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.example.memosm.R
import org.example.memosm.api.MemosApi
import org.example.memosm.model.SignInRequest
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class LoginMode {
    TOKEN, PASSWORD
}

@Composable
fun LoginScreen(
    onLoginSuccess: (String, String) -> Unit, modifier: Modifier = Modifier
) {
    var loginMode by remember { mutableStateOf(LoginMode.TOKEN) }
    var hostUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    val errorEmptyHost = stringResource(R.string.login_error_empty_host)
    val errorFailed = stringResource(R.string.login_error_failed)

    val performLogin = {
        scope.launch {
            isLoading = true
            errorMessage = null
            var normalizedHost = hostUrl.trim()

            if (normalizedHost.isBlank()) {
                errorMessage = errorEmptyHost
                isLoading = false
                return@launch
            }

            if (!normalizedHost.startsWith("http")) {
                normalizedHost = "https://$normalizedHost"
            }
            val baseUrl =
                if (normalizedHost.endsWith("/")) normalizedHost else "$normalizedHost/"

            val httpUrl = normalizedHost.toHttpUrlOrNull()

            if (httpUrl == null) {
                errorMessage = "Invalid URL format"
                isLoading = false
                return@launch
            }

            try {
                val logging = HttpLoggingInterceptor { message ->
                    Log.d("MemosApi", message)
                }.apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }

                val client = OkHttpClient.Builder().addInterceptor(logging).build()

                val retrofit = Retrofit.Builder().baseUrl(baseUrl).client(client)
                    .addConverterFactory(GsonConverterFactory.create()).build()

                val api = retrofit.create(MemosApi::class.java)

                // Check if instance is valid by fetching instance profile
                try {
                    api.getInstanceProfile()
                } catch (e: Exception) {
                    errorMessage = "Invalid Memos instance or host URL"
                    isLoading = false
                    return@launch
                }

                if (loginMode == LoginMode.TOKEN) {
                    val trimmedToken = token.trim()
                    if (trimmedToken.isBlank()) {
                        errorMessage = "Token cannot be empty"
                        isLoading = false
                        return@launch
                    }

                    // Verify token validity by fetching current user
                    val authClient =
                        OkHttpClient.Builder().addInterceptor(logging).addInterceptor { chain ->
                            val request = chain.request().newBuilder()
                                .addHeader("Authorization", "Bearer $trimmedToken").build()
                            chain.proceed(request)
                        }.build()

                    val authApi = retrofit.newBuilder().client(authClient).build()
                        .create(MemosApi::class.java)

                    try {
                        authApi.getCurrentSession()
                        onLoginSuccess(baseUrl, trimmedToken)
                    } catch (e: Exception) {
                        errorMessage =
                            "Invalid token: ${e.localizedMessage ?: "Verification failed"}"
                    }
                } else {
                    // Password login via REST
                    if (username.isBlank() || password.isBlank()) {
                        errorMessage = "Username and password cannot be empty"
                        isLoading = false
                        return@launch
                    }

                    try {
                        val response = api.signIn(SignInRequest(username.trim(), password))
                        val accessToken = response.accessToken

                        // Verify token validity by fetching current user
                        val authClient =
                            OkHttpClient.Builder().addInterceptor(logging).addInterceptor { chain ->
                                val request = chain.request().newBuilder()
                                    .addHeader("Authorization", "Bearer $accessToken").build()
                                chain.proceed(request)
                            }.build()

                        val authApi = retrofit.newBuilder().client(authClient).build()
                            .create(MemosApi::class.java)

                        authApi.getCurrentSession()
                        onLoginSuccess(baseUrl, accessToken)
                    } catch (e: Exception) {
                        errorMessage =
                            "Login failed: ${e.localizedMessage ?: "Check your credentials"}"
                    }
                }

            } catch (e: Exception) {
                Log.e("MemosLogin", "Login failed", e)
                errorMessage = errorFailed.format(e.localizedMessage ?: "unknown")
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .padding(top = 64.dp)
                .padding(horizontal = 16.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            SecondaryTabRow(
                selectedTabIndex = loginMode.ordinal, modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = loginMode == LoginMode.TOKEN,
                    onClick = { loginMode = LoginMode.TOKEN },
                    text = { Text(stringResource(R.string.login_token)) })
                Tab(
                    selected = loginMode == LoginMode.PASSWORD,
                    onClick = { loginMode = LoginMode.PASSWORD },
                    text = { Text(stringResource(R.string.login_password)) })
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = hostUrl,
                onValueChange = { hostUrl = it },
                label = { Text(stringResource(R.string.login_host_url)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.login_host_url_placeholder)) },
                enabled = !isLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (loginMode) {
                LoginMode.TOKEN -> {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(stringResource(R.string.login_token)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { performLogin() })
                    )
                }

                LoginMode.PASSWORD -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(stringResource(R.string.login_username)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.login_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { performLogin() })
                        )
                    }
                }
            }

            errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { performLogin() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && hostUrl.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.login_button))
                }
            }
        }
    }
}
