package org.example.memosm.ui.component.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.example.memosm.R
import org.example.memosm.viewmodel.AppSettings
import org.example.memosm.viewmodel.delegates.AppSettingsDelegate

@Composable
fun QuickEditSettingsCard(settings: AppSettings, actions: AppSettingsDelegate) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.quick_edit_title),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        SettingToggleRow(label = stringResource(R.string.quick_edit_auto_publish),
            description = stringResource(R.string.quick_edit_auto_publish_description),
            checked = settings.quickEditAutoPublish, onCheckedChange = actions::updateQuickEditAutoPublish)
        SettingToggleRow(label = stringResource(R.string.quick_edit_immersive),
            checked = settings.immersiveEditing, onCheckedChange = actions::updateImmersiveEditing)
        SettingToggleRow(label = stringResource(R.string.quick_edit_hide_comments),
            checked = settings.hideComments, onCheckedChange = actions::updateHideComments)
        SettingToggleRow(label = stringResource(R.string.quick_edit_hide_empty_comments),
            checked = settings.hideEmptyComments, onCheckedChange = actions::updateHideEmptyComments)
        var reactions by rememberSaveable { mutableStateOf<String?>(null) }
        OutlinedTextField(value = reactions ?: settings.quickReactions, onValueChange = {
            reactions = it
            actions.updateQuickReactions(it.trim().split(Regex("\\s+")).filter(String::isNotBlank)
                .filter { token -> token.length <= 24 }.distinct().take(8).joinToString(" "))
        }, label = { Text(stringResource(R.string.quick_edit_reactions)) },
            supportingText = { Text(stringResource(R.string.quick_edit_reactions_description)) },
            modifier = Modifier.fillMaxWidth())
    }
}
