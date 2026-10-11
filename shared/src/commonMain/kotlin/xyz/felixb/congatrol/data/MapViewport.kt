package xyz.felixb.congatrol.data

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/**
 * How the map lies on the screen: a cell at `c` is drawn at `c * scale + offset`. Everything that
 * goes between the two — drawing, finding the cell under a finger — asks this.
 */
@Immutable
data class MapViewport(
    val scale: Float,
    val offset: Offset,
) {
    fun toScreen(cell: Offset): Offset = Offset(cell.x * scale + offset.x, cell.y * scale + offset.y)

    fun toMap(screen: Offset): Offset = Offset((screen.x - offset.x) / scale, (screen.y - offset.y) / scale)

    /**
     * Moved by [pan] and scaled by [zoom] around [centroid], which stays where it is on the screen.
     * The scale is kept between [minScale] and [maxScale].
     */
    fun transformedBy(
        centroid: Offset,
        pan: Offset,
        zoom: Float,
        minScale: Float,
        maxScale: Float,
    ): MapViewport {
        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
        val applied = newScale / scale
        return MapViewport(
            scale = newScale,
            offset =
                Offset(
                    centroid.x - (centroid.x - offset.x) * applied + pan.x,
                    centroid.y - (centroid.y - offset.y) * applied + pan.y,
                ),
        )
    }
}

/** The viewport that shows all of [bounds], centred in [size] with [padding] free on every side. */
fun fitViewport(
    bounds: Rect,
    size: Size,
    padding: Float,
): MapViewport {
    val width = (size.width - 2 * padding).coerceAtLeast(1f)
    val height = (size.height - 2 * padding).coerceAtLeast(1f)
    val scale = minOf(width / bounds.width.coerceAtLeast(1f), height / bounds.height.coerceAtLeast(1f))
    return MapViewport(
        scale = scale,
        offset =
            Offset(
                (size.width - bounds.width * scale) / 2f - bounds.left * scale,
                (size.height - bounds.height * scale) / 2f - bounds.top * scale,
            ),
    )
}

/** The rectangle between two corners, whichever way round they were dragged. */
fun rectBetween(
    a: Offset,
    b: Offset,
): Rect = Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))
