package xyz.felixb.congatrol.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/** Where the robot's address is kept between launches. Each platform has its own place for it. */
interface AddressStore {
    fun read(): String

    fun write(address: String)
}

/** Where things stand between the app and the robot. */
sealed interface Connection {
    /** Nobody has said where the robot is yet. */
    data object NoAddress : Connection

    /** Asked, not answered yet. */
    data class Connecting(
        val address: String,
    ) : Connection

    /** No answer at [address], or not one that makes sense; it keeps being asked. */
    data class Unreachable(
        val address: String,
        val reason: String?,
    ) : Connection

    data class Connected(
        val address: String,
        val robot: RobotSnapshot,
    ) : Connection
}

/** The robot as it last answered. */
data class RobotSnapshot(
    val state: RobotState,
    val map: RobotMap?,
    /** Valetudo's names for what this robot can do: `ZoneCleaningCapability` and so on. */
    val capabilities: Set<String>,
    val fanPresets: List<String>,
    val waterPresets: List<String>,
)

/**
 * The only door to the robot. It is asked how it is every couple of seconds for as long as
 * somebody is looking ([connection]), and told what to do through the `suspend` functions, each of
 * which says whether the robot took it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RobotRepository(
    http: RobotApi,
    private val store: AddressStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var api: RobotApi = http
    private val currentAddress = MutableStateFlow(normalizeAddress(store.read()))

    /** Asks for the next reading now rather than when it is due — after a command, say. */
    private val kicks = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** The robot's address as a URL without a trailing slash, or empty while there is none. */
    val address: StateFlow<String> = currentAddress

    fun setAddress(input: String) {
        val address = normalizeAddress(input)
        store.write(address)
        currentAddress.value = address
    }

    /**
     * Talks to the invented robot from here on, for previews and a `demo` launch. The stored
     * address is left alone.
     */
    fun enterDemo() {
        api = DemoRobotApi()
        currentAddress.value = DemoRobotApi.ADDRESS
    }

    val connection: SharedFlow<Connection> =
        currentAddress
            .flatMapLatest { address -> if (address.isEmpty()) flowOf(Connection.NoAddress) else poll(address) }
            .shareIn(scope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), replay = 1)

    /** What the Robot tab shows besides the state; null while there is no robot to ask. */
    val details: SharedFlow<RobotDetails?> =
        connection
            .map { it as? Connection.Connected }
            .distinctUntilChangedBy { it?.address }
            .flatMapLatest { connected ->
                if (connected == null) {
                    flowOf(null)
                } else {
                    flow {
                        while (true) {
                            emit(readDetails(connected.address, connected.robot.capabilities))
                            withTimeoutOrNull(DETAILS_EVERY) { kicks.first() }
                        }
                    }
                }
            }.shareIn(scope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), replay = 1)

    private fun poll(address: String): Flow<Connection> =
        flow {
            emit(Connection.Connecting(address))
            var setup: Setup? = null
            var map: RobotMap? = null
            var cycle = 0
            var kicked = false
            while (true) {
                val reading =
                    attempt {
                        val known = setup ?: readSetup(address).also { setup = it }
                        val state = parseAttributes(api.get(address, "robot/state/attributes").jsonArray)
                        // The map only changes while the robot moves, and it is the big answer.
                        if (map == null || kicked || state.status.onTheMove || cycle % IDLE_MAP_EVERY == 0) {
                            map = parseMap(api.get(address, "robot/state/map").jsonObject)
                        }
                        Connection.Connected(address, RobotSnapshot(state, map, known.capabilities, known.fanPresets, known.waterPresets))
                    }
                emit(reading.getOrElse { Connection.Unreachable(address, it.message) })
                cycle++
                kicked = withTimeoutOrNull(POLL_EVERY) { kicks.first() } != null
            }
        }

    /** What does not change while the app runs: read once per connection. */
    private class Setup(
        val capabilities: Set<String>,
        val fanPresets: List<String>,
        val waterPresets: List<String>,
    )

    private suspend fun readSetup(address: String): Setup {
        val capabilities = api.get(address, "robot/capabilities").jsonArray.mapNotNullTo(HashSet()) { it.jsonPrimitive.contentOrNull }

        suspend fun presets(capability: String): List<String> =
            if (capability in capabilities) {
                api.get(address, "robot/capabilities/$capability/presets").jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
            } else {
                emptyList()
            }
        return Setup(capabilities, presets(FAN), presets(WATER))
    }

    /** Each part on its own: a robot that cannot say one thing still gets to say the rest. */
    private suspend fun readDetails(
        address: String,
        capabilities: Set<String>,
    ): RobotDetails {
        suspend fun read(
            capability: String?,
            path: String,
        ): JsonObject? =
            if (capability == null || capability in capabilities) {
                attempt { api.get(address, path) }.getOrNull() as? JsonObject
            } else {
                null
            }
        val consumables =
            if (CONSUMABLES in capabilities) {
                attempt { api.get(address, "robot/capabilities/$CONSUMABLES").jsonArray }.getOrNull().orEmpty().mapNotNull { element ->
                    val item = element.jsonObject
                    val remaining = item["remaining"]?.jsonObject ?: return@mapNotNull null
                    Consumable(
                        type = item.text("type") ?: return@mapNotNull null,
                        subType = item.text("subType")?.takeUnless { it == "none" },
                        remaining = remaining["value"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null,
                        unit = remaining.text("unit") ?: "minutes",
                    )
                }
            } else {
                emptyList()
            }
        val quiet = read(DO_NOT_DISTURB, "robot/capabilities/$DO_NOT_DISTURB")
        val robot = read(null, "robot")
        return RobotDetails(
            consumables = consumables,
            volume = read(VOLUME, "robot/capabilities/$VOLUME")?.get("volume")?.jsonPrimitive?.intOrNull,
            carpetMode = read(CARPET, "robot/capabilities/$CARPET")?.get("enabled")?.jsonPrimitive?.booleanOrNull,
            doNotDisturb = quiet?.get("enabled")?.jsonPrimitive?.booleanOrNull,
            doNotDisturbHours = quiet?.let { "${it.clock("start")}–${it.clock("end")}" },
            wifiSignal =
                read(WIFI, "robot/capabilities/$WIFI")
                    ?.get("details")
                    ?.jsonObject
                    ?.get("signal")
                    ?.jsonPrimitive
                    ?.intOrNull,
            model = robot?.let { listOfNotNull(it.text("manufacturer"), it.text("modelName")).joinToString(" ") }?.takeIf { it.isNotBlank() },
            version = read(null, "valetudo/version")?.text("release"),
        )
    }

    private fun JsonObject.clock(key: String): String {
        val time = this[key]?.jsonObject
        val hour = time?.get("hour")?.jsonPrimitive?.intOrNull ?: 0
        val minute = time?.get("minute")?.jsonPrimitive?.intOrNull ?: 0
        return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    }

    // --- Commands ---------------------------------------------------------------------------

    suspend fun start(): Result<Unit> = basic("start")

    suspend fun pause(): Result<Unit> = basic("pause")

    suspend fun stop(): Result<Unit> = basic("stop")

    suspend fun dock(): Result<Unit> = basic("home")

    private suspend fun basic(action: String) = send("BasicControlCapability") { put("action", action) }

    suspend fun locate(): Result<Unit> = send("LocateCapability") { put("action", "locate") }

    suspend fun setFanSpeed(preset: String): Result<Unit> = send("$FAN/preset") { put("name", preset) }

    suspend fun setWaterGrade(preset: String): Result<Unit> = send("$WATER/preset") { put("name", preset) }

    suspend fun cleanRooms(ids: Collection<String>): Result<Unit> =
        send("MapSegmentationCapability") {
            put("action", "start_segment_action")
            putJsonArray("segment_ids") { ids.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            put("iterations", 1)
        }

    /** Cleans [zone], given in cells of a map whose cells are [cellSize] of the robot's units. */
    suspend fun cleanZone(
        zone: Rect,
        cellSize: Int,
    ): Result<Unit> =
        send("ZoneCleaningCapability") {
            put("action", "clean")
            put("iterations", 1)
            putJsonArray("zones") {
                addJsonObject {
                    putJsonObject("points") {
                        point("pA", zone.topLeft, cellSize)
                        point("pB", zone.topRight, cellSize)
                        point("pC", zone.bottomRight, cellSize)
                        point("pD", zone.bottomLeft, cellSize)
                    }
                }
            }
        }

    suspend fun goTo(
        cell: Offset,
        cellSize: Int,
    ): Result<Unit> =
        send("GoToLocationCapability") {
            put("action", "goto")
            point("coordinates", cell, cellSize)
        }

    private fun kotlinx.serialization.json.JsonObjectBuilder.point(
        key: String,
        cell: Offset,
        cellSize: Int,
    ) = putJsonObject(key) {
        put("x", (cell.x * cellSize).roundToInt())
        put("y", (cell.y * cellSize).roundToInt())
    }

    suspend fun setManualControl(enabled: Boolean): Result<Unit> =
        send("ManualControlCapability") { put("action", if (enabled) "enable" else "disable") }

    /** One step of [command], one of Valetudo's movement commands: `forward`, `rotate_clockwise` and so on. */
    suspend fun move(command: String): Result<Unit> =
        send("ManualControlCapability") {
            put("action", "move")
            put("movementCommand", command)
        }

    suspend fun setVolume(percent: Int): Result<Unit> =
        send(VOLUME) {
            put("action", "set_volume")
            put("value", percent)
        }

    suspend fun setCarpetMode(enabled: Boolean): Result<Unit> = send(CARPET) { put("action", if (enabled) "enable" else "disable") }

    suspend fun resetConsumable(consumable: Consumable): Result<Unit> =
        send("$CONSUMABLES/${consumable.type}" + consumable.subType?.let { "/$it" }.orEmpty()) { put("action", "reset") }

    private suspend fun send(
        capability: String,
        body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): Result<Unit> {
        val address = currentAddress.value
        if (address.isEmpty()) return Result.failure(IllegalStateException("no robot address"))
        return attempt { api.put(address, "robot/capabilities/$capability", buildJsonObject(body)) }.also { kicks.tryEmit(Unit) }
    }

    private companion object {
        const val FAN = "FanSpeedControlCapability"
        const val WATER = "WaterUsageControlCapability"
        const val CONSUMABLES = "ConsumableMonitoringCapability"
        const val VOLUME = "SpeakerVolumeControlCapability"
        const val CARPET = "CarpetModeControlCapability"
        const val DO_NOT_DISTURB = "DoNotDisturbCapability"
        const val WIFI = "WifiConfigurationCapability"

        val POLL_EVERY = 2.seconds
        val DETAILS_EVERY = 20.seconds

        /** While the robot stands still the map is read every this many polls. */
        const val IDLE_MAP_EVERY = 8

        /** How long the asking goes on after the last screen stopped looking: a tab change is shorter. */
        const val STOP_AFTER_MILLIS = 5_000L
    }
}

/** Whether the robot may be somewhere else by the next reading. */
private val RobotStatus.onTheMove: Boolean
    get() =
        this == RobotStatus.Cleaning || this == RobotStatus.Returning || this == RobotStatus.Moving || this == RobotStatus.ManualControl

/** [block]'s result or what went wrong — but a cancellation stays one. */
private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
