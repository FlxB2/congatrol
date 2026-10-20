package xyz.felixb.congatrol

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import congatrol.shared.generated.resources.Res
import congatrol.shared.generated.resources.action_pause
import congatrol.shared.generated.resources.action_resume
import congatrol.shared.generated.resources.action_start
import congatrol.shared.generated.resources.ic_pause
import congatrol.shared.generated.resources.ic_play
import congatrol.shared.generated.resources.ic_tab_drive
import congatrol.shared.generated.resources.ic_tab_map
import congatrol.shared.generated.resources.ic_tab_robot
import congatrol.shared.generated.resources.tab_drive
import congatrol.shared.generated.resources.tab_map
import congatrol.shared.generated.resources.tab_robot
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import xyz.felixb.congatrol.components.FloatingTabBar
import xyz.felixb.congatrol.components.FloatingTabBarClearance
import xyz.felixb.congatrol.components.TabBarAction
import xyz.felixb.congatrol.components.TabBarItem
import xyz.felixb.congatrol.screens.drive.DriveScreen
import xyz.felixb.congatrol.screens.licenses.LicenseDetailScreen
import xyz.felixb.congatrol.screens.licenses.LicensesScreen
import xyz.felixb.congatrol.screens.map.MapScreen
import xyz.felixb.congatrol.screens.robot.RobotScreen
import xyz.felixb.congatrol.theme.CongatrolTheme
import xyz.felixb.congatrol.theme.congatrolBackground
import xyz.felixb.congatrol.util.tapHaptics

private enum class Tab(
    val route: String,
    val label: StringResource,
    val icon: DrawableResource,
) {
    Map("map", Res.string.tab_map, Res.drawable.ic_tab_map),
    Drive("drive", Res.string.tab_drive, Res.drawable.ic_tab_drive),
    Robot("robot", Res.string.tab_robot, Res.drawable.ic_tab_robot),
}

/** Not tabs: reached from the Robot tab, and left by going back. */
private const val LICENSES_ROUTE = "licenses"
private const val LICENSE_ROUTE = "license"

private const val SCREEN_OUT_MILLIS = 90
private const val SCREEN_IN_MILLIS = 180

@Composable
fun App(modifier: Modifier = Modifier) {
    CongatrolTheme {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Box(modifier = modifier.tapHaptics()) {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route ?: Tab.Map.route
                // Which library the licence page is about. Kept here rather than in the route: a
                // library's id is a Maven coordinate, and not everything in one is fit for a path.
                var shownLibrary by rememberSaveable { mutableStateOf("") }

                fun open(route: String) {
                    navController.navigate(route) {
                        // One copy of each destination, and back from any of them leaves the app.
                        popUpTo(Tab.Map.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                val hazeState = rememberHazeState()
                // The bar sits a little above the system's own navigation, and never flush with the edge.
                val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val barBottom = (navigationBottom + 8.dp).coerceAtLeast(16.dp)
                val bottomInset = barBottom + FloatingTabBarClearance
                Box(modifier = Modifier.fillMaxSize()) {
                    // What the bar's glass shows: the screens, on an opaque ground so that the blurred
                    // copy covers the sharp one instead of letting it through. They run to the bottom
                    // edge, under the bar, and keep `bottomInset` free below their content themselves.
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .hazeSource(hazeState)
                                .congatrolBackground()
                                .statusBarsPadding(),
                    ) {
                        // The screens have no ground of their own, so two of them cross-fading would be
                        // read through each other: the old one leaves quickly, then the new one comes.
                        NavHost(
                            navController = navController,
                            startDestination = Tab.Map.route,
                            enterTransition = { fadeIn(tween(SCREEN_IN_MILLIS, delayMillis = SCREEN_OUT_MILLIS)) },
                            exitTransition = { fadeOut(tween(SCREEN_OUT_MILLIS)) },
                        ) {
                            composable(Tab.Map.route) { MapScreen(bottomInset = bottomInset) }
                            composable(Tab.Drive.route) { DriveScreen(bottomInset = bottomInset) }
                            composable(Tab.Robot.route) {
                                RobotScreen(onLicensesClick = { navController.navigate(LICENSES_ROUTE) }, bottomInset = bottomInset)
                            }
                            composable(LICENSES_ROUTE) {
                                LicensesScreen(
                                    onBack = { navController.popBackStack() },
                                    onLibraryClick = { id ->
                                        shownLibrary = id
                                        navController.navigate(LICENSE_ROUTE)
                                    },
                                    bottomInset = bottomInset,
                                )
                            }
                            composable(LICENSE_ROUTE) {
                                LicenseDetailScreen(
                                    libraryId = shownLibrary,
                                    onBack = { navController.popBackStack() },
                                    bottomInset = bottomInset,
                                )
                            }
                        }
                    }
                    FloatingTabBar(
                        items = Tab.entries.map { TabBarItem(it.route, stringResource(it.label), it.icon) }.toImmutableList(),
                        // The licences are part of the Robot tab, which is where they are reached from.
                        selectedKey =
                            if (currentRoute == LICENSES_ROUTE ||
                                currentRoute == LICENSE_ROUTE
                            ) {
                                Tab.Robot.route
                            } else {
                                currentRoute
                            },
                        onSelect = ::open,
                        action = robotAction(),
                        hazeState = hazeState,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = barBottom),
                    )
                }
            }
        }
    }
}

/** The bar's round button, as the robot's state has it: start, pause or resume. */
@Composable
private fun robotAction(): TabBarAction {
    val viewModel = koinViewModel<ActionViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val (label, icon) =
        when (state) {
            ActionUiState.Pause -> Res.string.action_pause to Res.drawable.ic_pause
            ActionUiState.Resume -> Res.string.action_resume to Res.drawable.ic_play
            ActionUiState.Start, ActionUiState.Unavailable -> Res.string.action_start to Res.drawable.ic_play
        }
    return TabBarAction(
        key = "action",
        label = stringResource(label),
        icon = icon,
        enabled = state != ActionUiState.Unavailable,
        onClick = viewModel::onClick,
        // Pause is what the button offers while the robot is at work.
        active = state == ActionUiState.Pause,
    )
}
