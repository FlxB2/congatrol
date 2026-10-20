package xyz.felixb.congatrol.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * How things move in this app, in one place: a spring that overshoots a little for what is
 * touched or picked, and a calmer one for what merely arrives.
 */
object Motion {
    /** For what answers a finger: lands a touch past where it is going and settles. */
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)

    /** For what comes into view or slides over: no overshoot to speak of. */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

    /** How much later each next thing of a screen arrives than the one before it. */
    const val STAGGER_MILLIS = 55L
}

/** Gives a little under the finger: shrinks to [pressed] of its size while held, and springs back. */
@Composable
fun Modifier.pressScale(
    interactions: InteractionSource,
    pressed: Float = 0.9f,
): Modifier {
    val held by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (held) pressed else 1f, Motion.bouncy())
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Arrives rather than is there: fades in while rising into place, once, when it enters the
 * composition. [index] is its place among the things arriving together — each a beat after the last.
 */
@Composable
fun Modifier.appear(index: Int = 0): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * Motion.STAGGER_MILLIS)
        progress.animateTo(1f, Motion.settle())
    }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * APPEAR_RISE.toPx()
        val scale = APPEAR_SCALE + (1f - APPEAR_SCALE) * progress.value
        scaleX = scale
        scaleY = scale
    }
}

/**
 * While [active], rings of [color] spread out from behind a round thing and fade, one after the
 * other: something is going on. Drawn outside its bounds, so nothing above it may clip.
 */
@Composable
fun Modifier.halo(
    active: Boolean,
    color: Color,
): Modifier {
    if (!active) return this
    val progress by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(HALO_MILLIS, easing = LinearOutSlowInEasing)),
    )
    return drawBehind {
        // Two rings, half a round apart, so that one is always on its way.
        listOf(progress, (progress + 0.5f) % 1f).forEach { ring ->
            drawCircle(
                color = color.copy(alpha = (1f - ring) * HALO_ALPHA),
                radius = size.minDimension / 2f * (1f + ring * HALO_REACH),
            )
        }
    }
}

private val APPEAR_RISE = 28.dp
private const val APPEAR_SCALE = 0.96f
private const val HALO_MILLIS = 2_200
private const val HALO_ALPHA = 0.4f
private const val HALO_REACH = 0.65f
