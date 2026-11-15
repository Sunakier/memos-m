package org.example.memosm.ui.component.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.example.memosm.ui.theme.LocalPaperTone
import org.example.memosm.ui.theme.paperToneCardBorder
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Rows share rounded outer corners and a small background-colored gap, like Android Settings. */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    val border = paperToneCardBorder()
    Column(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(if (LocalPaperTone.current) MaterialTheme.colorScheme.outlineVariant else Color.Transparent)
            .then(if (border != null) Modifier.border(border, MaterialTheme.shapes.large) else Modifier),
        verticalArrangement = Arrangement.spacedBy(if (LocalPaperTone.current) 0.5.dp else 2.dp),
        content = content
    )
}

@Composable
fun SettingsSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = paperToneCardBorder(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(content = content)
    }
}
