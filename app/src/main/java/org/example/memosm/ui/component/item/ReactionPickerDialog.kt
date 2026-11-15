package org.example.memosm.ui.component.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import org.example.memosm.R
import org.example.memosm.ui.theme.LocalPaperTone
import org.example.memosm.ui.theme.PaperToneSheetHandle
import org.example.memosm.ui.theme.paperToneControlShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReactionPickerDialog(
    reactionOptions: List<String>, onDismiss: () -> Unit, onReactionSelected: (String) -> Unit,
    selectedReactions: Set<String> = emptySet()
) {
    val commonEmojis = reactionOptions.ifEmpty {
        listOf(
            "\uD83D\uDE2D"
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = if (LocalPaperTone.current) MaterialTheme.shapes.large else BottomSheetDefaults.ExpandedShape,
        containerColor = if (LocalPaperTone.current) MaterialTheme.colorScheme.surfaceContainerHigh else BottomSheetDefaults.ContainerColor,
        dragHandle = { if (LocalPaperTone.current) PaperToneSheetHandle() else BottomSheetDefaults.DragHandle() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.memo_action_add_reaction),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                commonEmojis.forEach { emoji ->
                    Surface(
                        onClick = { onReactionSelected(emoji) },
                        shape = paperToneControlShape(CircleShape),
                        color = if (emoji in selectedReactions) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(56.dp).semantics { selected = emoji in selectedReactions }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = emoji, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
    }
}
