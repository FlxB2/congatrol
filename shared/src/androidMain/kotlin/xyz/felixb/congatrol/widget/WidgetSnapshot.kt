package xyz.felixb.congatrol.widget

import congatrol.shared.generated.resources.Res
import congatrol.shared.generated.resources.robot_title
import congatrol.shared.generated.resources.widget_battery
import congatrol.shared.generated.resources.widget_battery_unknown
import congatrol.shared.generated.resources.widget_caption
import congatrol.shared.generated.resources.widget_no_address
import congatrol.shared.generated.resources.widget_unreachable
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import org.koin.mp.KoinPlatform
import xyz.felixb.congatrol.data.RobotRepository
import xyz.felixb.congatrol.screens.common.label

/**
 * What the home screen widget says, in words ready to be shown: the robot's name with what it is
 * doing, and how full its battery is.
 */
data class WidgetSnapshot(
    val caption: String,
    val battery: String,
)

/** Asks the robot once. A robot that does not answer in time is one that does not answer. */
suspend fun widgetSnapshot(): WidgetSnapshot {
    val repository = KoinPlatform.getKoin().get<RobotRepository>()
    val name = repository.name.value.ifEmpty { getString(Res.string.robot_title) }
    val state = if (repository.address.value.isEmpty()) null else withTimeoutOrNull(ANSWER_MILLIS) { repository.readState() }
    val doing =
        when {
            repository.address.value.isEmpty() -> getString(Res.string.widget_no_address)
            state == null -> getString(Res.string.widget_unreachable)
            else -> getString(state.status.label())
        }
    return WidgetSnapshot(
        caption = getString(Res.string.widget_caption, name, doing),
        battery = state?.battery?.let { getString(Res.string.widget_battery, it) } ?: getString(Res.string.widget_battery_unknown),
    )
}

/** Sets the robot off, or on again after a pause. Whether it took that. */
suspend fun widgetStart(): Boolean {
    val repository = KoinPlatform.getKoin().get<RobotRepository>()
    return withTimeoutOrNull(ANSWER_MILLIS) { repository.start().isSuccess } ?: false
}

/** A broadcast may be kept open for ten seconds; this leaves room to draw what was learnt. */
private const val ANSWER_MILLIS = 7_000L
