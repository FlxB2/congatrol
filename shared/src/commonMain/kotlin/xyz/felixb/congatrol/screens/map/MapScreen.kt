package xyz.felixb.congatrol.screens.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import congatrol.shared.generated.resources.confirm_split
import congatrol.shared.generated.resources.confirm_zone
import congatrol.shared.generated.resources.dock
import congatrol.shared.generated.resources.hint_edit
import congatrol.shared.generated.resources.hint_go_to
import congatrol.shared.generated.resources.hint_rooms
import congatrol.shared.generated.resources.hint_split
import congatrol.shared.generated.resources.hint_zone
import congatrol.shared.generated.resources.ic_dock
import congatrol.shared.generated.resources.ic_edit
import congatrol.shared.generated.resources.ic_fan
import congatrol.shared.generated.resources.ic_go_to
import congatrol.shared.generated.resources.ic_recenter
import congatrol.shared.generated.resources.ic_rooms
import congatrol.shared.generated.resources.ic_stop
import congatrol.shared.generated.resources.ic_warning
import congatrol.shared.generated.resources.ic_water
import congatrol.shared.generated.resources.ic_zone
import congatrol.shared.generated.resources.map_edit_rooms
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
import congatrol.shared.generated.resources.robot_cancel
import congatrol.shared.generated.resources.robot_name_save
import congatrol.shared.generated.resources.room_join
import congatrol.shared.generated.resources.room_name_label
import congatrol.shared.generated.resources.room_rename
import congatrol.shared.generated.resources.room_rename_title
import congatrol.shared.generated.resources.room_split
import congatrol.shared.generated.resources.stop
import congatrol.shared.generated.resources.suction
import congatrol.shared.generated.resources.water
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import xyz.felixb.congatrol.components.CongatrolCard
import xyz.felixb.congatrol.components.Motion
import xyz.felixb.congatrol.components.appear
import xyz.felixb.congatrol.components.congatrolSegmentedColors
import xyz.felixb.congatrol.data.DemoRobotApi
import xyz.felixb.congatrol.screens.common.DisconnectedContent
import xyz.felixb.congatrol.screens.common.label
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
        onDraw = viewModel::onDraw,
        onConfirm = viewModel::onConfirm,
        roomActions =
            remember(viewModel) {
                RoomActions(
                    onEdit = viewModel::onEditRooms,
                    onSplit = viewModel::onSplitRoom,
                    onJoin = viewModel::onJoinRooms,
                    onRename = viewModel::onRenameRoom,
                )
            },
        onStop = viewModel::onStop,
        onDock = viewModel::onDock,
        onFan = viewModel::onFan,
        onWater = viewModel::onWater,
        onSaveAddress = viewModel::onSaveAddress,
        modifier = modifier,
        bottomInset = bottomInset,
    )
}

/** What can be done to the rooms themselves, as opposed to in them. */
@Immutable
class RoomActions(
    /** Starts working on the rooms, or stops. */
    val onEdit: () -> Unit = {},
    /** Goes on to drawing the line the picked room is split along. */
    val onSplit: () -> Unit = {},
    /** Makes one room of the two picked. */
    val onJoin: () -> Unit = {},
    val onRename: (id: String, name: String) -> Unit = { _, _ -> },
)

@Composable
fun MapScreenContent(
    state: MapUiState,
    onMode: (MapMode) -> Unit,
    onMapTap: (Offset) -> Unit,
    onDraw: (from: Offset, to: Offset) -> Unit,
    onConfirm: () -> Unit,
    roomActions: RoomActions,
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
            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                // The map is what the screen is for: it gets all the height there is, less what the
                // heading above it and the two rows under it need. What sets the robot up rather
                // than off — suction, water — is scrolled to.
                val mapHeight = (maxHeight - bottomInset - AroundMap).coerceAtLeast(MinMapHeight)
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(Modifier.height(4.dp))
                    StatusHeader(state, modifier = Modifier.appear(0))
                    // What a notice said is kept while it leaves: there is nothing to say by then.
                    var error by remember { mutableStateOf("") }
                    state.error?.let { error = it }
                    AnimatedVisibility(visible = state.error != null, enter = NoticeEnter, exit = NoticeExit) { Notice(error) }
                    AnimatedVisibility(visible = state.failed, enter = NoticeEnter, exit = NoticeExit) {
                        Notice(stringResource(Res.string.command_failed))
                    }
                    MapCard(
                        state = state,
                        onMapTap = onMapTap,
                        onDraw = onDraw,
                        onConfirm = onConfirm,
                        roomActions = roomActions,
                        modifier = Modifier.height(mapHeight).fillMaxWidth().appear(1),
                    )
                    if (state.modes.isNotEmpty()) ModeRow(state.modes, state.mode, onMode, modifier = Modifier.appear(2))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.appear(3)) {
                        OutlinedButton(onClick = onStop, enabled = state.canStop, modifier = Modifier.weight(1f).testTag("stop")) {
                            Icon(painterResource(Res.drawable.ic_stop), contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(Res.string.stop), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                        }
                        OutlinedButton(onClick = onDock, enabled = state.canDock, modifier = Modifier.weight(1f).testTag("dock")) {
                            Icon(painterResource(Res.drawable.ic_dock), contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(Res.string.dock), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                        }
                    }
                    if (state.fanPresets.isNotEmpty()) {
                        PresetRow(
                            Res.drawable.ic_fan,
                            stringResource(Res.string.suction),
                            state.fanPresets,
                            state.fan,
                            onFan,
                            Modifier.appear(4),
                        )
                    }
                    if (state.waterPresets.isNotEmpty()) {
                        PresetRow(
                            Res.drawable.ic_water,
                            stringResource(Res.string.water),
                            state.waterPresets,
                            state.water,
                            onWater,
                            Modifier.appear(5),
                        )
                    }
                    Spacer(Modifier.height(bottomInset))
                }
            }
    }
}

private const val EDITING_TILT = -35f
private val NoticeEnter = expandVertically(Motion.settle()) + fadeIn()
private val NoticeExit = shrinkVertically(Motion.settle()) + fadeOut()

/** The heading over the map, the mode row and the stop and dock buttons under it, and the gaps between. */
private val AroundMap = 252.dp

/** On a screen too short for all of that the map keeps this much, and the rows under it are scrolled to. */
private val MinMapHeight = 320.dp

/** What the robot is doing, large, with its name over it — if it has one — and its battery under it. */
@Composable
private fun StatusHeader(
    state: MapUiState.Ready,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (state.name.isNotEmpty()) {
            Text(
                text = state.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("robot_name"),
            )
        }
        // What the robot is doing now pushes what it did before out at the top.
        AnimatedContent(
            targetState = state.status,
            transitionSpec = {
                (slideInVertically(Motion.bouncy()) { it / 2 } + fadeIn()) togetherWith
                    (slideOutVertically { -it / 2 } + fadeOut()) using SizeTransform(clip = false)
            },
        ) { status ->
            Text(
                text = stringResource(status.label()),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.testTag("status"),
            )
        }
        state.battery?.let {
            Text(
                text = stringResource(if (state.charging) Res.string.battery_charging else Res.string.battery, it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
    onDraw: (from: Offset, to: Offset) -> Unit,
    onConfirm: () -> Unit,
    roomActions: RoomActions,
    modifier: Modifier = Modifier,
) {
    val viewState = rememberMapViewState()
    // The room being given a name, by its id, while the dialog that asks for it is up.
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
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
                cut = state.cut,
                target = state.target,
                onTap = onMapTap,
                onDraw = onDraw,
                modifier = Modifier.fillMaxSize(),
                viewState = viewState,
                active = state.working,
            )
            if (state.canEditRooms) {
                val editing = state.mode == MapMode.Edit || state.mode == MapMode.Split
                // The pencil is put to paper while the rooms are worked on.
                val tilt by animateFloatAsState(if (editing) EDITING_TILT else 0f, Motion.bouncy())
                FilledTonalIconButton(
                    onClick = roomActions.onEdit,
                    colors =
                        if (editing) {
                            IconButtonDefaults.filledIconButtonColors()
                        } else {
                            IconButtonDefaults.filledTonalIconButtonColors()
                        },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).testTag("map_edit"),
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_edit),
                        contentDescription = stringResource(Res.string.map_edit_rooms),
                        modifier = Modifier.size(20.dp).rotate(tilt),
                    )
                }
            }
            // Spelled out in full: in here there is a column's scope about too, whose own variant would be picked.
            androidx.compose.animation.AnimatedVisibility(
                visible = viewState.isMoved,
                enter = scaleIn(Motion.bouncy()) + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                FilledTonalIconButton(
                    onClick = viewState::recenter,
                    modifier = Modifier.padding(8.dp).testTag("map_recenter"),
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_recenter),
                        contentDescription = stringResource(Res.string.map_recenter),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            val confirm = state.confirmLabel()
            val hint = state.mode.hint()
            val prompt =
                when {
                    confirm != null -> MapPrompt.Confirm
                    state.mode == MapMode.Edit && state.selectedRooms.size == 1 -> MapPrompt.OneRoom
                    state.mode == MapMode.Edit && state.selectedRooms.size == 2 && state.canDivideRooms -> MapPrompt.TwoRooms
                    hint != null -> MapPrompt.Hint
                    else -> MapPrompt.None
                }
            // What a prompt said is kept while it leaves: by then the state has nothing to say for it.
            var confirmShown by remember { mutableStateOf("") }
            confirm?.let { confirmShown = it }
            var hintShown by remember { mutableStateOf<StringResource?>(null) }
            hint?.let { hintShown = it }
            AnimatedContent(
                targetState = prompt,
                transitionSpec = {
                    (scaleIn(Motion.bouncy(), initialScale = 0.7f) + fadeIn()) togetherWith
                        (scaleOut(targetScale = 0.85f) + fadeOut()) using SizeTransform(clip = false)
                },
                contentAlignment = Alignment.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            ) { shown ->
                when (shown) {
                    MapPrompt.Confirm ->
                        Button(onClick = onConfirm, modifier = Modifier.testTag("map_confirm")) {
                            Text(confirmShown, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    MapPrompt.OneRoom, MapPrompt.TwoRooms ->
                        RoomEditButtons(
                            single = shown == MapPrompt.OneRoom,
                            canRename = state.canRenameRooms,
                            canDivide = state.canDivideRooms,
                            onRename = { renaming = state.selectedRooms.singleOrNull() },
                            onSplit = roomActions.onSplit,
                            onJoin = roomActions.onJoin,
                        )
                    MapPrompt.Hint ->
                        hintShown?.let {
                            Text(
                                text = stringResource(it),
                                style = MaterialTheme.typography.labelLarge,
                                modifier =
                                    Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    MapPrompt.None -> Spacer(Modifier.size(0.dp))
                }
            }
        }
    }
    val room = state.map?.rooms?.firstOrNull { it.id == renaming }
    if (room != null) {
        RenameRoomDialog(
            name = room.name.takeUnless { it == room.id }.orEmpty(),
            onRename = {
                renaming = null
                roomActions.onRename(room.id, it)
            },
            onDismiss = { renaming = null },
        )
    }
}

/** What stands at the foot of the map: what a touch on it will do, or the button for what it did. */
private enum class MapPrompt { None, Hint, Confirm, OneRoom, TwoRooms }

/** What to do on the map in this mode, in a few words, or null where there is nothing to do. */
private fun MapMode.hint(): StringResource? =
    when (this) {
        MapMode.View -> null
        MapMode.Rooms -> Res.string.hint_rooms
        MapMode.Zone -> Res.string.hint_zone
        MapMode.GoTo -> Res.string.hint_go_to
        MapMode.Edit -> Res.string.hint_edit
        MapMode.Split -> Res.string.hint_split
    }

/** What can be done with the rooms picked: a [single] one is renamed or split, two are joined. */
@Composable
private fun RoomEditButtons(
    single: Boolean,
    canRename: Boolean,
    canDivide: Boolean,
    onRename: () -> Unit,
    onSplit: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (single) {
            if (canRename) {
                Button(onClick = onRename, modifier = Modifier.testTag("room_rename")) { Text(stringResource(Res.string.room_rename)) }
            }
            if (canDivide) {
                Button(onClick = onSplit, modifier = Modifier.testTag("room_split")) { Text(stringResource(Res.string.room_split)) }
            }
        } else if (canDivide) {
            Button(onClick = onJoin, modifier = Modifier.testTag("room_join")) { Text(stringResource(Res.string.room_join)) }
        }
    }
}

/** Asks what a room is to be called. The robot is told: a room's name is the robot's, not the app's. */
@Composable
private fun RenameRoomDialog(
    name: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(name) }
    val valid = text.isNotBlank() && text.trim() != name
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.room_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(Res.string.room_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onRename(text) }),
                modifier = Modifier.fillMaxWidth().testTag("room_name_field"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(text) }, enabled = valid, modifier = Modifier.testTag("room_name_save")) {
                Text(stringResource(Res.string.robot_name_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.robot_cancel)) } },
    )
}

/** What the button on the map says, or null while nothing has been pointed at. */
@Composable
private fun MapUiState.Ready.confirmLabel(): String? =
    when (mode) {
        MapMode.Rooms ->
            when (selectedRooms.size) {
                0 -> null
                1 ->
                    stringResource(
                        Res.string.confirm_room,
                        map
                            ?.rooms
                            ?.firstOrNull { it.id in selectedRooms }
                            ?.name
                            .orEmpty(),
                    )
                else -> stringResource(Res.string.confirm_rooms, selectedRooms.size)
            }
        MapMode.Zone -> zone?.let { stringResource(Res.string.confirm_zone) }
        MapMode.GoTo -> target?.let { stringResource(Res.string.confirm_go_to) }
        MapMode.Split -> cut?.let { stringResource(Res.string.confirm_split) }
        MapMode.View, MapMode.Edit -> null
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
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                onDraw = { _, _ -> },
                onConfirm = {},
                roomActions = RoomActions(),
                onStop = {},
                onDock = {},
                onFan = {},
                onWater = {},
                onSaveAddress = {},
            )
        }
    }
}
