package org.example.memosm.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.example.memosm.api.MemosApi
import retrofit2.Retrofit
import retrofit2.converter.protobuf.ProtoConverterFactory

enum class LoginMode {
    TOKEN, PASSWORD
}

@Composable
fun LoginScreen(
    onLoginSuccess: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var loginMode by remember { mutableStateOf(LoginMode.TOKEN) }
    var hostUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

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
                text = "Login to Memos",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            SecondaryTabRow(
                selectedTabIndex = loginMode.ordinal, modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = loginMode == LoginMode.TOKEN,
                    onClick = { loginMode = LoginMode.TOKEN },
                    text = { Text("Token") })
                Tab(
                    selected = loginMode == LoginMode.PASSWORD,
                    onClick = { loginMode = LoginMode.PASSWORD },
                    text = { Text("Password") })
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = hostUrl,
                onValueChange = { hostUrl = it },
                label = { Text("Host URL") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://demo.usememos.com") },
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (loginMode) {
                LoginMode.TOKEN -> {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Token") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )
                }

                LoginMode.PASSWORD -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
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
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            var normalizedHost = hostUrl.trim()
                            if (normalizedHost.isNotBlank()) {
                                if (!normalizedHost.startsWith("http")) {
                                    normalizedHost = "https://$normalizedHost"
                                }
                                val baseUrl =
                                    if (normalizedHost.endsWith("/")) normalizedHost else "$normalizedHost/"

                                if (loginMode == LoginMode.TOKEN) {
                                    // For token login, we just assume it's valid for now and proceed
                                    onLoginSuccess(baseUrl, token)
                                } else {
                                    val logging = HttpLoggingInterceptor { message ->
                                        Log.d("MemosApi", message)
                                    }.apply {
                                        level = HttpLoggingInterceptor.Level.BODY
                                    }

                                    val client = OkHttpClient.Builder()
                                        .addInterceptor(logging)
                                        .build()

                                    val retrofit = Retrofit.Builder()
                                        .baseUrl(baseUrl)
                                        .client(client)
                                        .addConverterFactory(ProtoConverterFactory.create())
                                        .build()

                                    val api = retrofit.create(MemosApi::class.java)
                                    // Password login implementation removed for now as per previous state
                                }
                            } else {
                                errorMessage = "Host URL cannot be empty"
                            }
                        } catch (e: Exception) {
                            Log.e("MemosLogin", "Login failed", e)
                            errorMessage = "Login failed: ${e.localizedMessage}"
                        } finally {
                            isLoading = false
                        }
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading && hostUrl.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("Login")
                }
            }
        }
    }
}
