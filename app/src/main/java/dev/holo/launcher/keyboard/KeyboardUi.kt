package dev.holo.launcher.keyboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.holo.launcher.data.HoloSettings
import dev.holo.launcher.data.KbDeckStyle
import dev.holo.launcher.data.LabelCase
import dev.holo.launcher.data.SettingsStore
import dev.holo.launcher.ui.components.holoShape
import dev.holo.launcher.ui.fx.rememberTilt
import dev.holo.launcher.ui.theme.HoloColors
import dev.holo.launcher.ui.theme.HoloFonts
import dev.holo.launcher.ui.theme.HoloIcons
import dev.holo.launcher.ui.theme.HoloMetrics
import dev.holo.launcher.ui.theme.HoloTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

private val StripHeight = 38.dp

/** Cross-component pokes, e.g. "replay the startup animation" from launcher settings. */
object KbSignals {
    val replay = MutableStateFlow(0)
}

/** Live state for one finger on the keyboard. */
private class Touch(var key: Int, val downX: Float) {
    var job: Job? = null
    var longFired = false
    var done = false
    var dragging = false
    var anchorX = downX
}

@Composable
fun HoloKeyboard(state: KbState, store: SettingsStore, actions: KbActions) {
    val settings by store.state.collectAsState()
    LaunchedEffect(settings) { HoloTheme.apply(settings) }

    val tilt = rememberTilt(
        enabled = settings.kbTilt && !settings.lowPower && state.shown,
        maxDeg = settings.kbMaxTilt,
        sensitivity = settings.tiltSensitivity,
        returnSeconds = settings.tiltReturn,
        invert = settings.invertTilt,
    )

    // Startup sequence: replays each time the keyboard opens on a new field.
    val replay by KbSignals.replay.collectAsState()
    val boot = remember { Animatable(1f) }
    LaunchedEffect(state.bootTick, replay) {
        if (!settings.lowPower) {
            boot.snapTo(0f)
            boot.animateTo(1f, tween((900 / settings.kbBootSpeed.coerceAtLeast(0.25f)).toInt(), easing = LinearEasing))
        } else {
            boot.snapTo(1f)
        }
    }

    val pal = kbPalette(settings)
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawBackdrop(settings, pal) }
    ) {
        Strip(state, actions, settings, pal, boot)
        KeyField(state, settings, pal, actions, tilt, boot)
        Spacer(Modifier.height(settings.kbBottomPad.dp))
    }
}

private fun DrawScope.drawBackdrop(s: HoloSettings, pal: KbPalette) {
    if (s.kbBackdrop > 0.01f) {
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.35f to Color.Black.copy(alpha = s.kbBackdrop * 0.6f),
                1f to Color.Black.copy(alpha = s.kbBackdrop),
            )
        )
    }
    if (s.kbDeckStyle == KbDeckStyle.GLASS && s.kbDeck > 0.01f) {
        drawRect(
            Brush.verticalGradient(
                0f to HoloColors.PanelTop.copy(alpha = s.kbDeck),
                0.4f to HoloColors.PanelMid.copy(alpha = s.kbDeck),
                1f to HoloColors.PanelBottom.copy(alpha = s.kbDeck),
            )
        )
        drawLine(pal.border.copy(alpha = 0.3f * HoloMetrics.borderBrightness), Offset(0f, 0.5f), Offset(size.width, 0.5f), 1.dp.toPx())
    }
}

// ---------------------------------------------------------------- strip

@Composable
private fun Strip(state: KbState, actions: KbActions, s: HoloSettings, pal: KbPalette, boot: Animatable<Float, AnimationVector1D>) {
    val font = if (s.kbFollowFont) HoloFonts.display else HoloFonts.of(s.kbFont)
    val label = TextStyle(
        fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 10.sp,
        letterSpacing = 0.18.em, color = pal.labelDim,
    )
    Row(
        Modifier.fillMaxWidth().height(StripHeight).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (s.kbHatch) {
            Canvas(Modifier.width(30.dp).height(10.dp)) {
                val b = boot.value
                val n = 4
                val step = size.width / n
                for (i in 0 until n) {
                    val a = ((b * 1.6f - i * 0.12f).coerceIn(0f, 1f)) * (0.25f + 0.18f * i)
                    val x = i * step
                    val p = Path().apply {
                        moveTo(x, 0f); lineTo(x + step * 0.45f, size.height / 2f); lineTo(x, size.height)
                    }
                    drawPath(p, pal.border.copy(alpha = a), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        } else {
            Box(Modifier.width(2.dp).height(10.dp).drawBehind { drawRect(pal.accent) })
        }
        Text("HOLO//INPUT", style = label.copy(color = pal.label))
        val modeName = when (state.mode) {
            KbMode.ALPHA -> if (state.caps) "CAPS" else "ALPHA"
            KbMode.SYMBOLS, KbMode.SYMBOLS2 -> "SYM"
            KbMode.NUMBER -> "NUM"
            KbMode.PHONE -> "DIAL"
        }
        Text("· $modeName · ${state.fieldLabel}", style = label, maxLines = 1)
        // Header rule, like the in-game panel titles.
        Box(
            Modifier.weight(1f).height(1.dp).drawBehind {
                drawRect(
                    Brush.horizontalGradient(listOf(pal.border.copy(alpha = 0.35f), Color.Transparent)),
                    size = Size(size.width * boot.value, size.height),
                )
            }
        )
        StripButton(pal, { glyphChevron(it, left = true) }) { actions.moveCursor(-1) }
        StripButton(pal, { glyphChevron(it, left = false) }) { actions.moveCursor(1) }
        StripButton(pal, { glyphClipboard(it) }) { actions.paste() }
        Box(
            Modifier.size(30.dp).clip(CircleShape).clickable(role = Role.Button) { actions.openSettings() },
            contentAlignment = Alignment.Center,
        ) { Icon(HoloIcons.Settings, "Keyboard settings", tint = pal.labelFn, modifier = Modifier.size(15.dp)) }
        StripButton(pal, { glyphHide(it) }) { actions.hide() }
    }
}

@Composable
private fun StripButton(pal: KbPalette, glyph: DrawScope.(Color) -> Unit, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) { glyph(pal.labelFn) }
    }
}

// ---------------------------------------------------------------- key field

@Composable
private fun KeyField(
    state: KbState,
    s: HoloSettings,
    pal: KbPalette,
    actions: KbActions,
    tilt: State<Offset>,
    boot: Animatable<Float, AnimationVector1D>,
) {
    val rows = remember(state.mode, state.kind, s.kbNumberRow) { KeyLayouts.rows(state.mode, state.kind, s.kbNumberRow) }
    val density = LocalDensity.current
    val layoutDir = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 128)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }
        // Only geometry settings rebuild the scene; looks are read per frame.
        val scene = remember(
            rows, widthPx, density.density, s.kbSurface, s.kbCurve, s.kbKeyAngle, s.kbLens, s.kbDepthProfile,
            s.kbDepth, s.kbThickness, s.kbKeyHeight, s.kbKeyGap,
        ) { buildScene(rows, widthPx, density.density, s) }
        val renderer = remember(scene) { KbRenderer(scene) }
        val n = scene.nodes.size
        val press = remember(scene) { List(n) { Animatable(0f) } }
        val pressVals = remember(scene) { FloatArray(n) }
        val held = remember(scene) { BooleanArray(n) }
        var popup by remember(scene) { mutableIntStateOf(-1) }
        val frame = remember { KbFrame() }

        val cut = HoloMetrics.cut
        val shape = remember(s.kbKeyRadius, cut, scene) { holoShape((s.kbKeyRadius * scene.fit).dp) }
        val outlines: Map<Size, Outline> = remember(scene, shape, layoutDir) {
            HashMap<Size, Outline>().also { m ->
                scene.nodes.forEach { k -> m.getOrPut(Size(k.w, k.h)) { shape.createOutline(Size(k.w, k.h), layoutDir, density) } }
            }
        }

        val upper = (state.shift || state.caps) && state.mode == KbMode.ALPHA
        val font = if (s.kbFollowFont) HoloFonts.display else HoloFonts.of(s.kbFont)
        val scale = s.kbLabelScale * scene.fit
        fun cased(t: String): String = when (s.kbLabelCase) {
            LabelCase.AUTO -> if (upper) t.uppercase() else t
            LabelCase.UPPER -> t.uppercase()
            LabelCase.LOWER -> t.lowercase()
        }
        val labels: List<TextLayoutResult?> = remember(scene, upper, scale, font, s.kbLabelCase, state.enterLabel) {
            val letter = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 21.sp * scale)
            val fn = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 12.5.sp * scale, letterSpacing = 0.1.em)
            scene.nodes.map { g ->
                when (val a = g.spec.action) {
                    is KeyAction.Text -> measurer.measure(cased(a.text), letter)
                    is KeyAction.Mode -> measurer.measure(g.spec.label, fn)
                    KeyAction.Enter -> state.enterLabel?.let { measurer.measure(it, fn) }
                    else -> null
                }
            }
        }
        val alts: List<TextLayoutResult?> = remember(scene, scale, font, state.mode) {
            val st = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 8.5.sp * scale)
            scene.nodes.map { g -> if (state.mode == KbMode.ALPHA) g.spec.alt?.let { measurer.measure(it, st) } else null }
        }

        val slop = scene.gap / 2f
        val touches = remember(scene) { HashMap<PointerId, Touch>() }

        fun pressKey(i: Int) {
            held[i] = true
            scope.launch { press[i].snapTo(1f) }
        }

        fun releaseKey(i: Int) {
            held[i] = false
            scope.launch { press[i].animateTo(0f, tween(320, easing = FastOutSlowInEasing)) }
        }

        fun feedbackFor(spec: KeySpec) = actions.feedback(
            when (spec.action) {
                KeyAction.Backspace -> FeedbackKind.DELETE
                KeyAction.Space -> FeedbackKind.SPACE
                KeyAction.Enter -> FeedbackKind.ENTER
                else -> FeedbackKind.KEY
            }
        )

        fun armLongPress(touch: Touch, spec: KeySpec, cs: CoroutineScope) {
            touch.job?.cancel()
            touch.job = when (spec.action) {
                is KeyAction.Text -> spec.alt?.let { alt ->
                    cs.launch {
                        delay(380)
                        touch.longFired = true
                        actions.text(alt)
                        actions.feedback(FeedbackKind.KEY)
                    }
                }
                KeyAction.Globe -> cs.launch {
                    delay(450)
                    touch.longFired = true
                    actions.picker()
                }
                else -> null
            }
        }

        fun finish(touch: Touch) {
            if (touch.done || touch.longFired) return
            touch.done = true
            when (val a = scene.nodes[touch.key].spec.action) {
                is KeyAction.Text -> actions.text(a.text)
                KeyAction.Space -> if (!touch.dragging) actions.space()
                KeyAction.Enter -> actions.enter()
                KeyAction.Globe -> actions.globe()
                else -> Unit
            }
        }

        val stepPx = with(density) { 9.dp.toPx() }
        val dragStartPx = with(density) { 14.dp.toPx() }
        val canvasH = with(density) { scene.height.toDp() }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(canvasH)
                .pointerInput(scene) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (c in event.changes) {
                                when {
                                    c.changedToDownIgnoreConsumed() -> {
                                        val i = renderer.hit(c.position, slop) ?: continue
                                        touches.values.forEach { o ->
                                            if (!o.done && scene.nodes[o.key].spec.isText) {
                                                o.job?.cancel()
                                                finish(o)
                                            }
                                        }
                                        val touch = Touch(i, c.position.x)
                                        touches[c.id] = touch
                                        val spec = scene.nodes[i].spec
                                        pressKey(i)
                                        feedbackFor(spec)
                                        when (val a = spec.action) {
                                            is KeyAction.Text -> popup = i
                                            KeyAction.Backspace -> {
                                                touch.done = true
                                                actions.backspace()
                                                touch.job = scope.launch {
                                                    delay(400)
                                                    while (true) {
                                                        actions.backspace()
                                                        delay(55)
                                                    }
                                                }
                                            }
                                            KeyAction.Shift -> {
                                                touch.done = true
                                                actions.shift()
                                            }
                                            is KeyAction.Mode -> {
                                                touch.done = true
                                                actions.mode(a.target)
                                            }
                                            else -> Unit
                                        }
                                        if (!touch.done) armLongPress(touch, spec, scope)
                                        c.consume()
                                    }
                                    c.changedToUpIgnoreConsumed() -> {
                                        val touch = touches.remove(c.id) ?: continue
                                        touch.job?.cancel()
                                        finish(touch)
                                        releaseKey(touch.key)
                                        if (popup == touch.key) popup = -1
                                        c.consume()
                                    }
                                    c.pressed -> {
                                        val touch = touches[c.id] ?: continue
                                        val spec = scene.nodes[touch.key].spec
                                        if (spec.action == KeyAction.Space && !touch.done) {
                                            val dx = c.position.x - touch.anchorX
                                            if (!touch.dragging && abs(c.position.x - touch.downX) > dragStartPx) {
                                                touch.dragging = true
                                                touch.anchorX = c.position.x
                                            } else if (touch.dragging && abs(dx) >= stepPx) {
                                                val steps = (dx / stepPx).toInt()
                                                actions.moveCursor(steps)
                                                touch.anchorX += steps * stepPx
                                            }
                                        } else if (spec.isText && !touch.done && !touch.longFired) {
                                            val j = renderer.hit(c.position, 0f)
                                            if (j != null && j != touch.key && scene.nodes[j].spec.isText) {
                                                releaseKey(touch.key)
                                                touch.key = j
                                                pressKey(j)
                                                popup = j
                                                armLongPress(touch, scene.nodes[j].spec, scope)
                                            }
                                        }
                                        c.consume()
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            for (i in 0 until n) pressVals[i] = press[i].value
            frame.tilt = tilt.value
            frame.boot = boot.value
            val pi = popup
            frame.popup = if (state.kind == FieldKind.PASSWORD) -1 else pi
            frame.upper = upper
            frame.caps = state.caps
            frame.enterText = state.enterLabel != null
            frame.popupText = if (pi in 0 until n) cased(scene.nodes[pi].spec.label) else ""
            renderer.draw(this, s, pal, frame, pressVals, held, outlines, shape, labels, alts, measurer, font)
        }
    }
}

// ---------------------------------------------------------------- strip glyphs

private fun DrawScope.strokeOf(px: Float) = Stroke(px, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.glyphChevron(color: Color, left: Boolean) {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        if (left) {
            moveTo(w * 0.62f, h * 0.2f); lineTo(w * 0.3f, h * 0.5f); lineTo(w * 0.62f, h * 0.8f)
        } else {
            moveTo(w * 0.38f, h * 0.2f); lineTo(w * 0.7f, h * 0.5f); lineTo(w * 0.38f, h * 0.8f)
        }
    }
    drawPath(p, color, style = strokeOf(1.8.dp.toPx()))
}

private fun DrawScope.glyphHide(color: Color) {
    val w = size.width
    val h = size.height
    val p = Path().apply { moveTo(w * 0.2f, h * 0.36f); lineTo(w * 0.5f, h * 0.66f); lineTo(w * 0.8f, h * 0.36f) }
    drawPath(p, color, style = strokeOf(1.8.dp.toPx()))
}

private fun DrawScope.glyphClipboard(color: Color) {
    val w = size.width
    val h = size.height
    val st = strokeOf(1.5.dp.toPx())
    drawRoundRect(color, Offset(w * 0.2f, h * 0.18f), Size(w * 0.6f, h * 0.72f), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = st)
    drawRoundRect(color, Offset(w * 0.36f, h * 0.08f), Size(w * 0.28f, h * 0.18f), androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()), style = st)
    drawLine(color, Offset(w * 0.34f, h * 0.5f), Offset(w * 0.66f, h * 0.5f), 1.4.dp.toPx(), StrokeCap.Round)
    drawLine(color, Offset(w * 0.34f, h * 0.68f), Offset(w * 0.56f, h * 0.68f), 1.4.dp.toPx(), StrokeCap.Round)
}
