package xyz.felixb.congatrol.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The invented robot's rooms can be worked on like a real one's, which is what the app's editing is tried on. */
class DemoRobotRoomsTest {
    private val robot = DemoRobotApi()

    private suspend fun rooms(): List<MapRoom> = parseMap(robot.get(DemoRobotApi.ADDRESS, "robot/state/map").jsonObject).rooms

    private suspend fun edit(
        capability: String,
        action: String,
        body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ) = robot.put(
        DemoRobotApi.ADDRESS,
        "robot/capabilities/$capability",
        buildJsonObject {
            put("action", action)
            body()
        },
    )

    /** A line across the whole flat at the height of cell row [y], in the robot's units. */
    private suspend fun splitAcross(
        room: String,
        y: Int,
    ) = edit("MapSegmentEditCapability", "split_segment") {
        put("segment_id", room)
        putJsonObject("pA") {
            put("x", 0)
            put("y", y * 5)
        }
        putJsonObject("pB") {
            put("x", 1000)
            put("y", y * 5)
        }
    }

    @Test
    fun aRoomIsRenamed() =
        runTest {
            edit("MapSegmentRenameCapability", "rename_segment") {
                put("segment_id", "2")
                put("name", "Pantry")
            }

            assertEquals(listOf("Living room", "Pantry", "Bedroom"), rooms().map { it.name })
        }

    @Test
    fun aSplitRoomBecomesTwoThatTogetherAreWhatItWas() =
        runTest {
            val before = rooms().first { it.id == "1" }.pixels.size

            splitAcross(room = "1", y = 120)

            val after = rooms()
            assertEquals(listOf("1", "2", "3", "4"), after.map { it.id })
            val kept = after.first { it.id == "1" }
            val made = after.first { it.id == "4" }
            assertEquals(before, kept.pixels.size + made.pixels.size)
            // The line was at row 120: one part lies wholly above it, the other wholly below.
            assertTrue(kept.bounds.bottom <= 120f || kept.bounds.top >= 120f)
            assertTrue(made.bounds.bottom <= 120f || made.bounds.top >= 120f)
            // A room without a name goes by its id.
            assertEquals("4", made.name)
        }

    @Test
    fun aLineThatMissesTheRoomSplitsNothing() =
        runTest {
            assertFailsWith<IllegalArgumentException> { splitAcross(room = "1", y = 10) }

            assertEquals(3, rooms().size)
        }

    @Test
    fun twoJoinedRoomsBecomeOneWithTheCellsOfBoth() =
        runTest {
            val before = rooms()

            edit("MapSegmentEditCapability", "join_segments") {
                put("segment_a_id", "2")
                put("segment_b_id", "3")
            }

            val after = rooms()
            assertEquals(listOf("1", "2"), after.map { it.id })
            assertEquals(before[1].pixels.size + before[2].pixels.size, after[1].pixels.size)
        }
}
