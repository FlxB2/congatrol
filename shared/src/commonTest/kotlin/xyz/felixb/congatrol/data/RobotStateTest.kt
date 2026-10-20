package xyz.felixb.congatrol.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RobotStateTest {
    @Test
    fun anAddressBecomesTheStartOfAUrl() {
        assertEquals("http://robot.local", normalizeAddress("  robot.local/ "))
        assertEquals("http://192.168.1.50:8080", normalizeAddress("192.168.1.50:8080"))
        assertEquals("https://robot.example", normalizeAddress("https://robot.example/"))
        assertEquals("", normalizeAddress("   "))
    }

    @Test
    fun attributesAreToldApartByClass() {
        val state =
            parseAttributes(
                Json
                    .parseToJsonElement(
                        """
                        [
                          {"__class":"StatusStateAttribute","value":"error","metaData":{"error_description":"Wheel stuck"}},
                          {"__class":"BatteryStateAttribute","level":42,"flag":"discharging"},
                          {"__class":"PresetSelectionStateAttribute","type":"fan_speed","value":"high"},
                          {"__class":"PresetSelectionStateAttribute","type":"water_grade","value":"low"},
                          {"__class":"AttachmentStateAttribute","type":"dustbin","attached":true},
                          {"__class":"SomethingNew","value":1}
                        ]
                        """.trimIndent(),
                    ).jsonArray,
            )

        assertEquals(RobotStatus.Error, state.status)
        assertEquals("Wheel stuck", state.error)
        assertEquals(42, state.battery)
        assertEquals(false, state.charging)
        assertEquals("high", state.fanSpeed)
        assertEquals("low", state.waterGrade)
        assertEquals(true, state.dustbin)
        assertNull(state.mop)
    }

    @Test
    fun aStatusTheAppHasNoWordForIsUnknown() {
        assertEquals(RobotStatus.Unknown, RobotStatus.of("singing"))
        assertEquals(RobotStatus.Unknown, RobotStatus.of(null))
    }

    @Test
    fun theNameIsTrimmedCutAndKept() {
        val store = MemoryStore()
        val repository = RobotRepository(DemoRobotApi(), store)
        assertEquals("", repository.name.value)

        repository.setName("  Rosie  ")
        assertEquals("Rosie", repository.name.value)
        assertEquals("Rosie", RobotRepository(DemoRobotApi(), store).name.value)

        repository.setName("x".repeat(100))
        assertEquals(40, repository.name.value.length)

        repository.setName(" ")
        assertEquals("", repository.name.value)
    }

    @Test
    fun theAddressIsKeptAsAUrl() {
        val store = MemoryStore()
        RobotRepository(DemoRobotApi(), store).setAddress("robot.local/")

        assertEquals("http://robot.local", RobotRepository(DemoRobotApi(), store).address.value)
    }

    @Test
    fun askedOnceTheRobotSaysHowItIs() =
        runTest {
            val repository = RobotRepository(DemoRobotApi(), MemoryStore())
            // Nowhere to ask yet.
            assertNull(repository.readState())

            repository.setAddress("robot.local")
            assertEquals(RobotStatus.Docked, repository.readState()?.status)

            repository.start()
            assertEquals(RobotStatus.Cleaning, repository.readState()?.status)
        }

    private class MemoryStore : SettingsStore {
        private val values = mutableMapOf<String, String>()

        override fun read(key: String): String = values[key].orEmpty()

        override fun write(
            key: String,
            value: String,
        ) {
            values[key] = value
        }
    }
}
