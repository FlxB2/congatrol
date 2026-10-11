package xyz.felixb.congatrol.data

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

    override suspend fun get(
        address: String,
        path: String,
    ): JsonElement =
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
    ) {
        val action = body["action"]?.jsonPrimitive?.contentOrNull
        when (path.removePrefix("robot/capabilities/")) {
            "BasicControlCapability" ->
                status =
                    when (action) {
                        "start" -> "cleaning"
                        "pause" -> "paused"
                        "home" -> "docked"
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
        }
    }

    private fun attributes(): JsonArray =
        buildJsonArray {
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

    private fun map(): JsonObject =
        buildJsonObject {
            put("pixelSize", CELL_SIZE)
            putJsonArray("layers") {
                Rooms.forEach { room ->
                    addJsonObject {
                        put("type", "segment")
                        putJsonObject("metaData") {
                            put("segmentId", room.id)
                            put("name", room.name)
                        }
                        putJsonArray("compressedPixels") {
                            for (y in room.top until room.bottom) {
                                add(room.left)
                                add(y)
                                add(room.right - room.left)
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
                    putJsonObject("metaData") { put("angle", 90) }
                    putJsonArray("points") {
                        add(108 * CELL_SIZE)
                        add(128 * CELL_SIZE)
                    }
                }
                addJsonObject {
                    put("type", "path")
                    putJsonArray("points") {
                        listOf(150 to 110, 150 to 140, 130 to 140, 130 to 128, 108 to 128).forEach {
                            add(it.first * CELL_SIZE)
                            add(it.second * CELL_SIZE)
                        }
                    }
                }
            }
        }

    private class Room(
        val id: String,
        val name: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    companion object {
        /** What stands in for an address while the demo robot is the one being talked to. */
        const val ADDRESS = "demo://robot"

        /** The invented flat, for previews. */
        fun sampleMap(): RobotMap = parseMap(DemoRobotApi().map())

        private const val CELL_SIZE = 5

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
                "MapSegmentationCapability",
                "SpeakerVolumeControlCapability",
                "WaterUsageControlCapability",
                "WifiConfigurationCapability",
                "ZoneCleaningCapability",
            )
    }
}
