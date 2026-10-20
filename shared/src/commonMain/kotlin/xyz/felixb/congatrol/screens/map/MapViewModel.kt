package xyz.felixb.congatrol.screens.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.felixb.congatrol.data.Connection
import xyz.felixb.congatrol.data.RobotMap
import xyz.felixb.congatrol.data.RobotRepository
import xyz.felixb.congatrol.data.RobotSnapshot
import xyz.felixb.congatrol.data.RobotStatus
import xyz.felixb.congatrol.data.rectBetween
import xyz.felixb.congatrol.screens.common.DisconnectedUi
import xyz.felixb.congatrol.screens.common.disconnectedUi

/** What a touch on the map means. */
enum class MapMode {
    /** Nothing: the map is only looked at. */
    View,

    /** A tap picks a room, or lets it go. */
    Rooms,

    /** A drag draws the zone to clean. */
    Zone,

    /** A tap says where the robot should go. */
    GoTo,

    /** The rooms themselves are worked on: a tap picks one to rename or split, or two to join. */
    Edit,

    /** A drag draws the line along which the picked room is cut in two. */
    Split,
    ;

    /** Whether one finger draws on the map instead of moving it. */
    val draws: Boolean get() = this == Zone || this == Split

    /** Whether the rooms are what is being pointed at, and so wear their names. */
    val showsRooms: Boolean get() = this == Rooms || this == Edit || this == Split
}

/** A line across the map, in cells. */
@Immutable
data class MapCut(
    val from: Offset,
    val to: Offset,
)

sealed interface MapUiState {
    data object Loading : MapUiState

    data class Disconnected(
        val ui: DisconnectedUi,
    ) : MapUiState

    @Immutable
    data class Ready(
        /** What the user calls the robot, or empty. */
        val name: String,
        val status: RobotStatus,
        /** What is wrong, in the robot's words, or null. */
        val error: String?,
        val battery: Int?,
        val charging: Boolean,
        val map: RobotMap?,
        /** The ways of pointing at the map to send the robot somewhere, as far as this robot understands them. */
        val modes: ImmutableList<MapMode>,
        val mode: MapMode,
        /** Whether the robot lets its rooms be split and joined, and whether it lets them be named. */
        val canDivideRooms: Boolean,
        val canRenameRooms: Boolean,
        val selectedRooms: ImmutableSet<String>,
        /** The zone drawn so far, in cells. */
        val zone: Rect?,
        /** The line drawn so far to split the picked room along. */
        val cut: MapCut?,
        /** Where the robot was told to go, in cells. */
        val target: Offset?,
        val fanPresets: ImmutableList<String>,
        val fan: String?,
        val waterPresets: ImmutableList<String>,
        val water: String?,
        val canStop: Boolean,
        val canDock: Boolean,
        /** The robot did not take the last command. */
        val failed: Boolean,
    ) : MapUiState {
        /** Whether there is anything about the rooms this robot lets the app change. */
        val canEditRooms: Boolean get() = canDivideRooms || canRenameRooms

        /** Whether the robot is out and about on its own, which the map shows on its marker. */
        val working: Boolean
            get() = status == RobotStatus.Cleaning || status == RobotStatus.Returning || status == RobotStatus.Moving
    }
}

class MapViewModel(
    private val repository: RobotRepository,
) : ViewModel() {
    /** What the user has pointed at and not sent yet. */
    private data class Selection(
        val mode: MapMode = MapMode.View,
        val rooms: Set<String> = emptySet(),
        val zone: Rect? = null,
        val cut: MapCut? = null,
        val target: Offset? = null,
    )

    private val selection = MutableStateFlow(Selection())
    private val failed = MutableStateFlow(false)

    val state: StateFlow<MapUiState> =
        combine(repository.connection, repository.name, selection, failed) { connection, name, selection, failed ->
            when (connection) {
                is Connection.Connected -> ready(connection.robot, name, selection, failed)
                else -> MapUiState.Disconnected(connection.disconnectedUi() ?: return@combine MapUiState.Loading)
            }
            // The upstream asks the robot every two seconds, so it only runs while somebody looks.
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), MapUiState.Loading)

    private fun ready(
        robot: RobotSnapshot,
        name: String,
        selection: Selection,
        failed: Boolean,
    ): MapUiState.Ready {
        val status = robot.state.status
        val modes =
            buildList {
                if ("MapSegmentationCapability" in robot.capabilities && robot.map?.rooms?.isNotEmpty() == true) add(MapMode.Rooms)
                if ("ZoneCleaningCapability" in robot.capabilities) add(MapMode.Zone)
                if ("GoToLocationCapability" in robot.capabilities) add(MapMode.GoTo)
            }
        val hasRooms = robot.map?.rooms?.isNotEmpty() == true
        val canDivide = hasRooms && "MapSegmentEditCapability" in robot.capabilities
        val canRename = hasRooms && "MapSegmentRenameCapability" in robot.capabilities
        val mode =
            when (selection.mode) {
                MapMode.View -> MapMode.View
                MapMode.Edit -> if (canDivide || canRename) MapMode.Edit else MapMode.View
                MapMode.Split -> if (canDivide) MapMode.Split else MapMode.View
                else -> selection.mode.takeIf { it in modes } ?: MapMode.View
            }
        // A room can be gone by the time the map is read again: joined into another, say.
        val rooms =
            robot.map
                ?.rooms
                .orEmpty()
                .map { it.id }
        return MapUiState.Ready(
            name = name,
            status = status,
            error = robot.state.error,
            battery = robot.state.battery,
            charging = robot.state.charging,
            map = robot.map,
            modes = modes.toImmutableList(),
            mode = mode,
            canDivideRooms = canDivide,
            canRenameRooms = canRename,
            selectedRooms = selection.rooms.filter { it in rooms }.toImmutableSet(),
            zone = selection.zone,
            cut = selection.cut,
            target = selection.target,
            fanPresets = robot.fanPresets.toImmutableList(),
            fan = robot.state.fanSpeed,
            waterPresets = robot.waterPresets.toImmutableList(),
            water = robot.state.waterGrade,
            canStop = status != RobotStatus.Docked && status != RobotStatus.Idle,
            canDock = status != RobotStatus.Docked && status != RobotStatus.Returning,
            failed = failed,
        )
    }

    /** Picks [mode], or leaves it when it is the one already picked. What was pointed at is forgotten. */
    fun onMode(mode: MapMode) {
        selection.update { Selection(mode = if (it.mode == mode) MapMode.View else mode) }
    }

    /** A tap on the map at [cell]. */
    fun onMapTap(cell: Offset) {
        val ready = state.value as? MapUiState.Ready ?: return
        when (ready.mode) {
            MapMode.Rooms -> {
                val room = ready.map?.roomAt(cell) ?: return
                selection.update { it.copy(rooms = if (room.id in it.rooms) it.rooms - room.id else it.rooms + room.id) }
            }
            MapMode.GoTo -> selection.update { it.copy(target = cell) }
            MapMode.Edit -> {
                val room = ready.map?.roomAt(cell) ?: return
                selection.update {
                    when {
                        room.id in it.rooms -> it.copy(rooms = it.rooms - room.id)
                        // Two is as many as anything is done with; a third takes the place of the first.
                        it.rooms.size >= MAX_EDITED_ROOMS -> it.copy(rooms = it.rooms.drop(1).toSet() + room.id)
                        else -> it.copy(rooms = it.rooms + room.id)
                    }
                }
            }
            MapMode.View, MapMode.Zone, MapMode.Split -> Unit
        }
    }

    /** One finger dragged from [from] to [to], in cells: the zone to clean, or the line to split along. */
    fun onDraw(
        from: Offset,
        to: Offset,
    ) {
        selection.update {
            when (it.mode) {
                MapMode.Zone -> it.copy(zone = rectBetween(from, to))
                MapMode.Split -> it.copy(cut = MapCut(from, to))
                else -> it
            }
        }
    }

    /** Sends what was pointed at: the rooms, the zone, the place to go, or the line to split a room along. */
    fun onConfirm() {
        val ready = state.value as? MapUiState.Ready ?: return
        val cellSize = ready.map?.cellSize ?: return
        val nothing = Result.success(Unit)
        command(then = if (ready.mode == MapMode.Split) Selection(mode = MapMode.Edit) else Selection()) {
            when (ready.mode) {
                MapMode.Rooms -> repository.cleanRooms(ready.selectedRooms)
                MapMode.Zone -> repository.cleanZone(ready.zone ?: return@command nothing, cellSize)
                MapMode.GoTo -> repository.goTo(ready.target ?: return@command nothing, cellSize)
                MapMode.Split -> {
                    val room = ready.selectedRooms.singleOrNull() ?: return@command nothing
                    val cut = ready.cut ?: return@command nothing
                    repository.splitRoom(room, cut.from, cut.to, cellSize)
                }
                MapMode.View, MapMode.Edit -> nothing
            }
        }
    }

    /** Starts working on the rooms, or stops. What was pointed at is forgotten. */
    fun onEditRooms() {
        selection.update { Selection(mode = if (it.mode == MapMode.Edit || it.mode == MapMode.Split) MapMode.View else MapMode.Edit) }
    }

    /** Goes on to drawing the line the one picked room is split along. */
    fun onSplitRoom() {
        selection.update { if (it.mode == MapMode.Edit && it.rooms.size == 1) it.copy(mode = MapMode.Split, cut = null) else it }
    }

    fun onJoinRooms() {
        val ready = state.value as? MapUiState.Ready ?: return
        // The room that has a name goes first: where the robot keeps one of the two, it is that one.
        val (first, second) =
            ready.map
                ?.rooms
                .orEmpty()
                .filter { it.id in ready.selectedRooms }
                .sortedBy { it.name == it.id }
                .takeIf { it.size == MAX_EDITED_ROOMS } ?: return
        command(then = Selection(mode = MapMode.Edit)) { repository.joinRooms(first.id, second.id) }
    }

    fun onRenameRoom(
        id: String,
        name: String,
    ) = command(then = Selection(mode = MapMode.Edit)) { repository.renameRoom(id, name) }

    fun onStop() = command { repository.stop() }

    fun onDock() = command { repository.dock() }

    fun onFan(preset: String) = command { repository.setFanSpeed(preset) }

    fun onWater(preset: String) = command { repository.setWaterGrade(preset) }

    fun onSaveAddress(address: String) = repository.setAddress(address)

    /** Sends a command; once the robot took it, what is pointed at becomes [then], if that is given. */
    private fun command(
        then: Selection? = null,
        send: suspend () -> Result<Unit>,
    ) {
        viewModelScope.launch {
            if (send().isSuccess) {
                then?.let { selection.value = it }
            } else {
                failed.value = true
                delay(FAILED_SHOWN_MILLIS)
                failed.value = false
            }
        }
    }

    private companion object {
        const val STOP_AFTER_MILLIS = 5_000L
        const val FAILED_SHOWN_MILLIS = 4_000L
        const val MAX_EDITED_ROOMS = 2
    }
}

/** Sample state for previews: nothing here touches the network. */
internal fun sampleMapReady(
    map: RobotMap?,
    mode: MapMode = MapMode.View,
): MapUiState.Ready =
    MapUiState.Ready(
        name = "Rosie",
        status = RobotStatus.Docked,
        error = null,
        battery = 86,
        charging = true,
        map = map,
        modes = persistentListOf(MapMode.Rooms, MapMode.Zone, MapMode.GoTo),
        mode = mode,
        canDivideRooms = true,
        canRenameRooms = true,
        selectedRooms = if (mode.showsRooms) persistentSetOf("2") else persistentSetOf(),
        zone = null,
        cut = null,
        target = null,
        fanPresets = persistentListOf("off", "low", "medium", "high"),
        fan = "medium",
        waterPresets = persistentListOf("off", "low", "medium", "high"),
        water = "low",
        canStop = false,
        canDock = false,
        failed = false,
    )
