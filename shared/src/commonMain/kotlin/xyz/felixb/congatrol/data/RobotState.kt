package xyz.felixb.congatrol.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What the robot is doing, by the value Valetudo's status attribute carries. */
enum class RobotStatus(
    val key: String,
) {
    Docked("docked"),
    Idle("idle"),
    Cleaning("cleaning"),
    Paused("paused"),
    Returning("returning"),
    Moving("moving"),
    ManualControl("manual_control"),
    Error("error"),

    /** Something this app has no word for. */
    Unknown(""),
    ;

    companion object {
        fun of(key: String?): RobotStatus = entries.firstOrNull { it.key == key } ?: Unknown
    }
}

/** The robot's state attributes, the ones the app shows or acts on. Null where the robot sent none. */
data class RobotState(
    val status: RobotStatus = RobotStatus.Unknown,
    /** What is wrong, in the robot's words, while [status] is [RobotStatus.Error]. */
    val error: String? = null,
    val battery: Int? = null,
    val charging: Boolean = false,
    val fanSpeed: String? = null,
    val waterGrade: String? = null,
    val dustbin: Boolean? = null,
    val waterTank: Boolean? = null,
    val mop: Boolean? = null,
)

/** Reads Valetudo's state attributes (`/api/v2/robot/state/attributes`), told apart by `__class`. */
fun parseAttributes(json: JsonArray): RobotState {
    var state = RobotState()
    json.forEach { element ->
        val attribute = element.jsonObject
        when (attribute.text("__class")) {
            "StatusStateAttribute" ->
                state =
                    state.copy(
                        status = RobotStatus.of(attribute.text("value")),
                        error = attribute["metaData"]?.jsonObject?.text("error_description")?.takeUnless { it == "none" },
                    )
            "BatteryStateAttribute" ->
                state =
                    state.copy(
                        battery = attribute["level"]?.jsonPrimitive?.intOrNull,
                        charging = attribute.text("flag") == "charging",
                    )
            "PresetSelectionStateAttribute" ->
                when (attribute.text("type")) {
                    "fan_speed" -> state = state.copy(fanSpeed = attribute.text("value"))
                    "water_grade" -> state = state.copy(waterGrade = attribute.text("value"))
                }
            "AttachmentStateAttribute" -> {
                val attached = attribute["attached"]?.jsonPrimitive?.booleanOrNull
                when (attribute.text("type")) {
                    "dustbin" -> state = state.copy(dustbin = attached)
                    "watertank" -> state = state.copy(waterTank = attached)
                    "mop" -> state = state.copy(mop = attached)
                }
            }
        }
    }
    return state
}

internal fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

/** Something that wears out, and how much of it is left. */
data class Consumable(
    val type: String,
    val subType: String?,
    /** In [unit]: `minutes` or `percent`. */
    val remaining: Int,
    val unit: String,
)

/** What the Robot tab shows besides the state: read less often, each null where the robot has no such thing. */
data class RobotDetails(
    val consumables: List<Consumable> = emptyList(),
    val volume: Int? = null,
    val carpetMode: Boolean? = null,
    val doNotDisturb: Boolean? = null,
    /** The quiet hours as the robot has them, `22:00–07:00`. */
    val doNotDisturbHours: String? = null,
    /** Signal strength in dBm. */
    val wifiSignal: Int? = null,
    val model: String? = null,
    val version: String? = null,
)

/**
 * A robot address as the user typed it, made into the start of a URL: `robot.local` becomes
 * `http://robot.local`, a trailing slash goes, nothing stays nothing.
 */
fun normalizeAddress(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ""
    return if ("://" in trimmed) trimmed else "http://$trimmed"
}
