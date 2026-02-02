package org.example.memosm.widget.stats

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.memosm.MainActivity
import org.example.memosm.R
import org.example.memosm.api.MemosApiFactory
import org.example.memosm.data.DataStoreManager
import org.example.memosm.model.UserStats
import okhttp3.OkHttpClient
import org.example.memosm.api.TokenAuthenticator
import org.example.memosm.api.MemosCookieJar
import org.example.memosm.api.AuthInterceptor
import org.example.memosm.model.Account

class UserStatsWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val accountId = prefs[stringPreferencesKey("account_id")]

            val state by produceState<StatsState>(initialValue = StatsState.Loading, key1 = accountId) {
                if (accountId == null) {
                    // Stay loading or handle as special non-error case in UI? 
                    // Let's assume loading until we determine it's empty in UI check
                    value = StatsState.Loading 
                } else {
                    value = try {
                        withContext(Dispatchers.IO) {
                            val dataStoreManager = DataStoreManager(context)
                            val accounts = dataStoreManager.getAccounts()
                            val account = accounts.find { it.id == accountId }

                            if (account != null) {
                                try {
                                    val client = OkHttpClient.Builder()
                                        .addInterceptor(AuthInterceptor(account.accessToken))
                                        .build()
                                    val api = MemosApiFactory.create(account.hostUrl, client)

                                    val username = account.user?.name ?: api.getCurrentSession().user?.name

                                    if (username != null) {
                                        val stats = api.getUserStats(username)
                                        StatsState.Success(stats, account)
                                    } else {
                                        StatsState.Error("User not found")
                                    }
                                } catch (e: Exception) {
                                    StatsState.Error(e.message ?: "Network error")
                                }
                            } else {
                                StatsState.Error("Account not found")
                            }
                        }
                    } catch (e: Exception) {
                        StatsState.Error(e.message ?: "Unknown error")
                    }
                }
            }

            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .padding(16.dp)
                        .clickable(actionStartActivity<MainActivity>())
                ) {
                    if (accountId == null) {
                        EmptyState(context)
                    } else {
                        when (val currentState = state) {
                            is StatsState.Loading -> LoadingState()
                            is StatsState.Error -> ErrorState(currentState.message)
                            is StatsState.Success -> StatsContent(currentState.stats, currentState.account)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun EmptyState(context: Context) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Tap to configure",
                style = TextStyle(color = GlanceTheme.colors.onSurface),
                modifier = GlanceModifier.clickable(actionStartActivity<UserStatsWidgetConfigActivity>())
            )
        }
    }

    @Composable
    fun LoadingState() {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading...", style = TextStyle(color = GlanceTheme.colors.onSurface))
        }
    }

    @Composable
    fun ErrorState(message: String) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Error: $message",
                style = TextStyle(color = GlanceTheme.colors.error)
            )
        }
    }

    @Composable
    fun StatsContent(stats: UserStats, account: Account) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
               Text(
                   text = "Stats for ${account.name}",
                   style = TextStyle(
                       color = GlanceTheme.colors.onSurface,
                       fontWeight = FontWeight.Bold,
                       fontSize = 14.sp
                   )
               )
            }
            Spacer(GlanceModifier.height(8.dp))
            
            // Stats Grid
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                StatItem(
                    label = "Memos",
                    value = stats.totalMemoCount.toString(),
                    modifier = GlanceModifier.defaultWeight()
                )
                StatItem(
                    label = "Tags",
                    value = stats.tagCount?.size?.toString() ?: "0",
                    modifier = GlanceModifier.defaultWeight()
                )
                StatItem(
                    label = "Pinned",
                    value = stats.pinnedMemos?.size?.toString() ?: "0",
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
    }

    @Composable
    fun StatItem(
        label: String, 
        value: String, 
        modifier: GlanceModifier = GlanceModifier
    ) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = label,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
        }
    }

}

sealed class StatsState {
    object Loading : StatsState()
    data class Success(val stats: UserStats, val account: Account) : StatsState()
    data class Error(val message: String) : StatsState()
}
