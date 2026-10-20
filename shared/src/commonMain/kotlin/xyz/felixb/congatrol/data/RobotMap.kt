package xyz.felixb.congatrol.data

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The robot's map as the app draws it. Everything is in **cells**: the map's own pixels, each a
 * few centimetres of floor, x to the right and y down. What the robot sends in its own units is
 * turned into cells on the way in ([parseMap]) and back on the way out ([cellSize]).
 *
 * A layer is the centres of its cells, ready to be drawn as square points.
 */
@Immutable
class RobotMap(
    /** How many of the robot's units one cell is: what a cell coordinate is multiplied by in a command. */
    val cellSize: Int,
    val floor: List<Offset>,
    val walls: List<Offset>,
    val rooms: List<MapRoom>,
    val robot: MapPose?,
    val charger: Offset?,
    /** Where the robot has been on this run, as a line. */
    val path: List<Offset>,
    /** Everything that is drawn, or null for a map with nothing on it. */
    val bounds: Rect?,
) {
    /**
     * The room [cell] is in, or null. A room's cells may be sparse — some robots send only part of
     * them — so a cell that is in none falls to the smallest room whose outline contains it.
     */
    fun roomAt(cell: Offset): MapRoom? {
        val key = cellKey(cell.x.toInt(), cell.y.toInt())
        return rooms.firstOrNull { key in it.cells }
            ?: rooms.filter { it.bounds.contains(cell) }.minByOrNull { it.bounds.width * it.bounds.height }
    }
}

@Immutable
class MapRoom(
    val id: String,
    val name: String,
    val pixels: List<Offset>,
    val bounds: Rect,
    internal val cells: Set<Long>,
)

/** Where something is and which way it faces, in degrees clockwise from up. */
@Immutable
data class MapPose(
    val position: Offset,
    val angle: Float,
)

private fun cellKey(
    x: Int,
    y: Int,
): Long = x.toLong() shl 32 or (y.toLong() and 0xFFFFFFFFL)

/**
 * Reads a Valetudo map (`/api/v2/robot/state/map`). Layers carry their cells either run-length
 * encoded (`compressedPixels`: x, y, count — a run along x) or plainly (`pixels`: x, y); entities
 * carry points in the robot's units, [RobotMap.cellSize] to a cell.
 */
fun parseMap(json: JsonObject): RobotMap {
    val cellSize = json["pixelSize"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: 1
    val floor = mutableListOf<Offset>()
    val walls = mutableListOf<Offset>()
    val rooms = mutableListOf<MapRoom>()
    json["layers"]?.jsonArray.orEmpty().forEach { element ->
        val layer = element.jsonObject
        val cells = layer.cells()
        when (layer["type"]?.jsonPrimitive?.contentOrNull) {
            "floor" -> floor += cells.map { it.center() }
            "wall" -> walls += cells.map { it.center() }
            "segment" -> {
                val meta = layer["metaData"]?.jsonObject
                val id = meta?.get("segmentId")?.jsonPrimitive?.contentOrNull ?: return@forEach
                val pixels = cells.map { it.center() }
                rooms +=
                    MapRoom(
                        id = id,
                        name = meta["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: id,
                        pixels = pixels,
                        bounds = boundsOf(pixels) ?: Rect.Zero,
                        cells = cells.mapTo(HashSet()) { cellKey(it.first, it.second) },
                    )
            }
        }
    }
    var robot: MapPose? = null
    var charger: Offset? = null
    var path = emptyList<Offset>()
    json["entities"]?.jsonArray.orEmpty().forEach { element ->
        val entity = element.jsonObject
        val points = entity["points"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.floatOrNull }
        val first = if (points.size >= 2) Offset(points[0] / cellSize, points[1] / cellSize) else null
        when (entity["type"]?.jsonPrimitive?.contentOrNull) {
            "robot_position" -> {
                val angle =
                    entity["metaData"]
                        ?.jsonObject
                        ?.get("angle")
                        ?.jsonPrimitive
                        ?.floatOrNull ?: 0f
                robot = first?.let { MapPose(it, angle) }
            }
            "charger_location" -> charger = first
            "path" -> path = points.chunked(2).filter { it.size == 2 }.map { Offset(it[0] / cellSize, it[1] / cellSize) }
        }
    }
    return RobotMap(
        cellSize = cellSize,
        floor = floor,
        walls = walls,
        rooms = rooms,
        robot = robot,
        charger = charger,
        path = path,
        bounds = boundsOf(floor + walls + rooms.flatMap { it.pixels }),
    )
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

private fun Pair<Int, Int>.center(): Offset = Offset(first + 0.5f, second + 0.5f)

/** The cells of a layer, whichever way it spells them. */
private fun JsonObject.cells(): List<Pair<Int, Int>> {
    val compressed = this["compressedPixels"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.intOrNull }
    if (compressed.isNotEmpty()) {
        val cells = ArrayList<Pair<Int, Int>>(compressed.size / 3)
        for (run in 0 until compressed.size / 3) {
            val x = compressed[run * 3]
            val y = compressed[run * 3 + 1]
            repeat(compressed[run * 3 + 2]) { cells += (x + it) to y }
        }
        return cells
    }
    val plain = this["pixels"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.intOrNull }
    return plain.chunked(2).filter { it.size == 2 }.map { it[0] to it[1] }
}

/** The smallest rectangle around the cells whose centres are [points], or null for none. */
internal fun boundsOf(points: List<Offset>): Rect? {
    if (points.isEmpty()) return null
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    points.forEach {
        if (it.x < left) left = it.x
        if (it.y < top) top = it.y
        if (it.x > right) right = it.x
        if (it.y > bottom) bottom = it.y
    }
    return Rect(left - 0.5f, top - 0.5f, right + 0.5f, bottom + 0.5f)
}
