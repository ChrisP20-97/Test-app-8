package dev.holo.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.holo.launcher.BuildConfig
import dev.holo.launcher.data.HoloSettings
import dev.holo.launcher.data.SettingsStore
import dev.holo.launcher.ui.LauncherModel
import dev.holo.launcher.ui.components.CircleButton
import dev.holo.launcher.ui.components.HeaderPill
import dev.holo.launcher.ui.components.HoloCard
import dev.holo.launcher.ui.components.PillButton
import dev.holo.launcher.ui.components.SubPanel
import dev.holo.launcher.ui.components.TickLabel
import dev.holo.launcher.ui.theme.HoloColors
import dev.holo.launcher.ui.theme.HoloIcons
import dev.holo.launcher.ui.theme.HoloType
import dev.holo.launcher.ui.theme.LocalHolo

@Composable
fun SettingsScreen(store: SettingsStore, s: HoloSettings, m: LauncherModel, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD903060A))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp)
    ) {
        HoloCard(Modifier.widthIn(max = 640.dp).fillMaxSize().align(Alignment.Center)) {
            HeaderPill("LAUNCHER CONFIG", HoloIcons.Settings) {
                CircleButton(HoloIcons.Close, "Close settings", onClick = onClose)
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Section("BACKDROP")
                ToggleRow("Live camera backdrop", "See-through background from the rear camera", s.cameraBackdrop) { v ->
                    store.update { it.copy(cameraBackdrop = v) }
                    if (v && !m.perms.camera) m.requestCamera()
                }
                SliderRow("Backdrop blur", s.backdropBlur) { v -> store.update { it.copy(backdropBlur = v) } }
                SliderRow("Colour grade", s.gradeStrength) { v -> store.update { it.copy(gradeStrength = v) } }

                Section("MOTION")
                ToggleRow("3D tilt", "Panels lean slightly as the phone moves", s.tilt) { v -> store.update { it.copy(tilt = v) } }
                SliderRow("Tilt amount", s.tiltStrength) { v -> store.update { it.copy(tiltStrength = v) } }
                ToggleRow("Invert tilt", null, s.invertTilt) { v -> store.update { it.copy(invertTilt = v) } }

                Section("LOOK")
                SliderRow("Panel opacity", (s.glass - 0.5f) / 0.5f) { v -> store.update { it.copy(glass = 0.5f + v * 0.5f) } }
                SliderRow("Effects", s.fx) { v -> store.update { it.copy(fx = v) } }
                TintRow(s.holoTint) { c -> store.update { it.copy(holoTint = c.toArgb().toLong() and 0xFFFFFFFFL) } }
                ToggleRow("Holo app icons", "Monochrome, tinted icons", s.monoIcons) { v -> store.update { it.copy(monoIcons = v) } }

                Section("POWER")
                ToggleRow("Low power mode", "Stops the camera, motion and animations", s.lowPower) { v -> store.update { it.copy(lowPower = v) } }

                Section("IDENTITY")
                CallsignRow(s.operatorName) { v -> store.update { it.copy(operatorName = v.take(24)) } }

                Section("SYSTEM LINKS")
                ActionRow("Home app", if (m.perms.defaultHome) "ACTIVE" else "SET", m.perms.defaultHome, m.requestHome)
                ActionRow("Camera", if (m.perms.camera) "GRANTED" else "GRANT", m.perms.camera, m.requestCamera)
                ActionRow("Notification access", if (m.perms.notifications) "GRANTED" else "GRANT", m.perms.notifications, m.openNotificationAccess)
                ActionRow("Step counter", if (m.perms.activity) "GRANTED" else "GRANT", m.perms.activity, m.requestActivity)
                ActionRow("Setup card", "SHOW", false) { store.update { it.copy(setupDismissed = false) } }

                Text(
                    "HOLO LAUNCHER ${BuildConfig.VERSION_NAME}",
                    style = HoloType.label,
                    modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Box(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)) { TickLabel(title) }
}

@Composable
private fun ToggleRow(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val holo = LocalHolo.current.holo
    SubPanel(Modifier.fillMaxWidth().heightIn(min = 54.dp), onClick = { onChange(!checked) }) {
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
                    uncheckedTrackColor = Color(0x33607080),
                    uncheckedBorderColor = Color(0x667B8A9B),
                ),
            )
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, onChange: (Float) -> Unit) {
    val holo = LocalHolo.current.holo
    SubPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp)) {
            Text(label, style = HoloType.body.copy(fontSize = 14.sp, color = HoloColors.TextBright))
            Slider(
                value = value.coerceIn(0f, 1f),
                onValueChange = onChange,
                colors = SliderDefaults.colors(
                    thumbColor = holo,
                    activeTrackColor = holo,
                    inactiveTrackColor = HoloColors.Track,
                ),
            )
        }
    }
}

@Composable
private fun TintRow(current: Long, onPick: (Color) -> Unit) {
    SubPanel(Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
        Row(
            Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Hologram tint",
                style = HoloType.body.copy(fontSize = 14.sp, color = HoloColors.TextBright),
                modifier = Modifier.weight(1f),
            )
            HoloColors.Tints.forEach { c ->
                val selected = (c.toArgb().toLong() and 0xFFFFFFFFL) == (current and 0xFFFFFFFFL)
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color(0x40FFFFFF), CircleShape)
                        .clickable(role = Role.RadioButton) { onPick(c) }
                )
            }
        }
    }
}

@Composable
private fun CallsignRow(value: String, onChange: (String) -> Unit) {
    SubPanel(Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
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
                cursorBrush = SolidColor(LocalHolo.current.holo),
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
            PillButton(action, color = if (done) HoloColors.Green else LocalHolo.current.holo, onClick = onClick)
        }
    }
}
