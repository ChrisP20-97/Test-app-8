package dev.holo.launcher.ui

import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import dev.holo.launcher.AppContainer
import dev.holo.launcher.data.AppEntry
import dev.holo.launcher.data.CommsTargets
import dev.holo.launcher.data.DeviceStats
import dev.holo.launcher.data.EnvReadings
import dev.holo.launcher.data.HoloSettings
import dev.holo.launcher.notifications.HoloNotification

enum class Tab(val label: String) { HOME("HOME"), COMMS("COMMS"), MEDIA("MEDIA"), TOOLS("TOOLS"), APPS("APPS") }

data class PermState(
    val camera: Boolean = false,
    val activity: Boolean = false,
    val notifications: Boolean = false,
    val defaultHome: Boolean = false,
) {
    val doneCount: Int get() = listOf(camera, activity, notifications, defaultHome).count { it }
    val allDone: Boolean get() = doneCount == 4
}

/** Everything the screens read, plus the actions they can trigger. Rebuilt on each recomposition of the root. */
class LauncherModel(
    val container: AppContainer,
    val settings: HoloSettings,
    val perms: PermState,
    val stats: DeviceStats,
    val env: EnvReadings,
    val notifications: List<HoloNotification>,
    val apps: List<AppEntry>,
    val labels: Map<String, String>,
    val badges: Map<String, Int>,
    val comms: CommsTargets,
    val tilt: State<Offset>,
    val active: Boolean,
    val bootTick: Int,
    val hasPressure: Boolean,
    val hasHinge: Boolean,
    val hasSteps: Boolean,
    val openSettings: () -> Unit,
    val requestCamera: () -> Unit,
    val requestActivity: () -> Unit,
    val requestHome: () -> Unit,
    val openNotificationAccess: () -> Unit,
    val dismissSetup: () -> Unit,
) {
    val commsPackages: Set<String>
        get() = comms.all + apps.filter { it.category == android.content.pm.ApplicationInfo.CATEGORY_SOCIAL }
            .map { it.packageName }

    val showSetup: Boolean get() = !settings.setupDismissed && !perms.allDone
}
