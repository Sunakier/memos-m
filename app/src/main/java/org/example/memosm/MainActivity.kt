package org.example.memosm

import android.content.Intent
import android.net.Uri
import android.os.Build
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.example.memosm.data.DataStoreManager
import org.example.memosm.data.DraftManager
import org.example.memosm.model.ShareIntentData
import org.example.memosm.ui.component.LoginScreen
import org.example.memosm.ui.MainScreen
import org.example.memosm.ui.theme.MemosMTheme
import org.example.memosm.widget.DraftWidget

class MainActivity : ComponentActivity() {
    
    // StateFlow to hold pending share data, observable by Compose
    private val pendingShareDataFlow = MutableStateFlow<ShareIntentData?>(null)
    
    // StateFlow to trigger composer opening from widget
    private val shouldOpenComposerFlow = MutableStateFlow(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Parse share intent data from initial launch
        pendingShareDataFlow.value = parseShareIntent(intent)
        
        // Check if launched from widget
        if (intent.action == DraftWidget.ACTION_OPEN_COMPOSER) {
            shouldOpenComposerFlow.value = true
        }
        
        setContent {
            MemosMTheme {
                val context = LocalContext.current
                val dataStoreManager = remember { DataStoreManager(context) }
                val draftManager = remember { DraftManager(context) }
                val scope = rememberCoroutineScope()

                // Observe accounts instead of single credentials
                val accounts by dataStoreManager.accounts.collectAsState(initial = null)
                
                // Wait for DataStore to emit initial values
                var isCheckingSession by remember { mutableStateOf(true) }
                
                // Collect pending share data from the flow
                val pendingShareData by pendingShareDataFlow.collectAsState()
                val shouldOpenComposer by shouldOpenComposerFlow.collectAsState()

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
                                draftManager = draftManager,
                                onLogout = {
                                    scope.launch {
                                        dataStoreManager.deleteAccount(activeAccount.id)
                                    }
                                },
                                shareIntentData = pendingShareData,
                                onShareIntentConsumed = { pendingShareDataFlow.value = null },
                                shouldOpenComposer = shouldOpenComposer,
                                onComposerOpened = { shouldOpenComposerFlow.value = false }
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
    
    /**
     * Called when the activity is already running and receives a new intent (e.g., share).
     * With launchMode="singleTask", share intents will come here instead of onCreate.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the current intent
        
        // Parse and emit the new share data
        val shareData = parseShareIntent(intent)
        if (shareData != null) {
            pendingShareDataFlow.value = shareData
        }
        
        if (intent.action == DraftWidget.ACTION_OPEN_COMPOSER) {
            shouldOpenComposerFlow.value = true
        }
    }
    
    /**
     * Parses a share intent (ACTION_SEND or ACTION_SEND_MULTIPLE) and extracts
     * text content and file URIs.
     */
    private fun parseShareIntent(intent: Intent?): ShareIntentData? {
        if (intent == null) return null
        
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) {
            return null
        }
        
        // Extract text content
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
        
        // Extract URIs
        val uris = mutableListOf<Uri>()
        
        when (action) {
            Intent.ACTION_SEND -> {
                // Single file/image share
                val singleUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                singleUri?.let { uri ->
                    takePersistentUriPermission(uri)
                    uris.add(uri)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                // Multiple files/images share
                val multipleUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                multipleUris?.forEach { uri ->
                    takePersistentUriPermission(uri)
                    uris.add(uri)
                }
            }
        }
        
        val shareData = ShareIntentData(text = text, uris = uris)
        return if (shareData.isEmpty) null else shareData
    }
    
    /**
     * Takes persistable URI permission for content URIs to ensure
     * we can access the file later.
     */
    private fun takePersistentUriPermission(uri: Uri) {
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: SecurityException) {
            // Permission may not be grantable for all URIs, ignore
        }
    }
}
