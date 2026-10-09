package dev.holo.launcher.keyboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.holo.launcher.data.HoloSettings
import dev.holo.launcher.data.KeyDepthProfile
import dev.holo.launcher.data.SettingsStore
import dev.holo.launcher.ui.components.holoShape
import dev.holo.launcher.ui.components.scaledRadius
import dev.holo.launcher.ui.fx.rememberTilt
import dev.holo.launcher.ui.theme.HoloColors
import dev.holo.launcher.ui.theme.HoloFonts
import dev.holo.launcher.ui.theme.HoloIcons
import dev.holo.launcher.ui.theme.HoloMetrics
import dev.holo.launcher.ui.theme.HoloTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private val StripHeight = 38.dp

/** A laid-out key: where it rests on the deck and how high it floats. */
private class KeyGeom(
    val index: Int,
    val spec: KeySpec,
    val rect: Rect,
    val depth: Float,
    val row: Int,
    val rowFrac: Float,
    val colFrac: Float,
)

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

    val motionOn = settings.kbTilt && !settings.lowPower && state.shown
    val tilt = rememberTilt(
        enabled = motionOn,
        maxDeg = settings.kbMaxTilt,
        sensitivity = settings.tiltSensitivity,
        returnSeconds = settings.tiltReturn,
        invert = settings.invertTilt,
    )

    // Keys rise out of the deck each time the keyboard opens on a new field.
    val boot = remember { Animatable(1f) }
    LaunchedEffect(state.bootTick) {
        if (settings.kbBootAnim && !settings.lowPower) {
            boot.snapTo(0f)
            boot.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        } else {
            boot.snapTo(1f)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val t = tilt.value
                rotationY = t.x * 0.6f
                rotationX = -t.y * 0.6f
                cameraDistance = 40f * density
            }
            .drawBehind { drawDeck(settings, tilt.value, settings.kbMaxTilt) }
    ) {
        Strip(state, actions)
        KeyField(state, settings, actions, tilt, boot)
        Spacer(Modifier.height(settings.kbBottomPad.dp))
    }
}

// ---------------------------------------------------------------- deck + strip

private fun DrawScope.drawDeck(s: HoloSettings, t: Offset, maxTilt: Float) {
    val a = s.kbDeck
    if (a > 0.01f) {
        drawRect(
            Brush.verticalGradient(
                0f to HoloColors.PanelTop.copy(alpha = a),
                0.4f to HoloColors.PanelMid.copy(alpha = a),
                1f to HoloColors.PanelBottom.copy(alpha = a),
            )
        )
    }
    val n = (t.x / maxTilt.coerceAtLeast(1f)).coerceIn(-1f, 1f)
    // Top rim with a highlight that slides with the tilt.
    val cx = size.width * (0.5f + n * 0.3f)
    drawLine(HoloColors.Border.copy(alpha = 0.18f * HoloMetrics.borderBrightness), Offset(0f, 0.5f), Offset(size.width, 0.5f), 1.dp.toPx())
    drawLine(
        Brush.horizontalGradient(
            listOf(Color.Transparent, HoloColors.Border.copy(alpha = (0.75f * HoloMetrics.borderBrightness).coerceIn(0f, 1f)), Color.Transparent),
            startX = cx - size.width * 0.3f, endX = cx + size.width * 0.3f,
        ),
        Offset(cx - size.width * 0.3f, 0.75f), Offset(cx + size.width * 0.3f, 0.75f), 1.5.dp.toPx(),
    )
    // Faint dot grid on the deck, like the hologram bays on the home screen.
    val sp = 14.dp.toPx()
    val r = 0.7.dp.toPx()
    val dot = HoloColors.Border.copy(alpha = 0.08f)
    var y = StripHeight.toPx() + sp / 2f
    while (y < size.height) {
        var x = sp / 2f
        while (x < size.width) {
            drawCircle(dot, r, Offset(x, y))
            x += sp
        }
        y += sp
    }
}

@Composable
private fun Strip(state: KbState, actions: KbActions) {
    val label = TextStyle(
        fontFamily = HoloFonts.display, fontWeight = FontWeight.Bold, fontSize = 10.sp,
        letterSpacing = 0.18.em, color = HoloColors.TextDim,
    )
    Row(
        Modifier.fillMaxWidth().height(StripHeight).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.width(2.dp).height(10.dp).drawBehind { drawRect(HoloColors.Holo) })
        Text("HOLO//INPUT", style = label.copy(color = HoloColors.Text))
        val modeName = when (state.mode) {
            KbMode.ALPHA -> if (state.caps) "CAPS" else "ALPHA"
            KbMode.SYMBOLS, KbMode.SYMBOLS2 -> "SYM"
            KbMode.NUMBER -> "NUM"
            KbMode.PHONE -> "DIAL"
        }
        Text("· $modeName · ${state.fieldLabel}", style = label, maxLines = 1, modifier = Modifier.weight(1f))
        StripButton({ glyphChevron(it, left = true) }) { actions.moveCursor(-1) }
        StripButton({ glyphChevron(it, left = false) }) { actions.moveCursor(1) }
        StripButton({ glyphClipboard(it) }) { actions.paste() }
        Box(
            Modifier.size(30.dp).clip(CircleShape).clickable(role = Role.Button) { actions.openSettings() },
            contentAlignment = Alignment.Center,
        ) { Icon(HoloIcons.Settings, "Keyboard settings", tint = HoloColors.TextMid, modifier = Modifier.size(15.dp)) }
        StripButton({ glyphHide(it) }) { actions.hide() }
    }
}

@Composable
private fun StripButton(glyph: DrawScope.(Color) -> Unit, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) { glyph(HoloColors.TextMid) }
    }
}

// ---------------------------------------------------------------- key field

@Composable
private fun KeyField(
    state: KbState,
    s: HoloSettings,
    actions: KbActions,
    tilt: androidx.compose.runtime.State<Offset>,
    boot: Animatable<Float, *>,
) {
    val rows = remember(state.mode, state.kind, s.kbNumberRow) { KeyLayouts.rows(state.mode, state.kind, s.kbNumberRow) }
    val density = LocalDensity.current
    val layoutDir = LocalLayoutDirection.current
    val keyH = s.kbKeyHeight.dp
    val gap = s.kbKeyGap.dp
    val fieldH = keyH * rows.size + gap * (rows.size + 1)
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 96)

    BoxWithConstraints(Modifier.fillMaxWidth().height(fieldH)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val geoms = remember(rows, widthPx, keyH, gap, s.kbDepthProfile, density) {
            layoutKeys(rows, widthPx, density, keyH, gap, s.kbDepthProfile)
        }
        val press = remember(geoms) { List(geoms.size) { Animatable(0f) } }
        val held = remember(geoms) { BooleanArray(geoms.size) }
        var popup by remember(geoms) { mutableIntStateOf(-1) }
        // Far keys first so nearer keys overlap them correctly.
        val drawOrder = remember(geoms) { geoms.sortedBy { it.depth } }

        val radius = scaledRadius(8.dp)
        val cut = HoloMetrics.cut
        val upper = (state.shift || state.caps) && state.mode == KbMode.ALPHA
        val scale = s.kbLabelScale
        val fontD = HoloFonts.display
        val textColor = HoloColors.Text
        val labels: List<TextLayoutResult?> = remember(geoms, upper, scale, fontD, textColor, state.enterLabel) {
            val letter = TextStyle(fontFamily = fontD, fontWeight = FontWeight.SemiBold, fontSize = 21.sp * scale)
            val fn = TextStyle(fontFamily = fontD, fontWeight = FontWeight.Bold, fontSize = 12.5.sp * scale, letterSpacing = 0.1.em)
            geoms.map { g ->
                when (val a = g.spec.action) {
                    is KeyAction.Text -> measurer.measure(if (upper) a.text.uppercase() else a.text, letter)
                    is KeyAction.Mode -> measurer.measure(g.spec.label, fn)
                    KeyAction.Enter -> state.enterLabel?.let { measurer.measure(it, fn) }
                    else -> null
                }
            }
        }
        val altLabels: List<TextLayoutResult?> = remember(geoms, scale, fontD) {
            val st = TextStyle(fontFamily = fontD, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp * scale)
            geoms.map { g -> g.spec.alt?.let { measurer.measure(it, st) } }
        }
        val outlines = remember(geoms, radius, cut, density) {
            val shape = holoShape(radius)
            HashMap<Size, Outline>().also { m ->
                geoms.forEach { g -> m.getOrPut(g.rect.size) { shape.createOutline(g.rect.size, layoutDir, density) } }
            }
        }

        val maxT = s.kbMaxTilt.coerceAtLeast(1f)
        val depthScale = s.kbDepth
        val thickness = s.kbThickness
        val popupOn = s.kbPopup && state.kind != FieldKind.PASSWORD
        val pressFx = s.kbPressFx

        fun faceOffset(g: KeyGeom, p: Float, b: Float, t: Offset): Offset {
            val d = g.depth * (1f - 0.85f * p) * b
            val k = depthScale * 0.9f * density.density
            val bias = thickness * 5f * density.density * d
            return Offset(t.x * k * d, -t.y * k * d - bias)
        }

        fun bootOf(g: KeyGeom, b: Float): Float {
            val delayFrac = g.rowFrac * 0.35f + g.colFrac * 0.15f
            return ((b - delayFrac) / 0.5f).coerceIn(0f, 1f)
        }

        fun hit(pos: Offset): Int? {
            val t = tilt.value
            val half = with(density) { gap.toPx() } / 2f
            var best: Int? = null
            var bestDist = Float.MAX_VALUE
            for (g in geoms) {
                val r = g.rect.translate(faceOffset(g, 0f, 1f, t)).inflate(half)
                if (r.contains(pos)) return g.index
                val dx = max(0f, max(r.left - pos.x, pos.x - r.right))
                val dy = max(0f, max(r.top - pos.y, pos.y - r.bottom))
                val dist = hypot(dx, dy)
                if (dist < bestDist) {
                    bestDist = dist
                    best = g.index
                }
            }
            return if (bestDist < half * 4f) best else null
        }

        val touches = remember(geoms) { HashMap<PointerId, Touch>() }

        fun pressKey(i: Int) {
            held[i] = true
            scope.launch { press[i].snapTo(1f) }
        }

        fun releaseKey(i: Int) {
            held[i] = false
            scope.launch { press[i].animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
        }

        fun feedbackFor(spec: KeySpec) = actions.feedback(
            when (spec.action) {
                KeyAction.Backspace -> FeedbackKind.DELETE
                KeyAction.Space -> FeedbackKind.SPACE
                KeyAction.Enter -> FeedbackKind.ENTER
                else -> FeedbackKind.KEY
            }
        )

        fun armLongPress(touch: Touch, spec: KeySpec, scope: CoroutineScope) {
            touch.job?.cancel()
            touch.job = when (spec.action) {
                is KeyAction.Text -> spec.alt?.let { alt ->
                    scope.launch {
                        delay(380)
                        touch.longFired = true
                        actions.text(alt)
                        actions.feedback(FeedbackKind.KEY)
                    }
                }
                KeyAction.Globe -> scope.launch {
                    delay(450)
                    touch.longFired = true
                    actions.picker()
                }
                else -> null
            }
        }

        /** Commit whatever an unfinished touch was going to type. */
        fun finish(touch: Touch) {
            if (touch.done || touch.longFired) return
            touch.done = true
            val spec = geoms[touch.key].spec
            when (val a = spec.action) {
                is KeyAction.Text -> actions.text(a.text)
                KeyAction.Space -> if (!touch.dragging) actions.space()
                KeyAction.Enter -> actions.enter()
                KeyAction.Globe -> actions.globe()
                else -> Unit
            }
        }

        val stepPx = with(density) { 9.dp.toPx() }
        val dragStartPx = with(density) { 14.dp.toPx() }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(geoms) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (c in event.changes) {
                                when {
                                    c.changedToDownIgnoreConsumed() -> {
                                        val i = hit(c.position) ?: continue
                                        // Rollover: a new finger lands, so earlier text keys commit now.
                                        touches.values.forEach { o ->
                                            if (!o.done && geoms[o.key].spec.isText) {
                                                o.job?.cancel()
                                                finish(o)
                                            }
                                        }
                                        val touch = Touch(i, c.position.x)
                                        touches[c.id] = touch
                                        val spec = geoms[i].spec
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
                                        val spec = geoms[touch.key].spec
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
                                            // Slide to a neighbouring letter before lifting.
                                            val j = hit(c.position)
                                            if (j != null && j != touch.key && geoms[j].spec.isText) {
                                                releaseKey(touch.key)
                                                touch.key = j
                                                pressKey(j)
                                                popup = j
                                                armLongPress(touch, geoms[j].spec, scope)
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
            val t = tilt.value
            val n = Offset((t.x / maxT).coerceIn(-1f, 1f), (t.y / maxT).coerceIn(-1f, 1f))
            val b = boot.value
            for (g in drawOrder) {
                val ba = bootOf(g, b)
                if (ba <= 0.001f) continue
                val p = press[g.index].value
                drawKey(
                    g = g,
                    outline = outlines[g.rect.size] ?: continue,
                    face = faceOffset(g, p, ba, t),
                    light = n,
                    press = p,
                    held = held[g.index],
                    alpha = ba,
                    pressFx = pressFx,
                    label = labels[g.index],
                    alt = if (state.mode == KbMode.ALPHA) altLabels[g.index] else null,
                    upper = upper,
                    caps = state.caps,
                    enterText = state.enterLabel != null,
                )
            }
            val pi = popup
            if (popupOn && pi >= 0 && pi < geoms.size) {
                val g = geoms[pi]
                val spec = g.spec
                if (spec.action is KeyAction.Text) {
                    drawPopup(
                        g = g,
                        face = faceOffset(g, 0f, 1f, t) * 1.6f,
                        text = if (upper) spec.label.uppercase() else spec.label,
                        measurer = measurer,
                        font = fontD,
                        scale = scale,
                        radius = radius,
                        cut = cut,
                        minTop = -StripHeight.toPx() + 2.dp.toPx(),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- layout

private fun layoutKeys(
    rows: List<KeyRow>,
    width: Float,
    density: Density,
    keyH: Dp,
    gap: Dp,
    profile: KeyDepthProfile,
): List<KeyGeom> {
    val g = with(density) { gap.toPx() }
    val h = with(density) { keyH.toPx() }
    val side = g
    val unit = (width - side * 2f) / 10f
    val out = ArrayList<KeyGeom>()
    val nRows = rows.size
    rows.forEachIndexed { r, row ->
        val units = row.lead + row.trail + row.keys.sumOf { it.width.toDouble() }.toFloat()
        // Centre rows that are narrower than 10 units.
        var x = side + (10f - units) / 2f * unit + row.lead * unit
        val top = g + r * (h + g)
        row.keys.forEach { k ->
            val w = k.width * unit
            val rect = Rect(x + g / 2f, top, x + w - g / 2f, top + h)
            val idx = out.size
            val rowFrac = if (nRows > 1) r / (nRows - 1f) else 0f
            val colFrac = (rect.center.x / width).coerceIn(0f, 1f)
            out += KeyGeom(idx, k, rect, depthFor(profile, k, idx, rowFrac, colFrac), r, rowFrac, colFrac)
            x += w
        }
    }
    return out
}

private fun depthFor(profile: KeyDepthProfile, k: KeySpec, idx: Int, rowFrac: Float, colFrac: Float): Float = when (profile) {
    KeyDepthProfile.FLAT -> 0.55f
    // Like a real keyboard's rake: rows step up toward you.
    KeyDepthProfile.RAKE -> 0.25f + 0.75f * rowFrac
    // Centre keys float highest.
    KeyDepthProfile.DOME -> {
        val dx = (colFrac - 0.5f) * 2f
        val dy = (rowFrac - 0.5f) * 2f
        1f - 0.75f * sqrt((dx * dx * 0.7f + dy * dy * 0.3f)).coerceIn(0f, 1f)
    }
    // Control keys stand proud of the letters.
    KeyDepthProfile.ISLANDS -> when {
        k.action == KeyAction.Space -> 0.75f
        k.isFunction -> 1f
        else -> 0.38f
    }
    KeyDepthProfile.SCATTER -> {
        val v = sin(idx * 12.9898f + rowFrac * 78.233f) * 43758.547f
        0.25f + 0.75f * (v - floor(v))
    }
}

// ---------------------------------------------------------------- drawing

private fun DrawScope.drawKey(
    g: KeyGeom,
    outline: Outline,
    face: Offset,
    light: Offset,
    press: Float,
    held: Boolean,
    alpha: Float,
    pressFx: Boolean,
    label: TextLayoutResult?,
    alt: TextLayoutResult?,
    upper: Boolean,
    caps: Boolean,
    enterText: Boolean,
) {
    val r = g.rect
    val spec = g.spec
    val isEnter = spec.action == KeyAction.Enter
    val fn = spec.isFunction
    val shadowAmt = HoloMetrics.shadow
    val bw = (HoloMetrics.borderWidth * 0.8f).dp.toPx()
    val br = HoloMetrics.borderBrightness

    // Soft contact shadow on the deck, pushed away from the light.
    if (shadowAmt > 0.01f) {
        val lift = (-face.y).coerceAtLeast(0f) + 2.dp.toPx()
        translate(r.left - face.x * 0.45f, r.top + lift * 0.6f) {
            drawOutline(outline, Color.Black.copy(alpha = 0.22f * shadowAmt * alpha))
        }
        translate(r.left - face.x * 0.7f - 1.dp.toPx(), r.top + lift) {
            drawOutline(outline, Color.Black.copy(alpha = 0.12f * shadowAmt * alpha))
        }
    }

    // Extruded side walls: stack the outline from the deck up to the face.
    val len = hypot(face.x, face.y)
    if (len > 0.75f) {
        val steps = (len / 1.2f).toInt().coerceIn(2, 10)
        val side = lerp(HoloColors.PanelBottom, Color.Black, 0.25f)
        val rim = lerp(side, HoloColors.Border, 0.12f)
        for (i in 0 until steps) {
            val f = i / steps.toFloat()
            translate(r.left + face.x * f, r.top + face.y * f) {
                drawOutline(outline, (if (i == 0) rim else side).copy(alpha = 0.92f * alpha))
            }
        }
    }

    translate(r.left + face.x, r.top + face.y) {
        val w = r.width
        val h = r.height
        val g0 = HoloMetrics.glass
        val top = if (fn) lerp(HoloColors.PanelTop, HoloColors.HeaderFill, 0.35f) else lerp(HoloColors.PanelTop, Color.White, 0.05f)
        val bottom = if (fn) HoloColors.HeaderFill else HoloColors.PanelMid
        drawOutline(
            outline,
            Brush.verticalGradient(0f to top.copy(alpha = g0), 1f to bottom.copy(alpha = g0), startY = 0f, endY = h),
            alpha = alpha,
        )
        if (isEnter) drawOutline(outline, HoloColors.Holo.copy(alpha = 0.2f), alpha = alpha)
        if (press > 0.01f) drawOutline(outline, HoloColors.Holo.copy(alpha = 0.32f * press), alpha = alpha)

        // Specular sheen that follows the tilt.
        drawOutline(
            outline,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.06f + 0.07f * abs(light.x)),
                0.45f to Color.Transparent,
                1f to Color.White.copy(alpha = 0.015f + 0.03f * abs(light.y)),
                start = Offset(w * (-0.2f + light.x * 0.6f), h * (-0.2f + light.y * 0.4f)),
                end = Offset(w * (0.8f + light.x * 0.6f), h * (1.1f + light.y * 0.4f)),
            ),
            alpha = alpha,
        )
        // Rim light: brighter on the edge facing the light.
        if (bw > 0.05f) {
            val bc = if (isEnter) HoloColors.Holo else HoloColors.Border
            drawOutline(
                outline,
                Brush.linearGradient(
                    0f to bc.copy(alpha = (0.8f * br + 0.3f * press).coerceIn(0f, 1f)),
                    0.5f to bc.copy(alpha = (0.28f * br).coerceIn(0f, 1f)),
                    1f to bc.copy(alpha = (0.5f * br).coerceIn(0f, 1f)),
                    start = Offset(w * (0.5f - light.x * 0.8f), 0f),
                    end = Offset(w * (0.5f + light.x * 0.8f), h),
                ),
                alpha = alpha,
                style = Stroke(bw),
            )
        }
        // Press glow and a shockwave ring as the key springs back.
        if (pressFx && press > 0.01f) {
            drawIntoCanvas { c ->
                val paint = Paint()
                val fp = paint.asFrameworkPaint()
                fp.isAntiAlias = true
                fp.style = android.graphics.Paint.Style.STROKE
                fp.strokeWidth = 1.5.dp.toPx()
                fp.color = HoloColors.Holo.copy(alpha = 0.9f * press).toArgb()
                fp.setShadowLayer(10.dp.toPx() * press, 0f, 0f, HoloColors.Holo.copy(alpha = 0.8f * press).toArgb())
                c.drawRoundRect(0f, 0f, w, h, 8.dp.toPx(), 8.dp.toPx(), paint)
            }
            if (!held) {
                val grow = (1f - press) * 10.dp.toPx()
                drawRoundRect(
                    HoloColors.Holo.copy(alpha = 0.45f * press * alpha),
                    topLeft = Offset(-grow, -grow),
                    size = Size(w + grow * 2f, h + grow * 2f),
                    cornerRadius = CornerRadius(8.dp.toPx() + grow),
                    style = Stroke(1.dp.toPx()),
                )
            }
        }

        val c = Offset(w / 2f, h / 2f)
        val ink = (if (fn) HoloColors.TextMid else HoloColors.Text).let { lerp(it, Color.White, 0.4f * press) }
        val iconSize = 15.dp.toPx()
        when (spec.action) {
            KeyAction.Shift -> glyphShift(c, iconSize, ink.copy(alpha = alpha), filled = upper, caps = caps)
            KeyAction.Backspace -> glyphBackspace(c, iconSize, ink.copy(alpha = alpha))
            KeyAction.Globe -> glyphGlobe(c, iconSize * 0.95f, ink.copy(alpha = alpha))
            KeyAction.Space -> {
                val sw = w * 0.28f
                val y = c.y + 4.dp.toPx()
                val col = HoloColors.TextDim.copy(alpha = 0.8f * alpha)
                drawLine(col, Offset(c.x - sw / 2f, y), Offset(c.x + sw / 2f, y), 1.5.dp.toPx())
                drawLine(col, Offset(c.x - sw / 2f, y), Offset(c.x - sw / 2f, y - 4.dp.toPx()), 1.5.dp.toPx())
                drawLine(col, Offset(c.x + sw / 2f, y), Offset(c.x + sw / 2f, y - 4.dp.toPx()), 1.5.dp.toPx())
            }
            KeyAction.Enter -> if (!enterText) glyphEnter(c, iconSize, HoloColors.TextBright.copy(alpha = alpha))
            else -> Unit
        }
        if (label != null) {
            val col = if (isEnter) HoloColors.TextBright else ink
            drawText(
                label,
                color = col,
                topLeft = Offset(c.x - label.size.width / 2f, c.y - label.size.height / 2f),
                alpha = alpha,
            )
        }
        if (alt != null) {
            drawText(
                alt,
                color = HoloColors.TextDim,
                topLeft = Offset(w - alt.size.width - 4.dp.toPx(), 2.dp.toPx()),
                alpha = 0.75f * alpha,
            )
        }
    }
}

private fun DrawScope.drawPopup(
    g: KeyGeom,
    face: Offset,
    text: String,
    measurer: androidx.compose.ui.text.TextMeasurer,
    font: androidx.compose.ui.text.font.FontFamily,
    scale: Float,
    radius: Dp,
    cut: Boolean,
    minTop: Float,
) {
    val r = g.rect
    val w = max(r.width * 1.25f, 46.dp.toPx())
    val h = r.height * 1.3f
    val left = (r.center.x - w / 2f + face.x).coerceIn(0f, size.width - w)
    val top = max(r.top + face.y - h - 6.dp.toPx(), minTop)
    val sz = Size(w, h)
    val outline = holoShape(radius).createOutline(sz, layoutDirection, this)
    // Glow and shadow, then the plate, then the glyph.
    drawIntoCanvas { c ->
        val paint = Paint()
        val fp = paint.asFrameworkPaint()
        fp.isAntiAlias = true
        fp.color = HoloColors.PanelMid.toArgb()
        fp.setShadowLayer(16.dp.toPx(), 0f, 6.dp.toPx(), Color.Black.copy(alpha = 0.55f).toArgb())
        val rr = if (cut) 0f else radius.toPx()
        c.drawRoundRect(left, top, left + w, top + h, rr, rr, paint)
    }
    translate(left, top) {
        drawOutline(
            outline,
            Brush.verticalGradient(
                0f to lerp(HoloColors.PanelTop, HoloColors.Holo, 0.18f),
                1f to HoloColors.PanelMid,
                startY = 0f, endY = h,
            ),
        )
        drawOutline(outline, HoloColors.Holo.copy(alpha = 0.85f), style = Stroke(1.5.dp.toPx()))
        val layout = measurer.measure(
            text,
            TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 30.sp * scale),
        )
        drawText(
            layout,
            color = HoloColors.TextBright,
            topLeft = Offset(w / 2f - layout.size.width / 2f, h / 2f - layout.size.height / 2f),
        )
    }
}

// ---------------------------------------------------------------- glyphs

private fun DrawScope.strokeOf(px: Float = 1.6.dp.toPx()) = Stroke(px, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.glyphShift(c: Offset, s: Float, color: Color, filled: Boolean, caps: Boolean) {
    val p = Path().apply {
        moveTo(c.x, c.y - 0.55f * s)
        lineTo(c.x + 0.5f * s, c.y - 0.02f * s)
        lineTo(c.x + 0.22f * s, c.y - 0.02f * s)
        lineTo(c.x + 0.22f * s, c.y + 0.4f * s)
        lineTo(c.x - 0.22f * s, c.y + 0.4f * s)
        lineTo(c.x - 0.22f * s, c.y - 0.02f * s)
        lineTo(c.x - 0.5f * s, c.y - 0.02f * s)
        close()
    }
    if (filled) drawPath(p, color.copy(alpha = color.alpha * 0.9f), style = Fill)
    drawPath(p, color, style = strokeOf())
    if (caps) drawLine(color, Offset(c.x - 0.3f * s, c.y + 0.62f * s), Offset(c.x + 0.3f * s, c.y + 0.62f * s), 1.8.dp.toPx(), StrokeCap.Round)
}

private fun DrawScope.glyphBackspace(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x - 0.65f * s, c.y)
        lineTo(c.x - 0.3f * s, c.y - 0.42f * s)
        lineTo(c.x + 0.62f * s, c.y - 0.42f * s)
        lineTo(c.x + 0.62f * s, c.y + 0.42f * s)
        lineTo(c.x - 0.3f * s, c.y + 0.42f * s)
        close()
    }
    drawPath(p, color, style = strokeOf())
    val x = c.x + 0.16f * s
    val d = 0.16f * s
    drawLine(color, Offset(x - d, c.y - d), Offset(x + d, c.y + d), 1.6.dp.toPx(), StrokeCap.Round)
    drawLine(color, Offset(x + d, c.y - d), Offset(x - d, c.y + d), 1.6.dp.toPx(), StrokeCap.Round)
}

private fun DrawScope.glyphEnter(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x + 0.5f * s, c.y - 0.45f * s)
        lineTo(c.x + 0.5f * s, c.y + 0.12f * s)
        lineTo(c.x - 0.5f * s, c.y + 0.12f * s)
        moveTo(c.x - 0.22f * s, c.y - 0.16f * s)
        lineTo(c.x - 0.5f * s, c.y + 0.12f * s)
        lineTo(c.x - 0.22f * s, c.y + 0.4f * s)
    }
    drawPath(p, color, style = strokeOf(1.8.dp.toPx()))
}

private fun DrawScope.glyphGlobe(c: Offset, s: Float, color: Color) {
    val r = 0.5f * s
    val st = strokeOf(1.4.dp.toPx())
    drawCircle(color, r, c, style = st)
    drawOval(color, Offset(c.x - r * 0.42f, c.y - r), Size(r * 0.84f, r * 2f), style = st)
    drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.4.dp.toPx())
}

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
    drawRoundRect(color, Offset(w * 0.2f, h * 0.18f), Size(w * 0.6f, h * 0.72f), CornerRadius(2.dp.toPx()), style = st)
    drawRoundRect(color, Offset(w * 0.36f, h * 0.08f), Size(w * 0.28f, h * 0.18f), CornerRadius(1.5.dp.toPx()), style = st)
    drawLine(color, Offset(w * 0.34f, h * 0.5f), Offset(w * 0.66f, h * 0.5f), 1.4.dp.toPx(), StrokeCap.Round)
    drawLine(color, Offset(w * 0.34f, h * 0.68f), Offset(w * 0.56f, h * 0.68f), 1.4.dp.toPx(), StrokeCap.Round)
}
