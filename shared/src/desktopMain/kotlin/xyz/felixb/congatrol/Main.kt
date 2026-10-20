package xyz.felixb.congatrol

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.koin.dsl.module
import xyz.felixb.congatrol.data.SettingsStore
import xyz.felixb.congatrol.di.initKoin
import xyz.felixb.congatrol.di.requestDemoRobot

/**
 * Desktop entry point, for Compose Hot Reload — run it with `just hot`.
 *
 * Koin is started before the window, outside composition: a hot reload re-runs composables, never
 * `main`, so the graph survives every swap. Talks to the invented robot, since a development window
 * with nothing to show is nothing worth iterating on — unless `CONGATROL_ROBOT` names a real one.
 */
fun main() {
    val robot = System.getenv("CONGATROL_ROBOT").orEmpty()
    initKoin(
        module {
            single<SettingsStore> { MemorySettingsStore(mutableMapOf("address" to robot)) }
        },
    )
    if (robot.isBlank()) requestDemoRobot()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Congatrol",
            // A phone-shaped window (iPhone 17 Pro, in points), since every layout is designed for one.
            state = rememberWindowState(size = DpSize(402.dp, 874.dp)),
        ) {
            App()
        }
    }
}

/** Forgets everything when the window closes: the desktop target is a development surface. */
private class MemorySettingsStore(
    private val values: MutableMap<String, String>,
) : SettingsStore {
    override fun read(key: String): String = values[key].orEmpty()

    override fun write(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}
