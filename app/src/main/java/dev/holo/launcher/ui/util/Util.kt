package dev.holo.launcher.ui.util

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.holo.launcher.ui.theme.HoloColors
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun openIntent(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: Exception) {
    false
}

/** Wall-clock millis, ticking on each second while [active]. */
@Composable
fun rememberNow(active: Boolean): State<Long> = produceState(System.currentTimeMillis(), active) {
    while (active) {
        value = System.currentTimeMillis()
        delay(1000 - value % 1000 + 5)
    }
}

/** Counter that increments on every occurrence of [event]; handy for re-checking permissions. */
@Composable
fun rememberLifecycleTick(event: Lifecycle.Event): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner, event) {
        val observer = LifecycleEventObserver { _, e -> if (e == event) tick++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

fun clockTime(millis: Long): String = timeFmt.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

fun formatUptime(ms: Long): String {
    val totalMin = ms / 60_000
    val d = totalMin / (60 * 24)
    val h = (totalMin / 60) % 24
    val m = totalMin % 60
    return "%02dD %02dH %02dM".format(d, h, m)
}

fun compassPoint(deg: Float): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[(((deg % 360f) + 360f) % 360f / 45f + 0.5f).toInt() % 8]
}

fun thermalState(status: Int): Pair<String, Color> = when (status) {
    PowerManager.THERMAL_STATUS_NONE -> Pair("NOMINAL", HoloColors.Green)
    PowerManager.THERMAL_STATUS_LIGHT -> Pair("WARM · LIGHT LOAD", HoloColors.Green)
    PowerManager.THERMAL_STATUS_MODERATE -> Pair("MODERATE HEAT", HoloColors.Yellow)
    PowerManager.THERMAL_STATUS_SEVERE -> Pair("SEVERE HEAT", HoloColors.Orange)
    else -> Pair("CRITICAL HEAT", HoloColors.Red)
}

/** Stable accent per app, used for notification bars. */
fun accentFor(packageName: String): Color =
    HoloColors.Accents[Math.floorMod(packageName.hashCode(), HoloColors.Accents.size)]
