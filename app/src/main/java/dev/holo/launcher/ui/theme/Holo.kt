package dev.holo.launcher.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.holo.launcher.R

object HoloColors {
    val Bg = Color(0xFF06080B)
    val Text = Color(0xFFE8EEF5)
    val TextBright = Color(0xFFF1F5F9)
    val TextMid = Color(0xFFC3CFDB)
    val TextDim = Color(0xFF8A99AB)
    val TextFaint = Color(0xFF7B8A9B)
    val Body = Color(0xFFDFE7F0)
    val Hint = Color(0xFF8FB0D4)
    val Green = Color(0xFF6FCFA5)
    val Blue = Color(0xFF7AAEE2)
    val Orange = Color(0xFFE8955A)
    val Red = Color(0xFFE06A75)
    val Yellow = Color(0xFFDCC25E)
    val BadgeRed = Color(0xFFD65463)
    val Track = Color(0x29B0C6E0)
    val Glow = Color(0x336090C0)
    val SubFill = Color(0x2B54667E)
    val SubBorder = Color(0x21A6BEDA)
    val Divider = Color(0x1AA6BEDA)
    val Tints = listOf(Color(0xFF9CC7EC), Color(0xFF8FD3C4), Color(0xFFC8B48C), Color(0xFFB8C0CA))
    val Accents = listOf(Green, Blue, Orange, Yellow, Red)
}

val Rajdhani = FontFamily(
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)

val Titillium = FontFamily(
    Font(R.font.titillium_regular, FontWeight.Normal),
    Font(R.font.titillium_semibold, FontWeight.SemiBold),
)

object HoloType {
    val title = TextStyle(
        fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        letterSpacing = 0.16.em, color = HoloColors.Text,
        shadow = Shadow(Color(0x4DA0C4E8), blurRadius = 8f),
    )
    val label = TextStyle(
        fontFamily = Rajdhani, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp,
        letterSpacing = 0.16.em, color = HoloColors.TextDim,
    )
    val value = TextStyle(
        fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 12.5.sp,
        letterSpacing = 0.05.em, color = HoloColors.Text,
    )
    val body = TextStyle(
        fontFamily = Titillium, fontWeight = FontWeight.Normal, fontSize = 12.5.sp,
        color = HoloColors.Body,
    )
    val tab = TextStyle(
        fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 9.5.sp,
        letterSpacing = 0.14.em,
    )
    val clock = TextStyle(
        fontFamily = Rajdhani, fontWeight = FontWeight.SemiBold, fontSize = 31.sp,
        letterSpacing = 0.04.em, color = HoloColors.TextBright,
        shadow = Shadow(Color(0x6BA0C8F0), blurRadius = 14f),
    )
}

/** Look settings that most components read. */
data class HoloStyle(
    val glass: Float = 0.86f,
    val holo: Color = HoloColors.Tints[0],
    val fx: Float = 1f,
    val lowPower: Boolean = false,
    val monoIcons: Boolean = true,
)

val LocalHolo = staticCompositionLocalOf { HoloStyle() }

val HoloColorScheme = darkColorScheme(
    primary = HoloColors.Blue,
    onPrimary = HoloColors.Bg,
    surface = Color(0xFF141B24),
    onSurface = HoloColors.Text,
    surfaceVariant = Color(0xFF1C2530),
    onSurfaceVariant = HoloColors.TextMid,
    background = HoloColors.Bg,
    onBackground = HoloColors.Text,
)
