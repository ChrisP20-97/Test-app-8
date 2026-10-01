package dev.holo.launcher.ui.home

import android.content.Intent
import android.text.format.DateFormat
import android.provider.AlarmClock
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import dev.holo.launcher.data.ClockMode
import dev.holo.launcher.data.TopLabel
import dev.holo.launcher.ui.components.holoShape
import kotlin.math.abs
import kotlin.math.floor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.holo.launcher.data.DeviceStats
import dev.holo.launcher.notifications.HoloNotification
import dev.holo.launcher.notifications.HoloNotificationListener
import dev.holo.launcher.ui.LauncherModel
import dev.holo.launcher.ui.components.CircleButton
import dev.holo.launcher.ui.components.DeviceHologram
import dev.holo.launcher.ui.components.EmptyState
import dev.holo.launcher.ui.components.HeaderPill
import dev.holo.launcher.ui.components.HoloCard
import dev.holo.launcher.ui.components.PillButton
import dev.holo.launcher.ui.components.RingGauge
import dev.holo.launcher.ui.components.SubPanel
import dev.holo.launcher.ui.components.ThinBar
import dev.holo.launcher.ui.components.TickLabel
import dev.holo.launcher.ui.components.bootIn
import dev.holo.launcher.ui.components.dotGrid
import dev.holo.launcher.ui.components.glowArc
import dev.holo.launcher.ui.theme.HoloColors
import dev.holo.launcher.ui.theme.HoloIcons
import dev.holo.launcher.ui.theme.HoloType
import dev.holo.launcher.ui.theme.LocalHolo
import dev.holo.launcher.ui.util.accentFor
import dev.holo.launcher.ui.util.clockTime
import dev.holo.launcher.ui.util.compassPoint
import dev.holo.launcher.ui.util.formatUptime
import dev.holo.launcher.ui.util.openIntent
import dev.holo.launcher.ui.util.rememberNow
import dev.holo.launcher.ui.util.thermalState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val dateFmt = DateTimeFormatter.ofPattern("EEE · dd MMM yyyy", Locale.US)

// ---------------------------------------------------------------- top bar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopBar(m: LauncherModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val s = m.settings
    val now by rememberNow(m.active)
    val dt = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val is24 = when (s.clockMode) {
        ClockMode.SYSTEM -> DateFormat.is24HourFormat(context)
        ClockMode.H24 -> true
        ClockMode.H12 -> false
    }
    val hour = if (is24) dt.hour else (dt.hour + 11) % 12 + 1
    val date = dateFmt.format(dt).uppercase()
    val sub = when (s.topLabel) {
        TopLabel.DATE -> date
        TopLabel.CALLSIGN -> s.operatorName.uppercase()
        TopLabel.BOTH -> s.operatorName.uppercase() + " · " + date
    }
    HoloCard(modifier.bootIn(0, m.bootTick, !s.lowPower), padding = 0.dp, depth = 0.9f) {
        Row(
            Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClickLabel = "Open alarms",
                    onLongClickLabel = "Launcher settings",
                    onLongClick = { m.openSettings() },
                    onClick = { openIntent(context, Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
                )
                .padding(start = 14.dp, end = if (s.topBarGear) 2.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%02d:%02d".format(hour, dt.minute), style = HoloType.clock, modifier = Modifier.alignByBaseline())
                    if (s.showSeconds) {
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "%02d".format(dt.second),
                            style = HoloType.value.copy(fontSize = 12.sp, color = HoloColors.Holo),
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                    if (!is24) {
                        Spacer(Modifier.width(3.dp))
                        Text(
                            if (dt.hour < 12) "AM" else "PM",
                            style = HoloType.label.copy(color = HoloColors.TextMid),
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
                Text(
                    sub, style = HoloType.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp),
                )
            }
            Box(Modifier.width(1.dp).fillMaxHeight(0.6f).background(HoloColors.Divider))
            CompassTape(m.env.headingDeg, s.compassSpan, Modifier.weight(1f).fillMaxHeight())
            if (s.topBarGear) {
                CircleButton(HoloIcons.Settings, "Launcher settings", size = 26.dp, iconSize = 14.dp) { m.openSettings() }
            }
        }
    }
}

private val cardinals = mapOf(0 to "N", 45 to "NE", 90 to "E", 135 to "SE", 180 to "S", 225 to "SW", 270 to "W", 315 to "NW")

/** Scrolling heading tape: ticks every 5°, numbers every 15°, cardinal points, centre caret and readout. */
@Composable
fun CompassTape(heading: Float?, span: Float, modifier: Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val minorStyle = HoloType.label.copy(color = HoloColors.TextMid, fontSize = 9.sp, letterSpacing = 0.04.em)
    val majorStyle = HoloType.value.copy(color = HoloColors.Text, fontSize = 11.sp)
    val holo = HoloColors.Holo
    val tick = HoloColors.TextMid
    val anim = remember { Animatable(heading ?: 0f) }
    LaunchedEffect(heading) {
        val target = heading ?: return@LaunchedEffect
        val cur = anim.value
        var d = (target - cur) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        anim.animateTo(cur + d, tween(240, easing = LinearEasing))
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val h = anim.value
            val w = size.width
            val cx = w / 2f
            val visible = span.coerceIn(30f, 360f)
            val ppd = w / visible
            val base = size.height - 13.dp.toPx()
            val keepClear = 30.dp.toPx()
            val half = visible / 2f + 10f
            var a = floor((h - half) / 5f) * 5f
            val end = h + half
            while (a <= end) {
                val x = cx + (a - h) * ppd
                val norm = ((a.roundToInt() % 360) + 360) % 360
                val major = norm % 45 == 0
                val mid = norm % 15 == 0
                val len = (if (major) 11.dp else if (mid) 7.dp else 4.dp).toPx()
                drawLine(
                    tick.copy(alpha = if (major) 0.95f else 0.55f),
                    Offset(x, base - len), Offset(x, base),
                    strokeWidth = (if (major) 1.4f else 1f).dp.toPx(),
                )
                if ((major || mid) && abs(x - cx) > keepClear) {
                    val layout = measurer.measure(cardinals[norm] ?: "%03d".format(norm), if (major) majorStyle else minorStyle)
                    drawText(
                        layout,
                        topLeft = Offset(x - layout.size.width / 2f, base - len - 2.dp.toPx() - layout.size.height),
                    )
                }
                a += 5f
            }
            drawLine(tick.copy(alpha = 0.35f), Offset(0f, base), Offset(w, base), strokeWidth = 1.dp.toPx())
            val caret = Path().apply {
                moveTo(cx, base + 2.dp.toPx())
                lineTo(cx - 5.dp.toPx(), base + 9.dp.toPx())
                lineTo(cx + 5.dp.toPx(), base + 9.dp.toPx())
                close()
            }
            drawPath(caret, holo)
            drawLine(holo, Offset(cx, base - 15.dp.toPx()), Offset(cx, base), strokeWidth = 1.5.dp.toPx())
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Transparent, 0.18f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        val shown = heading?.let { "%03d° %s".format(((it.roundToInt() % 360) + 360) % 360, compassPoint(it)) } ?: "NO HDG"
        val chip = holoShape(4.dp)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
                .clip(chip)
                .background(HoloColors.HeaderFill.copy(alpha = 0.9f))
                .border(1.dp, holo.copy(alpha = 0.7f), chip)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Text(shown, style = HoloType.value.copy(fontSize = 10.5.sp, color = HoloColors.TextBright))
        }
    }
}

// ---------------------------------------------------------------- device

@Composable
fun DeviceCard(m: LauncherModel, showTiles: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val s = m.stats
    HoloCard(modifier.bootIn(1, m.bootTick, !m.settings.lowPower), depth = 0.35f) {
        HeaderPill("DEVICE", HoloIcons.Phone) {
            StatusTag(
                if (s.charging) "CHARGING" else "ONLINE",
                if (s.charging) HoloColors.Yellow else HoloColors.Green,
            )
            CircleButton(HoloIcons.ArrowOut, "Battery details") {
                openIntent(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HologramBay(m, Modifier.weight(1f).fillMaxHeight())
            if (showTiles) {
                Column(Modifier.width(68.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    PowerTile(s, Modifier.weight(1f).fillMaxWidth())
                    ThermalTile(s, Modifier.weight(1f).fillMaxWidth())
                    LinkTile(s, Modifier.weight(1f).fillMaxWidth())
                    StorageTile(s, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun StatusTag(text: String, color: Color) {
    Row(
        Modifier.padding(end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text, style = HoloType.tab.copy(fontSize = 10.sp, color = color))
    }
}

@Composable
private fun HologramBay(m: LauncherModel, modifier: Modifier) {
    val s = m.stats
    SubPanel(modifier.dotGrid(), fill = HoloColors.HeaderFill.copy(alpha = 0.35f)) {
        if (m.settings.showHologram) DeviceHologram(Modifier.fillMaxSize(), s.batteryPct / 100f, s.charging, m.tilt)
        Column(Modifier.align(Alignment.TopStart).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TickLabel("CORE INTEGRITY")
            val (text, color) = thermalState(s.thermalStatus)
            Text(text, style = HoloType.value.copy(fontSize = 11.sp, letterSpacing = 0.12.em, color = color))
        }
        Column(
            Modifier.align(Alignment.TopEnd).padding(10.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("UPTIME", style = HoloType.label)
            Text(formatUptime(s.uptimeMs), style = HoloType.value.copy(fontSize = 11.sp))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            TickLabel("LINK · ${s.link.name}")
            SignalDashes(s.linkBars)
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(10.dp).width(82.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("MEMORY", style = HoloType.label)
            Text("%.1f / %.0f GB".format(s.memUsedGb, s.memTotalGb), style = HoloType.value.copy(fontSize = 11.sp))
            ThinBar(s.memFraction, HoloColors.Orange, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SignalDashes(bars: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until 4) {
            Box(
                Modifier.width(11.dp).height(2.dp)
                    .background(if (i < bars) HoloColors.Green else HoloColors.Track)
            )
        }
    }
}

// ---------------------------------------------------------------- vital tiles

@Composable
private fun VitalTile(
    modifier: Modifier,
    label: String?,
    value: String,
    icon: @Composable () -> Unit,
) {
    SubPanel(modifier) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon()
            Text(value, style = HoloType.value, maxLines = 1)
            if (label != null) Text(label, style = HoloType.label, maxLines = 1)
        }
    }
}

@Composable
fun PowerTile(s: DeviceStats, modifier: Modifier, label: String? = null) =
    VitalTile(modifier, label, "${s.batteryPct}%") {
        RingGauge(s.batteryPct / 100f, HoloColors.Red, Modifier.size(32.dp)) {
            Icon(HoloIcons.Bolt, null, tint = HoloColors.TextBright, modifier = Modifier.size(15.dp))
        }
    }

@Composable
fun ThermalTile(s: DeviceStats, modifier: Modifier, label: String? = null) =
    VitalTile(modifier, label, "%.1f°C".format(s.batteryTempC)) {
        Canvas(Modifier.size(32.dp)) {
            val u = size.minDimension / 32f
            val line = HoloColors.Text
            drawRoundRect(line, Offset(6.5f * u, 7f * u), Size(10f * u, 18f * u), androidx.compose.ui.geometry.CornerRadius(2.2f * u), style = Stroke(1.4f * u))
            drawLine(line, Offset(10f * u, 22f * u), Offset(13f * u, 22f * u), strokeWidth = 1.2f * u, cap = StrokeCap.Round)
            drawRoundRect(line, Offset(21f * u, 5f * u), Size(4.6f * u, 17.5f * u), androidx.compose.ui.geometry.CornerRadius(2.3f * u), style = Stroke(1.2f * u))
            val heat = ((s.batteryTempC - 20f) / 25f).coerceIn(0.1f, 1f)
            drawRect(HoloColors.Red, Offset(22.3f * u, (9f + 9f * (1f - heat)) * u), Size(2f * u, 9f * heat * u))
            drawRect(HoloColors.Blue, Offset(22.3f * u, 17f * u), Size(2f * u, 5f * u))
            drawCircle(HoloColors.Blue, radius = 3.3f * u, center = Offset(23.3f * u, 24.6f * u))
            drawCircle(line, radius = 3.3f * u, center = Offset(23.3f * u, 24.6f * u), style = Stroke(1.2f * u))
        }
    }

@Composable
fun LinkTile(s: DeviceStats, modifier: Modifier, label: String? = null) =
    VitalTile(modifier, label, "%.1f Mb/s".format(s.downMbps)) { WaveBracket() }

@Composable
fun StorageTile(s: DeviceStats, modifier: Modifier, label: String? = null) =
    VitalTile(modifier, label, "${(s.storageFraction * 100).roundToInt()}%") {
        RingGauge(s.storageFraction, HoloColors.Blue, Modifier.size(32.dp)) {
            Icon(HoloIcons.Drive, null, tint = HoloColors.TextBright, modifier = Modifier.size(14.dp))
        }
    }

@Composable
fun MemoryTile(s: DeviceStats, modifier: Modifier, label: String? = null) =
    VitalTile(modifier, label, "${(s.memFraction * 100).roundToInt()}%") {
        RingGauge(s.memFraction, HoloColors.Orange, Modifier.size(32.dp)) {
            Icon(HoloIcons.Chip, null, tint = HoloColors.TextBright, modifier = Modifier.size(14.dp))
        }
    }

@Composable
fun UptimeTile(s: DeviceStats, modifier: Modifier, label: String? = null) =
    VitalTile(modifier, label, formatUptime(s.uptimeMs).substring(0, 7)) {
        Icon(HoloIcons.Clock, null, tint = HoloColors.TextBright, modifier = Modifier.size(26.dp))
    }

/** Bracketed pulse trace, like the heart-rate tile in the reference. */
@Composable
private fun WaveBracket() {
    val animate = !LocalHolo.current.lowPower
    val phase = if (animate) {
        rememberInfiniteTransition(label = "wave").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "phase",
        )
    } else {
        null
    }
    Canvas(Modifier.width(40.dp).height(24.dp)) {
        val u = size.width / 40f
        val line = HoloColors.Text
        val sw = 1.3f * u
        val bracket = Path().apply {
            moveTo(6f * u, 2f * u); lineTo(2f * u, 2f * u); lineTo(2f * u, 22f * u); lineTo(6f * u, 22f * u)
            moveTo(34f * u, 2f * u); lineTo(38f * u, 2f * u); lineTo(38f * u, 22f * u); lineTo(34f * u, 22f * u)
        }
        drawPath(bracket, line, style = Stroke(sw, join = StrokeJoin.Round))
        val shift = (phase?.value ?: 0f) * 24f
        clipRect(5f * u, 0f, 35f * u, size.height) {
            val pts = floatArrayOf(0f, 13f, 6f, 13f, 8f, 13f, 10f, 6f, 12f, 19f, 14f, 9f, 16f, 13f, 24f, 13f)
            val wave = Path()
            for (rep in 0 until 3) {
                for (i in pts.indices step 2) {
                    val x = (5f + pts[i] + rep * 24f - shift) * u
                    val y = pts[i + 1] * u
                    if (rep == 0 && i == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
                }
            }
            drawPath(wave, HoloColors.Green, style = Stroke(sw, join = StrokeJoin.Round))
        }
    }
}

/** Wide-screen column of labelled vitals (the "health" column of the reference UI). */
@Composable
fun VitalsCard(m: LauncherModel, modifier: Modifier = Modifier) {
    val s = m.stats
    HoloCard(modifier.bootIn(3, m.bootTick, !m.settings.lowPower), depth = 0.55f) {
        HeaderPill("VITALS", HoloIcons.Chip)
        val tile = Modifier.fillMaxWidth()
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            PowerTile(s, tile.weight(1f), "POWER")
            ThermalTile(s, tile.weight(1f), "THERMAL")
            LinkTile(s, tile.weight(1f), "DOWNLINK")
            StorageTile(s, tile.weight(1f), "STORAGE")
            MemoryTile(s, tile.weight(1f), "MEMORY")
        }
    }
}

// ---------------------------------------------------------------- environment

private sealed interface Gauge {
    data object None : Gauge
    data object Tri : Gauge
    data class Progress(val fraction: Float, val color: Color) : Gauge
    data class Heading(val deg: Float) : Gauge
}

private data class EnvCell(val value: String, val icon: ImageVector?, val gauge: Gauge)

@Composable
fun EnvironmentCard(m: LauncherModel, wide: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val e = m.env
    val cells = buildList {
        add(EnvCell(e.gForce?.let { "%.2f G".format(it) } ?: "-- G", HoloIcons.Gravity, Gauge.None))
        add(EnvCell(e.lux?.let { "${it.roundToInt()} LX" } ?: "-- LX", HoloIcons.Sun, Gauge.None))
        if (m.hasPressure) {
            add(EnvCell(e.pressureHpa?.let { "${it.roundToInt()} HPA" } ?: "-- HPA", HoloIcons.Gauge, Gauge.Tri))
        }
        val hdg = e.headingDeg
        add(
            EnvCell(
                if (hdg != null) "%03d° %s".format(hdg.roundToInt() % 360, compassPoint(hdg)) else "---°",
                null,
                Gauge.Heading(hdg ?: 0f),
            )
        )
        if (m.hasSteps && m.perms.activity) {
            val steps = e.stepsToday
            add(
                EnvCell(
                    if (steps != null) "%,d".format(steps) else "--",
                    HoloIcons.Steps,
                    Gauge.Progress((steps ?: 0) / 10_000f, HoloColors.Green),
                )
            )
        }
        val hinge = e.hingeDeg
        if (m.hasHinge && hinge != null) {
            add(EnvCell("${hinge.roundToInt()}° HNG", HoloIcons.Hinge, Gauge.Progress(hinge / 180f, HoloColors.Blue)))
        }
    }.take(if (wide) 6 else 5)

    HoloCard(modifier.bootIn(2, m.bootTick, !m.settings.lowPower), depth = 0.6f) {
        HeaderPill("ENVIRONMENT", HoloIcons.Globe) {
            CircleButton(HoloIcons.ArrowOut, "Open weather") {
                openIntent(context, Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=weather")))
            }
        }
        SubPanel(Modifier.fillMaxWidth().weight(1f)) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                cells.forEachIndexed { i, cell ->
                    EnvCellView(cell, Modifier.weight(1f))
                    if (i < cells.lastIndex) {
                        Box(Modifier.width(1.dp).fillMaxHeight(0.7f).background(HoloColors.Divider))
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvCellView(cell: EnvCell, modifier: Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(30.dp).drawBehind { drawGauge(cell.gauge) },
            contentAlignment = Alignment.Center,
        ) {
            val g = cell.gauge
            if (g is Gauge.Heading) {
                Canvas(Modifier.size(30.dp)) {
                    val u = size.minDimension / 32f
                    rotate(g.deg) {
                        val up = Path().apply {
                            moveTo(16f * u, 8.5f * u); lineTo(18.6f * u, 16f * u); lineTo(13.4f * u, 16f * u); close()
                        }
                        val down = Path().apply {
                            moveTo(16f * u, 23.5f * u); lineTo(18.6f * u, 16f * u); lineTo(13.4f * u, 16f * u); close()
                        }
                        drawPath(up, HoloColors.TextBright)
                        drawPath(down, HoloColors.Text, style = Stroke(1.1f * u, join = StrokeJoin.Round))
                    }
                }
            } else if (cell.icon != null) {
                Icon(cell.icon, null, tint = HoloColors.Text, modifier = Modifier.size(30.dp))
            }
        }
        Text(cell.value, style = HoloType.value.copy(fontSize = 11.5.sp), maxLines = 1)
    }
}

private fun DrawScope.drawGauge(g: Gauge) {
    if (g == Gauge.None) return
    val u = size.minDimension / 32f
    val sw = 2.2f * u
    val inset = 2f * u
    drawCircle(HoloColors.Track.copy(alpha = 0.12f), radius = 14f * u, style = Stroke(sw))
    when (g) {
        Gauge.Tri -> {
            glowArc(HoloColors.Green, 94f, 57f, sw, inset, 2f * u, StrokeCap.Butt)
            glowArc(HoloColors.Orange, 160f, 49f, sw, inset, 2f * u, StrokeCap.Butt)
            glowArc(HoloColors.Red, 217f, 49f, sw, inset, 2f * u, StrokeCap.Butt)
            drawCircle(HoloColors.TextBright, radius = 1.6f * u, center = Offset(29.5f * u, 12.5f * u))
        }
        is Gauge.Progress -> if (g.fraction > 0.005f) {
            glowArc(g.color, -90f, 360f * g.fraction.coerceIn(0f, 1f), sw, inset, 2f * u)
        }
        is Gauge.Heading -> glowArc(HoloColors.Blue, g.deg - 90f - 15f, 30f, sw, inset, 2f * u)
        Gauge.None -> Unit
    }
}

// ---------------------------------------------------------------- notifications

@Composable
fun NotificationsCard(m: LauncherModel, modifier: Modifier = Modifier, filter: Set<String>? = null, title: String = "NOTIFICATIONS") {
    val context = LocalContext.current
    val list = if (filter == null) m.notifications else m.notifications.filter { it.packageName in filter }
    HoloCard(modifier.bootIn(4, m.bootTick, !m.settings.lowPower)) {
        HeaderPill(title, HoloIcons.Alert) {
            Text(
                "${list.size} ACTIVE",
                style = HoloType.label.copy(color = HoloColors.TextMid),
                modifier = Modifier.padding(end = 4.dp),
            )
            if (list.any { it.clearable }) {
                CircleButton(HoloIcons.ClearAll, "Clear notifications") {
                    if (filter == null) HoloNotificationListener.dismissAll()
                    else list.filter { it.clearable }.forEach { HoloNotificationListener.dismiss(it.key) }
                }
            }
        }
        when {
            !m.perms.notifications -> EmptyState(
                "NOTIFICATION LINK OFFLINE",
                "Grant notification access to show alerts here.",
                Modifier.fillMaxWidth().weight(1f),
                action = "GRANT",
                onAction = m.openNotificationAccess,
            )
            list.isEmpty() -> EmptyState(
                "NO NEW NOTIFICATIONS",
                "Alerts from your apps will appear here.",
                Modifier.fillMaxWidth().weight(1f),
            )
            else -> LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(list, key = { it.key }) { n ->
                    NotificationRow(
                        n,
                        m.labels[n.packageName] ?: n.packageName,
                        onOpen = { HoloNotificationListener.open(context, n) },
                        onDismiss = { HoloNotificationListener.dismiss(n.key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: HoloNotification, appLabel: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val color = accentFor(n.packageName)
    SubPanel(Modifier.fillMaxWidth().height(46.dp), radius = 9.dp, onClick = onOpen) {
        Row(
            Modifier.fillMaxSize().padding(start = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(Modifier.width(3.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${appLabel.uppercase()} · ${clockTime(n.postTime)}",
                    style = HoloType.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(n.title, n.text).filter { it.isNotBlank() }.joinToString(" — "),
                    style = HoloType.body, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (n.clearable) {
                CircleButton(HoloIcons.Close, "Dismiss notification", size = 22.dp, iconSize = 9.dp, onClick = onDismiss)
            }
        }
    }
}

// ---------------------------------------------------------------- first-run setup

@Composable
fun SetupCard(m: LauncherModel, modifier: Modifier = Modifier) {
    HoloCard(modifier.bootIn(4, m.bootTick, !m.settings.lowPower)) {
        HeaderPill("SYSTEM SETUP", HoloIcons.Settings) {
            Text(
                "${m.perms.doneCount}/4 ONLINE",
                style = HoloType.label.copy(color = HoloColors.TextMid),
                modifier = Modifier.padding(end = 4.dp),
            )
            CircleButton(HoloIcons.Close, "Hide setup") { m.dismissSetup() }
        }
        val row = Modifier.fillMaxWidth().weight(1f)
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SetupRow("Home app", "Make this your home screen", m.perms.defaultHome, "SET", m.requestHome, row)
            SetupRow("Camera backdrop", "Live see-through background", m.perms.camera, "GRANT", m.requestCamera, row)
            SetupRow("Notification link", "Alerts, badges, media", m.perms.notifications, "GRANT", m.openNotificationAccess, row)
            SetupRow("Step counter", "Steps in the environment row", m.perms.activity, "GRANT", m.requestActivity, row)
        }
    }
}

@Composable
private fun SetupRow(
    title: String,
    subtitle: String,
    done: Boolean,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier,
) {
    SubPanel(modifier, radius = 9.dp) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (done) HoloColors.Green else HoloColors.Orange))
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), style = HoloType.title.copy(fontSize = 11.sp), maxLines = 1)
                Text(subtitle, style = HoloType.body.copy(fontSize = 11.sp, color = HoloColors.TextDim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (done) {
                Icon(HoloIcons.Check, "Done", tint = HoloColors.Green, modifier = Modifier.size(16.dp))
            } else {
                PillButton(action, onClick = onAction)
            }
        }
    }
}
