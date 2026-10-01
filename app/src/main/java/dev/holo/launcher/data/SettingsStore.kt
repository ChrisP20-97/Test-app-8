package dev.holo.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HoloSettings(
    val cameraBackdrop: Boolean = true,
    val backdropBlur: Float = 0.45f,
    val gradeStrength: Float = 0.8f,
    val tilt: Boolean = true,
    val tiltStrength: Float = 0.45f,
    val invertTilt: Boolean = false,
    val glass: Float = 0.86f,
    val fx: Float = 1f,
    val holoTint: Long = 0xFF9CC7ECL,
    val monoIcons: Boolean = true,
    val lowPower: Boolean = false,
    val operatorName: String = "OPERATOR-01",
    val setupDismissed: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("holo_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<HoloSettings> = _state.asStateFlow()

    fun update(transform: (HoloSettings) -> HoloSettings) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        write(next)
    }

    private fun read(): HoloSettings {
        val d = HoloSettings()
        return HoloSettings(
            cameraBackdrop = prefs.getBoolean("cameraBackdrop", d.cameraBackdrop),
            backdropBlur = prefs.getFloat("backdropBlur", d.backdropBlur),
            gradeStrength = prefs.getFloat("gradeStrength", d.gradeStrength),
            tilt = prefs.getBoolean("tilt", d.tilt),
            tiltStrength = prefs.getFloat("tiltStrength", d.tiltStrength),
            invertTilt = prefs.getBoolean("invertTilt", d.invertTilt),
            glass = prefs.getFloat("glass", d.glass),
            fx = prefs.getFloat("fx", d.fx),
            holoTint = prefs.getLong("holoTint", d.holoTint),
            monoIcons = prefs.getBoolean("monoIcons", d.monoIcons),
            lowPower = prefs.getBoolean("lowPower", d.lowPower),
            operatorName = prefs.getString("operatorName", d.operatorName) ?: d.operatorName,
            setupDismissed = prefs.getBoolean("setupDismissed", d.setupDismissed),
        )
    }

    private fun write(s: HoloSettings) {
        prefs.edit()
            .putBoolean("cameraBackdrop", s.cameraBackdrop)
            .putFloat("backdropBlur", s.backdropBlur)
            .putFloat("gradeStrength", s.gradeStrength)
            .putBoolean("tilt", s.tilt)
            .putFloat("tiltStrength", s.tiltStrength)
            .putBoolean("invertTilt", s.invertTilt)
            .putFloat("glass", s.glass)
            .putFloat("fx", s.fx)
            .putLong("holoTint", s.holoTint)
            .putBoolean("monoIcons", s.monoIcons)
            .putBoolean("lowPower", s.lowPower)
            .putString("operatorName", s.operatorName)
            .putBoolean("setupDismissed", s.setupDismissed)
            .apply()
    }
}
