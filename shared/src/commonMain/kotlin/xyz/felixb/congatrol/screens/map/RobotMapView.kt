package xyz.felixb.congatrol.screens.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import congatrol.shared.generated.resources.Res
import congatrol.shared.generated.resources.map_description
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import xyz.felixb.congatrol.data.MapViewport
import xyz.felixb.congatrol.data.RobotMap
import xyz.felixb.congatrol.data.fitViewport
import xyz.felixb.congatrol.theme.LocalCongatrolLook
import xyz.felixb.congatrol.theme.seriesColor
import kotlin.math.abs

/**
 * How the user has moved the map about. Until they do — and again after [recenter] — the map is
 * shown whole, however its size or the view's changes.
 */
@Stable
class MapViewState {
    internal var moved by mutableStateOf<MapViewport?>(null)

    /** Whether the map is anywhere but where [recenter] puts it. */
    val isMoved: Boolean get() = moved != null

    fun recenter() {
        moved = null
    }
}

@Composable
fun rememberMapViewState(): MapViewState = remember { MapViewState() }

/**
 * The robot's map: floor, walls and rooms as the robot sent them, cell by cell, with where it has
 * been, where it is and where its dock stands on top. Two fingers move and zoom it, and so does
 * one — except in a mode that [draws][MapMode.draws], where one finger's drag is handed on as the
 * two cells it went between. A tap is handed on as the cell it landed in.
 */
@Composable
fun RobotMapView(
    map: RobotMap,
    mode: MapMode,
    selectedRooms: ImmutableSet<String>,
    zone: Rect?,
    cut: MapCut?,
    target: Offset?,
    onTap: (Offset) -> Unit,
    onDraw: (from: Offset, to: Offset) -> Unit,
    modifier: Modifier = Modifier,
    viewState: MapViewState = rememberMapViewState(),
    /** Whether the robot is at work, which its marker shows by sending out rings. */
    active: Boolean = false,
) {
    val look = LocalCongatrolLook.current
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelMedium
    val textMeasurer = rememberTextMeasurer()
    var size by remember { mutableStateOf(Size.Zero) }
    val bounds = map.bounds ?: return
    val fit = remember(bounds, size) { fitViewport(bounds, size, FIT_PADDING) }
    val viewport = viewState.moved ?: fit

    // The gesture loop outlives recompositions, so it reads what is current through these.
    val currentViewport by rememberUpdatedState(viewport)
    val currentFit by rememberUpdatedState(fit)
    val currentMode by rememberUpdatedState(mode)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDraw by rememberUpdatedState(onDraw)
    // The robot says where it is once a second at best. Between two readings it is moved along at
    // an even pace, so that it drives across the map rather than hops — unless it is suddenly
    // somewhere else altogether, as after being carried.
    val pose = map.robot
    val robotPosition = remember { Animatable(pose?.position ?: Offset.Zero, Offset.VectorConverter) }
    val robotAngle = remember { Animatable(pose?.angle ?: 0f) }
    LaunchedEffect(pose) {
        if (pose == null) return@LaunchedEffect
        if ((pose.position - robotPosition.value).getDistance() > GLIDE_MAX_CELLS) {
            robotPosition.snapTo(pose.position)
            robotAngle.snapTo(pose.angle)
        } else {
            launch { robotPosition.animateTo(pose.position, tween(GLIDE_MILLIS, easing = LinearEasing)) }
            // Turned the short way round: from 350° to 10° is twenty degrees, not 340 back.
            val turn = ((pose.angle - robotAngle.value) % 360f + 540f) % 360f - 180f
            launch { robotAngle.animateTo(robotAngle.value + turn, tween(TURN_MILLIS)) }
        }
    }
    val pulse = robotPulse(active)
    // A room picked lights up, and the names come and go, rather than switch.
    val roomAlphas =
        map.rooms.map { room ->
            key(room.id) { animateFloatAsState(if (room.id in selectedRooms) ROOM_PICKED_ALPHA else ROOM_ALPHA, tween(ROOM_FADE_MILLIS)) }
        }
    val labelAlphas =
        map.rooms.map { room ->
            key(room.id) { animateFloatAsState(if (room.id in selectedRooms || mode.showsRooms) 1f else 0f, tween(ROOM_FADE_MILLIS)) }
        }
    // The map is a drawing, so what it shows is said in words for whoever cannot see it.
    val description = stringResource(Res.string.map_description, map.rooms.joinToString { it.name })

    Canvas(
        modifier =
            modifier
                .testTag("map")
                .semantics { contentDescription = description }
                .onSizeChanged { size = it.toSize() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val drawStart = currentViewport.toMap(down.position)
                        var travelled = Offset.Zero
                        var moved = false
                        var twoFingers = false
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) twoFingers = true
                            if (twoFingers || !currentMode.draws) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                travelled += pan
                                if (travelled.getDistance() > viewConfiguration.touchSlop || abs(zoom - 1f) > ZOOM_SLOP) moved = true
                                // The event a finger lifts on has no centre: nothing was down both
                                // before and after. Moving the map by it would put it nowhere.
                                val centroid = event.calculateCentroid(useCurrent = false)
                                if (moved && centroid.isSpecified) {
                                    viewState.moved =
                                        currentViewport.transformedBy(
                                            centroid = centroid,
                                            pan = pan,
                                            zoom = zoom,
                                            minScale = currentFit.scale * MIN_ZOOM,
                                            maxScale = currentFit.scale * MAX_ZOOM,
                                        )
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } else {
                                val finger = event.changes.firstOrNull { it.id == down.id } ?: continue
                                if ((finger.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                if (moved) {
                                    currentOnDraw(drawStart, currentViewport.toMap(finger.position))
                                    if (finger.positionChanged()) finger.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        if (!moved && !twoFingers) currentOnTap(currentViewport.toMap(down.position))
                    }
                },
    ) {
        withTransform({
            translate(viewport.offset.x, viewport.offset.y)
            scale(viewport.scale, viewport.scale, pivot = Offset.Zero)
        }) {
            cells(map.floor, look.well)
            map.rooms.forEachIndexed { index, room ->
                val color = seriesColor(index, look.dark)
                cells(room.pixels, color.copy(alpha = roomAlphas[index].value))
            }
            cells(map.walls, colors.onSurface.copy(alpha = WALL_ALPHA))
            if (map.path.size > 1) {
                // Where the path ends at the robot it ends at the marker, which may not have got there yet.
                val atRobot = pose != null && (map.path.last() - pose.position).getDistance() < PATH_TIP_CELLS
                val points = if (atRobot) map.path.dropLast(1) + robotPosition.value else map.path
                val line = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                drawPath(
                    path = line,
                    color = colors.onSurface.copy(alpha = PATH_ALPHA),
                    style = Stroke(width = PATH_WIDTH_CELLS, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            zone?.let {
                drawRect(colors.primary.copy(alpha = ZONE_FILL_ALPHA), it.topLeft, it.size)
                drawRect(colors.primary, it.topLeft, it.size, style = Stroke(width = 2.dp.toPx() / viewport.scale))
            }
        }
        // Markers and labels keep their size however far the map is zoomed.
        cut?.let { cutLine(viewport.toScreen(it.from), viewport.toScreen(it.to), colors.primary, colors.surface) }
        map.rooms.forEachIndexed { index, room ->
            val alpha = labelAlphas[index].value
            if (alpha > 0f) {
                roomLabel(
                    measurer = textMeasurer,
                    name = room.name,
                    style = labelStyle.copy(color = colors.onSurface),
                    center = viewport.toScreen(room.bounds.center),
                    plate = colors.surface,
                    alpha = alpha,
                )
            }
        }
        map.charger?.let { dockMarker(viewport.toScreen(it), colors.onSurface, colors.surface) }
        target?.let { targetMarker(viewport.toScreen(it), colors.primary) }
        if (pose != null) {
            val center = viewport.toScreen(robotPosition.value)
            pulse?.value?.let { ring ->
                drawCircle(colors.primary.copy(alpha = (1f - ring) * PULSE_ALPHA), (9.dp + PULSE_REACH * ring).toPx(), center)
            }
            robotMarker(center, robotAngle.value, colors.primary, colors.onPrimary)
        }
    }
}

/** How far the ring around a working robot has spread, from none to all the way — or null while it rests. */
@Composable
private fun robotPulse(active: Boolean): State<Float>? {
    if (!active) return null
    return rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS, easing = LinearOutSlowInEasing)),
    )
}

/** Cells as squares a hair wider than a cell, so that neighbours close up without a seam. */
private fun DrawScope.cells(
    centers: List<Offset>,
    color: Color,
) {
    if (centers.isEmpty()) return
    drawPoints(centers, PointMode.Points, color, strokeWidth = CELL_WIDTH, cap = StrokeCap.Square)
}

private fun DrawScope.roomLabel(
    measurer: TextMeasurer,
    name: String,
    style: androidx.compose.ui.text.TextStyle,
    center: Offset,
    plate: Color,
    alpha: Float,
) {
    val text = measurer.measure(name, style.copy(color = style.color.copy(alpha = alpha)), maxLines = 1)
    val padding = Offset(8.dp.toPx(), 4.dp.toPx())
    val topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f)
    drawRoundRect(
        color = plate.copy(alpha = LABEL_PLATE_ALPHA * alpha),
        topLeft = topLeft - padding,
        size = Size(text.size.width + padding.x * 2, text.size.height + padding.y * 2),
        cornerRadius = CornerRadius(10.dp.toPx()),
    )
    drawText(text, topLeft = topLeft)
}

/** The robot: a disc in the accent with a notch where its front is. */
private fun DrawScope.robotMarker(
    center: Offset,
    angle: Float,
    color: Color,
    onColor: Color,
) {
    val radius = 9.dp.toPx()
    drawCircle(onColor, radius + 2.dp.toPx(), center)
    drawCircle(color, radius, center)
    rotate(angle, pivot = center) {
        drawLine(onColor, center, center.copy(y = center.y - radius * 0.8f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** The dock: a small rounded square. */
private fun DrawScope.dockMarker(
    center: Offset,
    color: Color,
    rim: Color,
) {
    val half = 6.dp.toPx()
    val rimWidth = 2.dp.toPx()
    drawRoundRect(
        color = rim,
        topLeft = center - Offset(half + rimWidth, half + rimWidth),
        size = Size((half + rimWidth) * 2, (half + rimWidth) * 2),
        cornerRadius = CornerRadius(5.dp.toPx()),
    )
    drawRoundRect(color, center - Offset(half, half), Size(half * 2, half * 2), CornerRadius(3.dp.toPx()))
}

/** The line a room is split along: rimmed so that it shows on any room's colour, with a knob at each end. */
private fun DrawScope.cutLine(
    from: Offset,
    to: Offset,
    color: Color,
    rim: Color,
) {
    drawLine(rim, from, to, strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
    drawLine(color, from, to, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    listOf(from, to).forEach {
        drawCircle(rim, 7.dp.toPx(), it)
        drawCircle(color, 5.dp.toPx(), it)
    }
}

/** Where the robot was told to go: a ring with a dot in it. */
private fun DrawScope.targetMarker(
    center: Offset,
    color: Color,
) {
    drawCircle(color, 11.dp.toPx(), center, style = Stroke(width = 3.dp.toPx()))
    drawCircle(color, 3.5.dp.toPx(), center)
}

private const val FIT_PADDING = 28f
private const val MIN_ZOOM = 0.6f
private const val MAX_ZOOM = 12f
private const val ZOOM_SLOP = 0.01f
private const val CELL_WIDTH = 1.08f
private const val ROOM_ALPHA = 0.42f
private const val ROOM_PICKED_ALPHA = 0.95f
private const val WALL_ALPHA = 0.78f
private const val PATH_ALPHA = 0.5f
private const val PATH_WIDTH_CELLS = 0.5f
private const val ZONE_FILL_ALPHA = 0.22f
private const val LABEL_PLATE_ALPHA = 0.92f
private const val ROOM_FADE_MILLIS = 220

/** As long as the robot's readings are apart while it moves: the marker arrives as the next one does. */
private const val GLIDE_MILLIS = 1_000
private const val TURN_MILLIS = 350

/** Further than this between two readings is not driving: the marker is put there, not moved. */
private const val GLIDE_MAX_CELLS = 40f
private const val PATH_TIP_CELLS = 2f
private const val PULSE_MILLIS = 1_600
private const val PULSE_ALPHA = 0.45f
private val PULSE_REACH = 22.dp
