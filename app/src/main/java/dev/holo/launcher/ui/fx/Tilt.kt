package dev.holo.launcher.ui.fx

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlin.math.exp

/**
 * Device-motion tilt for the 3D UI, in degrees: x drives rotationY, y drives rotationX.
 *
 * Uses the gyroscope as a leaky integrator: when the phone turns, the UI leans against the motion,
 * then eases back to flat within about a second. No gimbal problems when the phone is upright,
 * and nothing to calibrate.
 */
@Composable
fun rememberTilt(enabled: Boolean, maxDeg: Float, invert: Boolean): State<Offset> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(enabled, maxDeg, invert) {
        if (!enabled) {
            state.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }
        val sm = context.getSystemService(SensorManager::class.java)
        val gyro = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        var ax = 0f
        var ay = 0f
        var sx = 0f
        var sy = 0f
        var lastTs = 0L
        val sign = if (invert) -1f else 1f
        val gain = 57.2958f * 0.6f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val e = event ?: return
                if (lastTs == 0L) {
                    lastTs = e.timestamp
                    return
                }
                val dt = ((e.timestamp - lastTs) * 1e-9f).coerceIn(0f, 0.1f)
                lastTs = e.timestamp
                val decay = exp(-dt / 0.85f)
                ax = ax * decay + e.values[0] * dt
                ay = ay * decay + e.values[1] * dt
                val tx = (ay * gain * sign).coerceIn(-maxDeg, maxDeg)
                val ty = (ax * gain * sign).coerceIn(-maxDeg, maxDeg)
                sx += (tx - sx) * 0.25f
                sy += (ty - sy) * 0.25f
                state.value = Offset(sx, sy)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (gyro != null) sm?.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_GAME)
        onDispose {
            sm?.unregisterListener(listener)
            state.value = Offset.Zero
        }
    }
    return state
}
