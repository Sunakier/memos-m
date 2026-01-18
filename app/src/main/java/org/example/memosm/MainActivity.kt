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

                // Observe accounts instead of single credentials
                val accounts by dataStoreManager.accounts.collectAsState(initial = null)
                
                // Wait for DataStore to emit initial values
                var isCheckingSession by remember { mutableStateOf(true) }

                LaunchedEffect(accounts) {
                    if (accounts != null) {
                        // Once we have a non-null list (even if empty), we've finished the initial load
                        isCheckingSession = false
                    }
                }

                if (isCheckingSession) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val activeAccount = accounts?.find { it.isActive }
                    
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        if (activeAccount != null) {
                            MainScreen(
                                baseUrl = activeAccount.hostUrl,
                                token = activeAccount.accessToken,
                                dataStoreManager = dataStoreManager,
                                onLogout = {
                                    scope.launch {
                                        dataStoreManager.deleteAccount(activeAccount.id)
                                    }
                                },
                            )
                        } else {
                            // If no active account, show login
                            LoginScreen(
                                modifier = Modifier.padding(innerPadding),
                                onLoginSuccess = { baseUrl, token ->
                                    scope.launch {
                                        dataStoreManager.addAccount(baseUrl, token)
                                    }
                                })
                        }
                    }
                }
            }
        }
    }
}
