package dev.holo.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import dev.holo.launcher.data.KeyDepthProfile
import dev.holo.launcher.data.KeyHaptics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.holo.launcher.BuildConfig
import dev.holo.launcher.data.ClockMode
import dev.holo.launcher.data.CornerStyle
import dev.holo.launcher.data.FontChoice
import dev.holo.launcher.data.HoloSettings
import dev.holo.launcher.data.SettingsStore
import dev.holo.launcher.data.ThemePreset
import dev.holo.launcher.data.TopLabel
import dev.holo.launcher.ui.LauncherModel
import dev.holo.launcher.ui.components.CircleButton
import dev.holo.launcher.ui.components.HeaderPill
import dev.holo.launcher.ui.components.HoloCard
import dev.holo.launcher.ui.components.PillButton
import dev.holo.launcher.ui.components.SubPanel
import dev.holo.launcher.ui.components.TickLabel
import dev.holo.launcher.ui.components.holoShape
import dev.holo.launcher.ui.components.scaledRadius
import dev.holo.launcher.ui.theme.HoloColors
import dev.holo.launcher.ui.theme.HoloFonts
import dev.holo.launcher.ui.theme.HoloIcons
import dev.holo.launcher.ui.theme.HoloType
import kotlin.math.roundToInt

private enum class Section(val label: String) {
    THEME("THEME"), PANELS("PANELS"), TYPE("TYPE"), LAYOUT("LAYOUT"), CLOCK("CLOCK"),
    MOTION("MOTION"), RENDER("RENDER"), HOLOGRAM("HOLOGRAM"), BACKDROP("BACKDROP"), KEYBOARD("KEYBOARD"), SYSTEM("SYSTEM"),
}

private val brightSwatches = listOf(
    0xFF9CC7EC, 0xFF7AAEE2, 0xFF6FE0D0, 0xFF6FCFA5, 0xFFB5E35A, 0xFFDCC25E, 0xFFF2B866, 0xFFE8955A,
    0xFFEF7D3C, 0xFFFF5A5A, 0xFFE06A75, 0xFFC48BE8, 0xFFDCE6F0, 0xFFFFFFFF, 0xFFC6DAF0, 0xFF8A99AB,
)
private val darkSwatches = listOf(
    0xFF18212C, 0xFF0F1F25, 0xFF231B14, 0xFF1D2228, 0xFF21151A, 0xFF101418, 0xFF1A1A1A, 0xFF0B1A2E,
    0xFF2A2F36, 0xFF13201A, 0xFF1E1830, 0xFF262626,
)

/**
 * Launcher config, docked as a sheet (bottom on phones, right side on the Fold's inner screen)
 * so every change previews live on the dashboard behind it.
 */
@Composable
fun SettingsScreen(
    store: SettingsStore,
    s: HoloSettings,
    m: LauncherModel,
    startSection: String? = null,
    onClose: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(Section.THEME) }
    LaunchedEffect(startSection) {
        Section.entries.firstOrNull { it.name == startSection }?.let { section = it }
    }
    val set: ((HoloSettings) -> HoloSettings) -> Unit = { store.update(it) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        // Tap the visible dashboard area to close.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
        )
        val sheet = if (wide) {
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(maxWidth * 0.46f)
        } else {
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(maxHeight * 0.6f)
        }
        Box(
            sheet
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(10.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
        ) {
            HoloCard(Modifier.fillMaxSize(), depth = 0f) {
                HeaderPill("LAUNCHER CONFIG", HoloIcons.Settings) {
                    PillButton("RESET") { store.reset() }
                    CircleButton(HoloIcons.Close, "Close settings", onClick = onClose)
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Section.entries.forEach { sec -> SectionChip(sec.label, sec == section) { section = sec } }
                }
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when (section) {
                        Section.THEME -> ThemeSection(s, set)
                        Section.PANELS -> PanelsSection(s, set)
                        Section.TYPE -> TypeSection(s, set)
                        Section.LAYOUT -> LayoutSection(s, set)
                        Section.CLOCK -> ClockSection(s, set)
                        Section.MOTION -> MotionSection(s, set)
                        Section.RENDER -> RenderSection(s, set)
                        Section.HOLOGRAM -> HologramSection(s, set)
                        Section.BACKDROP -> BackdropSection(s, set, m)
                        Section.KEYBOARD -> KeyboardSection(s, set, m)
                        Section.SYSTEM -> SystemSection(s, set, m, store)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private typealias Setter = ((HoloSettings) -> HoloSettings) -> Unit

// ---------------------------------------------------------------- sections

@Composable
private fun ThemeSection(s: HoloSettings, set: Setter) {
    Label("PRESETS")
    PresetRow { p -> set { p.applyTo(it) } }
    Label("COLOURS")
    ColorRow("Hologram / accent", s.holoTint, brightSwatches) { c -> set { it.copy(holoTint = c) } }
    ColorRow("Highlight", s.highlight, brightSwatches) { c -> set { it.copy(highlight = c) } }
    ColorRow("Panel", s.panelColor, darkSwatches) { c -> set { it.copy(panelColor = c) } }
    ColorRow("Rim", s.borderColor, brightSwatches) { c -> set { it.copy(borderColor = c) } }
    ColorRow("Text", s.textColor, brightSwatches) { c -> set { it.copy(textColor = c) } }
    ColorRow("Labels", s.labelColor, brightSwatches) { c -> set { it.copy(labelColor = c) } }
    ColorRow("Glow", s.glowColor, brightSwatches) { c -> set { it.copy(glowColor = c) } }
    ToggleRow("Monochrome status colours", "Gauges use your accent instead of green/blue/amber", s.monoStatus) { v -> set { it.copy(monoStatus = v) } }
    ToggleRow("Holo app icons", "Tinted monochrome icons", s.monoIcons) { v -> set { it.copy(monoIcons = v) } }
}

@Composable
private fun PanelsSection(s: HoloSettings, set: Setter) {
    SliderRow("Panel opacity", s.glass, 0.3f..1f, pct) { v -> set { it.copy(glass = v) } }
    SliderRow("Border width", s.borderWidth, 0f..4f, dp1) { v -> set { it.copy(borderWidth = v) } }
    SliderRow("Rim brightness", s.borderBrightness, 0f..1.5f, pct) { v -> set { it.copy(borderBrightness = v) } }
    SliderRow("Corner radius", s.cornerRadius, 0f..32f, dp0) { v -> set { it.copy(cornerRadius = v) } }
    ChoiceRow("Corner style", CornerStyle.entries, s.cornerStyle, { if (it == CornerStyle.CUT) "CUT" else "ROUNDED" }) { v -> set { it.copy(cornerStyle = v) } }
    SliderRow("Rim glow", s.glow, 0f..1f, pct) { v -> set { it.copy(glow = v) } }
    SliderRow("Cast shadow", s.shadow, 0f..1f, pct) { v -> set { it.copy(shadow = v) } }
    ToggleRow("Header capsules", "Dark pill behind each card title", s.headerCapsules) { v -> set { it.copy(headerCapsules = v) } }
    ToggleRow("Dynamic lighting", "Sheen, rims and shadows follow the tilt", s.dynamicLight) { v -> set { it.copy(dynamicLight = v) } }
}

@Composable
private fun TypeSection(s: HoloSettings, set: Setter) {
    FontRow("Display font (titles, numbers)", s.displayFont) { f -> set { it.copy(displayFont = f) } }
    FontRow("Body font", s.bodyFont) { f -> set { it.copy(bodyFont = f) } }
    SliderRow("Text size", s.textScale, 0.8f..1.35f, times) { v -> set { it.copy(textScale = v) } }
    SliderRow("Title letter spacing", s.titleSpacing, 0f..0.4f, em2) { v -> set { it.copy(titleSpacing = v) } }
}

@Composable
private fun LayoutSection(s: HoloSettings, set: Setter) {
    SliderRow("Card spacing", s.cardGap, 2f..20f, dp0) { v -> set { it.copy(cardGap = v) } }
    SliderRow("Screen margin", s.sideMargin, 4f..28f, dp0) { v -> set { it.copy(sideMargin = v) } }
    ToggleRow("Environment row", null, s.showEnvironment) { v -> set { it.copy(showEnvironment = v) } }
    ToggleRow("Notifications card", null, s.showNotifications) { v -> set { it.copy(showNotifications = v) } }
    ToggleRow("Vital tiles", "Power / thermal / link / storage column", s.showTiles) { v -> set { it.copy(showTiles = v) } }
    ToggleRow("Device hologram", null, s.showHologram) { v -> set { it.copy(showHologram = v) } }
    ToggleRow("Tab labels", null, s.navLabels) { v -> set { it.copy(navLabels = v) } }
    ToggleRow("Settings button in top bar", "Long-press the top bar also opens config", s.topBarGear) { v -> set { it.copy(topBarGear = v) } }
}

@Composable
private fun ClockSection(s: HoloSettings, set: Setter) {
    ChoiceRow("Clock", ClockMode.entries, s.clockMode, { when (it) { ClockMode.SYSTEM -> "SYSTEM"; ClockMode.H24 -> "24H"; ClockMode.H12 -> "12H" } }) { v -> set { it.copy(clockMode = v) } }
    ToggleRow("Show seconds", null, s.showSeconds) { v -> set { it.copy(showSeconds = v) } }
    ChoiceRow("Under the clock", TopLabel.entries, s.topLabel, { it.name }) { v -> set { it.copy(topLabel = v) } }
    CallsignRow(s.operatorName) { v -> set { it.copy(operatorName = v.take(24)) } }
    SliderRow("Compass span", s.compassSpan, 40f..180f, deg) { v -> set { it.copy(compassSpan = v) } }
}

@Composable
private fun MotionSection(s: HoloSettings, set: Setter) {
    ToggleRow("3D tilt", "The UI leans as the phone moves", s.tilt) { v -> set { it.copy(tilt = v) } }
    SliderRow("Max tilt", s.maxTilt, 0f..30f, deg) { v -> set { it.copy(maxTilt = v) } }
    SliderRow("Sensitivity", s.tiltSensitivity, 0.1f..2.5f, times) { v -> set { it.copy(tiltSensitivity = v) } }
    SliderRow("Settle time", s.tiltReturn, 0.2f..3f, secs) { v -> set { it.copy(tiltReturn = v) } }
    ToggleRow("Invert tilt", null, s.invertTilt) { v -> set { it.copy(invertTilt = v) } }
    SliderRow("Card depth separation", s.depth, 0f..1.5f, pct) { v -> set { it.copy(depth = v) } }
    SliderRow("Backdrop parallax", s.backdropParallax, 0f..1f, pct) { v -> set { it.copy(backdropParallax = v) } }
}

@Composable
private fun RenderSection(s: HoloSettings, set: Setter) {
    ToggleRow("Render pipeline", "GPU bloom, lens and film grade over everything. Uses more battery.", s.renderPipeline) { v -> set { it.copy(renderPipeline = v) } }
    SliderRow("Bloom", s.bloom, 0f..1f, pct) { v -> set { it.copy(bloom = v) } }
    SliderRow("Bloom radius", s.bloomRadius, 0f..1f, pct) { v -> set { it.copy(bloomRadius = v) } }
    SliderRow("Lens aberration", s.aberration, 0f..1f, pct) { v -> set { it.copy(aberration = v) } }
    SliderRow("Film grade", s.filmGrade, 0f..1f, pct) { v -> set { it.copy(filmGrade = v) } }
    SliderRow("Vignette", s.vignette, 0f..1f, pct) { v -> set { it.copy(vignette = v) } }
    SliderRow("Film grain", s.grain, 0f..1f, pct) { v -> set { it.copy(grain = v) } }
    ToggleRow("Light sweep", "Occasional soft reflection across the glass", s.lightSweep) { v -> set { it.copy(lightSweep = v) } }
}

@Composable
private fun HologramSection(s: HoloSettings, set: Setter) {
    SliderRow("Spin speed", s.holoSpin, 0f..3f, times) { v -> set { it.copy(holoSpin = v) } }
    ToggleRow("Projector beam", null, s.holoBeam) { v -> set { it.copy(holoBeam = v) } }
    ToggleRow("Hologram bloom", null, s.holoBloom) { v -> set { it.copy(holoBloom = v) } }
    ColorRow("Hologram colour", s.holoTint, brightSwatches) { c -> set { it.copy(holoTint = c) } }
}

@Composable
private fun BackdropSection(s: HoloSettings, set: Setter, m: LauncherModel) {
    ToggleRow("Live camera backdrop", "See-through background from the rear camera", s.cameraBackdrop) { v ->
        set { it.copy(cameraBackdrop = v) }
        if (v && !m.perms.camera) m.requestCamera()
    }
    SliderRow("Blur", s.backdropBlur, 0f..1f, pct) { v -> set { it.copy(backdropBlur = v) } }
    SliderRow("Colour grade", s.gradeStrength, 0f..1f, pct) { v -> set { it.copy(gradeStrength = v) } }
    SliderRow("Dim", s.backdropDim, 0f..1f, pct) { v -> set { it.copy(backdropDim = v) } }
}

@Composable
private fun KeyboardSection(s: HoloSettings, set: Setter, m: LauncherModel) {
    val context = LocalContext.current
    val (enabled, current) = remember(m.perms) {
        val imm = context.getSystemService(InputMethodManager::class.java)
        val on = imm?.enabledInputMethodList?.any { it.packageName == context.packageName } == true
        val def = android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD,
        ).orEmpty()
        on to def.startsWith(context.packageName + "/")
    }
    Label("SETUP")
    ActionRow("1 · Enable Holo keyboard", if (enabled) "ENABLED" else "ENABLE", enabled) {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
    ActionRow("2 · Switch to it", if (current) "ACTIVE" else "SWITCH", current) {
        context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }
    TestFieldRow()
    Label("MOTION & DEPTH")
    ToggleRow("3D keys", "Keys tilt and shift in depth as the phone moves", s.kbTilt) { v -> set { it.copy(kbTilt = v) } }
    SliderRow("Max tilt", s.kbMaxTilt, 0f..30f, deg) { v -> set { it.copy(kbMaxTilt = v) } }
    ChoiceRow("Depth profile", KeyDepthProfile.entries, s.kbDepthProfile, { it.label }) { v -> set { it.copy(kbDepthProfile = v) } }
    SliderRow("Depth separation", s.kbDepth, 0f..2.5f, pct) { v -> set { it.copy(kbDepth = v) } }
    SliderRow("Key thickness", s.kbThickness, 0f..1.5f, pct) { v -> set { it.copy(kbThickness = v) } }
    Label("LOOK")
    SliderRow("Key height", s.kbKeyHeight, 36f..64f, dp0) { v -> set { it.copy(kbKeyHeight = v) } }
    SliderRow("Key gap", s.kbKeyGap, 2f..12f, dp1) { v -> set { it.copy(kbKeyGap = v) } }
    SliderRow("Deck opacity", s.kbDeck, 0f..1f, pct) { v -> set { it.copy(kbDeck = v) } }
    SliderRow("Label size", s.kbLabelScale, 0.8f..1.3f, times) { v -> set { it.copy(kbLabelScale = v) } }
    SliderRow("Bottom padding", s.kbBottomPad, 0f..48f, dp0) { v -> set { it.copy(kbBottomPad = v) } }
    Label("FEEL")
    ToggleRow("Key preview", "Hologram plate above the key you press", s.kbPopup) { v -> set { it.copy(kbPopup = v) } }
    ToggleRow("Press glow", "Glow and a ring as each key springs back", s.kbPressFx) { v -> set { it.copy(kbPressFx = v) } }
    ToggleRow("Boot animation", "Keys rise out of the deck when the keyboard opens", s.kbBootAnim) { v -> set { it.copy(kbBootAnim = v) } }
    ChoiceRow("Vibration", KeyHaptics.entries, s.kbHaptics, { it.name }) { v -> set { it.copy(kbHaptics = v) } }
    ToggleRow("Key sounds", null, s.kbSound) { v -> set { it.copy(kbSound = v) } }
    ToggleRow("Number row", null, s.kbNumberRow) { v -> set { it.copy(kbNumberRow = v) } }
    ToggleRow("Auto capitals", null, s.kbAutoCaps) { v -> set { it.copy(kbAutoCaps = v) } }
    ToggleRow("Double-space full stop", null, s.kbDoubleSpacePeriod) { v -> set { it.copy(kbDoubleSpacePeriod = v) } }
}

@Composable
private fun TestFieldRow() {
    var text by remember { mutableStateOf("") }
    SubPanel(Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Row(
            Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Test", style = HoloType.body.copy(fontSize = 14.sp, color = HoloColors.TextBright))
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text("Tap to try the keyboard", style = HoloType.body.copy(fontSize = 14.sp, color = HoloColors.TextDim))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = HoloType.body.copy(fontSize = 14.sp, color = HoloColors.TextBright),
                    cursorBrush = SolidColor(HoloColors.Holo),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SystemSection(s: HoloSettings, set: Setter, m: LauncherModel, store: SettingsStore) {
    ToggleRow("Low power mode", "Stops the camera, motion, render pipeline and animations", s.lowPower) { v -> set { it.copy(lowPower = v) } }
    Label("SYSTEM LINKS")
    ActionRow("Home app", if (m.perms.defaultHome) "ACTIVE" else "SET", m.perms.defaultHome, m.requestHome)
    ActionRow("Camera", if (m.perms.camera) "GRANTED" else "GRANT", m.perms.camera, m.requestCamera)
    ActionRow("Notification access", if (m.perms.notifications) "GRANTED" else "GRANT", m.perms.notifications, m.openNotificationAccess)
    ActionRow("Step counter", if (m.perms.activity) "GRANTED" else "GRANT", m.perms.activity, m.requestActivity)
    ActionRow("Setup card", "SHOW", false) { set { it.copy(setupDismissed = false) } }
    ActionRow("Reset everything to defaults", "RESET", false) { store.reset() }
    Text(
        "HOLO LAUNCHER ${BuildConfig.VERSION_NAME}",
        style = HoloType.label,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp),
    )
}

// ---------------------------------------------------------------- formatters

private val pct: (Float) -> String = { "${(it * 100).roundToInt()}%" }
private val dp0: (Float) -> String = { "${it.roundToInt()} dp" }
private val dp1: (Float) -> String = { "%.1f dp".format(it) }
private val deg: (Float) -> String = { "${it.roundToInt()}°" }
private val times: (Float) -> String = { "%.2f×".format(it) }
private val secs: (Float) -> String = { "%.1f s".format(it) }
private val em2: (Float) -> String = { "%.2f em".format(it) }

// ---------------------------------------------------------------- rows

@Composable
private fun Label(text: String) {
    Box(Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)) { TickLabel(text) }
}

@Composable
private fun SectionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = holoShape(scaledRadius(9.dp))
    val holo = HoloColors.Holo
    Box(
        Modifier
            .height(30.dp)
            .clip(shape)
            .background(if (selected) holo.copy(alpha = 0.22f) else HoloColors.SubFill)
            .border(1.dp, if (selected) holo.copy(alpha = 0.8f) else HoloColors.SubBorder, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HoloType.tab.copy(color = if (selected) HoloColors.TextBright else HoloColors.TextMid, fontSize = 10.5.sp))
    }
}

@Composable
private fun ToggleRow(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val holo = HoloColors.Holo
    SubPanel(Modifier.fillMaxWidth().heightIn(min = 52.dp), onClick = { onChange(!checked) }) {
        Row(
            Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = HoloType.body.copy(fontSize = 14.sp, color = HoloColors.TextBright))
                if (hint != null) Text(hint, style = HoloType.body.copy(fontSize = 11.5.sp, color = HoloColors.TextDim))
            }
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = HoloColors.Bg,
                    checkedTrackColor = holo,
                    checkedBorderColor = holo,
                    uncheckedThumbColor = HoloColors.TextDim,
                    uncheckedTrackColor = HoloColors.Track,
                    uncheckedBorderColor = HoloColors.TextDim.copy(alpha = 0.5f),
                ),
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    val holo = HoloColors.Holo
    SubPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = HoloType.body.copy(fontSize = 14.sp, color = HoloColors.TextBright), modifier = Modifier.weight(1f))
                Text(format(value), style = HoloType.value.copy(color = holo))
            }
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = holo,
                    activeTrackColor = holo,
                    inactiveTrackColor = HoloColors.Track,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(label: String, options: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
    SubPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = HoloType.body.copy(fontSize = 14.sp, color = HoloColors.TextBright))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { o -> SectionChip(name(o), o == selected) { onSelect(o) } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetRow(onPick: (ThemePreset) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ThemePreset.entries.forEach { p ->
            val look = p.applyTo(HoloSettings())
            val shape = holoShape(scaledRadius(9.dp))
            Row(
                Modifier
                    .height(36.dp)
                    .clip(shape)
                    .background(Color(look.panelColor))
                    .border(1.dp, Color(look.borderColor).copy(alpha = 0.6f), shape)
                    .clickable(role = Role.Button) { onPick(p) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(look.holoTint)))
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(look.highlight)))
                Text(
                    p.label.uppercase(),
                    style = HoloType.tab.copy(color = Color(look.textColor), fontSize = 10.5.sp, fontFamily = HoloFonts.of(look.displayFont)),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontRow(label: String, selected: FontChoice, onSelect: (FontChoice) -> Unit) {
    SubPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = HoloType.body.copy(fontSize = 14.sp, color = HoloColors.TextBright))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FontChoice.entries.forEach { f ->
                    val on = f == selected
                    val shape = holoShape(scaledRadius(9.dp))
                    Box(
                        Modifier
                            .height(34.dp)
                            .clip(shape)
                            .background(if (on) HoloColors.Holo.copy(alpha = 0.22f) else HoloColors.SubFill)
                            .border(1.dp, if (on) HoloColors.Holo.copy(alpha = 0.8f) else HoloColors.SubBorder, shape)
                            .clickable(role = Role.RadioButton) { onSelect(f) }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            f.label,
                            style = HoloType.body.copy(
                                fontFamily = HoloFonts.of(f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                color = if (on) HoloColors.TextBright else HoloColors.TextMid,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Colour slot: swatch palette plus hue / saturation / brightness sliders. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorRow(label: String, value: Long, swatches: List<Long>, onChange: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val color = Color(value)
    SubPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .clickable { open = !open }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(label, style = HoloType.body.copy(fontSize = 14.sp, color = HoloColors.TextBright), modifier = Modifier.weight(1f))
                Text("#%06X".format(value and 0xFFFFFFL), style = HoloType.label.copy(color = HoloColors.TextMid))
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(color)
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                )
            }
            if (open) {
                Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        swatches.forEach { sw ->
                            val on = (sw and 0xFFFFFFFFL) == (value and 0xFFFFFFFFL)
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(sw))
                                    .border(if (on) 2.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = 0.25f), CircleShape)
                                    .clickable(role = Role.RadioButton) { onChange(sw) }
                            )
                        }
                    }
                    val hsv = remember(value) {
                        FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }
                    }
                    fun emit(h: Float, sat: Float, v: Float) {
                        onChange(Color.hsv(h.coerceIn(0f, 359.9f), sat.coerceIn(0f, 1f), v.coerceIn(0f, 1f)).toArgb().toLong() and 0xFFFFFFFFL)
                    }
                    MiniSlider("HUE", hsv[0], 0f..359.9f) { emit(it, hsv[1], hsv[2]) }
                    MiniSlider("SAT", hsv[1], 0f..1f) { emit(hsv[0], it, hsv[2]) }
                    MiniSlider("BRT", hsv[2], 0f..1f) { emit(hsv[0], hsv[1], it) }
                }
            }
        }
    }
}

@Composable
private fun MiniSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val holo = HoloColors.Holo
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HoloType.label, modifier = Modifier.width(34.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).height(32.dp),
            colors = SliderDefaults.colors(thumbColor = holo, activeTrackColor = holo, inactiveTrackColor = HoloColors.Track),
        )
    }
}

@Composable
private fun CallsignRow(value: String, onChange: (String) -> Unit) {
    SubPanel(Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Row(
            Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Callsign", style = HoloType.body.copy(fontSize = 14.sp, color = HoloColors.TextBright))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = HoloType.title.copy(fontSize = 14.sp),
                cursorBrush = SolidColor(HoloColors.Holo),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActionRow(label: String, action: String, done: Boolean, onClick: () -> Unit) {
    SubPanel(Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
        Row(
            Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = HoloType.body.copy(fontSize = 14.sp, color = HoloColors.TextBright), modifier = Modifier.weight(1f))
            PillButton(action, color = if (done) HoloColors.Green else HoloColors.Holo, onClick = onClick)
        }
    }
}
