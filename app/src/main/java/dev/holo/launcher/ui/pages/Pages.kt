package dev.holo.launcher.ui.pages

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.holo.launcher.data.AppEntry
import dev.holo.launcher.data.AppIcon
import dev.holo.launcher.data.AppRepository
import dev.holo.launcher.notifications.MediaHub
import dev.holo.launcher.notifications.NowPlaying
import dev.holo.launcher.ui.LauncherModel
import dev.holo.launcher.ui.components.CircleButton
import dev.holo.launcher.ui.components.CountBadge
import dev.holo.launcher.ui.components.EmptyState
import dev.holo.launcher.ui.components.HeaderPill
import dev.holo.launcher.ui.components.HoloCard
import dev.holo.launcher.ui.components.SubPanel
import dev.holo.launcher.ui.components.bootIn
import dev.holo.launcher.ui.home.NotificationsCard
import dev.holo.launcher.ui.theme.HoloColors
import dev.holo.launcher.ui.theme.HoloIcons
import dev.holo.launcher.ui.theme.HoloType
import dev.holo.launcher.ui.theme.LocalHolo
import dev.holo.launcher.ui.theme.HoloMetrics
import dev.holo.launcher.ui.components.holoShape
import dev.holo.launcher.ui.components.scaledRadius
import dev.holo.launcher.ui.util.openIntent
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- icons

/** App icon in the holo style: the app's themed glyph tinted, or a desaturated tinted original. */
@Composable
fun AppIconView(app: AppEntry, m: LauncherModel, modifier: Modifier) {
    val style = LocalHolo.current
    val sizePx = with(LocalDensity.current) { 48.dp.roundToPx() }
    val icon by produceState<AppIcon?>(null, app.key) { value = m.container.apps.icon(app, sizePx) }
    val loaded = icon ?: run {
        Box(modifier)
        return
    }
    val mono = loaded.mono
    when {
        style.monoIcons && mono != null -> Image(
            mono, null, modifier, contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(style.holo),
        )
        style.monoIcons -> {
            val filter = remember(style.holo) { holoMatrix(style.holo) }
            Image(loaded.full, null, modifier, contentScale = ContentScale.Fit, colorFilter = filter, alpha = 0.92f)
        }
        else -> Image(loaded.full, null, modifier, contentScale = ContentScale.Fit)
    }
}

private fun holoMatrix(tint: Color): ColorFilter {
    val r = tint.red * 1.25f
    val g = tint.green * 1.25f
    val b = tint.blue * 1.25f
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.3f * r, 0.59f * r, 0.11f * r, 0f, 0f,
                0.3f * g, 0.59f * g, 0.11f * g, 0f, 0f,
                0.3f * b, 0.59f * b, 0.11f * b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(app: AppEntry, m: LauncherModel, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val shape = holoShape(scaledRadius(10.dp))
    val badge = m.badges[app.packageName] ?: 0
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(HoloColors.SubFill)
                .border(1.dp, HoloColors.SubBorder, shape)
                .combinedClickable(
                    onClick = { m.container.apps.launch(app) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                )
                .padding(top = 12.dp, bottom = 9.dp, start = 4.dp, end = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            AppIconView(app, m, Modifier.size(34.dp))
            Text(
                app.label.uppercase(),
                style = HoloType.tab.copy(color = HoloColors.TextMid, letterSpacing = 0.06.em, fontSize = 9.5.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (badge > 0) CountBadge(badge, Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 5.dp))
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("App info") }, onClick = { menu = false; m.container.apps.appInfo(app) })
            DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = false; m.container.apps.uninstall(app) })
        }
    }
}

@Composable
fun AppGrid(apps: List<AppEntry>, m: LauncherModel, modifier: Modifier = Modifier) {
    if (apps.isEmpty()) {
        EmptyState("NO APPS IN THIS SECTOR", "Nothing installed matches this category yet.", modifier)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(76.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 4.dp),
    ) {
        items(apps, key = { it.key }) { app -> AppTile(app, m) }
    }
}

// ---------------------------------------------------------------- pages

@Composable
fun AppsPage(m: LauncherModel, modifier: Modifier = Modifier) {
    HoloCard(modifier.bootIn(0, m.bootTick, !m.settings.lowPower)) {
        HeaderPill("APPLICATIONS", HoloIcons.Apps) {
            Text(
                "${m.apps.size} INSTALLED",
                style = HoloType.label.copy(color = HoloColors.TextMid),
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        AppGrid(m.apps, m, Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
fun CommsPage(m: LauncherModel, modifier: Modifier = Modifier) {
    val commsSet = m.commsPackages
    val commsApps = remember(m.apps, commsSet) { m.apps.filter { it.packageName in commsSet } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HoloMetrics.gap.dp)) {
        HoloCard(Modifier.fillMaxWidth().bootIn(0, m.bootTick, !m.settings.lowPower)) {
            HeaderPill("QUICK COMMS", HoloIcons.Comms)
            Row(Modifier.fillMaxWidth().height(66.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickTile("PHONE", HoloIcons.Phone, m.comms.dialer, m, Modifier.weight(1f).fillMaxHeight())
                QuickTile("MESSAGES", HoloIcons.Comms, m.comms.sms, m, Modifier.weight(1f).fillMaxHeight())
                QuickTile("MAIL", HoloIcons.Mail, m.comms.email, m, Modifier.weight(1f).fillMaxHeight())
                QuickTile("CONTACTS", HoloIcons.Contacts, m.comms.contacts, m, Modifier.weight(1f).fillMaxHeight())
            }
        }
        HoloCard(Modifier.fillMaxWidth().weight(1f).bootIn(1, m.bootTick, !m.settings.lowPower)) {
            HeaderPill("COMMS APPS", HoloIcons.Apps)
            AppGrid(commsApps, m, Modifier.fillMaxWidth().weight(1f))
        }
        NotificationsCard(m, Modifier.fillMaxWidth().weight(0.9f), filter = commsSet, title = "INCOMING")
    }
}

@Composable
private fun QuickTile(label: String, icon: ImageVector, pkg: String?, m: LauncherModel, modifier: Modifier) {
    val enabled = pkg != null
    val color = if (enabled) HoloColors.Text else HoloColors.TextFaint
    SubPanel(modifier, onClick = if (enabled) ({ m.container.apps.launchPackage(pkg) }) else null) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, tint = if (enabled) LocalHolo.current.holo else color, modifier = Modifier.size(20.dp))
            Text(label, style = HoloType.tab.copy(color = color), maxLines = 1)
        }
        val badge = if (pkg != null) m.badges[pkg] ?: 0 else 0
        if (badge > 0) CountBadge(badge, Modifier.align(Alignment.TopEnd).padding(5.dp))
    }
}

@Composable
fun MediaPage(m: LauncherModel, modifier: Modifier = Modifier) {
    val mediaApps = remember(m.apps) { m.apps.filter { it.category in AppRepository.MEDIA_CATEGORIES } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HoloMetrics.gap.dp)) {
        NowPlayingCard(m, Modifier.fillMaxWidth().height(156.dp).bootIn(0, m.bootTick, !m.settings.lowPower))
        HoloCard(Modifier.fillMaxWidth().weight(1f).bootIn(1, m.bootTick, !m.settings.lowPower)) {
            HeaderPill("MEDIA APPS", HoloIcons.Media)
            AppGrid(mediaApps, m, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun NowPlayingCard(m: LauncherModel, modifier: Modifier) {
    val context = LocalContext.current
    val nowPlaying by produceState<NowPlaying?>(null, m.perms.notifications, m.active) {
        while (m.active) {
            value = MediaHub.current(context)
            delay(1000)
        }
    }
    HoloCard(modifier) {
        HeaderPill("NOW PLAYING", HoloIcons.Media)
        val np = nowPlaying
        when {
            !m.perms.notifications -> EmptyState(
                "MEDIA LINK OFFLINE", "Notification access also unlocks media controls.",
                Modifier.fillMaxWidth().weight(1f), action = "GRANT", onAction = m.openNotificationAccess,
            )
            np == null -> EmptyState(
                "NO ACTIVE MEDIA", "Start something in a music or video app.",
                Modifier.fillMaxWidth().weight(1f),
            )
            else -> SubPanel(Modifier.fillMaxWidth().weight(1f)) {
                Row(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val art = np.art
                    Box(
                        Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                            .background(HoloColors.Track),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (art != null) {
                            Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Icon(HoloIcons.Media, null, tint = LocalHolo.current.holo, modifier = Modifier.size(26.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(np.title, style = HoloType.body.copy(fontSize = 14.sp, color = HoloColors.TextBright), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(np.artist.uppercase(), style = HoloType.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text((m.labels[np.packageName] ?: np.packageName).uppercase(), style = HoloType.label.copy(color = LocalHolo.current.holo), maxLines = 1)
                    }
                    val controls = np.controller.transportControls
                    CircleButton(HoloIcons.Prev, "Previous", size = 30.dp, iconSize = 12.dp) { controls.skipToPrevious() }
                    CircleButton(if (np.playing) HoloIcons.Pause else HoloIcons.Play, if (np.playing) "Pause" else "Play", size = 36.dp, iconSize = 14.dp) {
                        if (np.playing) controls.pause() else controls.play()
                    }
                    CircleButton(HoloIcons.Next, "Next", size = 30.dp, iconSize = 12.dp) { controls.skipToNext() }
                }
            }
        }
    }
}

private class ToolItem(val label: String, val icon: ImageVector, val action: () -> Unit)

@Composable
fun ToolsPage(m: LauncherModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tools = listOf(
        ToolItem("NETWORK", HoloIcons.Wifi) { openIntent(context, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) },
        ToolItem("BLUETOOTH", HoloIcons.Bluetooth) { openIntent(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
        ToolItem("AUDIO", HoloIcons.Volume) { openIntent(context, Intent(Settings.Panel.ACTION_VOLUME)) },
        ToolItem("DISPLAY", HoloIcons.Display) { openIntent(context, Intent(Settings.ACTION_DISPLAY_SETTINGS)) },
        ToolItem("BATTERY", HoloIcons.Battery) { openIntent(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) },
        ToolItem("HOME APP", HoloIcons.Layers) { openIntent(context, Intent(Settings.ACTION_HOME_SETTINGS)) },
        ToolItem("LAUNCHER", HoloIcons.Settings) { m.openSettings() },
        ToolItem("SYSTEM", HoloIcons.Tools) { openIntent(context, Intent(Settings.ACTION_SETTINGS)) },
    )
    val utilities = remember(m.apps) { m.apps.filter { it.category in AppRepository.UTILITY_CATEGORIES } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HoloMetrics.gap.dp)) {
        HoloCard(Modifier.fillMaxWidth().bootIn(0, m.bootTick, !m.settings.lowPower)) {
            HeaderPill("SYSTEM CONTROL", HoloIcons.Tools)
            tools.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().height(62.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { t ->
                        SubPanel(Modifier.weight(1f).fillMaxHeight(), onClick = t.action) {
                            Column(
                                Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(t.icon, null, tint = LocalHolo.current.holo, modifier = Modifier.size(20.dp))
                                Text(t.label, style = HoloType.tab.copy(color = HoloColors.TextMid), maxLines = 1)
                            }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        HoloCard(Modifier.fillMaxWidth().weight(1f).bootIn(1, m.bootTick, !m.settings.lowPower)) {
            HeaderPill("UTILITIES", HoloIcons.Apps)
            AppGrid(utilities, m, Modifier.fillMaxWidth().weight(1f))
        }
    }
}
