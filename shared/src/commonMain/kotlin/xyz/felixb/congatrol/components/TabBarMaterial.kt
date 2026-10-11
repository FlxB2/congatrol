package xyz.felixb.congatrol.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * What the tab bar's pill is made of: the content behind it, taken from [hazeState], blurred under
 * a wash of [surface], with a hairline of [rim]. The same on every platform.
 */
internal fun Modifier.tabBarMaterial(
    hazeState: HazeState,
    surface: Color,
    rim: Color,
): Modifier =
    hazeBlur(
        input = HazeInput.Backdrop(hazeState),
        style =
            HazeBlurStyle {
                blurRadius(24.dp)
                colorEffects(listOf(HazeColorEffect.tint(surface.copy(alpha = SURFACE_ALPHA))))
            },
    ).border(1.dp, rim, CircleShape)

private const val SURFACE_ALPHA = 0.78f
