package org.example.memosm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import org.example.memosm.data.DataStoreManager
import org.example.memosm.ui.component.LoginScreen
import org.example.memosm.ui.MainScreen
import org.example.memosm.ui.theme.MemosMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MemosMTheme {
                val context = LocalContext.current
                val dataStoreManager = remember { DataStoreManager(context) }
                val scope = rememberCoroutineScope()

                val savedUrl by dataStoreManager.hostUrl.collectAsState(initial = null)
                val savedToken by dataStoreManager.accessToken.collectAsState(initial = null)

                // Wait for DataStore to emit initial values
                var isCheckingSession by remember { mutableStateOf(true) }

                LaunchedEffect(savedUrl, savedToken) {
                    // This is a simple way to wait for the first emission from DataStore
                    // In a real app, you'd use a more robust way to handle the "loading" state
                    kotlinx.coroutines.delay(100)
                    isCheckingSession = false
                }

                if (isCheckingSession) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        if (savedUrl != null && savedToken != null) {
                            MainScreen(
                                baseUrl = savedUrl!!,
                                token = savedToken!!,
                                dataStoreManager = dataStoreManager,
                                onLogout = {
                                    scope.launch {
                                        dataStoreManager.clearCredentials()
                                    }
                                },
                            )
                        } else {
                            LoginScreen(
                                modifier = Modifier.padding(innerPadding),
                                onLoginSuccess = { baseUrl, token ->
                                    scope.launch {
                                        dataStoreManager.saveCredentials(baseUrl, token)
                                    }
                                })
                        }
                    }
                }
            }
        }
    }
}
