package org.example.memosm.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.memosm.R

/**
 * PaperTone (纸风): an opt-in presentation layer inspired by Timo 3.11.0.
 * Keep fixed colors and component exceptions here; screens keep their existing actions and state.
 * This local also reaches separate activities and nested appearance previews through MemosMTheme.
 */
val LocalPaperTone = staticCompositionLocalOf { false }

fun paperToneColorScheme(isDark: Boolean): ColorScheme = if (isDark) PaperToneDark else PaperToneLight

// Timo's light canvas is white, with ink-colored controls, fine gray rules and flat paper cards.
private val PaperToneLight = lightColorScheme(
    primary = Color(0xFF1D1B20), onPrimary = Color.White,
    primaryContainer = Color(0xFFF1F0F2), onPrimaryContainer = Color(0xFF1D1B20),
    secondary = Color(0xFF555259), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5F4F6), onSecondaryContainer = Color(0xFF45424A),
    tertiary = Color(0xFF555259), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF1F0F2), onTertiaryContainer = Color(0xFF45424A),
    background = Color.White, onBackground = Color(0xFF1D1B20),
    surface = Color.White, onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFF5F4F6), onSurfaceVariant = Color(0xFF66636A),
    surfaceTint = Color.Transparent,
    surfaceBright = Color.White, surfaceDim = Color(0xFFF5F4F6),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    outline = Color(0xFF8C8891), outlineVariant = Color(0xFFEAE8ED),
    error = Color(0xFFAC3434), onError = Color.White,
    errorContainer = Color(0xFFFFEDEA), onErrorContainer = Color(0xFF862323),
    inverseSurface = Color(0xFF333137), inverseOnSurface = Color(0xFFF7F5FA),
    inversePrimary = Color(0xFFE6E0E9), scrim = Color.Black,
    primaryFixed = Color(0xFFEAE8ED), primaryFixedDim = Color(0xFFD5D2D9),
    onPrimaryFixed = Color(0xFF1D1B20), onPrimaryFixedVariant = Color(0xFF45424A),
    secondaryFixed = Color(0xFFEAE8ED), secondaryFixedDim = Color(0xFFD5D2D9),
    onSecondaryFixed = Color(0xFF1D1B20), onSecondaryFixedVariant = Color(0xFF45424A),
    tertiaryFixed = Color(0xFFEAE8ED), tertiaryFixedDim = Color(0xFFD5D2D9),
    onTertiaryFixed = Color(0xFF1D1B20), onTertiaryFixedVariant = Color(0xFF45424A)
)

// The reference's dark mode uses a charcoal canvas and lighter graphite cards, not inverted white.
private val PaperToneDark = darkColorScheme(
    primary = Color(0xFFE6E0E9), onPrimary = Color(0xFF1D1B20),
    primaryContainer = Color(0xFF49454F), onPrimaryContainer = Color(0xFFE6E0E9),
    secondary = Color(0xFFCAC4D0), onSecondary = Color(0xFF242127),
    secondaryContainer = Color(0xFF36343B), onSecondaryContainer = Color(0xFFE6E0E9),
    tertiary = Color(0xFFCAC4D0), onTertiary = Color(0xFF242127),
    tertiaryContainer = Color(0xFF49454F), onTertiaryContainer = Color(0xFFE6E0E9),
    background = Color(0xFF141218), onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218), onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F), onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceTint = Color.Transparent,
    surfaceBright = Color(0xFF49454F), surfaceDim = Color(0xFF141218),
    surfaceContainerLowest = Color(0xFF141218), surfaceContainerLow = Color(0xFF141218),
    surfaceContainer = Color(0xFF36343B), surfaceContainerHigh = Color(0xFF36343B),
    surfaceContainerHighest = Color(0xFF36343B),
    outline = Color(0xFF938F99), outlineVariant = Color(0xFF49454F),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF64312E), onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE6E0E9), inverseOnSurface = Color(0xFF1D1B20),
    inversePrimary = Color(0xFF45424A), scrim = Color.Black,
    primaryFixed = Color(0xFFEAE8ED), primaryFixedDim = Color(0xFFD5D2D9),
    onPrimaryFixed = Color(0xFF1D1B20), onPrimaryFixedVariant = Color(0xFF45424A),
    secondaryFixed = Color(0xFFEAE8ED), secondaryFixedDim = Color(0xFFD5D2D9),
    onSecondaryFixed = Color(0xFF1D1B20), onSecondaryFixedVariant = Color(0xFF45424A),
    tertiaryFixed = Color(0xFFEAE8ED), tertiaryFixedDim = Color(0xFFD5D2D9),
    onTertiaryFixed = Color(0xFF1D1B20), onTertiaryFixedVariant = Color(0xFF45424A)
)

val PaperToneShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(2.dp), large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

/** Retain the chosen font family; change only the scale and spacing of the paper presentation. */
fun paperToneTypography(base: Typography): Typography = base.copy(
    displayLarge = base.displayLarge.copy(fontSize = 30.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    displayMedium = base.displayMedium.copy(fontSize = 26.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    displaySmall = base.displaySmall.copy(fontSize = 24.sp, lineHeight = 34.sp, letterSpacing = 0.sp),
    headlineLarge = base.headlineLarge.copy(fontSize = 22.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    headlineMedium = base.headlineMedium.copy(fontSize = 20.sp, lineHeight = 30.sp, letterSpacing = 0.sp),
    headlineSmall = base.headlineSmall.copy(fontSize = 18.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    titleSmall = base.titleSmall.copy(fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 14.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    labelLarge = base.labelLarge.copy(fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    labelMedium = base.labelMedium.copy(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    labelSmall = base.labelSmall.copy(fontSize = 10.sp, lineHeight = 16.sp, letterSpacing = 0.sp)
)

@Composable
internal fun paperToneCardBorder(): BorderStroke? = if (LocalPaperTone.current) {
    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
} else null

@Composable
internal fun PaperToneTabs(
    labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Row(Modifier.selectableGroup().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            labels.forEachIndexed { index, label ->
                val selected = selectedIndex == index
                Column(
                    Modifier.selectable(selected, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                        Text(label, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.width(52.dp).height(3.dp).background(
                        if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        RoundedCornerShape(2.dp)
                    ))
                }
            }
        }
    }
}

/** A dock into the existing editor; draft handling, uploads and outbox writes stay with its owner. */
@Composable
internal fun PaperToneComposeBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.appearance_papertone_write),
    icon: ImageVector = Icons.Outlined.Edit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.heightIn(min = 52.dp).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(icon, contentDescription = null)
        }
    }
}
