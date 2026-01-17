import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.memosm.R
import org.example.memosm.model.UserGeneralSetting
import org.example.memosm.ui.components.composer.getVisibilityLabel

val SUPPORTED_LANGUAGES = listOf(
    "ar", "cs", "de", "en", "en-GB", "es", "fa", "fr", "hi", "hr", "hu", "id", "it", "ja", "ka",
    "ko", "mr", "nb", "nl", "pl", "pt-BR", "pt", "ru", "sl", "sv", "th", "tr", "uk", "vi", "zh-Hans", "zh-Hant"
)

@Composable
fun SettingsCard(settings: UserGeneralSetting, onUpdate: (String?, String?) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                stringResource(R.string.profile_settings_general),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Locale
            SettingsSelectionItem(
                label = stringResource(R.string.profile_settings_locale),
                currentValue = settings.locale ?: "en",
                options = SUPPORTED_LANGUAGES,
                onSelect = { onUpdate(it, null) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Memo Visibility
            SettingsSelectionItem(
                label = stringResource(R.string.profile_settings_visibility),
                currentValue = if (settings.memoVisibility.isNullOrBlank()) "PRIVATE" else settings.memoVisibility!!,
                options = listOf("PRIVATE", "PROTECTED", "PUBLIC"),
                labelProvider = { getVisibilityLabel(it) },
                onSelect = { onUpdate(null, it) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSelectionItem(
    label: String,
    currentValue: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    labelProvider: @Composable (String) -> String = { it }
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                text = labelProvider(currentValue),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable { showSheet = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(WindowInsets.navigationBars.asPaddingValues())
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                LazyColumn {
                    items(options) { option ->
                        val isSelected = option == currentValue
                        ListItem(
                            headlineContent = {
                                Text(
                                    labelProvider(option),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingContent = {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(24.dp))
                                }
                            },
                            modifier = Modifier.clickable {
                                onSelect(option)
                                showSheet = false
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (isSelected) 
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) 
                                    else Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    }
}
