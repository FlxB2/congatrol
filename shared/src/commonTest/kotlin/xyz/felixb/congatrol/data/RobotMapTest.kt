package xyz.felixb.congatrol.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RobotMapTest {
    private fun map(json: String) = parseMap(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun runsAreSpreadAlongX() {
        val map = map("""{"pixelSize":5,"layers":[{"type":"floor","pixels":[],"compressedPixels":[10,20,3,10,21,1]}]}""")

        assertEquals(listOf(Offset(10.5f, 20.5f), Offset(11.5f, 20.5f), Offset(12.5f, 20.5f), Offset(10.5f, 21.5f)), map.floor)
        assertEquals(Rect(10f, 20f, 13f, 22f), map.bounds)
    }

    @Test
    fun plainPixelsAreReadWhereThereAreNoRuns() {
        val map = map("""{"pixelSize":5,"layers":[{"type":"wall","pixels":[1,2,3,4]}]}""")

        assertEquals(listOf(Offset(1.5f, 2.5f), Offset(3.5f, 4.5f)), map.walls)
    }

    @Test
    fun entitiesAreTurnedIntoCells() {
        val map =
            map(
                """
                {"pixelSize":5,"layers":[],"entities":[
                  {"type":"robot_position","points":[100,50],"metaData":{"angle":90}},
                  {"type":"charger_location","points":[10,20]},
                  {"type":"path","points":[0,0,5,10,15]}
                ]}
                """.trimIndent(),
            )

        assertEquals(MapPose(Offset(20f, 10f), 90f), map.robot)
        assertEquals(Offset(2f, 4f), map.charger)
        // The odd coordinate left over at the end is dropped.
        assertEquals(listOf(Offset(0f, 0f), Offset(1f, 2f)), map.path)
        assertNull(map.bounds)
    }

    @Test
    fun aSparseRoomIsFoundByItsOutline() {
        // Only the corners of the room are sent, as some robots do.
        val map =
            map(
                """
                {"pixelSize":1,"layers":[{"type":"segment","metaData":{"segmentId":"10","name":"room0"},
                  "compressedPixels":[0,0,1,9,0,1,0,9,1,9,9,1]}]}
                """.trimIndent(),
            )

        assertEquals("room0", map.roomAt(Offset(4.5f, 4.5f))?.name)
        assertEquals("10", map.roomAt(Offset(0.5f, 0.5f))?.id)
        assertNull(map.roomAt(Offset(20f, 20f)))
    }

    @Test
    fun theInventedFlatHasRoomsAndARobot() {
        val map = DemoRobotApi.sampleMap()

        assertEquals(3, map.rooms.size)
        assertNotNull(map.robot)
        assertNotNull(map.bounds)
    }

    @Test
    fun aFittedViewportCentresTheBounds() {
        val viewport = fitViewport(Rect(10f, 10f, 30f, 20f), Size(200f, 200f), padding = 0f)

        assertEquals(10f, viewport.scale)
        assertEquals(Offset(0f, 50f), viewport.toScreen(Offset(10f, 10f)))
        assertEquals(Offset(30f, 20f), viewport.toMap(Offset(200f, 150f)))
    }

    @Test
    fun zoomingKeepsTheCentroidInPlace() {
        val viewport = MapViewport(scale = 2f, offset = Offset(10f, 10f))
        val centroid = Offset(50f, 70f)
        val under = viewport.toMap(centroid)

        val zoomed = viewport.transformedBy(centroid, pan = Offset.Zero, zoom = 3f, minScale = 1f, maxScale = 4f)

        // Asked for six, allowed four.
        assertEquals(4f, zoomed.scale)
        assertEquals(centroid, zoomed.toScreen(under))
    }

    @Test
    fun aRectangleDoesNotMindWhichWayItWasDragged() {
        assertEquals(Rect(1f, 2f, 5f, 8f), rectBetween(Offset(5f, 2f), Offset(1f, 8f)))
    }
}
