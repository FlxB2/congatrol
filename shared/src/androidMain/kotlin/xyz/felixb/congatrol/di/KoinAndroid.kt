package xyz.felixb.congatrol.di

import android.content.Context
import org.koin.dsl.module
import xyz.felixb.congatrol.data.SettingsStore

/** Android entry point — call from `CongatrolApp.onCreate`. Keeps Koin types out of androidApp. */
fun initKoinAndroid(context: Context) =
    initKoin(
        module {
            single<SettingsStore> { PreferencesSettingsStore(context.applicationContext) }
        },
    )

private class PreferencesSettingsStore(
    context: Context,
) : SettingsStore {
    private val preferences = context.getSharedPreferences("robot", Context.MODE_PRIVATE)

    override fun read(key: String): String = preferences.getString(key, "").orEmpty()

    override fun write(
        key: String,
        value: String,
    ) = preferences.edit().putString(key, value).apply()
}
