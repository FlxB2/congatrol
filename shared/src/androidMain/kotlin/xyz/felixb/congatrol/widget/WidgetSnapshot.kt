package xyz.felixb.congatrol.widget

import congatrol.shared.generated.resources.Res
import congatrol.shared.generated.resources.robot_title
import congatrol.shared.generated.resources.widget_battery
import congatrol.shared.generated.resources.widget_battery_unknown
import congatrol.shared.generated.resources.widget_caption
import congatrol.shared.generated.resources.widget_no_address
import congatrol.shared.generated.resources.widget_unreachable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import org.koin.mp.KoinPlatform
import xyz.felixb.congatrol.data.RobotRepository
import xyz.felixb.congatrol.data.namedRooms
import xyz.felixb.congatrol.screens.common.label

/**
 * What the home screen widget says, in words ready to be shown: the robot's name with what it is
 * doing, how full its battery is, and the rooms it can be sent to clean.
 */
data class WidgetSnapshot(
    val caption: String,
    val battery: String,
    /** The same as a number, 0 to 100, for a widget that draws it; null when it is not known. */
    val batteryLevel: Int?,
    val rooms: List<WidgetRoom>,
)

/** A room the robot has a name for, as a button on the widget. */
data class WidgetRoom(
    val id: String,
    val name: String,
    /** Its place among the map's rooms, which is what its colour on the map goes by. */
    val slot: Int,
)

/** Asks the robot once. A robot that does not answer in time is one that does not answer. */
suspend fun widgetSnapshot(): WidgetSnapshot =
    coroutineScope {
        val repository = KoinPlatform.getKoin().get<RobotRepository>()
        val name = repository.name.value.ifEmpty { getString(Res.string.robot_title) }
        val asked = repository.address.value.isNotEmpty()
        val reading = async { if (asked) withTimeoutOrNull(ANSWER_MILLIS) { repository.readState() } else null }
        val map = async { if (asked) withTimeoutOrNull(ANSWER_MILLIS) { repository.readMap() } else null }
        val state = reading.await()
        val doing =
            when {
                !asked -> getString(Res.string.widget_no_address)
                state == null -> getString(Res.string.widget_unreachable)
                else -> getString(state.status.label())
            }
        WidgetSnapshot(
            caption = getString(Res.string.widget_caption, name, doing),
            battery = state?.battery?.let { getString(Res.string.widget_battery, it) } ?: getString(Res.string.widget_battery_unknown),
            batteryLevel = state?.battery?.coerceIn(0, 100),
            rooms =
                map
                    .await()
                    ?.let { read ->
                        read.namedRooms.map { WidgetRoom(it.id, it.name.trim(), slot = read.rooms.indexOf(it)) }
                    }.orEmpty(),
        )
    }

/** Sets the robot off, or on again after a pause. Whether it took that. */
suspend fun widgetStart(): Boolean {
    val repository = KoinPlatform.getKoin().get<RobotRepository>()
    return withTimeoutOrNull(ANSWER_MILLIS) { repository.start().isSuccess } ?: false
}

/** Sends the robot to clean the room [id]. Whether it took that. */
suspend fun widgetCleanRoom(id: String): Boolean {
    val repository = KoinPlatform.getKoin().get<RobotRepository>()
    return withTimeoutOrNull(ANSWER_MILLIS) { repository.cleanRooms(listOf(id)).isSuccess } ?: false
}

/** A broadcast may be kept open for ten seconds; this leaves room to draw what was learnt. */
private const val ANSWER_MILLIS = 7_000L
