package xyz.felixb.congatrol.screens.robot

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.felixb.congatrol.data.Connection
import xyz.felixb.congatrol.data.Consumable
import xyz.felixb.congatrol.data.RobotDetails
import xyz.felixb.congatrol.data.RobotRepository
import xyz.felixb.congatrol.screens.common.DisconnectedUi
import xyz.felixb.congatrol.screens.common.disconnectedUi
import xyz.felixb.congatrol.screens.common.shown

/** Something that wears out, as a row: [key] names it to the ViewModel, [kind] to the UI. */
@Immutable
data class ConsumableUi(
    val key: String,
    val kind: Kind,
    /** What the robot calls it, for a kind the app has no name for. */
    val rawName: String,
    val remaining: Int,
    /** Whether [remaining] is hours; percent otherwise. */
    val hours: Boolean,
) {
    enum class Kind { MainBrush, SideBrush, Filter, Mop, Sensor, Other }
}

sealed interface RobotUiState {
    data object Loading : RobotUiState

    data class Disconnected(
        val ui: DisconnectedUi,
    ) : RobotUiState

    @Immutable
    data class Ready(
        /** What the user calls the robot, or empty. */
        val name: String,
        /** The robot's address as the user would type it. */
        val address: String,
        val canLocate: Boolean,
        /** Null where the robot has no such thing, or has not said yet. */
        val volume: Int?,
        val carpetMode: Boolean?,
        val doNotDisturb: Boolean?,
        val doNotDisturbHours: String?,
        val consumables: ImmutableList<ConsumableUi>,
        val model: String?,
        val version: String?,
        val wifiSignal: Int?,
        /** The robot did not take the last command. */
        val failed: Boolean,
    ) : RobotUiState
}

class RobotViewModel(
    private val repository: RobotRepository,
) : ViewModel() {
    private val failed = MutableStateFlow(false)

    /** The consumables as last read, to find the one a row's key stands for. */
    private var consumables = emptyList<Consumable>()

    val state: StateFlow<RobotUiState> =
        combine(
            repository.connection,
            repository.name,
            repository.details.onStart { emit(null) },
            failed,
        ) { connection, name, details, failed ->
            when (connection) {
                is Connection.Connected -> ready(connection, name, details ?: RobotDetails(), failed)
                else -> RobotUiState.Disconnected(connection.disconnectedUi() ?: return@combine RobotUiState.Loading)
            }
            // The upstream asks the robot every two seconds, so it only runs while somebody looks.
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), RobotUiState.Loading)

    private fun ready(
        connection: Connection.Connected,
        name: String,
        details: RobotDetails,
        failed: Boolean,
    ): RobotUiState.Ready {
        consumables = details.consumables
        return RobotUiState.Ready(
            name = name,
            address = connection.address.shown(),
            canLocate = "LocateCapability" in connection.robot.capabilities,
            volume = details.volume,
            carpetMode = details.carpetMode,
            doNotDisturb = details.doNotDisturb,
            doNotDisturbHours = details.doNotDisturbHours,
            consumables = details.consumables.map { it.ui() }.toImmutableList(),
            model = details.model,
            version = details.version,
            wifiSignal = details.wifiSignal,
            failed = failed,
        )
    }

    private fun Consumable.ui(): ConsumableUi {
        val inMinutes = unit == "minutes"
        return ConsumableUi(
            key = key(),
            kind =
                when {
                    type == "brush" && subType?.startsWith("side") == true -> ConsumableUi.Kind.SideBrush
                    type == "brush" -> ConsumableUi.Kind.MainBrush
                    type == "filter" -> ConsumableUi.Kind.Filter
                    type == "mop" -> ConsumableUi.Kind.Mop
                    type == "sensor" -> ConsumableUi.Kind.Sensor
                    else -> ConsumableUi.Kind.Other
                },
            rawName = listOfNotNull(type, subType).joinToString(" ").replace('_', ' ').replaceFirstChar { it.uppercase() },
            remaining = if (inMinutes) remaining / MINUTES_PER_HOUR else remaining,
            hours = inMinutes,
        )
    }

    private fun Consumable.key() = "$type/${subType.orEmpty()}"

    fun onSaveAddress(address: String) = repository.setAddress(address)

    fun onSaveName(name: String) = repository.setName(name)

    fun onLocate() = command { repository.locate() }

    fun onVolume(percent: Int) = command { repository.setVolume(percent) }

    fun onCarpetMode(enabled: Boolean) = command { repository.setCarpetMode(enabled) }

    fun onResetConsumable(key: String) {
        val consumable = consumables.firstOrNull { it.key() == key } ?: return
        command { repository.resetConsumable(consumable) }
    }

    private fun command(send: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            if (send().isFailure) {
                failed.value = true
                delay(FAILED_SHOWN_MILLIS)
                failed.value = false
            }
        }
    }

    private companion object {
        const val STOP_AFTER_MILLIS = 5_000L
        const val FAILED_SHOWN_MILLIS = 4_000L
        const val MINUTES_PER_HOUR = 60
    }
}
