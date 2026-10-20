package xyz.felixb.congatrol.di

import org.koin.dsl.module
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUserDefaults
import xyz.felixb.congatrol.data.SettingsStore
import kotlin.experimental.ExperimentalNativeApi

/** iOS entry point — call from Swift via `KoinIosKt.doInitKoinIos()`. */
@OptIn(ExperimentalNativeApi::class)
fun initKoinIos() {
    initKoin(
        module {
            single<SettingsStore> { DefaultsSettingsStore() }
        },
    )
    if (Platform.isDebugBinary && demoRequested()) requestDemoRobot()
}

private class DefaultsSettingsStore : SettingsStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun read(key: String): String = defaults.stringForKey(PREFIX + key).orEmpty()

    override fun write(
        key: String,
        value: String,
    ) = defaults.setObject(value, forKey = PREFIX + key)

    private companion object {
        const val PREFIX = "robot_"
    }
}

/**
 * Whether the app was launched with a `demo true` argument pair, dash-prefixed or bare — read off
 * the raw arguments, so both forms work.
 */
private fun demoRequested(): Boolean {
    val arguments = NSProcessInfo.processInfo.arguments.map { it.toString() }
    val key = arguments.indexOfFirst { it == "demo" || it == "-demo" }
    return key >= 0 && arguments.getOrNull(key + 1) == "true"
}
