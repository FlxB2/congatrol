package xyz.felixb.congatrol.screens.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.collections.immutable.ImmutableSet
import xyz.felixb.congatrol.data.MapViewport
import xyz.felixb.congatrol.data.RobotMap
import xyz.felixb.congatrol.data.fitViewport
import xyz.felixb.congatrol.data.rectBetween
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
 * one — except in [MapMode.Zone], where one finger draws the zone. A tap is handed on as the cell
 * it landed in.
 */
@Composable
fun RobotMapView(
    map: RobotMap,
    mode: MapMode,
    selectedRooms: ImmutableSet<String>,
    zone: Rect?,
    target: Offset?,
    onTap: (Offset) -> Unit,
    onZone: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
    viewState: MapViewState = rememberMapViewState(),
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
    val currentOnZone by rememberUpdatedState(onZone)

    Canvas(
        modifier =
            modifier
                .testTag("map")
                .onSizeChanged { size = it.toSize() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val zoneStart = currentViewport.toMap(down.position)
                        var travelled = Offset.Zero
                        var moved = false
                        var twoFingers = false
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) twoFingers = true
                            if (twoFingers || currentMode != MapMode.Zone) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                travelled += pan
                                if (travelled.getDistance() > viewConfiguration.touchSlop || abs(zoom - 1f) > ZOOM_SLOP) moved = true
                                if (moved) {
                                    viewState.moved =
                                        currentViewport.transformedBy(
                                            centroid = event.calculateCentroid(useCurrent = false),
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
                                    currentOnZone(rectBetween(zoneStart, currentViewport.toMap(finger.position)))
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
                cells(room.pixels, color.copy(alpha = if (room.id in selectedRooms) ROOM_PICKED_ALPHA else ROOM_ALPHA))
            }
            cells(map.walls, colors.onSurface.copy(alpha = WALL_ALPHA))
            if (map.path.size > 1) {
                val line = Path().apply { map.path.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
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
        map.rooms.forEach { room ->
            if (room.id in selectedRooms || mode == MapMode.Rooms) {
                roomLabel(
                    measurer = textMeasurer,
                    name = room.name,
                    style = labelStyle.copy(color = colors.onSurface),
                    center = viewport.toScreen(room.bounds.center),
                    plate = colors.surface,
                )
            }
        }
        map.charger?.let { dockMarker(viewport.toScreen(it), colors.onSurface, colors.surface) }
        target?.let { targetMarker(viewport.toScreen(it), colors.primary) }
        map.robot?.let { robotMarker(viewport.toScreen(it.position), it.angle, colors.primary, colors.onPrimary) }
    }
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
) {
    val text = measurer.measure(name, style, maxLines = 1)
    val padding = Offset(8.dp.toPx(), 4.dp.toPx())
    val topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f)
    drawRoundRect(
        color = plate.copy(alpha = LABEL_PLATE_ALPHA),
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

/** Where the robot was told to go: a ring with a dot in it. */
private fun DrawScope.targetMarker(
    center: Offset,
    color: Color,
) {
    drawCircle(color, 11.dp.toPx(), center, style = Stroke(width = 3.dp.toPx()))
    drawCircle(color, 3.5.dp.toPx(), center)
}

private const val FIT_PADDING = 48f
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
