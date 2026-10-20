package xyz.felixb.congatrol.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * A robot that is not there: an invented flat of three rooms and a robot on its dock that takes
 * every command and says it is doing it. For previews, the desktop window and a `demo` launch —
 * and so the app can be looked at without a robot in the house.
 */
class DemoRobotApi : RobotApi {
    private var status = "docked"
    private var fan = "medium"
    private var water = "low"
    private var volume = 70
    private var carpet = true
    private var quiet = true
    private val rooms = Rooms.mapTo(mutableListOf()) { DemoRoom(it.id, it.name, it.cells()) }

    /** It is asked and told from several sides at once, and takes them one at a time. */
    private val lock = Mutex()

    /** How far along [Route] the robot has got on this run, in cells, and when that was last worked out. */
    private var travelled = 0f
    private var travelledAt = TimeSource.Monotonic.markNow()

    /**
     * Moves the robot on by the time gone by since it was last asked. It only gets anywhere while
     * it cleans; at the end of its round it is back on its dock.
     */
    private fun advance() {
        val elapsed = travelledAt.elapsedNow()
        travelledAt = TimeSource.Monotonic.markNow()
        if (status != "cleaning") return
        travelled += elapsed.inWholeMilliseconds / 1000f * CELLS_PER_SECOND
        if (travelled >= RouteLength) {
            travelled = 0f
            status = "docked"
        }
    }

    /** The corners of [Route] behind the robot and, last, where it is now. */
    private fun trail(): List<Pair<Float, Float>> {
        val trail = mutableListOf(Route.first())
        if (travelled <= 0f) return trail
        var left = travelled
        for (i in 1 until Route.size) {
            val from = Route[i - 1]
            val to = Route[i]
            val length = abs(to.first - from.first) + abs(to.second - from.second)
            if (left >= length) {
                trail += to
                left -= length
            } else {
                val part = left / length
                trail += from.first + (to.first - from.first) * part to from.second + (to.second - from.second) * part
                break
            }
        }
        return trail
    }

    override suspend fun get(
        address: String,
        path: String,
    ): JsonElement = lock.withLock { answer(path) }

    private fun answer(path: String): JsonElement =
        when (path) {
            "robot" ->
                buildJsonObject {
                    put("manufacturer", "Congatrol")
                    put("modelName", "Demo robot")
                }
            "robot/capabilities" -> JsonArray(Capabilities.map(::JsonPrimitive))
            "robot/state/attributes" -> attributes()
            "robot/state/map" -> map()
            "robot/capabilities/FanSpeedControlCapability/presets",
            "robot/capabilities/WaterUsageControlCapability/presets",
            -> JsonArray(listOf("off", "low", "medium", "high").map(::JsonPrimitive))
            "robot/capabilities/ConsumableMonitoringCapability" ->
                buildJsonArray {
                    listOf(Triple("brush", "main", 14_300), Triple("brush", "side_right", 7_900), Triple("filter", "main", 2_100)).forEach {
                        addJsonObject {
                            put("type", it.first)
                            put("subType", it.second)
                            putJsonObject("remaining") {
                                put("unit", "minutes")
                                put("value", it.third)
                            }
                        }
                    }
                }
            "robot/capabilities/SpeakerVolumeControlCapability" -> buildJsonObject { put("volume", volume) }
            "robot/capabilities/CarpetModeControlCapability" -> buildJsonObject { put("enabled", carpet) }
            "robot/capabilities/DoNotDisturbCapability" ->
                buildJsonObject {
                    put("enabled", quiet)
                    putJsonObject("start") {
                        put("hour", 22)
                        put("minute", 0)
                    }
                    putJsonObject("end") {
                        put("hour", 7)
                        put("minute", 0)
                    }
                }
            "robot/capabilities/WifiConfigurationCapability" -> buildJsonObject { putJsonObject("details") { put("signal", -52) } }
            "valetudo/version" -> buildJsonObject { put("release", "demo") }
            else -> throw IllegalArgumentException("demo robot has no $path")
        }

    override suspend fun put(
        address: String,
        path: String,
        body: JsonObject,
    ) = lock.withLock { take(path, body) }

    private fun take(
        path: String,
        body: JsonObject,
    ) {
        val action = body["action"]?.jsonPrimitive?.contentOrNull
        when (path.removePrefix("robot/capabilities/")) {
            "BasicControlCapability" ->
                status =
                    when (action) {
                        "start" -> "cleaning"
                        "pause" -> "paused"
                        "home" -> "docked".also { travelled = 0f }
                        else -> "idle"
                    }
            "MapSegmentationCapability", "ZoneCleaningCapability" -> status = "cleaning"
            "GoToLocationCapability" -> status = "moving"
            "ManualControlCapability" ->
                when (action) {
                    "enable" -> status = "manual_control"
                    "disable" -> status = "idle"
                }
            "FanSpeedControlCapability/preset" -> fan = body["name"]?.jsonPrimitive?.contentOrNull ?: fan
            "WaterUsageControlCapability/preset" -> water = body["name"]?.jsonPrimitive?.contentOrNull ?: water
            "SpeakerVolumeControlCapability" -> volume = body["value"]?.jsonPrimitive?.intOrNull ?: volume
            "CarpetModeControlCapability" -> carpet = action == "enable"
            "DoNotDisturbCapability" -> quiet = body["enabled"]?.jsonPrimitive?.booleanOrNull ?: quiet
            "MapSegmentRenameCapability" -> room(body, "segment_id").name = body["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            "MapSegmentEditCapability" ->
                when (action) {
                    "join_segments" -> join(room(body, "segment_a_id"), room(body, "segment_b_id"))
                    "split_segment" -> split(room(body, "segment_id"), body.cell("pA"), body.cell("pB"))
                }
        }
    }

    /** Says how it is once a second, as a real robot does at most while something changes. */
    override fun events(
        address: String,
        path: String,
    ): Flow<JsonElement> =
        flow {
            while (true) {
                emit(get(address, path.removeSuffix("/sse")))
                delay(PUSH_EVERY_MILLIS)
            }
        }

    private fun room(
        body: JsonObject,
        key: String,
    ): DemoRoom {
        val id = body[key]?.jsonPrimitive?.contentOrNull
        return rooms.firstOrNull { it.id == id } ?: throw IllegalArgumentException("demo robot has no room $id")
    }

    /** A point of a command, which is in the robot's units, as the cell it is in. */
    private fun JsonObject.cell(key: String): Pair<Float, Float> {
        val point = this[key] as? JsonObject ?: throw IllegalArgumentException("no $key")
        val x = point["x"]?.jsonPrimitive?.intOrNull ?: throw IllegalArgumentException("no $key.x")
        val y = point["y"]?.jsonPrimitive?.intOrNull ?: throw IllegalArgumentException("no $key.y")
        return x.toFloat() / CELL_SIZE to y.toFloat() / CELL_SIZE
    }

    /** [second] becomes part of [first]. A robot wants the two to touch; the invented one does not mind. */
    private fun join(
        first: DemoRoom,
        second: DemoRoom,
    ) {
        if (first === second) throw IllegalArgumentException("a room cannot be joined to itself")
        first.cells += second.cells
        rooms -= second
    }

    /**
     * What of [room] lies on one side of the line through [from] and [to] becomes a room of its
     * own, without a name yet. A line that leaves everything on one side cuts nothing, and is refused.
     */
    private fun split(
        room: DemoRoom,
        from: Pair<Float, Float>,
        to: Pair<Float, Float>,
    ) {
        val (beyond, kept) =
            room.cells.partition { (x, y) ->
                (to.first - from.first) * (y + 0.5f - from.second) - (to.second - from.second) * (x + 0.5f - from.first) > 0
            }
        if (beyond.isEmpty() || kept.isEmpty()) throw IllegalArgumentException("the line does not cross the room")
        room.cells = kept
        rooms += DemoRoom(id = (rooms.maxOf { it.id.toIntOrNull() ?: 0 } + 1).toString(), name = "", cells = beyond)
    }

    private fun attributes(): JsonArray {
        advance()
        return buildJsonArray {
            addJsonObject {
                put("__class", "StatusStateAttribute")
                put("value", status)
            }
            addJsonObject {
                put("__class", "BatteryStateAttribute")
                put("level", 86)
                put("flag", if (status == "docked") "charging" else "discharging")
            }
            addJsonObject {
                put("__class", "PresetSelectionStateAttribute")
                put("type", "fan_speed")
                put("value", fan)
            }
            addJsonObject {
                put("__class", "PresetSelectionStateAttribute")
                put("type", "water_grade")
                put("value", water)
            }
        }
    }

    private fun map(): JsonObject {
        advance()
        val trail = trail()
        val robot = trail.last()
        // Which way it faces: along the stretch it is on, and to the right while it stands on its dock.
        val before = trail.getOrNull(trail.size - 2) ?: (robot.first - 1f to robot.second)
        val angle = atan2(robot.first - before.first, before.second - robot.second) * 180f / PI.toFloat()
        return buildJsonObject {
            put("pixelSize", CELL_SIZE)
            putJsonArray("layers") {
                rooms.forEach { room ->
                    addJsonObject {
                        put("type", "segment")
                        putJsonObject("metaData") {
                            put("segmentId", room.id)
                            put("name", room.name)
                        }
                        putJsonArray("pixels") {
                            room.cells.forEach { (x, y) ->
                                add(x)
                                add(y)
                            }
                        }
                    }
                }
                addJsonObject {
                    put("type", "wall")
                    putJsonArray("pixels") {
                        Rooms.forEach { room ->
                            for (x in room.left - 1..room.right) {
                                add(x)
                                add(room.top - 1)
                                add(x)
                                add(room.bottom)
                            }
                            for (y in room.top until room.bottom) {
                                // A gap in the wall on the left: the door to the room next to it.
                                if (room.left > Rooms.first().left && y in room.top + 4..room.top + 9) continue
                                add(room.left - 1)
                                add(y)
                            }
                            for (y in room.top until room.bottom) {
                                add(room.right)
                                add(y)
                            }
                        }
                    }
                }
            }
            putJsonArray("entities") {
                addJsonObject {
                    put("type", "charger_location")
                    putJsonArray("points") {
                        add(104 * CELL_SIZE)
                        add(128 * CELL_SIZE)
                    }
                }
                addJsonObject {
                    put("type", "robot_position")
                    putJsonObject("metaData") { put("angle", angle.roundToInt()) }
                    putJsonArray("points") {
                        add((robot.first * CELL_SIZE).roundToInt())
                        add((robot.second * CELL_SIZE).roundToInt())
                    }
                }
                addJsonObject {
                    put("type", "path")
                    putJsonArray("points") {
                        // On its dock the robot shows the round it last made: a map without a path looks unused.
                        (if (trail.size > 1) trail else Route).forEach {
                            add((it.first * CELL_SIZE).roundToInt())
                            add((it.second * CELL_SIZE).roundToInt())
                        }
                    }
                }
            }
        }
    }

    /** A room as it was built: a rectangle, which is also what its walls go around. */
    private class Room(
        val id: String,
        val name: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        fun cells(): List<Pair<Int, Int>> = (top until bottom).flatMap { y -> (left until right).map { x -> x to y } }
    }

    /** A room as it is now, after whatever splitting, joining and renaming: any cells at all. */
    private class DemoRoom(
        val id: String,
        var name: String,
        var cells: List<Pair<Int, Int>>,
    )

    companion object {
        /** What stands in for an address while the demo robot is the one being talked to. */
        const val ADDRESS = "demo://robot"

        /** The invented flat, for previews. */
        fun sampleMap(): RobotMap = parseMap(DemoRobotApi().map())

        private const val CELL_SIZE = 5
        private const val PUSH_EVERY_MILLIS = 1_000L

        /** A brisk robot: the whole round takes about a minute and a half. */
        private const val CELLS_PER_SECOND = 5f

        /**
         * The round the robot makes when it cleans, corner by corner, in cells: up and down the
         * living room, through each door into the room behind it, and back to the dock.
         */
        private val Route: List<Pair<Float, Float>> =
            listOf(
                108 to 128,
                108 to 106,
                116 to 106,
                116 to 144,
                124 to 144,
                124 to 106,
                132 to 106,
                132 to 144,
                136 to 144,
                136 to 107,
                148 to 107,
                148 to 118,
                158 to 118,
                158 to 104,
                168 to 104,
                168 to 118,
                172 to 118,
                172 to 107,
                136 to 107,
                136 to 129,
                148 to 129,
                148 to 146,
                158 to 146,
                158 to 127,
                168 to 127,
                168 to 146,
                172 to 146,
                172 to 129,
                136 to 129,
                108 to 128,
            ).map { it.first.toFloat() to it.second.toFloat() }

        private val RouteLength =
            Route.zipWithNext { from, to -> abs(to.first - from.first) + abs(to.second - from.second) }.sum()

        private val Rooms =
            listOf(
                Room("1", "Living room", 100, 100, 140, 150),
                Room("2", "Kitchen", 141, 100, 175, 122),
                Room("3", "Bedroom", 141, 123, 175, 150),
            )

        private val Capabilities =
            listOf(
                "BasicControlCapability",
                "CarpetModeControlCapability",
                "ConsumableMonitoringCapability",
                "DoNotDisturbCapability",
                "FanSpeedControlCapability",
                "GoToLocationCapability",
                "LocateCapability",
                "ManualControlCapability",
                "MapSegmentEditCapability",
                "MapSegmentRenameCapability",
                "MapSegmentationCapability",
                "SpeakerVolumeControlCapability",
                "WaterUsageControlCapability",
                "WifiConfigurationCapability",
                "ZoneCleaningCapability",
            )
    }
}
