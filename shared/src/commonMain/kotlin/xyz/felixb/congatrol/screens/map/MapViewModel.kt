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
}

sealed interface MapUiState {
    data object Loading : MapUiState

    data class Disconnected(
        val ui: DisconnectedUi,
    ) : MapUiState

    @Immutable
    data class Ready(
        val status: RobotStatus,
        /** What is wrong, in the robot's words, or null. */
        val error: String?,
        val battery: Int?,
        val charging: Boolean,
        val map: RobotMap?,
        /** The ways of pointing at the map this robot understands; [MapMode.View] is not among them. */
        val modes: ImmutableList<MapMode>,
        val mode: MapMode,
        val selectedRooms: ImmutableSet<String>,
        /** The zone drawn so far, in cells. */
        val zone: Rect?,
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
    ) : MapUiState
}

class MapViewModel(
    private val repository: RobotRepository,
) : ViewModel() {
    /** What the user has pointed at and not sent yet. */
    private data class Selection(
        val mode: MapMode = MapMode.View,
        val rooms: Set<String> = emptySet(),
        val zone: Rect? = null,
        val target: Offset? = null,
    )

    private val selection = MutableStateFlow(Selection())
    private val failed = MutableStateFlow(false)

    val state: StateFlow<MapUiState> =
        combine(repository.connection, selection, failed) { connection, selection, failed ->
            when (connection) {
                is Connection.Connected -> ready(connection.robot, selection, failed)
                else -> MapUiState.Disconnected(connection.disconnectedUi() ?: return@combine MapUiState.Loading)
            }
            // The upstream asks the robot every two seconds, so it only runs while somebody looks.
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), MapUiState.Loading)

    private fun ready(
        robot: RobotSnapshot,
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
        return MapUiState.Ready(
            status = status,
            error = robot.state.error,
            battery = robot.state.battery,
            charging = robot.state.charging,
            map = robot.map,
            modes = modes.toImmutableList(),
            mode = selection.mode.takeIf { it in modes } ?: MapMode.View,
            selectedRooms = selection.rooms.toImmutableSet(),
            zone = selection.zone,
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
            MapMode.View, MapMode.Zone -> Unit
        }
    }

    fun onZone(zone: Rect?) {
        selection.update { it.copy(zone = zone) }
    }

    /** Sends what was pointed at: the rooms, the zone or the place to go. */
    fun onConfirm() {
        val ready = state.value as? MapUiState.Ready ?: return
        val cellSize = ready.map?.cellSize ?: return
        command(clearSelection = true) {
            when (ready.mode) {
                MapMode.Rooms -> repository.cleanRooms(ready.selectedRooms)
                MapMode.Zone -> repository.cleanZone(ready.zone ?: return@command Result.success(Unit), cellSize)
                MapMode.GoTo -> repository.goTo(ready.target ?: return@command Result.success(Unit), cellSize)
                MapMode.View -> Result.success(Unit)
            }
        }
    }

    fun onStop() = command { repository.stop() }

    fun onDock() = command { repository.dock() }

    fun onFan(preset: String) = command { repository.setFanSpeed(preset) }

    fun onWater(preset: String) = command { repository.setWaterGrade(preset) }

    fun onSaveAddress(address: String) = repository.setAddress(address)

    private fun command(
        clearSelection: Boolean = false,
        send: suspend () -> Result<Unit>,
    ) {
        viewModelScope.launch {
            if (send().isSuccess) {
                if (clearSelection) selection.value = Selection()
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
    }
}

/** Sample state for previews: nothing here touches the network. */
internal fun sampleMapReady(
    map: RobotMap?,
    mode: MapMode = MapMode.View,
): MapUiState.Ready =
    MapUiState.Ready(
        status = RobotStatus.Docked,
        error = null,
        battery = 86,
        charging = true,
        map = map,
        modes = persistentListOf(MapMode.Rooms, MapMode.Zone, MapMode.GoTo),
        mode = mode,
        selectedRooms = if (mode == MapMode.Rooms) persistentSetOf("2") else persistentSetOf(),
        zone = null,
        target = null,
        fanPresets = persistentListOf("off", "low", "medium", "high"),
        fan = "medium",
        waterPresets = persistentListOf("off", "low", "medium", "high"),
        water = "low",
        canStop = false,
        canDock = false,
        failed = false,
    )
