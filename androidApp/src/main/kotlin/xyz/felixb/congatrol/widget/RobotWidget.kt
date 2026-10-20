package xyz.felixb.congatrol.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.felixb.congatrol.R
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The home screen widgets, in Glance: the robot's name, what it is doing, its battery, and a
 * button that sets it off. Given the room for it, a widget also has a button for every room the
 * robot has a name for, which cleans that room. Touching the rest of it opens the app.
 *
 * There are three looks of the one widget ([WidgetLook]): a clear one with no pane at all, a
 * one-row ring of the battery in the app's glow, and a floorplan of the rooms. All follow the
 * system's light and dark.
 *
 * The robot is asked when the launcher asks for an update (every half hour), when the app is left,
 * and after one of the buttons was pressed — not in between: a widget that kept asking would keep
 * the phone and the robot awake for something nobody is looking at.
 */
class ClearWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClearWidget()
}

class RingWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RingWidget()
}

class FloorplanWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FloorplanWidget()
}

/**
 * Each look is its own class: Glance finds the widgets to update by their class, and one class for
 * all would draw every widget in one look.
 */
class ClearWidget : RobotGlanceWidget(WidgetLook.Clear)

class RingWidget : RobotGlanceWidget(WidgetLook.Ring)

class FloorplanWidget : RobotGlanceWidget(WidgetLook.Floorplan)

private fun everyLook(): List<GlanceAppWidget> = listOf(ClearWidget(), RingWidget(), FloorplanWidget())

abstract class RobotGlanceWidget(
    private val look: WidgetLook,
) : GlanceAppWidget() {
    /** The rooms are only shown where there is the height for them, so the content needs the size. */
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        // The launcher's half-hourly update lands here, as does the first drawing of a new widget.
        RobotWidget.readIfStale()
        provideContent { WidgetContent(look) }
    }
}

object RobotWidget {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reading = Mutex()
    private var lastRead: TimeMark? = null

    private val currentSnapshot = MutableStateFlow<WidgetSnapshot?>(null)
    private val currentPending = MutableStateFlow<String?>(null)

    /** What the robot last said, or null while it was not asked yet. */
    val snapshot: StateFlow<WidgetSnapshot?> = currentSnapshot

    /** Said in place of name and status while a button's command is on its way. */
    val pending: StateFlow<String?> = currentPending

    /** Asks the robot and redraws, from wherever: the app calls this as it is left. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch { if (anyPlaced(app)) updateAll(app) }
    }

    /** Asks the robot how it is and shows that on every widget of every look. */
    suspend fun updateAll(context: Context) {
        read()
        redraw(context)
    }

    /** One answer is enough for every widget that starts drawing at about the same time. */
    internal suspend fun readIfStale() {
        if (lastRead?.let { it.elapsedNow() < FRESH } == true) return
        read()
    }

    private suspend fun read() =
        reading.withLock {
            currentSnapshot.value = widgetSnapshot()
            lastRead = TimeSource.Monotonic.markNow()
        }

    internal suspend fun showPending(
        context: Context,
        caption: String?,
    ) {
        currentPending.value = caption
        redraw(context)
    }

    private suspend fun redraw(context: Context) {
        everyLook().forEach { it.updateAll(context) }
    }

    private suspend fun anyPlaced(context: Context): Boolean {
        val manager = GlanceAppWidgetManager(context)
        return everyLook().any { manager.getGlanceIds(it.javaClass).isNotEmpty() }
    }

    /** An answer this young is the robot's answer still. */
    private val FRESH = 5.seconds
}

/** The round button: start, pause or resume. */
class StartAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        RobotWidget.showPending(context, context.getString(R.string.widget_starting))
        val taken = widgetStart()
        // The robot takes a moment to say that it is off; asked at once it is still docked.
        if (taken) delay(SETTLE_MILLIS)
        RobotWidget.updateAll(context)
        RobotWidget.showPending(context, null)
    }
}

/** A room's button: cleans that room, and no other. */
class CleanRoomAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val room = parameters[RoomKey] ?: return
        if (widgetCleanRoom(room)) delay(SETTLE_MILLIS)
        RobotWidget.updateAll(context)
    }

    companion object {
        val RoomKey = ActionParameters.Key<String>("room")
    }
}

private const val SETTLE_MILLIS = 1_500L
