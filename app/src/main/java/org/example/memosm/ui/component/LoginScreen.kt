package org.example.memosm.ui.component

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.example.memosm.R
import org.example.memosm.api.MemosApiFactory
import org.example.memosm.api.SessionCookieJar
import org.example.memosm.api.GrpcCookieInterceptor
import org.example.memosm.api.loginAndCreateToken
import org.example.memosm.model.Account
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.example.memosm.model.SignInRequest

enum class LoginMode {
    PASSWORD, TOKEN
}

@Composable
fun LoginScreen(
    onLoginSuccess: (String, String, Map<String, String>) -> Unit, modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter
        ) {
            LoginContent(
                onLoginSuccess = onLoginSuccess,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 64.dp, bottom = 24.dp)
            )
        }
    }
}

/**
 * Login dialog that can also be used for editing existing account credentials.
 * 
 * @param onLoginSuccess Callback with (baseUrl, token) on successful login/save
 * @param onDismiss Callback when dialog is dismissed
 * @param editAccount Optional - if provided, the dialog is in "edit mode" with pre-filled values
 */
@Composable
fun LoginDialog(
    onLoginSuccess: (String, String, Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
    editAccount: Account? = null
) {
    Dialog(
        onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Box {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }

                    LoginContent(
                        onLoginSuccess = onLoginSuccess,
                        modifier = Modifier.padding(24.dp),
                        editAccount = editAccount
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginContent(
    onLoginSuccess: (String, String, Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
    editAccount: Account? = null
) {
    // If editing, default to token mode and pre-fill values
    val isEditMode = editAccount != null
    var loginMode by remember { mutableStateOf(if (isEditMode) LoginMode.TOKEN else LoginMode.PASSWORD) }
    var hostUrl by remember { mutableStateOf(editAccount?.hostUrl ?: "") }
    var token by remember { mutableStateOf(editAccount?.accessToken ?: "") }
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
            val baseUrl = if (normalizedHost.endsWith("/")) normalizedHost else "$normalizedHost/"

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
                    level = HttpLoggingInterceptor.Level.BASIC
                }

                val capturedCookies = mutableMapOf<String, String>()
                val cookieJar = SessionCookieJar { cookies ->
                    capturedCookies.putAll(cookies)
                }

                val client = OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .addNetworkInterceptor(GrpcCookieInterceptor())
                    .cookieJar(cookieJar)
                    .build()

                val api = MemosApiFactory.create(baseUrl, client)

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

                    val authApi = MemosApiFactory.create(baseUrl, authClient)

                    try {
                        authApi.getCurrentSession()
                        onLoginSuccess(baseUrl, trimmedToken, capturedCookies)
                    } catch (e: Exception) {
                        errorMessage =
                            "Invalid token: ${e.localizedMessage ?: "Verification failed"}"
                    }
                } else {
                    // Password login via Connect RPC
                    if (username.isBlank() || password.isBlank()) {
                        errorMessage = "Username and password cannot be empty"
                        isLoading = false
                        return@launch
                    }

                    try {
                        // Use REST API signIn
                        val response = api.signIn(SignInRequest(username = username.trim(), password = password))
                        
                        Log.d("MemosLogin", "Login successful! Token: ${response.accessToken}")

                        onLoginSuccess(baseUrl, response.accessToken, capturedCookies)

                    } catch (e: Exception) {
                        Log.e("MemosLogin", "Login failed", e)
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = if (isEditMode) stringResource(R.string.profile_edit_credentials) else stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        SecondaryTabRow(
            selectedTabIndex = loginMode.ordinal, modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = loginMode == LoginMode.PASSWORD,
                onClick = { loginMode = LoginMode.PASSWORD },
                text = { Text(stringResource(R.string.login_password)) })
            Tab(
                selected = loginMode == LoginMode.TOKEN,
                onClick = { loginMode = LoginMode.TOKEN },
                text = { Text(stringResource(R.string.login_token)) })
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
                Text(if (isEditMode) stringResource(R.string.common_save) else stringResource(R.string.login_button))
            }
        }
    }
}
