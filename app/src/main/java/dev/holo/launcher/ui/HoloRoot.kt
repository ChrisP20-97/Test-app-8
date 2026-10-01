package dev.holo.launcher.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.holo.launcher.AppContainer
import dev.holo.launcher.data.DeviceStats
import dev.holo.launcher.data.EnvReadings
import dev.holo.launcher.notifications.HoloNotificationListener
import dev.holo.launcher.ui.backdrop.Backdrop
import dev.holo.launcher.ui.fx.PostFxOverlay
import dev.holo.launcher.ui.fx.rememberTilt
import dev.holo.launcher.ui.home.DeviceCard
import dev.holo.launcher.ui.home.EnvironmentCard
import dev.holo.launcher.ui.home.NavBar
import dev.holo.launcher.ui.home.NotificationsCard
import dev.holo.launcher.ui.home.SearchBar
import dev.holo.launcher.ui.home.SearchResults
import dev.holo.launcher.ui.home.SetupCard
import dev.holo.launcher.ui.home.TopBar
import dev.holo.launcher.ui.home.VitalsCard
import dev.holo.launcher.ui.pages.AppsPage
import dev.holo.launcher.ui.pages.CommsPage
import dev.holo.launcher.ui.pages.MediaPage
import dev.holo.launcher.ui.pages.ToolsPage
import dev.holo.launcher.ui.settings.SettingsScreen
import dev.holo.launcher.ui.theme.HoloColorScheme
import dev.holo.launcher.ui.theme.HoloColors
import dev.holo.launcher.ui.theme.HoloStyle
import dev.holo.launcher.ui.theme.LocalHolo
import dev.holo.launcher.ui.util.rememberLifecycleTick
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun HoloRoot(container: AppContainer, homePresses: SharedFlow<Unit>) {
    val context = LocalContext.current
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val active = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val resumeTick = rememberLifecycleTick(Lifecycle.Event.ON_RESUME)
    val startTick = rememberLifecycleTick(Lifecycle.Event.ON_START)

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(homePresses) {
        homePresses.collect {
            tab = Tab.HOME
            query = ""
            showSettings = false
        }
    }
    BackHandler {
        when {
            showSettings -> showSettings = false
            query.isNotEmpty() -> query = ""
            tab != Tab.HOME -> tab = Tab.HOME
        }
    }

    // ---- permissions & system roles
    val listenerConnected by HoloNotificationListener.connected.collectAsState()
    val perms = remember(resumeTick, listenerConnected) {
        PermState(
            camera = granted(context, Manifest.permission.CAMERA),
            activity = granted(context, Manifest.permission.ACTIVITY_RECOGNITION),
            notifications = HoloNotificationListener.isGranted(context),
            defaultHome = isDefaultHome(context),
        )
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) container.settings.update { it.copy(cameraBackdrop = true) }
    }
    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    // ---- live data
    val stats by container.stats.stats.collectAsStateWithLifecycle(initialValue = DeviceStats())
    val envFlow = remember(perms.activity) { container.sensors.readings(perms.activity) }
    val env by envFlow.collectAsStateWithLifecycle(initialValue = EnvReadings())
    val notifications by HoloNotificationListener.items.collectAsStateWithLifecycle()
    val apps by container.apps.apps.collectAsStateWithLifecycle()
    val labels = remember(apps) { apps.associate { it.packageName to it.label } }
    val comms = remember(apps) { container.apps.commsTargets() }
    val badges = remember(notifications) { notifications.groupingBy { it.packageName }.eachCount() }

    val tilt = rememberTilt(
        enabled = settings.tilt && !settings.lowPower && active,
        maxDeg = 1.2f + 3.8f * settings.tiltStrength,
        invert = settings.invertTilt,
    )

    val model = LauncherModel(
        container = container,
        settings = settings,
        perms = perms,
        stats = stats,
        env = env,
        notifications = notifications,
        apps = apps,
        labels = labels,
        badges = badges,
        comms = comms,
        tilt = tilt,
        active = active,
        bootTick = startTick,
        hasPressure = container.sensors.hasPressure,
        hasHinge = container.sensors.hasHinge,
        hasSteps = container.sensors.hasStepCounter,
        openSettings = { showSettings = true },
        requestCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
        requestActivity = { activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
        requestHome = {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                roleRequest.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
            } else {
                runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            }
        },
        openNotificationAccess = { openNotificationAccess(context) },
        dismissSetup = { container.settings.update { it.copy(setupDismissed = true) } },
    )

    val style = HoloStyle(
        glass = settings.glass,
        holo = Color(settings.holoTint),
        fx = settings.fx,
        lowPower = settings.lowPower,
        monoIcons = settings.monoIcons,
    )

    MaterialTheme(colorScheme = HoloColorScheme) {
        CompositionLocalProvider(LocalHolo provides style) {
            Box(Modifier.fillMaxSize().background(HoloColors.Bg)) {
                Backdrop(
                    useCamera = settings.cameraBackdrop && perms.camera && !settings.lowPower && active,
                    blur = settings.backdropBlur,
                    grade = settings.gradeStrength,
                    tint = style.holo,
                    tilt = tilt,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val t = tilt.value
                            rotationY = t.x
                            rotationX = -t.y
                            cameraDistance = 48f * density
                        }
                ) {
                    LauncherScaffold(
                        m = model,
                        tab = tab,
                        onTab = { t ->
                            tab = t
                            query = ""
                        },
                        query = query,
                        onQuery = { query = it },
                    )
                }
                if (settings.fx > 0.01f) PostFxOverlay(settings.fx, animate = !settings.lowPower && active)
                if (showSettings) {
                    SettingsScreen(container.settings, settings, model) { showSettings = false }
                }
            }
        }
    }
}

@Composable
private fun LauncherScaffold(
    m: LauncherModel,
    tab: Tab,
    onTab: (Tab) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
) {
    val commsSet = m.commsPackages
    val tabBadges = mapOf(
        Tab.COMMS to m.notifications.count { it.packageName in commsSet },
    )
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 6.dp)
    ) {
        val wide = maxWidth >= 600.dp
        val searching = query.isNotBlank()
        if (wide) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        searching -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                TopBar(m, Modifier.fillMaxWidth().height(52.dp))
                                SearchResults(query, m, { onQuery("") }, Modifier.fillMaxWidth().weight(1f))
                            }
                            DeviceCard(m, showTiles = false, modifier = Modifier.weight(1.1f).fillMaxHeight())
                        }
                        tab == Tab.HOME -> WideDashboard(m)
                        else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            TopBar(m, Modifier.fillMaxWidth().height(52.dp))
                            Page(tab, m, Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(64.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchBar(query, onQuery, m, Modifier.weight(0.42f))
                    NavBar(tab, onTab, tabBadges, Modifier.weight(0.58f))
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TopBar(m, Modifier.fillMaxWidth().height(52.dp))
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (searching) {
                        SearchResults(query, m, { onQuery("") }, Modifier.fillMaxSize())
                    } else if (tab == Tab.HOME) {
                        NarrowDashboard(m)
                    } else {
                        Page(tab, m, Modifier.fillMaxSize())
                    }
                }
                SearchBar(query, onQuery, m, Modifier.fillMaxWidth())
                NavBar(tab, onTab, tabBadges, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun NarrowDashboard(m: LauncherModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DeviceCard(m, showTiles = true, modifier = Modifier.fillMaxWidth().weight(1.35f))
        EnvironmentCard(m, wide = false, modifier = Modifier.fillMaxWidth().height(112.dp))
        if (m.showSetup) {
            SetupCard(m, Modifier.fillMaxWidth().weight(1f))
        } else {
            NotificationsCard(m, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/** Fold inner screen: notifications | hologram | vitals, like the in-game layout. */
@Composable
private fun WideDashboard(m: LauncherModel) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TopBar(m, Modifier.fillMaxWidth().height(52.dp))
            if (m.showSetup) {
                SetupCard(m, Modifier.fillMaxWidth().weight(1f))
            } else {
                NotificationsCard(m, Modifier.fillMaxWidth().weight(1f))
            }
            EnvironmentCard(m, wide = true, modifier = Modifier.fillMaxWidth().height(112.dp))
        }
        DeviceCard(m, showTiles = false, modifier = Modifier.weight(1.2f).fillMaxHeight())
        VitalsCard(m, Modifier.weight(0.55f).fillMaxHeight())
    }
}

@Composable
private fun Page(tab: Tab, m: LauncherModel, modifier: Modifier) {
    when (tab) {
        Tab.HOME -> Unit
        Tab.COMMS -> CommsPage(m, modifier)
        Tab.MEDIA -> MediaPage(m, modifier)
        Tab.TOOLS -> ToolsPage(m, modifier)
        Tab.APPS -> AppsPage(m, modifier)
    }
}

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun isDefaultHome(context: Context): Boolean =
    runCatching { context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true }
        .getOrDefault(false)

private fun openNotificationAccess(context: Context) {
    val component = ComponentName(context, HoloNotificationListener::class.java).flattenToString()
    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
    val ok = runCatching { context.startActivity(detail) }.isSuccess
    if (!ok) runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    Toast.makeText(
        context,
        "If the switch is greyed out: App info › ⋮ › Allow restricted settings",
        Toast.LENGTH_LONG,
    ).show()
}
