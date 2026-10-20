package xyz.felixb.congatrol.screens.drive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.felixb.congatrol.data.Connection
import xyz.felixb.congatrol.data.RobotRepository
import xyz.felixb.congatrol.data.RobotStatus
import xyz.felixb.congatrol.screens.common.DisconnectedUi
import xyz.felixb.congatrol.screens.common.disconnectedUi

/** One step the robot can be told to take, by Valetudo's name for it. */
enum class DriveStep(
    val command: String,
) {
    Forward("forward"),
    Backward("backward"),
    Left("rotate_counterclockwise"),
    Right("rotate_clockwise"),
}

sealed interface DriveUiState {
    data object Loading : DriveUiState

    data class Disconnected(
        val ui: DisconnectedUi,
    ) : DriveUiState

    data class Ready(
        /** Whether this robot can be driven by hand at all. */
        val supported: Boolean,
        /** Whether it is being driven by hand right now. */
        val enabled: Boolean,
        /** The robot did not take the last command. */
        val failed: Boolean,
    ) : DriveUiState
}

class DriveViewModel(
    private val repository: RobotRepository,
) : ViewModel() {
    private val failed = MutableStateFlow(false)

    val state: StateFlow<DriveUiState> =
        combine(repository.connection, failed) { connection, failed ->
            when (connection) {
                is Connection.Connected ->
                    DriveUiState.Ready(
                        supported = "ManualControlCapability" in connection.robot.capabilities,
                        enabled = connection.robot.state.status == RobotStatus.ManualControl,
                        failed = failed,
                    )
                else -> DriveUiState.Disconnected(connection.disconnectedUi() ?: return@combine DriveUiState.Loading)
            }
            // The upstream asks the robot every two seconds, so it only runs while somebody looks.
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), DriveUiState.Loading)

    fun onEnabledChange(enabled: Boolean) = command { repository.setManualControl(enabled) }

    fun onStep(step: DriveStep) = command { repository.move(step.command) }

    fun onSaveAddress(address: String) = repository.setAddress(address)

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
    }
}
