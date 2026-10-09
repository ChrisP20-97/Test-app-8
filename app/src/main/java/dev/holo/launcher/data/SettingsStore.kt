package dev.holo.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class CornerStyle { ROUNDED, CUT }

@Serializable
enum class ClockMode { SYSTEM, H24, H12 }

@Serializable
enum class TopLabel { DATE, CALLSIGN, BOTH }

@Serializable
/** Shape of the surface the keys sit on. */
enum class KbSurface(val label: String) {
    BOWL("BOWL"), CYLINDER("CYLINDER"), FLAT("FLAT"), DOME("DOME"), SLOPE("SLOPE"),
}

/** Extra float of individual keys above the surface. */
enum class KeyDepthProfile(val label: String) {
    UNIFORM("UNIFORM"), RAKE("RAKE"), ISLANDS("ISLANDS"), SCATTER("SCATTER"),
}

enum class KeyStyle(val label: String) {
    TILE("TILE"), GLASS("GLASS"), WIRE("WIRE"), SOLID("SOLID"), BRACKET("BRACKET"),
}

enum class KbBoot(val label: String) {
    NONE("NONE"), RISE("RISE"), ASSEMBLE("ASSEMBLE"), SWEEP("SCAN"), UNFOLD("UNFOLD"), FLICKER("FLICKER"), CASCADE("CASCADE"),
}

enum class KbDeckStyle(val label: String) { NONE("NONE"), GLASS("GLASS"), FRAME("FRAME") }

enum class LabelCase { AUTO, UPPER, LOWER }

enum class KeyHaptics { OFF, LIGHT, FIRM }

enum class FontChoice(val label: String) {
    RAJDHANI("Rajdhani"),
    TITILLIUM("Titillium"),
    EXO("Exo 2"),
    OXANIUM("Oxanium"),
    SAIRA("Saira"),
    ORBITRON("Orbitron"),
    CHAKRA("Chakra Petch"),
    MONO("Share Tech Mono"),
    SYSTEM("System"),
}

/**
 * Every user-tunable option. Stored as JSON, so new fields can be added freely:
 * missing keys fall back to these defaults.
 */
@Serializable
data class HoloSettings(
    // backdrop
    val cameraBackdrop: Boolean = true,
    val backdropBlur: Float = 0.45f,
    val gradeStrength: Float = 0.8f,
    val backdropDim: Float = 0.15f,
    val backdropParallax: Float = 0.5f,
    // motion
    val tilt: Boolean = true,
    val maxTilt: Float = 6f,
    val tiltSensitivity: Float = 0.6f,
    val tiltReturn: Float = 0.85f,
    val invertTilt: Boolean = false,
    val depth: Float = 0.5f,
    // panels
    val glass: Float = 0.86f,
    val borderWidth: Float = 1.5f,
    val borderBrightness: Float = 0.6f,
    val cornerRadius: Float = 16f,
    val cornerStyle: CornerStyle = CornerStyle.ROUNDED,
    val glow: Float = 0.5f,
    val shadow: Float = 0.5f,
    val headerCapsules: Boolean = true,
    val dynamicLight: Boolean = true,
    // colours (ARGB)
    val holoTint: Long = 0xFF9CC7EC,
    val highlight: Long = 0xFFE8955A,
    val panelColor: Long = 0xFF18212C,
    val borderColor: Long = 0xFFC6DAF0,
    val textColor: Long = 0xFFE8EEF5,
    val labelColor: Long = 0xFF8A99AB,
    val glowColor: Long = 0xFF6090C0,
    val monoStatus: Boolean = false,
    // type
    val displayFont: FontChoice = FontChoice.RAJDHANI,
    val bodyFont: FontChoice = FontChoice.TITILLIUM,
    val textScale: Float = 1f,
    val titleSpacing: Float = 0.16f,
    // layout
    val cardGap: Float = 10f,
    val sideMargin: Float = 12f,
    val showEnvironment: Boolean = true,
    val showNotifications: Boolean = true,
    val showTiles: Boolean = true,
    val showHologram: Boolean = true,
    val navLabels: Boolean = true,
    val topBarGear: Boolean = true,
    // clock & compass
    val clockMode: ClockMode = ClockMode.SYSTEM,
    val showSeconds: Boolean = true,
    val topLabel: TopLabel = TopLabel.DATE,
    val compassSpan: Float = 90f,
    // hologram
    val holoSpin: Float = 1f,
    val holoBeam: Boolean = true,
    val holoBloom: Boolean = true,
    // render
    val renderPipeline: Boolean = true,
    val bloom: Float = 0.45f,
    val bloomRadius: Float = 0.5f,
    val aberration: Float = 0.35f,
    val filmGrade: Float = 0.5f,
    val vignette: Float = 0.6f,
    val grain: Float = 0.5f,
    val lightSweep: Boolean = true,
    // misc
    val monoIcons: Boolean = true,
    val lowPower: Boolean = false,
    val operatorName: String = "OPERATOR-01",
    val setupDismissed: Boolean = false,
    // keyboard: motion + shape
    val kbTilt: Boolean = true,
    val kbMaxTilt: Float = 8f,
    val kbSurface: KbSurface = KbSurface.BOWL,
    val kbCurve: Float = 0.85f,
    val kbKeyAngle: Float = 1.15f,
    val kbLens: Float = 1.25f,
    val kbDepthProfile: KeyDepthProfile = KeyDepthProfile.UNIFORM,
    val kbDepth: Float = 0.5f,
    val kbThickness: Float = 0.6f,
    val kbPressDepth: Float = 0.6f,
    // keyboard: keys
    val kbKeyStyle: KeyStyle = KeyStyle.TILE,
    val kbKeyRadius: Float = 8f,
    val kbKeyHeight: Float = 46f,
    val kbKeyGap: Float = 5f,
    val kbKeyOpacity: Float = 0.9f,
    val kbBorderWidth: Float = 1.2f,
    val kbBorderGlow: Float = 0.4f,
    // keyboard: labels
    val kbLabelScale: Float = 1f,
    val kbFollowFont: Boolean = true,
    val kbFont: FontChoice = FontChoice.RAJDHANI,
    val kbLabelCase: LabelCase = LabelCase.AUTO,
    val kbLabelGlow: Float = 0.35f,
    val kbAltBadges: Boolean = true,
    // keyboard: colour + light
    val kbCustomColors: Boolean = false,
    val kbAccent: Long = 0xFF9CC7EC,
    val kbKeyColor: Long = 0xFF18212C,
    val kbLabelColor: Long = 0xFFE8EEF5,
    val kbFnTint: Float = 0.5f,
    val kbEnterAccent: Boolean = true,
    val kbSpecular: Float = 0.6f,
    val kbShade: Float = 0.5f,
    val kbShadow: Float = 0.5f,
    val kbDepthFade: Float = 0.3f,
    // keyboard: deck
    val kbDeckStyle: KbDeckStyle = KbDeckStyle.FRAME,
    val kbDeck: Float = 0.85f,
    val kbBackdrop: Float = 0.35f,
    val kbGrid: Boolean = true,
    val kbHatch: Boolean = true,
    // keyboard: startup
    val kbBootStyle: KbBoot = KbBoot.ASSEMBLE,
    val kbBootSpeed: Float = 1f,
    // keyboard: feel
    val kbBottomPad: Float = 4f,
    val kbNumberRow: Boolean = false,
    val kbPopup: Boolean = true,
    val kbPressFx: Boolean = true,
    val kbHaptics: KeyHaptics = KeyHaptics.LIGHT,
    val kbSound: Boolean = false,
    val kbAutoCaps: Boolean = true,
    val kbDoubleSpacePeriod: Boolean = true,
)

/** One-tap keyboard looks. Only keyboard look fields change. */
enum class KbPreset(val label: String) {
    INVENTORY("Inventory"), HOLOGRAM("Hologram"), COCKPIT("Cockpit"), MINIMAL("Minimal"), ARENA("Arena");

    fun applyTo(s: HoloSettings): HoloSettings = when (this) {
        INVENTORY -> s.copy(
            kbSurface = KbSurface.BOWL, kbCurve = 0.85f, kbKeyAngle = 1.15f, kbLens = 1.25f, kbDepthProfile = KeyDepthProfile.UNIFORM,
            kbDepth = 0.5f, kbThickness = 0.6f, kbKeyStyle = KeyStyle.TILE, kbKeyRadius = 8f, kbKeyOpacity = 0.9f,
            kbBorderWidth = 1.2f, kbBorderGlow = 0.4f, kbLabelGlow = 0.35f, kbAltBadges = true, kbSpecular = 0.6f,
            kbShade = 0.5f, kbShadow = 0.5f, kbDepthFade = 0.3f, kbDeckStyle = KbDeckStyle.FRAME, kbDeck = 0.85f,
            kbGrid = true, kbHatch = true, kbBootStyle = KbBoot.ASSEMBLE, kbBackdrop = 0.35f,
        )
        HOLOGRAM -> s.copy(
            kbSurface = KbSurface.BOWL, kbCurve = 0.9f, kbKeyAngle = 1.2f, kbLens = 1.3f, kbDepthProfile = KeyDepthProfile.SCATTER,
            kbDepth = 0.6f, kbThickness = 0.15f, kbKeyStyle = KeyStyle.WIRE, kbKeyRadius = 6f, kbKeyOpacity = 0.35f,
            kbBorderWidth = 1.2f, kbBorderGlow = 0.9f, kbLabelGlow = 0.8f, kbAltBadges = false, kbSpecular = 0.3f,
            kbShade = 0.2f, kbShadow = 0f, kbDepthFade = 0.5f, kbDeckStyle = KbDeckStyle.NONE, kbDeck = 0.4f,
            kbGrid = true, kbHatch = false, kbBootStyle = KbBoot.FLICKER, kbBackdrop = 0.5f,
        )
        COCKPIT -> s.copy(
            kbSurface = KbSurface.CYLINDER, kbCurve = 0.75f, kbKeyAngle = 1f, kbLens = 1.1f, kbDepthProfile = KeyDepthProfile.RAKE,
            kbDepth = 0.7f, kbThickness = 1.1f, kbKeyStyle = KeyStyle.SOLID, kbKeyRadius = 5f, kbKeyOpacity = 1f,
            kbBorderWidth = 1f, kbBorderGlow = 0.2f, kbLabelGlow = 0.2f, kbAltBadges = true, kbSpecular = 0.8f,
            kbShade = 0.7f, kbShadow = 0.8f, kbDepthFade = 0.2f, kbDeckStyle = KbDeckStyle.FRAME, kbDeck = 0.95f,
            kbGrid = false, kbHatch = true, kbBootStyle = KbBoot.UNFOLD, kbBackdrop = 0.3f,
        )
        MINIMAL -> s.copy(
            kbSurface = KbSurface.FLAT, kbCurve = 0f, kbKeyAngle = 0f, kbLens = 1f, kbDepthProfile = KeyDepthProfile.UNIFORM,
            kbDepth = 0.3f, kbThickness = 0.3f, kbKeyStyle = KeyStyle.GLASS, kbKeyRadius = 10f, kbKeyOpacity = 0.85f,
            kbBorderWidth = 1f, kbBorderGlow = 0.15f, kbLabelGlow = 0.1f, kbAltBadges = false, kbSpecular = 0.4f,
            kbShade = 0.3f, kbShadow = 0.4f, kbDepthFade = 0f, kbDeckStyle = KbDeckStyle.GLASS, kbDeck = 0.9f,
            kbGrid = false, kbHatch = false, kbBootStyle = KbBoot.RISE, kbBackdrop = 0f,
        )
        ARENA -> s.copy(
            kbSurface = KbSurface.BOWL, kbCurve = 1.3f, kbKeyAngle = 1.5f, kbLens = 1.6f, kbDepthProfile = KeyDepthProfile.ISLANDS,
            kbDepth = 0.8f, kbThickness = 0.9f, kbKeyStyle = KeyStyle.BRACKET, kbKeyRadius = 4f, kbKeyOpacity = 0.6f,
            kbBorderWidth = 1.6f, kbBorderGlow = 0.6f, kbLabelGlow = 0.5f, kbAltBadges = true, kbSpecular = 0.7f,
            kbShade = 0.6f, kbShadow = 0.6f, kbDepthFade = 0.6f, kbDeckStyle = KbDeckStyle.FRAME, kbDeck = 0.7f,
            kbGrid = true, kbHatch = true, kbBootStyle = KbBoot.CASCADE, kbBackdrop = 0.45f,
        )
    }
}

/** Colour + font looks that can be applied in one tap. Layout and motion settings are kept. */
enum class ThemePreset(val label: String) {
    STEEL("Steel"), AMBER("Amber cockpit"), TEAL("Teal ops"), GHOST("Ghost"), CRIMSON("Crimson");

    fun applyTo(s: HoloSettings): HoloSettings = when (this) {
        STEEL -> s.copy(
            holoTint = 0xFF9CC7EC, highlight = 0xFFE8955A, panelColor = 0xFF18212C, borderColor = 0xFFC6DAF0,
            textColor = 0xFFE8EEF5, labelColor = 0xFF8A99AB, glowColor = 0xFF6090C0,
            displayFont = FontChoice.RAJDHANI, bodyFont = FontChoice.TITILLIUM, cornerStyle = CornerStyle.ROUNDED,
        )
        AMBER -> s.copy(
            holoTint = 0xFFF2B866, highlight = 0xFFEF7D3C, panelColor = 0xFF231B14, borderColor = 0xFFF2D2A8,
            textColor = 0xFFF5ECDF, labelColor = 0xFFA8957F, glowColor = 0xFFC08040,
            displayFont = FontChoice.CHAKRA, bodyFont = FontChoice.TITILLIUM, cornerStyle = CornerStyle.CUT,
        )
        TEAL -> s.copy(
            holoTint = 0xFF6FE0D0, highlight = 0xFFF2C14E, panelColor = 0xFF0F1F25, borderColor = 0xFFA8E6E0,
            textColor = 0xFFE2F4F2, labelColor = 0xFF7FA3A0, glowColor = 0xFF3FA8A0,
            displayFont = FontChoice.OXANIUM, bodyFont = FontChoice.EXO, cornerStyle = CornerStyle.ROUNDED,
        )
        GHOST -> s.copy(
            holoTint = 0xFFDCE6F0, highlight = 0xFFB9D7FF, panelColor = 0xFF1D2228, borderColor = 0xFFEEF2F6,
            textColor = 0xFFF4F6F8, labelColor = 0xFF9AA3AD, glowColor = 0xFFA0AAB8,
            displayFont = FontChoice.SAIRA, bodyFont = FontChoice.SAIRA, cornerStyle = CornerStyle.CUT,
        )
        CRIMSON -> s.copy(
            holoTint = 0xFFFF9C9C, highlight = 0xFFFF5A5A, panelColor = 0xFF21151A, borderColor = 0xFFF0B8BE,
            textColor = 0xFFF6E8EA, labelColor = 0xFFA88A90, glowColor = 0xFFC04050,
            displayFont = FontChoice.ORBITRON, bodyFont = FontChoice.EXO, cornerStyle = CornerStyle.CUT,
        )
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("holo_settings", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
    private val _state = MutableStateFlow(read())
    val state: StateFlow<HoloSettings> = _state.asStateFlow()

    fun update(transform: (HoloSettings) -> HoloSettings) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        prefs.edit().putString(KEY, json.encodeToString(HoloSettings.serializer(), next)).apply()
    }

    fun reset(keepSystem: Boolean = true) = update { old ->
        if (keepSystem) HoloSettings(operatorName = old.operatorName, setupDismissed = old.setupDismissed) else HoloSettings()
    }

    private fun read(): HoloSettings {
        val raw = prefs.getString(KEY, null) ?: return HoloSettings()
        return runCatching { json.decodeFromString(HoloSettings.serializer(), raw) }.getOrDefault(HoloSettings())
    }

    private companion object {
        const val KEY = "settings_v2"
    }
}
