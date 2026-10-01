package dev.holo.launcher

import android.app.Application
import dev.holo.launcher.data.AppRepository
import dev.holo.launcher.data.DeviceStatsSource
import dev.holo.launcher.data.SensorHub
import dev.holo.launcher.data.SettingsStore

class HoloApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Long-lived singletons shared by the UI. */
class AppContainer(app: Application) {
    val settings = SettingsStore(app)
    val apps = AppRepository(app)
    val stats = DeviceStatsSource(app)
    val sensors = SensorHub(app)
}
