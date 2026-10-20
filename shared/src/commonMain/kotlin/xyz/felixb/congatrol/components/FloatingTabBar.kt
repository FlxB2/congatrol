package xyz.felixb.congatrol.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import congatrol.shared.generated.resources.Res
import congatrol.shared.generated.resources.ic_play
import congatrol.shared.generated.resources.ic_tab_drive
import congatrol.shared.generated.resources.ic_tab_map
import congatrol.shared.generated.resources.ic_tab_robot
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import xyz.felixb.congatrol.theme.CongatrolTheme
import xyz.felixb.congatrol.theme.LocalCongatrolLook
import xyz.felixb.congatrol.theme.PreviewCongatrol
import xyz.felixb.congatrol.theme.congatrolBackground

/**
 * How much room a screen keeps free below its content so the last element can be scrolled clear of
 * the [FloatingTabBar], which floats over the bottom of the screen instead of taking space from it.
 */
val FloatingTabBarClearance = 88.dp

@Immutable
data class TabBarItem(
    val key: String,
    val label: String,
    val icon: DrawableResource,
)

/**
 * The tab bar: a translucent pill of tabs floating above the content, which stays visible through
 * it, blurred (see [tabBarMaterial]), and a round [action] button beside it. [hazeState] is the one
 * whose `hazeSource` sits on the content behind.
 */
@Composable
fun FloatingTabBar(
    items: ImmutableList<TabBarItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    action: TabBarAction,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    // The bar keeps its size whatever the system font size: it is chrome, and three labels at twice
    // their size no longer fit next to each other.
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Where each tab lies in the pill, so that the mark of the current one can travel between them.
            val spans = remember { mutableStateMapOf<String, ClosedFloatingPointRange<Float>>() }
            val markLeft = remember { Animatable(0f) }
            val markRight = remember { Animatable(0f) }
            var marked by remember { mutableStateOf(false) }
            val span = spans[selectedKey]
            LaunchedEffect(span) {
                if (span == null) return@LaunchedEffect
                if (marked) {
                    // The edge in front leads and the one behind follows: the mark stretches on its way.
                    launch { markLeft.animateTo(span.start, Motion.bouncy()) }
                    launch { markRight.animateTo(span.endInclusive, Motion.bouncy()) }
                } else {
                    markLeft.snapTo(span.start)
                    markRight.snapTo(span.endInclusive)
                    marked = true
                }
            }
            // The current tab is pressed into the bar, not coloured in.
            val mark = MaterialTheme.colorScheme.onSurface.copy(alpha = MARK_ALPHA)
            Row(
                modifier =
                    Modifier.barMaterial(hazeState).padding(4.dp).drawBehind {
                        if (marked) {
                            drawRoundRect(
                                color = mark,
                                topLeft = Offset(markLeft.value, 0f),
                                size = Size((markRight.value - markLeft.value).coerceAtLeast(0f), size.height),
                                cornerRadius = CornerRadius(size.height / 2f),
                            )
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    Tab(
                        item = item,
                        selected = item.key == selectedKey,
                        onClick = { onSelect(item.key) },
                        modifier =
                            Modifier.onGloballyPositioned {
                                val left = it.positionInParent().x
                                spans[item.key] = left..left + it.size.width
                            },
                    )
                }
            }
            ActionButton(action = action)
        }
    }
}

/**
 * The round button next to the pill: the one thing to do from anywhere, as opposed to somewhere to
 * go — here, setting the robot off or holding it. Dimmed and deaf while not [enabled].
 */
@Immutable
data class TabBarAction(
    val key: String,
    val label: String,
    val icon: DrawableResource,
    val enabled: Boolean,
    val onClick: () -> Unit,
    /** Whether what the button set off is going on right now, which it shows by not sitting still. */
    val active: Boolean = false,
)

@Composable
private fun Modifier.barMaterial(hazeState: HazeState): Modifier {
    val look = LocalCongatrolLook.current
    return dropShadow(CircleShape, Shadow(radius = 24.dp, color = BarShadow, offset = DpOffset(0.dp, 8.dp)))
        .clip(CircleShape)
        .tabBarMaterial(
            hazeState = hazeState,
            surface = MaterialTheme.colorScheme.surfaceContainerLow,
            rim = if (look.dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.7f),
        )
}

private val BarShadow = Color(0xFF140A28).copy(alpha = 0.16f)

/** The accent's own button: the one place the bar is not glass. */
@Composable
private fun ActionButton(
    action: TabBarAction,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .testTag("tab_${action.key}")
                .size(ActionButtonSize)
                .pressScale(interactions, pressed = 0.86f)
                .halo(active = action.active && action.enabled, color = colors.primary)
                // Dimmed by its colours rather than as a layer, which would cut the shadow off square.
                .dropShadow(
                    CircleShape,
                    Shadow(radius = 24.dp, color = if (action.enabled) BarShadow else Color.Transparent, offset = DpOffset(0.dp, 8.dp)),
                ).clip(CircleShape)
                .background(colors.primary.copy(alpha = if (action.enabled) 1f else DISABLED_ALPHA))
                .clickable(
                    interactionSource = interactions,
                    indication = ripple(),
                    enabled = action.enabled,
                    role = Role.Button,
                    onClickLabel = action.label,
                    onClick = action.onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        // Play turns into pause and back: the one icon spins out small while the other spins in.
        AnimatedContent(
            targetState = action.icon,
            transitionSpec = {
                (scaleIn(Motion.bouncy(), initialScale = 0.3f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.3f) + fadeOut())
            },
        ) { icon ->
            Icon(
                painter = painterResource(icon),
                contentDescription = action.label,
                tint = colors.onPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

private val ActionButtonSize = 60.dp
private const val DISABLED_ALPHA = 0.45f
private const val MARK_ALPHA = 0.09f
private const val SELECTED_ICON_SCALE = 1.14f

@Composable
private fun Tab(
    item: TabBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = MaterialTheme.colorScheme.onSurface
    // The tab gone to perks up for a moment; the mark behind it is the bar's to draw.
    val lift by animateFloatAsState(if (selected) SELECTED_ICON_SCALE else 1f, Motion.bouncy())
    Column(
        modifier =
            modifier
                .testTag("tab_${item.key}")
                .clip(CircleShape)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = null,
            tint = content,
            modifier =
                Modifier.size(24.dp).graphicsLayer {
                    scaleX = lift
                    scaleY = lift
                },
        )
        Text(
            text = item.label,
            color = content,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@PreviewCongatrol
@Preview
@Composable
internal fun FloatingTabBarPreview() {
    CongatrolTheme {
        Box(
            modifier = Modifier.fillMaxSize().congatrolBackground().padding(16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            FloatingTabBar(
                items =
                    persistentListOf(
                        TabBarItem("map", "Map", Res.drawable.ic_tab_map),
                        TabBarItem("drive", "Drive", Res.drawable.ic_tab_drive),
                        TabBarItem("robot", "Robot", Res.drawable.ic_tab_robot),
                    ),
                selectedKey = "map",
                onSelect = {},
                action = TabBarAction("start", "Start", Res.drawable.ic_play, enabled = true, onClick = {}),
                hazeState = rememberHazeState(),
            )
        }
    }
}
