package xyz.felixb.congatrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.felixb.congatrol.data.Connection
import xyz.felixb.congatrol.data.RobotRepository
import xyz.felixb.congatrol.data.RobotStatus

/** What the round button beside the tab bar does right now. */
sealed interface ActionUiState {
    /** No robot to tell, or one that is being driven by hand. */
    data object Unavailable : ActionUiState

    data object Start : ActionUiState

    data object Pause : ActionUiState

    data object Resume : ActionUiState
}

/** The one thing to do from anywhere: set the robot off, or hold it. */
class ActionViewModel(
    private val repository: RobotRepository,
) : ViewModel() {
    val state: StateFlow<ActionUiState> =
        repository.connection
            .map { connection ->
                when ((connection as? Connection.Connected)?.robot?.state?.status) {
                    null, RobotStatus.ManualControl -> ActionUiState.Unavailable
                    RobotStatus.Cleaning, RobotStatus.Returning, RobotStatus.Moving -> ActionUiState.Pause
                    RobotStatus.Paused -> ActionUiState.Resume
                    else -> ActionUiState.Start
                }
                // The upstream asks the robot every two seconds, so it only runs while somebody looks.
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), ActionUiState.Unavailable)

    fun onClick() {
        viewModelScope.launch {
            when (state.value) {
                ActionUiState.Start, ActionUiState.Resume -> repository.start()
                ActionUiState.Pause -> repository.pause()
                ActionUiState.Unavailable -> Unit
            }
        }
    }

    private companion object {
        const val STOP_AFTER_MILLIS = 5_000L
    }
}
