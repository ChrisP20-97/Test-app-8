package dev.holo.launcher.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.sqrt

data class EnvReadings(
    val gForce: Float? = null,
    val lux: Float? = null,
    val pressureHpa: Float? = null,
    val headingDeg: Float? = null,
    val stepsToday: Int? = null,
    val hingeDeg: Float? = null,
)

/** Environment sensors, sampled into a single snapshot a few times per second. */
class SensorHub(private val context: Context) {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val stepPrefs = context.getSharedPreferences("holo_steps", Context.MODE_PRIVATE)

    val hasPressure: Boolean get() = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
    val hasHinge: Boolean get() = sm.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null
    val hasStepCounter: Boolean get() = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun readings(includeSteps: Boolean): Flow<EnvReadings> = callbackFlow {
        var g: Float? = null
        var lux: Float? = null
        var pressure: Float? = null
        var heading: Float? = null
        var steps: Int? = null
        var hinge: Float? = null
        val rot = FloatArray(9)
        val ori = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val e = event ?: return
                when (e.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        val x = e.values[0]
                        val y = e.values[1]
                        val z = e.values[2]
                        val v = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                        val prev = g
                        g = if (prev == null) v else prev + (v - prev) * 0.15f
                    }
                    Sensor.TYPE_LIGHT -> {
                        val prev = lux
                        lux = if (prev == null) e.values[0] else prev + (e.values[0] - prev) * 0.3f
                    }
                    Sensor.TYPE_PRESSURE -> pressure = e.values[0]
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rot, e.values)
                        SensorManager.getOrientation(rot, ori)
                        val deg = ((Math.toDegrees(ori[0].toDouble()) + 360.0) % 360.0).toFloat()
                        val prev = heading
                        heading = if (prev == null) {
                            deg
                        } else {
                            var d = deg - prev
                            if (d > 180f) d -= 360f
                            if (d < -180f) d += 360f
                            (prev + d * 0.15f + 360f) % 360f
                        }
                    }
                    Sensor.TYPE_STEP_COUNTER -> steps = stepsToday(e.values[0].toLong())
                    Sensor.TYPE_HINGE_ANGLE -> hinge = e.values[0]
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val types = buildList {
            add(Sensor.TYPE_ACCELEROMETER)
            add(Sensor.TYPE_LIGHT)
            add(Sensor.TYPE_PRESSURE)
            add(Sensor.TYPE_ROTATION_VECTOR)
            add(Sensor.TYPE_HINGE_ANGLE)
            if (includeSteps) add(Sensor.TYPE_STEP_COUNTER)
        }
        types.forEach { type ->
            sm.getDefaultSensor(type)?.let { sensor ->
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }

        val ticker = launch {
            while (isActive) {
                trySend(EnvReadings(g, lux, pressure, heading, steps, hinge))
                delay(300)
            }
        }
        awaitClose {
            ticker.cancel()
            sm.unregisterListener(listener)
        }
    }

    /** The step counter counts since boot; keep a per-day baseline so we can show today's steps. */
    private fun stepsToday(counter: Long): Int {
        val today = LocalDate.now().toString()
        var base = stepPrefs.getLong("base", -1L)
        if (stepPrefs.getString("day", null) != today || base < 0 || counter < base) {
            base = counter
            stepPrefs.edit().putString("day", today).putLong("base", base).apply()
        }
        return (counter - base).toInt()
    }
}
