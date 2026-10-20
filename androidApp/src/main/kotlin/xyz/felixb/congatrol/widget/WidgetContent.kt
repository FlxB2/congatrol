package xyz.felixb.congatrol.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import xyz.felixb.congatrol.MainActivity
import xyz.felixb.congatrol.R
import xyz.felixb.congatrol.theme.seriesColor

/** How a widget is laid out and dressed. Each follows the system's light and dark. */
enum class WidgetLook {
    /** No pane at all: words and outlined buttons straight on the wallpaper. */
    Clear,

    /** One row in the app's glow: the battery as a ring round the start button, rooms by initial. */
    Ring,

    /** The rooms as tiles, roughly as they lie, each in its colour on the map. */
    Floorplan,
}

// The colours are copies of theme/Theme.kt's, which a widget cannot ask for. Those a shape is
// tinted with are opaque on purpose: a tint takes the colour without its alpha.
private val Ink = ColorProvider(day = Color(0xFF17151E), night = Color(0xFFF3F0F9))
private val Quiet = ColorProvider(day = Color(0xFF5E586B), night = Color(0xFFA7A1B6))
private val Accent = ColorProvider(day = Color(0xFF4B3AB8), night = Color(0xFFA59CDC))
private val OnAccent = ColorProvider(day = Color.White, night = Color(0xFF15131C))
private val OnRoom = ColorProvider(Color.White)

/**
 * On the wallpaper there is no pane to be read against, so the words are black or white, and
 * nothing is grey. The outlines carry their own colour, from colors.xml for day and for night: a
 * tint would fill an outline in.
 */
private val ClearInk = ColorProvider(day = Color(0xFF1C1B1F), night = Color.White)

private val RingTrack = ColorProvider(day = Color(0xFFD9D3EE), night = Color(0xFF4A4363))
private val RingGap = ColorProvider(day = Color(0xFFF6F2FB), night = Color(0xFF2A2340))
private val FloorplanPane = ColorProvider(day = Color.White, night = Color(0xFF1C1A23))
private val FloorplanEmpty = ColorProvider(day = Color(0xFFEDEBF2), night = Color(0xFF2E2B38))

/** Below this height a widget has only the one row: who, how, and the start button. */
private val TallMinHeight = 100.dp

/** Below this width the ring is only the ring. */
private val RingWideMinWidth = 150.dp
private const val ROOMS_PER_ROW = 3
private const val ROOMS_MAX = 6
private const val TILES_MAX = 4

@Composable
fun WidgetContent(look: WidgetLook) {
    val snapshot by RobotWidget.snapshot.collectAsState()
    val pending by RobotWidget.pending.collectAsState()
    val context = LocalContext.current
    val shown =
        Shown(
            caption = pending ?: snapshot?.caption ?: context.getString(R.string.app_name),
            battery = snapshot?.battery ?: context.getString(R.string.widget_battery_unknown),
            batteryLevel = snapshot?.batteryLevel,
            rooms = snapshot?.rooms.orEmpty(),
        )
    when (look) {
        WidgetLook.Clear -> ClearContent(shown)
        WidgetLook.Ring -> RingContent(shown)
        WidgetLook.Floorplan -> FloorplanContent(shown)
    }
}

/** What every look shows, with the placeholders filled in. */
private class Shown(
    val caption: String,
    val battery: String,
    val batteryLevel: Int?,
    val rooms: List<WidgetRoom>,
)

private val opensApp get() = GlanceModifier.clickable(actionStartActivity<MainActivity>())

@Composable
private fun ClearContent(shown: Shown) {
    val rooms = if (LocalSize.current.height >= TallMinHeight) shown.rooms.take(ROOMS_MAX) else emptyList()
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp).then(opensApp),
        verticalAlignment = if (rooms.isEmpty()) Alignment.CenterVertically else Alignment.Top,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Words(shown, ink = ClearInk, quiet = ClearInk, captionSize = 17, modifier = GlanceModifier.defaultWeight())
            StartButton(
                size = 40.dp,
                shape = R.drawable.widget_clear_circle,
                fill = null,
                icon = ClearInk,
            )
        }
        if (rooms.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.defaultWeight())
            rooms.chunked(ROOMS_PER_ROW).forEachIndexed { row, chunk ->
                if (row > 0) Spacer(modifier = GlanceModifier.height(6.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    // A short row keeps its buttons the width of a full one.
                    repeat(ROOMS_PER_ROW) { index ->
                        if (index > 0) Spacer(modifier = GlanceModifier.width(6.dp))
                        val room = chunk.getOrNull(index)
                        if (room == null) {
                            Spacer(modifier = GlanceModifier.defaultWeight().height(30.dp))
                        } else {
                            RoomButton(
                                room,
                                GlanceModifier
                                    .defaultWeight()
                                    .height(30.dp)
                                    .background(ImageProvider(R.drawable.widget_clear_pill)),
                            ) {
                                Text(
                                    text = room.name,
                                    maxLines = 1,
                                    modifier = GlanceModifier.padding(horizontal = 6.dp),
                                    style = TextStyle(color = ClearInk, fontSize = 12.sp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RingContent(shown: Shown) {
    val size = LocalSize.current
    val pane = GlanceModifier.background(ImageProvider(R.drawable.widget_pane)).then(opensApp)
    if (size.width < RingWideMinWidth) {
        Column(
            modifier = pane.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BatteryRing(shown.batteryLevel, outer = 44.dp, inner = 36.dp)
            Spacer(modifier = GlanceModifier.height(3.dp))
            Text(text = shown.battery, maxLines = 1, style = TextStyle(color = Quiet, fontSize = 11.sp, fontWeight = FontWeight.Medium))
        }
        return
    }
    // What is left once the ring, the words at their narrowest and the padding have their room.
    val initials = ((size.width.value - 190) / 32).toInt().coerceIn(0, 4)
    Row(
        modifier = pane.fillMaxSize().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BatteryRing(shown.batteryLevel, outer = 52.dp, inner = 42.dp)
        Spacer(modifier = GlanceModifier.width(10.dp))
        Words(shown, ink = Ink, quiet = Quiet, captionSize = 15, modifier = GlanceModifier.defaultWeight())
        shown.rooms.take(initials).forEachIndexed { index, room ->
            if (index > 0) Spacer(modifier = GlanceModifier.width(4.dp))
            RoomButton(
                room,
                GlanceModifier
                    .size(28.dp)
                    .background(ImageProvider(R.drawable.widget_shape_circle), colorFilter = ColorFilter.tint(roomColour(room))),
            ) {
                Text(
                    text = room.name.take(1).uppercase(),
                    style = TextStyle(color = OnRoom, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

/** The start button with the battery round it, filled as far as the battery is. */
@Composable
private fun BatteryRing(
    level: Int?,
    outer: Dp,
    inner: Dp,
) {
    val context = LocalContext.current
    val label = context.getString(R.string.widget_start)
    Box(
        modifier = GlanceModifier.size(outer).clickable(actionRunCallback<StartAction>()).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_shape_circle),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(RingTrack),
        )
        if (level != null && level > 0) {
            val pixels = (outer.value * context.resources.displayMetrics.density).toInt()
            Image(
                provider = ImageProvider(wedge(level, pixels)),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(Accent),
            )
        }
        // A sliver of the pane between ring and button, without which the two are one disc.
        Image(
            provider = ImageProvider(R.drawable.widget_shape_circle),
            contentDescription = null,
            modifier = GlanceModifier.size(inner + 4.dp),
            colorFilter = ColorFilter.tint(RingGap),
        )
        Box(
            modifier =
                GlanceModifier
                    .size(inner)
                    .background(ImageProvider(R.drawable.widget_shape_circle), colorFilter = ColorFilter.tint(Accent)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_play),
                contentDescription = null,
                modifier = GlanceModifier.size(inner * 0.4f),
                colorFilter = ColorFilter.tint(OnAccent),
            )
        }
    }
}

/**
 * A white slice of a circle, [level] percent of it, from twelve o'clock round: a widget cannot draw,
 * so the ring is a picture, tinted like the shapes are.
 */
private fun wedge(
    level: Int,
    pixels: Int,
): Bitmap {
    val bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    Canvas(bitmap).drawArc(0f, 0f, pixels.toFloat(), pixels.toFloat(), -90f, 360f * level / 100, true, paint)
    return bitmap
}

@Composable
private fun FloorplanContent(shown: Shown) {
    val tiles = shown.rooms.take(TILES_MAX)
    val pane =
        GlanceModifier
            .background(ImageProvider(R.drawable.widget_shape_pane), colorFilter = ColorFilter.tint(FloorplanPane))
            .then(opensApp)
            .fillMaxSize()
            .padding(10.dp)
    if (LocalSize.current.height < TallMinHeight) {
        Row(modifier = pane, verticalAlignment = Alignment.CenterVertically) {
            Tiles(tiles, labelled = false, gap = 2.dp, modifier = GlanceModifier.size(52.dp))
            Spacer(modifier = GlanceModifier.width(8.dp))
            Words(shown, ink = Ink, quiet = Quiet, captionSize = 13, modifier = GlanceModifier.defaultWeight())
            StartButton(size = 32.dp, shape = R.drawable.widget_shape_circle, fill = Accent, icon = OnAccent)
        }
        return
    }
    Column(modifier = pane) {
        Row(modifier = GlanceModifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Words(shown, ink = Ink, quiet = Quiet, captionSize = 13, modifier = GlanceModifier.defaultWeight())
            StartButton(size = 32.dp, shape = R.drawable.widget_shape_circle, fill = Accent, icon = OnAccent)
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        Tiles(tiles, labelled = true, gap = 3.dp, modifier = GlanceModifier.fillMaxWidth().defaultWeight())
    }
}

/**
 * The first room large on the left and the others stacked on the right: not the flat as it is, but
 * near enough to find a room by where it lies. With no named room, one empty tile saying so.
 */
@Composable
private fun Tiles(
    rooms: List<WidgetRoom>,
    labelled: Boolean,
    gap: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    if (rooms.isEmpty()) {
        Box(
            modifier =
                modifier
                    .background(
                        ImageProvider(R.drawable.widget_shape_tile),
                        colorFilter = ColorFilter.tint(FloorplanEmpty),
                    ).padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (labelled) {
                Text(
                    text = LocalContext.current.getString(R.string.widget_no_rooms),
                    style = TextStyle(color = Quiet, fontSize = 11.sp),
                )
            }
        }
        return
    }
    Row(modifier = modifier) {
        Tile(rooms.first(), labelled, GlanceModifier.defaultWeight().fillMaxHeight())
        val rest = rooms.drop(1)
        if (rest.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.width(gap))
            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                rest.forEachIndexed { index, room ->
                    if (index > 0) Spacer(modifier = GlanceModifier.height(gap))
                    Tile(room, labelled, GlanceModifier.fillMaxWidth().defaultWeight())
                }
            }
        }
    }
}

/** A room's tile; only a labelled one, big enough to aim at, is a button. */
@Composable
private fun Tile(
    room: WidgetRoom,
    labelled: Boolean,
    modifier: GlanceModifier = GlanceModifier,
) {
    val shape = modifier.background(ImageProvider(R.drawable.widget_shape_tile), colorFilter = ColorFilter.tint(roomColour(room)))
    if (!labelled) {
        Box(modifier = shape) {}
        return
    }
    RoomButton(room, shape.padding(6.dp), contentAlignment = Alignment.BottomStart) {
        Text(
            text = room.name,
            maxLines = 2,
            style = TextStyle(color = OnRoom, fontSize = 10.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/** The robot's name with what it is doing, and its battery underneath. */
@Composable
private fun Words(
    shown: Shown,
    ink: ColorProvider,
    quiet: ColorProvider,
    captionSize: Int,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(modifier = modifier) {
        Text(
            text = shown.caption,
            maxLines = 1,
            style = TextStyle(color = ink, fontSize = captionSize.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = shown.battery,
            maxLines = 1,
            style = TextStyle(color = quiet, fontSize = (captionSize - 3).sp, fontWeight = FontWeight.Medium),
        )
    }
}

/** The round button: start, pause or resume. */
@Composable
private fun StartButton(
    size: Dp,
    shape: Int,
    /** What [shape] is tinted with, or null for a shape that has its colours already. */
    fill: ColorProvider?,
    icon: ColorProvider,
) {
    val label = LocalContext.current.getString(R.string.widget_start)
    Box(
        modifier =
            GlanceModifier
                .size(size)
                .background(ImageProvider(shape), colorFilter = fill?.let { ColorFilter.tint(it) })
                .clickable(actionRunCallback<StartAction>())
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_play),
            contentDescription = null,
            modifier = GlanceModifier.size(size * 0.4f),
            colorFilter = ColorFilter.tint(icon),
        )
    }
}

/** Something that cleans [room] when touched, dressed by [modifier]. */
@Composable
private fun RoomButton(
    room: WidgetRoom,
    modifier: GlanceModifier = GlanceModifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    val label = LocalContext.current.getString(R.string.widget_clean_room, room.name)
    Box(
        modifier =
            modifier
                .clickable(actionRunCallback<CleanRoomAction>(actionParametersOf(CleanRoomAction.RoomKey to room.id)))
                .semantics { contentDescription = label },
        contentAlignment = contentAlignment,
        content = content,
    )
}

private fun roomColour(room: WidgetRoom) =
    ColorProvider(day = seriesColor(room.slot, dark = false), night = seriesColor(room.slot, dark = true))
