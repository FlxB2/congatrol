package xyz.felixb.congatrol.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable
import xyz.felixb.congatrol.theme.LocalCongatrolLook

/**
 * How a row of segmented buttons is coloured: the chosen one in the accent, the others in the
 * card's material. Material's own choice, a faint wash, does not tell them apart over the glow.
 */
@Composable
fun congatrolSegmentedColors(): SegmentedButtonColors {
    val colors = MaterialTheme.colorScheme
    val look = LocalCongatrolLook.current
    return SegmentedButtonDefaults.colors(
        activeContainerColor = colors.primary,
        activeContentColor = colors.onPrimary,
        activeBorderColor = colors.primary,
        inactiveContainerColor = look.card,
        inactiveContentColor = colors.onSurface,
        inactiveBorderColor = colors.outlineVariant,
    )
}
