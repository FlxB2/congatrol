package xyz.felixb.congatrol.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.felixb.congatrol.MainActivity
import xyz.felixb.congatrol.R

/**
 * The home screen widget: the robot's name, what it is doing, its battery, and a button that sets
 * it off. Touching the rest of it opens the app.
 *
 * It asks the robot when the launcher asks for it (every half hour), when the app is left, and
 * after its button was pressed — not in between: a widget that kept asking would keep the phone
 * and the robot awake for something nobody is looking at.
 */
class RobotWidgetProvider : AppWidgetProvider() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            ACTION_START -> start(context)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) = async { RobotWidget.updateAll(context.applicationContext) }

    private fun start(context: Context) =
        async {
            val app = context.applicationContext
            RobotWidget.show(app, caption = app.getString(R.string.widget_starting))
            val taken = widgetStart()
            // The robot takes a moment to say that it is off; asked at once it is still docked.
            if (taken) delay(SETTLE_MILLIS)
            RobotWidget.updateAll(app)
        }

    /** Asking the robot takes a moment, so the broadcast is kept open until it is done. */
    private fun async(block: suspend () -> Unit) {
        val pending = goAsync()
        RobotWidget.scope.launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_START = "xyz.felixb.congatrol.widget.START"
        private const val SETTLE_MILLIS = 1_500L
    }
}

object RobotWidget {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val drawing = Mutex()

    /** Asks the robot and redraws, from wherever: the app calls this as it is left. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        if (ids(app).isEmpty()) return
        scope.launch { updateAll(app) }
    }

    /** Asks the robot how it is and shows that on every widget. */
    suspend fun updateAll(context: Context) {
        if (ids(context).isEmpty()) return
        drawing.withLock {
            val snapshot = widgetSnapshot()
            show(context, snapshot.caption, snapshot.battery)
        }
    }

    /** Puts [caption] and, if given, [battery] on every widget; what is not given stays as it was. */
    fun show(
        context: Context,
        caption: String,
        battery: String? = null,
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val views = RemoteViews(context.packageName, R.layout.widget_robot)
        views.setTextViewText(R.id.widget_caption, caption)
        battery?.let { views.setTextViewText(R.id.widget_battery, it) }
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        )
        views.setOnClickPendingIntent(
            R.id.widget_start,
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, RobotWidgetProvider::class.java).setAction(RobotWidgetProvider.ACTION_START),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        ids(context).forEach { id ->
            // Without a battery this only changes the caption of what is already there.
            if (battery == null) manager.partiallyUpdateAppWidget(id, views) else manager.updateAppWidget(id, views)
        }
    }

    private fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, RobotWidgetProvider::class.java))
}
