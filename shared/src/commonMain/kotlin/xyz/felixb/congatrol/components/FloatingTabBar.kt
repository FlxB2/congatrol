package xyz.felixb.congatrol.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
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
            Row(
                modifier = Modifier.barMaterial(hazeState).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    Tab(item = item, selected = item.key == selectedKey, onClick = { onSelect(item.key) })
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
    Box(
        modifier =
            modifier
                .testTag("tab_${action.key}")
                .size(ActionButtonSize)
                .alpha(if (action.enabled) 1f else DISABLED_ALPHA)
                .dropShadow(CircleShape, Shadow(radius = 24.dp, color = BarShadow, offset = DpOffset(0.dp, 8.dp)))
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(enabled = action.enabled, role = Role.Button, onClickLabel = action.label, onClick = action.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(action.icon),
            contentDescription = action.label,
            tint = colors.onPrimary,
            modifier = Modifier.size(28.dp),
        )
    }
}

private val ActionButtonSize = 60.dp
private const val DISABLED_ALPHA = 0.45f

@Composable
private fun Tab(
    item: TabBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The current tab is pressed into the bar, not coloured in.
    val content = MaterialTheme.colorScheme.onSurface
    val highlight by animateColorAsState(content.copy(alpha = if (selected) 0.09f else 0f))
    Column(
        modifier =
            modifier
                .testTag("tab_${item.key}")
                .clip(CircleShape)
                .background(highlight)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(24.dp),
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
