package dev.holo.launcher.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.holo.launcher.R
import dev.holo.launcher.data.CornerStyle
import dev.holo.launcher.data.FontChoice
import dev.holo.launcher.data.HoloSettings

/**
 * Live palette. Values are snapshot state, so changing a setting re-composes and re-draws
 * everything that reads them, including draw lambdas.
 */
object HoloColors {
    var Bg by mutableStateOf(Color(0xFF06080B))
    var Text by mutableStateOf(Color(0xFFE8EEF5))
    var TextBright by mutableStateOf(Color(0xFFF1F5F9))
    var TextMid by mutableStateOf(Color(0xFFC3CFDB))
    var TextDim by mutableStateOf(Color(0xFF8A99AB))
    var TextFaint by mutableStateOf(Color(0xFF7B8A9B))
    var Body by mutableStateOf(Color(0xFFDFE7F0))
    var Hint by mutableStateOf(Color(0xFF8FB0D4))
    var Holo by mutableStateOf(Color(0xFF9CC7EC))
    var Highlight by mutableStateOf(Color(0xFFE8955A))
    var Green by mutableStateOf(Color(0xFF6FCFA5))
    var Blue by mutableStateOf(Color(0xFF7AAEE2))
    var Orange by mutableStateOf(Color(0xFFE8955A))
    var Red by mutableStateOf(Color(0xFFE06A75))
    var Yellow by mutableStateOf(Color(0xFFDCC25E))
    val BadgeRed = Color(0xFFD65463)
    var Track by mutableStateOf(Color(0x29B0C6E0))
    var Glow by mutableStateOf(Color(0x336090C0))
    var Border by mutableStateOf(Color(0xFFC6DAF0))
    var PanelTop by mutableStateOf(Color(0xFF1F2936))
    var PanelMid by mutableStateOf(Color(0xFF121922))
    var PanelBottom by mutableStateOf(Color(0xFF0D121A))
    var HeaderFill by mutableStateOf(Color(0xFF090D13))
    var SubFill by mutableStateOf(Color(0x2B54667E))
    var SubBorder by mutableStateOf(Color(0x21A6BEDA))
    var Divider by mutableStateOf(Color(0x1AA6BEDA))
    val Tints = listOf(Color(0xFF9CC7EC), Color(0xFF8FD3C4), Color(0xFFC8B48C), Color(0xFFB8C0CA))
    val Accents: List<Color> get() = listOf(Green, Blue, Orange, Yellow, Red)
}

/** Live geometry and effect amounts. */
object HoloMetrics {
    var borderWidth by mutableFloatStateOf(1.5f)
    var borderBrightness by mutableFloatStateOf(0.6f)
    var radius by mutableFloatStateOf(16f)
    var cut by mutableStateOf(false)
    var glow by mutableFloatStateOf(0.5f)
    var shadow by mutableFloatStateOf(0.5f)
    var headerCapsules by mutableStateOf(true)
    var dynamicLight by mutableStateOf(true)
    var gap by mutableFloatStateOf(10f)
    var margin by mutableFloatStateOf(12f)
    var titleSpacing by mutableFloatStateOf(0.16f)
    var depth by mutableFloatStateOf(0.5f)
    var maxTilt by mutableFloatStateOf(6f)
    var glass by mutableFloatStateOf(0.86f)
}

object HoloFonts {
    private fun family(c: FontChoice): FontFamily = when (c) {
        FontChoice.RAJDHANI -> FontFamily(
            Font(R.font.rajdhani_regular, FontWeight.Normal), Font(R.font.rajdhani_medium, FontWeight.Medium),
            Font(R.font.rajdhani_semibold, FontWeight.SemiBold), Font(R.font.rajdhani_bold, FontWeight.Bold),
        )
        FontChoice.TITILLIUM -> FontFamily(
            Font(R.font.titillium_regular, FontWeight.Normal), Font(R.font.titillium_semibold, FontWeight.SemiBold),
            Font(R.font.titillium_bold, FontWeight.Bold),
        )
        FontChoice.EXO -> FontFamily(
            Font(R.font.exo_regular, FontWeight.Normal), Font(R.font.exo_medium, FontWeight.Medium),
            Font(R.font.exo_semibold, FontWeight.SemiBold), Font(R.font.exo_bold, FontWeight.Bold),
        )
        FontChoice.OXANIUM -> FontFamily(
            Font(R.font.oxanium_regular, FontWeight.Normal), Font(R.font.oxanium_medium, FontWeight.Medium),
            Font(R.font.oxanium_semibold, FontWeight.SemiBold), Font(R.font.oxanium_bold, FontWeight.Bold),
        )
        FontChoice.SAIRA -> FontFamily(
            Font(R.font.saira_regular, FontWeight.Normal), Font(R.font.saira_medium, FontWeight.Medium),
            Font(R.font.saira_semibold, FontWeight.SemiBold), Font(R.font.saira_bold, FontWeight.Bold),
        )
        FontChoice.ORBITRON -> FontFamily(
            Font(R.font.orbitron_regular, FontWeight.Normal), Font(R.font.orbitron_medium, FontWeight.Medium),
            Font(R.font.orbitron_semibold, FontWeight.SemiBold), Font(R.font.orbitron_bold, FontWeight.Bold),
        )
        FontChoice.CHAKRA -> FontFamily(
            Font(R.font.chakra_regular, FontWeight.Normal), Font(R.font.chakra_medium, FontWeight.Medium),
            Font(R.font.chakra_semibold, FontWeight.SemiBold), Font(R.font.chakra_bold, FontWeight.Bold),
        )
        FontChoice.MONO -> FontFamily(Font(R.font.sharetech_regular, FontWeight.Normal))
        FontChoice.SYSTEM -> FontFamily.SansSerif
    }

    private val cache = HashMap<FontChoice, FontFamily>()
    fun of(c: FontChoice): FontFamily = cache.getOrPut(c) { family(c) }

    var display by mutableStateOf(of(FontChoice.RAJDHANI))
    var body by mutableStateOf(of(FontChoice.TITILLIUM))
}

/** Text styles built from the live fonts and colours. */
object HoloType {
    val title: TextStyle
        get() = TextStyle(
            fontFamily = HoloFonts.display, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            letterSpacing = HoloMetrics.titleSpacing.em, color = HoloColors.Text,
            shadow = Shadow(HoloColors.Holo.copy(alpha = 0.3f), blurRadius = 8f),
        )
    val label: TextStyle
        get() = TextStyle(
            fontFamily = HoloFonts.display, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp,
            letterSpacing = (HoloMetrics.titleSpacing).em, color = HoloColors.TextDim,
        )
    val value: TextStyle
        get() = TextStyle(
            fontFamily = HoloFonts.display, fontWeight = FontWeight.Bold, fontSize = 12.5.sp,
            letterSpacing = 0.05.em, color = HoloColors.Text,
        )
    val body: TextStyle
        get() = TextStyle(
            fontFamily = HoloFonts.body, fontWeight = FontWeight.Normal, fontSize = 12.5.sp,
            color = HoloColors.Body,
        )
    val tab: TextStyle
        get() = TextStyle(
            fontFamily = HoloFonts.display, fontWeight = FontWeight.Bold, fontSize = 9.5.sp,
            letterSpacing = (HoloMetrics.titleSpacing * 0.9f).em,
        )
    val clock: TextStyle
        get() = TextStyle(
            fontFamily = HoloFonts.display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp,
            letterSpacing = 0.04.em, color = HoloColors.TextBright,
            shadow = Shadow(HoloColors.Holo.copy(alpha = 0.45f), blurRadius = 14f),
        )
}

object HoloTheme {
    fun apply(s: HoloSettings) {
        val text = Color(s.textColor)
        val label = Color(s.labelColor)
        val holo = Color(s.holoTint)
        val panel = Color(s.panelColor)
        val border = Color(s.borderColor)
        HoloColors.Text = text
        HoloColors.TextBright = lerp(text, Color.White, 0.35f)
        HoloColors.TextMid = lerp(text, label, 0.45f)
        HoloColors.TextDim = label
        HoloColors.TextFaint = label.copy(alpha = 0.85f)
        HoloColors.Body = lerp(text, label, 0.15f)
        HoloColors.Hint = lerp(holo, label, 0.35f)
        HoloColors.Holo = holo
        HoloColors.Highlight = Color(s.highlight)
        HoloColors.Border = border
        HoloColors.Glow = Color(s.glowColor).copy(alpha = 0.12f + 0.4f * s.glow)
        HoloColors.Track = text.copy(alpha = 0.16f)
        HoloColors.PanelTop = lerp(panel, Color.White, 0.06f)
        HoloColors.PanelMid = panel
        HoloColors.PanelBottom = lerp(panel, Color.Black, 0.3f)
        HoloColors.HeaderFill = lerp(panel, Color.Black, 0.6f)
        HoloColors.SubFill = lerp(panel, Color.White, 0.28f).copy(alpha = 0.22f)
        HoloColors.SubBorder = border.copy(alpha = 0.13f)
        HoloColors.Divider = border.copy(alpha = 0.1f)
        if (s.monoStatus) {
            HoloColors.Green = holo
            HoloColors.Blue = lerp(holo, Color.White, 0.2f)
            HoloColors.Orange = Color(s.highlight)
            HoloColors.Yellow = Color(s.highlight)
        } else {
            HoloColors.Green = Color(0xFF6FCFA5)
            HoloColors.Blue = Color(0xFF7AAEE2)
            HoloColors.Orange = Color(0xFFE8955A)
            HoloColors.Yellow = Color(0xFFDCC25E)
        }

        HoloMetrics.borderWidth = s.borderWidth
        HoloMetrics.borderBrightness = s.borderBrightness
        HoloMetrics.radius = s.cornerRadius
        HoloMetrics.cut = s.cornerStyle == CornerStyle.CUT
        HoloMetrics.glow = s.glow
        HoloMetrics.shadow = s.shadow
        HoloMetrics.headerCapsules = s.headerCapsules
        HoloMetrics.dynamicLight = s.dynamicLight
        HoloMetrics.gap = s.cardGap
        HoloMetrics.margin = s.sideMargin
        HoloMetrics.titleSpacing = s.titleSpacing
        HoloMetrics.depth = s.depth
        HoloMetrics.maxTilt = s.maxTilt
        HoloMetrics.glass = s.glass

        HoloFonts.display = HoloFonts.of(s.displayFont)
        HoloFonts.body = HoloFonts.of(s.bodyFont)
    }
}

/** Look settings that most components read. */
data class HoloStyle(
    val glass: Float = 0.86f,
    val holo: Color = HoloColors.Tints[0],
    val lowPower: Boolean = false,
    val monoIcons: Boolean = true,
    val holoSpin: Float = 1f,
    val holoBeam: Boolean = true,
    val holoBloom: Boolean = true,
    val navLabels: Boolean = true,
)

val LocalHolo = staticCompositionLocalOf { HoloStyle() }

/** Device-motion tilt in degrees (x drives yaw, y drives pitch). */
val LocalTilt = staticCompositionLocalOf<State<Offset>> { mutableStateOf(Offset.Zero) }

val HoloColorScheme = darkColorScheme(
    primary = Color(0xFF7AAEE2),
    onPrimary = Color(0xFF06080B),
    surface = Color(0xFF141B24),
    onSurface = Color(0xFFE8EEF5),
    surfaceVariant = Color(0xFF1C2530),
    onSurfaceVariant = Color(0xFFC3CFDB),
    background = Color(0xFF06080B),
    onBackground = Color(0xFFE8EEF5),
)
