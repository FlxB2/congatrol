package xyz.felixb.congatrol.screens.common

import congatrol.shared.generated.resources.Res
import congatrol.shared.generated.resources.status_cleaning
import congatrol.shared.generated.resources.status_docked
import congatrol.shared.generated.resources.status_error
import congatrol.shared.generated.resources.status_idle
import congatrol.shared.generated.resources.status_manual_control
import congatrol.shared.generated.resources.status_moving
import congatrol.shared.generated.resources.status_paused
import congatrol.shared.generated.resources.status_returning
import congatrol.shared.generated.resources.status_unknown
import org.jetbrains.compose.resources.StringResource
import xyz.felixb.congatrol.data.RobotStatus

/** What the robot is doing, in the app's words. */
fun RobotStatus.label(): StringResource =
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
