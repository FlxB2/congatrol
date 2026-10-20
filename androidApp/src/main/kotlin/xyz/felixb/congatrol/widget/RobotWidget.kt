package xyz.felixb.congatrol.widget

import android.appwidget.AppWidgetManager
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
class ClearWidgetProvider : RobotWidgetReceiver()

class RingWidgetProvider : RobotWidgetReceiver()

class FloorplanWidgetProvider : RobotWidgetReceiver()

/**
 * There is one receiver per look only because each is an entry in the launcher's list of widgets.
 * They all hand out the one widget, so that one update reaches every widget whatever its look.
 */
abstract class RobotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RobotGlanceWidget()
}

class RobotGlanceWidget : GlanceAppWidget() {
    /** The rooms are only shown where there is the height for them, so the content needs the size. */
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val look = lookOf(context, id)
        // The launcher's half-hourly update lands here, as does the first drawing of a new widget.
        RobotWidget.readIfStale()
        provideContent { WidgetContent(look) }
    }

    /** A widget's look is the entry it was placed from, which the launcher keeps for it. */
    private fun lookOf(
        context: Context,
        id: GlanceId,
    ): WidgetLook {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val placedFrom =
            AppWidgetManager
                .getInstance(context)
                .getAppWidgetInfo(widgetId)
                ?.provider
                ?.className
        return when (placedFrom) {
            RingWidgetProvider::class.java.name -> WidgetLook.Ring
            FloorplanWidgetProvider::class.java.name -> WidgetLook.Floorplan
            else -> WidgetLook.Clear
        }
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

    private suspend fun redraw(context: Context) = RobotGlanceWidget().updateAll(context)

    private suspend fun anyPlaced(context: Context) =
        GlanceAppWidgetManager(context).getGlanceIds(RobotGlanceWidget::class.java).isNotEmpty()

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
