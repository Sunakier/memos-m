package org.example.memosm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

enum class LoginMode {
    TOKEN, PASSWORD
}

@Composable
fun LoginScreen(modifier: Modifier = Modifier) {
    var loginMode by remember { mutableStateOf(LoginMode.TOKEN) }
    var hostUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Login to Memos",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        TabRow(selectedTabIndex = loginMode.ordinal) {
            Tab(
                selected = loginMode == LoginMode.TOKEN,
                onClick = { loginMode = LoginMode.TOKEN },
                text = { Text("Token") }
            )
            Tab(
                selected = loginMode == LoginMode.PASSWORD,
                onClick = { loginMode = LoginMode.PASSWORD },
                text = { Text("Password") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = hostUrl,
            onValueChange = { hostUrl = it },
            label = { Text("Host URL") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://your-memos-host.com") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (loginMode) {
            LoginMode.TOKEN -> {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            LoginMode.PASSWORD -> {
                Column {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { /* TODO: Implement login logic */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login")
        }
    }
}