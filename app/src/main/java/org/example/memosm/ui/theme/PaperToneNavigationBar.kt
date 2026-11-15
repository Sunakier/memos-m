package org.example.memosm.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The Luori bar's geometry, isolated from the standard Material navigation used by other themes. */
@Composable
internal fun mainBottomBarHeight() = if (LocalPaperTone.current) {
    64.dp + 32.dp * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)
} else 80.dp

/**
 * Adapted from PaperWorld's AppNavigationBar / AppBottomBarItem.
 * The host owns committed navigation; this bar only previews until the finger lifts.
 * Keep MemosM icons and PaperTone colors instead of depending on the other app's font/settings.
 */
@Composable
internal fun PaperToneNavigationBar(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onPreview: (Int) -> Unit,
    onCommit: (Int) -> Unit,
    onCancel: () -> Unit,
    icon: @Composable (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (labels.isEmpty()) return
    val state = remember(labels) { PaperToneScrubState() }
    val index = (if (state.active) state.index else selectedIndex).coerceIn(labels.indices)
    val collapse by animateFloatAsState(
        if (state.active) 1f else 0f,
        spring(dampingRatio = 0.9f, stiffness = 950f), label = "paperToneRailCollapse"
    )
    val position by animateFloatAsState(
        if (state.active) state.position else index.toFloat(),
        if (state.active) snap() else spring(dampingRatio = 0.9f, stiffness = 950f),
        label = "paperToneRailPosition"
    )
    val colors = MaterialTheme.colorScheme
    val reach = with(LocalDensity.current) { 160.dp.toPx() }
    Surface(modifier.fillMaxWidth(), color = colors.surface) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(thickness = 0.5.dp, color = colors.outlineVariant)
            Box(
                Modifier.fillMaxWidth().height(mainBottomBarHeight()).testTag("paperToneBottomBar")
                    .paperToneScrubSelection(state, labels.size, selectedIndex, reach,
                        onPreview = onPreview, onCommit = onCommit, onCancel = onCancel)
            ) {
                Row(
                    Modifier.fillMaxSize().selectableGroup().graphicsLayer {
                        alpha = 1f - collapse
                        scaleY = 1f - collapse * .85f
                    }.then(if (state.active) Modifier.clearAndSetSemantics { } else Modifier),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    labels.forEachIndexed { item, label ->
                        val selected = selectedIndex == item
                        val scale by animateFloatAsState(
                            if (selected) 1f else .55f,
                            spring(dampingRatio = .72f, stiffness = 1500f), label = "paperToneNavCircle"
                        )
                        val alpha by animateFloatAsState(
                            if (selected) 1f else 0f,
                            spring(dampingRatio = .72f, stiffness = 1500f), label = "paperToneNavAlpha"
                        )
                        val iconColor by animateColorAsState(
                            if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                            tween(160), label = "paperToneNavIcon"
                        )
                        val labelColor by animateColorAsState(
                            if (selected) colors.primary else colors.outline,
                            tween(160), label = "paperToneNavLabel"
                        )
                        val interaction = remember { MutableInteractionSource() }
                        Box(
                            Modifier.weight(1f).height(mainBottomBarHeight())
                                .selectable(selected, role = Role.Tab, interactionSource = interaction,
                                    indication = null, onClick = { onSelect(item) }),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center) {
                                Box(
                                    Modifier.size(40.dp).indication(interaction, ripple(bounded = false, radius = 20.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(Modifier.size(40.dp).graphicsLayer {
                                        scaleX = scale; scaleY = scale; this.alpha = alpha
                                    }.clip(CircleShape).background(colors.primaryContainer))
                                    CompositionLocalProvider(LocalContentColor provides iconColor) { icon(item, selected) }
                                }
                                Spacer(Modifier.height(1.dp))
                                Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Canvas(Modifier.fillMaxSize()) {
                    if (collapse <= .001f) return@Canvas
                    val slot = size.width / labels.size
                    val start = slot / 2
                    val y = size.height * .66f
                    val stroke = 3.dp.toPx()
                    drawLine(colors.outlineVariant.copy(alpha = collapse), Offset(start, y),
                        Offset(size.width - start, y), stroke, StrokeCap.Round)
                    repeat(labels.size) {
                        drawCircle(colors.primary.copy(alpha = .4f * collapse), 2.dp.toPx(), Offset(start + it * slot, y))
                    }
                    val x = (start + position * slot).coerceIn(8.dp.toPx(), size.width - 8.dp.toPx())
                    drawLine(colors.primary.copy(alpha = collapse), Offset(start, y), Offset(x, y), stroke, StrokeCap.Round)
                    drawCircle(colors.primary.copy(alpha = .14f * collapse), 12.dp.toPx(), Offset(x, y))
                    drawCircle(colors.primary.copy(alpha = collapse), 6.dp.toPx(), Offset(x, y))
                }
                if (state.active) {
                    val preview = "${labels[index]} · ${index + 1}/${labels.size}"
                    Text(preview, style = MaterialTheme.typography.labelMedium, color = colors.primary,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
                            .testTag("paperToneNavigationPreview").clearAndSetSemantics { contentDescription = preview })
                }
            }
        }
    }
}
