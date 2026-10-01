package dev.holo.launcher.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.abs

enum class LinkType { WIFI, CELL, ETHERNET, OFFLINE }

data class DeviceStats(
    val batteryPct: Int = 0,
    val charging: Boolean = false,
    val batteryTempC: Float = 0f,
    val currentMa: Int = 0,
    val memUsedGb: Float = 0f,
    val memTotalGb: Float = 0f,
    val storageUsedGb: Float = 0f,
    val storageTotalGb: Float = 0f,
    val downMbps: Float = 0f,
    val upMbps: Float = 0f,
    val link: LinkType = LinkType.OFFLINE,
    val linkBars: Int = 0,
    val uptimeMs: Long = 0L,
    val thermalStatus: Int = 0,
) {
    val memFraction: Float get() = if (memTotalGb > 0f) memUsedGb / memTotalGb else 0f
    val storageFraction: Float get() = if (storageTotalGb > 0f) storageUsedGb / storageTotalGb else 0f
}

/** Polls the phone's own vitals once a second. Collect it only while the UI is visible. */
class DeviceStatsSource(private val context: Context) {
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    val stats: Flow<DeviceStats> = flow {
        var lastRx = TrafficStats.getTotalRxBytes()
        var lastTx = TrafficStats.getTotalTxBytes()
        var lastT = SystemClock.elapsedRealtime()
        var storage = readStorage()
        var down = 0f
        var up = 0f
        var tick = 0
        while (true) {
            val now = SystemClock.elapsedRealtime()
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val dt = (now - lastT).coerceAtLeast(1L) / 1000f
            if (tick > 0 && rx >= 0 && lastRx >= 0) {
                down = (rx - lastRx).coerceAtLeast(0L) * 8f / 1_000_000f / dt
                up = (tx - lastTx).coerceAtLeast(0L) * 8f / 1_000_000f / dt
            }
            lastRx = rx
            lastTx = tx
            lastT = now
            if (tick % 30 == 0) storage = readStorage()

            val battery = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                Context.RECEIVER_NOT_EXPORTED,
            )
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val temp = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
            val rawCurrent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            // Most devices report microamps; some Samsung builds report milliamps.
            val currentMa = when {
                rawCurrent == Int.MIN_VALUE -> 0
                abs(rawCurrent) > 20_000 -> rawCurrent / 1000
                else -> rawCurrent
            }

            val mem = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
            val (link, bars) = readLink()

            emit(
                DeviceStats(
                    batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else 0,
                    charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL,
                    batteryTempC = temp,
                    currentMa = currentMa,
                    memUsedGb = (mem.totalMem - mem.availMem) / GB,
                    memTotalGb = mem.totalMem / GB,
                    storageUsedGb = storage.first,
                    storageTotalGb = storage.second,
                    downMbps = down,
                    upMbps = up,
                    link = link,
                    linkBars = bars,
                    uptimeMs = SystemClock.elapsedRealtime(),
                    thermalStatus = powerManager.currentThermalStatus,
                )
            )
            tick++
            delay(1000)
        }
    }.flowOn(Dispatchers.IO)

    private fun readStorage(): Pair<Float, Float> = runCatching {
        val fs = StatFs(Environment.getDataDirectory().path)
        val total = fs.totalBytes / GB
        val free = fs.availableBytes / GB
        Pair(total - free, total)
    }.getOrDefault(Pair(0f, 0f))

    private fun readLink(): Pair<LinkType, Int> {
        val caps = runCatching { connectivity.getNetworkCapabilities(connectivity.activeNetwork) }.getOrNull()
            ?: return Pair(LinkType.OFFLINE, 0)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> LinkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> LinkType.CELL
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> LinkType.ETHERNET
            else -> LinkType.WIFI
        }
        val s = caps.signalStrength
        val bars = when {
            s == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED -> 3
            s > -60 -> 4
            s > -70 -> 3
            s > -80 -> 2
            else -> 1
        }
        return Pair(type, bars)
    }

    private companion object {
        const val GB = 1_000_000_000f
    }
}
