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
import dev.holo.launcher.ui.theme.LocalTilt
import dev.holo.launcher.ui.theme.HoloMetrics
import dev.holo.launcher.ui.theme.HoloTheme
import dev.holo.launcher.ui.fx.RenderPipeline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import dev.holo.launcher.ui.util.rememberLifecycleTick
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun HoloRoot(container: AppContainer, homePresses: SharedFlow<Unit>, keyboardSettings: StateFlow<Int>) {
    val context = LocalContext.current
    val settings by container.settings.state.collectAsStateWithLifecycle()
    LaunchedEffect(settings) { HoloTheme.apply(settings) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val active = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val resumeTick = rememberLifecycleTick(Lifecycle.Event.ON_RESUME)
    val startTick = rememberLifecycleTick(Lifecycle.Event.ON_START)

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var settingsStart by remember { mutableStateOf<String?>(null) }
    var seenKbRequest by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(keyboardSettings) {
        keyboardSettings.collect { n ->
            if (n > seenKbRequest) {
                seenKbRequest = n
                settingsStart = "KEYBOARD"
                showSettings = true
            }
        }
    }

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
        maxDeg = settings.maxTilt,
        sensitivity = settings.tiltSensitivity,
        returnSeconds = settings.tiltReturn,
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
        lowPower = settings.lowPower,
        monoIcons = settings.monoIcons,
        holoSpin = settings.holoSpin,
        holoBeam = settings.holoBeam,
        holoBloom = settings.holoBloom,
        navLabels = settings.navLabels,
    )

    // GPU post-processing over backdrop + panels (bloom, lens aberration, film grade, vignette).
    val pipeline = remember { RenderPipeline() }
    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    val screenDensity = LocalDensity.current
    val postEffect = remember(settings, stageSize) {
        pipeline.build(stageSize.width.toFloat(), stageSize.height.toFloat(), screenDensity.density, settings)
            ?.asComposeRenderEffect()
    }
    val pipelineOn = postEffect != null

    MaterialTheme(colorScheme = HoloColorScheme) {
        CompositionLocalProvider(
            LocalHolo provides style,
            LocalTilt provides tilt,
            LocalDensity provides Density(screenDensity.density, screenDensity.fontScale * settings.textScale),
        ) {
            Box(Modifier.fillMaxSize().background(HoloColors.Bg)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { stageSize = it }
                        .graphicsLayer { renderEffect = postEffect }
                ) {
                    Backdrop(
                        useCamera = settings.cameraBackdrop && perms.camera && !settings.lowPower && active,
                        blur = settings.backdropBlur,
                        grade = settings.gradeStrength,
                        tint = style.holo,
                        tilt = tilt,
                        dim = settings.backdropDim,
                        parallax = settings.backdropParallax,
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
                }
                val overlayAnimate = !settings.lowPower && active
                PostFxOverlay(
                    grainAmount = if (settings.lowPower) 0f else settings.grain,
                    vignette = if (pipelineOn) 0f else settings.vignette,
                    sweepOn = settings.lightSweep,
                    animate = overlayAnimate,
                )
                if (showSettings) {
                    SettingsScreen(container.settings, settings, model, settingsStart) {
                        showSettings = false
                        settingsStart = null
                    }
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
            .padding(start = HoloMetrics.margin.dp, end = HoloMetrics.margin.dp, top = 4.dp, bottom = 6.dp)
    ) {
        val gap = HoloMetrics.gap.dp
        val wide = maxWidth >= 600.dp
        val searching = query.isNotBlank()
        if (wide) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        searching -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                                TopBar(m, Modifier.fillMaxWidth().height(56.dp))
                                SearchResults(query, m, { onQuery("") }, Modifier.fillMaxWidth().weight(1f))
                            }
                            DeviceCard(m, showTiles = false, modifier = Modifier.weight(1.1f).fillMaxHeight())
                        }
                        tab == Tab.HOME -> WideDashboard(m)
                        else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                            TopBar(m, Modifier.fillMaxWidth().height(56.dp))
                            Page(tab, m, Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(64.dp),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchBar(query, onQuery, m, Modifier.weight(0.42f))
                    NavBar(tab, onTab, tabBadges, Modifier.weight(0.58f))
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                TopBar(m, Modifier.fillMaxWidth().height(56.dp))
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
    val s = m.settings
    val gap = HoloMetrics.gap.dp
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
        DeviceCard(m, showTiles = s.showTiles, modifier = Modifier.fillMaxWidth().weight(1.35f))
        if (s.showEnvironment) {
            EnvironmentCard(m, wide = false, modifier = Modifier.fillMaxWidth().height(112.dp))
        }
        if (m.showSetup) {
            SetupCard(m, Modifier.fillMaxWidth().weight(1f))
        } else if (s.showNotifications) {
            NotificationsCard(m, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/** Fold inner screen: notifications | hologram | vitals, like the in-game layout. */
@Composable
private fun WideDashboard(m: LauncherModel) {
    val s = m.settings
    val gap = HoloMetrics.gap.dp
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
            TopBar(m, Modifier.fillMaxWidth().height(56.dp))
            if (m.showSetup) {
                SetupCard(m, Modifier.fillMaxWidth().weight(1f))
            } else if (s.showNotifications) {
                NotificationsCard(m, Modifier.fillMaxWidth().weight(1f))
            } else {
                Box(Modifier.fillMaxWidth().weight(1f))
            }
            if (s.showEnvironment) {
                EnvironmentCard(m, wide = true, modifier = Modifier.fillMaxWidth().height(112.dp))
            }
        }
        DeviceCard(m, showTiles = false, modifier = Modifier.weight(1.2f).fillMaxHeight())
        if (s.showTiles) VitalsCard(m, Modifier.weight(0.55f).fillMaxHeight())
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
