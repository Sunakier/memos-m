import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.memosm.R
import org.example.memosm.model.UserGeneralSetting
import org.example.memosm.ui.components.composer.getVisibilityLabel
//import org.example.memosm.ui.nav.SUPPORTED_LANGUAGES

val SUPPORTED_LANGUAGES = listOf(
    "ar",
    "cs",
    "de",
    "en",
    "en-GB",
    "es",
    "fa",
    "fr",
    "hi",
    "hr",
    "hu",
    "id",
    "it",
    "ja",
    "ka",
    "ko",
    "mr",
    "nb",
    "nl",
    "pl",
    "pt-BR",
    "pt",
    "ru",
    "sl",
    "sv",
    "th",
    "tr",
    "uk",
    "vi",
    "zh-Hans",
    "zh-Hant"
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCard(settings: UserGeneralSetting, onUpdate: (String?, String?) -> Unit) {
    var showLocaleDialog by remember { mutableStateOf(false) }
    var tempLocale by remember { mutableStateOf(settings.locale ?: "") }
    var showVisibilityMenu by remember { mutableStateOf(false) }

    if (showLocaleDialog) {
        var expanded by remember { mutableStateOf(false) }
        val initialDisplayName = tempLocale
        var textFieldValue by remember { mutableStateOf(initialDisplayName) }

        val filteredOptions = if (textFieldValue.isEmpty()) {
            SUPPORTED_LANGUAGES
        } else {
            SUPPORTED_LANGUAGES.filter {
                it.contains(textFieldValue, ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = { showLocaleDialog = false },
            title = { Text(stringResource(R.string.profile_settings_locale_edit)) },
            text = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = textFieldValue,
                            onValueChange = {
                                textFieldValue = it
                                expanded = true
                                tempLocale = it
                            },
                            label = { Text(stringResource(R.string.profile_settings_locale_label)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(
                                    ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true
                                ),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )

                        if (filteredOptions.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = expanded, onDismissRequest = { expanded = false }) {
                                filteredOptions.forEach { selectionOption ->
                                    DropdownMenuItem(
                                        text = { Text(selectionOption) },
                                        onClick = {
                                            textFieldValue = selectionOption
                                            tempLocale = selectionOption
                                            expanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(tempLocale, null)
                    showLocaleDialog = false
                }) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocaleDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            })
    }

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
            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_settings_locale)) },
                supportingContent = {
                    val displayName =
                        settings.locale ?: stringResource(R.string.profile_settings_locale_default)
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable {
                    tempLocale = settings.locale ?: ""
                    showLocaleDialog = true
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Memo Visibility
            Box {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.profile_settings_visibility)) },
                    supportingContent = {
                        Text(
                            text = if (settings.memoVisibility.isNullOrBlank()) getVisibilityLabel("PRIVATE") else getVisibilityLabel(
                                settings.memoVisibility
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.Outlined.ArrowDropDown, contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable { showVisibilityMenu = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                DropdownMenu(
                    expanded = showVisibilityMenu,
                    onDismissRequest = { showVisibilityMenu = false },
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    listOf("PRIVATE", "PROTECTED", "PUBLIC").forEach { visibility ->
                        DropdownMenuItem(
                            text = { Text(getVisibilityLabel(visibility)) },
                            onClick = {
                                onUpdate(null, visibility)
                                showVisibilityMenu = false
                            })
                    }
                }
            }
        }
    }
}
