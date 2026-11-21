package org.example.memosm.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.example.memosm.R

@Composable
internal fun PaperToneEditorHeader(onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = if (onBack == null) 32.dp else 48.dp).padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack,
                            stringResource(R.string.memo_detail_back), Modifier.size(20.dp))
                    }
                }
                Box(Modifier.weight(1f)) { content() }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Same submit action and mode label as the default editor, presented as a flat toolbar control. */
@Composable
internal fun PaperToneEditorSubmit(label: String, busy: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick, enabled = !busy,
        modifier = Modifier.padding(bottom = 4.dp).size(48.dp).testTag("paperToneEditorSubmit")
            .semantics { contentDescription = label }
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null,
            modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
