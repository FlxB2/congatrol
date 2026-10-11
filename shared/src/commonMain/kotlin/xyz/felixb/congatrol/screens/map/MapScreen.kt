package xyz.felixb.congatrol.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import congatrol.shared.generated.resources.Res
import congatrol.shared.generated.resources.battery
import congatrol.shared.generated.resources.battery_charging
import congatrol.shared.generated.resources.command_failed
import congatrol.shared.generated.resources.confirm_go_to
import congatrol.shared.generated.resources.confirm_room
import congatrol.shared.generated.resources.confirm_rooms
import congatrol.shared.generated.resources.confirm_zone
import congatrol.shared.generated.resources.dock
import congatrol.shared.generated.resources.hint_go_to
import congatrol.shared.generated.resources.hint_rooms
import congatrol.shared.generated.resources.hint_zone
import congatrol.shared.generated.resources.ic_dock
import congatrol.shared.generated.resources.ic_fan
import congatrol.shared.generated.resources.ic_go_to
import congatrol.shared.generated.resources.ic_recenter
import congatrol.shared.generated.resources.ic_rooms
import congatrol.shared.generated.resources.ic_stop
import congatrol.shared.generated.resources.ic_warning
import congatrol.shared.generated.resources.ic_water
import congatrol.shared.generated.resources.ic_zone
import congatrol.shared.generated.resources.map_empty
import congatrol.shared.generated.resources.map_recenter
import congatrol.shared.generated.resources.mode_go_to
import congatrol.shared.generated.resources.mode_rooms
import congatrol.shared.generated.resources.mode_zone
import congatrol.shared.generated.resources.preset_high
import congatrol.shared.generated.resources.preset_low
import congatrol.shared.generated.resources.preset_max
import congatrol.shared.generated.resources.preset_medium
import congatrol.shared.generated.resources.preset_min
import congatrol.shared.generated.resources.preset_off
import congatrol.shared.generated.resources.preset_turbo
import congatrol.shared.generated.resources.status_cleaning
import congatrol.shared.generated.resources.status_docked
import congatrol.shared.generated.resources.status_error
import congatrol.shared.generated.resources.status_idle
import congatrol.shared.generated.resources.status_manual_control
import congatrol.shared.generated.resources.status_moving
import congatrol.shared.generated.resources.status_paused
import congatrol.shared.generated.resources.status_returning
import congatrol.shared.generated.resources.status_unknown
import congatrol.shared.generated.resources.stop
import congatrol.shared.generated.resources.suction
import congatrol.shared.generated.resources.water
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import xyz.felixb.congatrol.components.CongatrolCard
import xyz.felixb.congatrol.components.congatrolSegmentedColors
import xyz.felixb.congatrol.data.DemoRobotApi
import xyz.felixb.congatrol.data.RobotStatus
import xyz.felixb.congatrol.screens.common.DisconnectedContent
import xyz.felixb.congatrol.theme.CongatrolBackground
import xyz.felixb.congatrol.theme.CongatrolTheme
import xyz.felixb.congatrol.theme.PreviewCongatrol

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val viewModel = koinViewModel<MapViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    MapScreenContent(
        state = state,
        onMode = viewModel::onMode,
        onMapTap = viewModel::onMapTap,
        onZone = viewModel::onZone,
        onConfirm = viewModel::onConfirm,
        onStop = viewModel::onStop,
        onDock = viewModel::onDock,
        onFan = viewModel::onFan,
        onWater = viewModel::onWater,
        onSaveAddress = viewModel::onSaveAddress,
        modifier = modifier,
        bottomInset = bottomInset,
    )
}

@Composable
fun MapScreenContent(
    state: MapUiState,
    onMode: (MapMode) -> Unit,
    onMapTap: (Offset) -> Unit,
    onZone: (Rect?) -> Unit,
    onConfirm: () -> Unit,
    onStop: () -> Unit,
    onDock: () -> Unit,
    onFan: (String) -> Unit,
    onWater: (String) -> Unit,
    onSaveAddress: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    when (state) {
        MapUiState.Loading -> Box(modifier.fillMaxSize())
        is MapUiState.Disconnected -> DisconnectedContent(state.ui, onSaveAddress, modifier, bottomInset)
        is MapUiState.Ready ->
            Column(
                modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                StatusHeader(state)
                state.error?.let { Notice(it) }
                if (state.failed) Notice(stringResource(Res.string.command_failed))
                MapCard(
                    state = state,
                    onMapTap = onMapTap,
                    onZone = onZone,
                    onConfirm = onConfirm,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                if (state.modes.isNotEmpty()) ModeRow(state.modes, state.mode, onMode)
                if (state.fanPresets.isNotEmpty()) {
                    PresetRow(Res.drawable.ic_fan, stringResource(Res.string.suction), state.fanPresets, state.fan, onFan)
                }
                if (state.waterPresets.isNotEmpty()) {
                    PresetRow(Res.drawable.ic_water, stringResource(Res.string.water), state.waterPresets, state.water, onWater)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onStop, enabled = state.canStop, modifier = Modifier.weight(1f).testTag("stop")) {
                        Icon(painterResource(Res.drawable.ic_stop), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.stop), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                    }
                    OutlinedButton(onClick = onDock, enabled = state.canDock, modifier = Modifier.weight(1f).testTag("dock")) {
                        Icon(painterResource(Res.drawable.ic_dock), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.dock), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(bottomInset))
            }
    }
}

/** What the robot is doing, large, and how its battery is under it. */
@Composable
private fun StatusHeader(
    state: MapUiState.Ready,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(state.status.label()),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("status"),
        )
        state.battery?.let {
            Text(
                text = stringResource(if (state.charging) Res.string.battery_charging else Res.string.battery, it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RobotStatus.label() =
    when (this) {
        RobotStatus.Docked -> Res.string.status_docked
        RobotStatus.Idle -> Res.string.status_idle
        RobotStatus.Cleaning -> Res.string.status_cleaning
        RobotStatus.Paused -> Res.string.status_paused
        RobotStatus.Returning -> Res.string.status_returning
        RobotStatus.Moving -> Res.string.status_moving
        RobotStatus.ManualControl -> Res.string.status_manual_control
        RobotStatus.Error -> Res.string.status_error
        RobotStatus.Unknown -> Res.string.status_unknown
    }

/** Something the user should know: what is wrong with the robot, or that it did not listen. */
@Composable
private fun Notice(
    text: String,
    modifier: Modifier = Modifier,
) {
    CongatrolCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(Res.drawable.ic_warning), contentDescription = null, modifier = Modifier.size(22.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The map in its card, with what a touch on it means spelled out and the button that sends it. */
@Composable
private fun MapCard(
    state: MapUiState.Ready,
    onMapTap: (Offset) -> Unit,
    onZone: (Rect?) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewState = rememberMapViewState()
    CongatrolCard(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            val map = state.map
            if (map?.bounds == null) {
                Text(
                    text = stringResource(Res.string.map_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                return@Box
            }
            RobotMapView(
                map = map,
                mode = state.mode,
                selectedRooms = state.selectedRooms,
                zone = state.zone,
                target = state.target,
                onTap = onMapTap,
                onZone = onZone,
                modifier = Modifier.fillMaxSize(),
                viewState = viewState,
            )
            if (viewState.isMoved) {
                FilledTonalIconButton(
                    onClick = viewState::recenter,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("map_recenter"),
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_recenter),
                        contentDescription = stringResource(Res.string.map_recenter),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            val confirm = state.confirmLabel()
            when {
                confirm != null ->
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).testTag("map_confirm"),
                    ) {
                        Text(confirm, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                state.mode != MapMode.View ->
                    Text(
                        text =
                            stringResource(
                                when (state.mode) {
                                    MapMode.Rooms -> Res.string.hint_rooms
                                    MapMode.Zone -> Res.string.hint_zone
                                    else -> Res.string.hint_go_to
                                },
                            ),
                        style = MaterialTheme.typography.labelLarge,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
            }
        }
    }
}

/** What the button on the map says, or null while nothing has been pointed at. */
@Composable
private fun MapUiState.Ready.confirmLabel(): String? =
    when (mode) {
        MapMode.Rooms ->
            when (selectedRooms.size) {
                0 -> null
                1 -> stringResource(Res.string.confirm_room, map?.rooms?.firstOrNull { it.id in selectedRooms }?.name.orEmpty())
                else -> stringResource(Res.string.confirm_rooms, selectedRooms.size)
            }
        MapMode.Zone -> zone?.let { stringResource(Res.string.confirm_zone) }
        MapMode.GoTo -> target?.let { stringResource(Res.string.confirm_go_to) }
        MapMode.View -> null
    }

/** The ways of pointing at the map. Picking the one that is on turns it off again. */
@Composable
private fun ModeRow(
    modes: ImmutableList<MapMode>,
    selected: MapMode,
    onMode: (MapMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                colors = congatrolSegmentedColors(),
                icon = { Icon(painterResource(mode.icon()), contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("mode_${mode.name}"),
            ) {
                Text(stringResource(mode.label()), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun MapMode.icon(): DrawableResource =
    when (this) {
        MapMode.Rooms -> Res.drawable.ic_rooms
        MapMode.Zone -> Res.drawable.ic_zone
        else -> Res.drawable.ic_go_to
    }

private fun MapMode.label() =
    when (this) {
        MapMode.Rooms -> Res.string.mode_rooms
        MapMode.Zone -> Res.string.mode_zone
        else -> Res.string.mode_go_to
    }

/** One of the robot's dials — suction, water — as a row of its steps. */
@Composable
private fun PresetRow(
    icon: DrawableResource,
    label: String,
    presets: ImmutableList<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(24.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            presets.forEachIndexed { index, preset ->
                SegmentedButton(
                    selected = preset == selected,
                    onClick = { onSelect(preset) },
                    shape = SegmentedButtonDefaults.itemShape(index, presets.size),
                    colors = congatrolSegmentedColors(),
                    icon = {},
                ) {
                    Text(presetLabel(preset), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A preset by a name of the app's own where it has one, and by the robot's where it has not. */
@Composable
private fun presetLabel(preset: String): String =
    when (preset) {
        "off" -> stringResource(Res.string.preset_off)
        "min" -> stringResource(Res.string.preset_min)
        "low" -> stringResource(Res.string.preset_low)
        "medium" -> stringResource(Res.string.preset_medium)
        "high" -> stringResource(Res.string.preset_high)
        "max" -> stringResource(Res.string.preset_max)
        "turbo" -> stringResource(Res.string.preset_turbo)
        else -> preset.replaceFirstChar { it.uppercase() }
    }

@PreviewCongatrol
@Preview
@Composable
internal fun MapScreenContentPreview() {
    CongatrolTheme {
        CongatrolBackground {
            MapScreenContent(
                state = sampleMapReady(DemoRobotApi.sampleMap(), mode = MapMode.Rooms),
                onMode = {},
                onMapTap = {},
                onZone = {},
                onConfirm = {},
                onStop = {},
                onDock = {},
                onFan = {},
                onWater = {},
                onSaveAddress = {},
            )
        }
    }
}
