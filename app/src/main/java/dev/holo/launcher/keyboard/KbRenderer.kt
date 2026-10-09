package dev.holo.launcher.keyboard

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.holo.launcher.data.HoloSettings
import dev.holo.launcher.data.KbBoot
import dev.holo.launcher.data.KbDeckStyle
import dev.holo.launcher.data.KeyStyle
import dev.holo.launcher.ui.theme.HoloColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** Resolved keyboard colours (theme or custom). */
class KbPalette(
    val accent: Color,
    val frameAccent: Color,
    val keyTop: Color,
    val keyBottom: Color,
    val fnTop: Color,
    val fnBottom: Color,
    val side: Color,
    val sideRim: Color,
    val border: Color,
    val label: Color,
    val labelFn: Color,
    val labelDim: Color,
    val deck: Color,
)

/** Reads the live theme, so call from composition to get recomposed on theme changes. */
fun kbPalette(s: HoloSettings): KbPalette {
    val accent = if (s.kbCustomColors) Color(s.kbAccent) else HoloColors.Holo
    val key = if (s.kbCustomColors) Color(s.kbKeyColor) else HoloColors.PanelMid
    val label = if (s.kbCustomColors) Color(s.kbLabelColor) else HoloColors.Text
    val border = if (s.kbCustomColors) lerp(accent, Color.White, 0.55f) else HoloColors.Border
    val fn = s.kbFnTint
    val side = lerp(lerp(key, Color.Black, 0.45f), border, 0.06f)
    return KbPalette(
        accent = accent,
        frameAccent = lerp(accent, Color(0xFF52D6C6), 0.55f),
        keyTop = lerp(key, Color.White, 0.1f),
        keyBottom = lerp(key, Color.Black, 0.15f),
        fnTop = lerp(lerp(key, Color.Black, 0.25f * fn), Color.White, 0.04f),
        fnBottom = lerp(key, Color.Black, 0.35f + 0.2f * fn),
        side = side,
        sideRim = lerp(side, border, 0.14f),
        border = border,
        label = label,
        labelFn = lerp(label, HoloColors.TextDim, 0.35f),
        labelDim = HoloColors.TextDim,
        deck = lerp(key, Color.Black, 0.35f),
    )
}

/** Per-frame inputs that are not part of the scene. */
class KbFrame {
    var tilt: Offset = Offset.Zero
    var boot: Float = 1f
    var popup: Int = -1
    var upper: Boolean = false
    var caps: Boolean = false
    var enterText: Boolean = false
    var popupText: String = ""
}

/**
 * Draws the keyboard scene and remembers where every key landed so touches can be mapped back
 * through the same perspective (exact hit testing on angled keys).
 */
class KbRenderer(val scene: Scene) {
    val count = scene.nodes.size
    private val mats = Array(count) { android.graphics.Matrix() }
    private val tmp = android.graphics.Matrix()
    private val inv = android.graphics.Matrix()
    private val order = IntArray(count) { it }
    private val depth = FloatArray(count)
    private val alphas = FloatArray(count)
    private val scx = FloatArray(count)
    private val scy = FloatArray(count)
    private val drawn = BooleanArray(count)
    private val src = FloatArray(8)
    private val dst = FloatArray(8)
    private val pts = FloatArray(2)
    private val xf = Xf()
    private val nPath = android.graphics.Path()
    private val meshPaint = android.graphics.Paint().apply {
        isAntiAlias = false
        style = android.graphics.Paint.Style.FILL
    }
    private val linePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }

    // Light from the upper left, in front of the screen.
    private val lx: Float
    private val ly: Float
    private val lz: Float
    private val hx: Float
    private val hy: Float
    private val hz: Float

    init {
        val l = sqrt(0.45f * 0.45f + 0.6f * 0.6f + 0.66f * 0.66f)
        lx = -0.45f / l; ly = -0.6f / l; lz = 0.66f / l
        val hl = sqrt(lx * lx + ly * ly + (lz + 1f) * (lz + 1f))
        hx = lx / hl; hy = ly / hl; hz = (lz + 1f) / hl
    }

    // Boot pose scratch.
    private var bDx = 0f
    private var bDz = 0f
    private var bRx = 0f
    private var bRy = 0f
    private var bFill = 1f
    private var bLine = 1f

    /** Index of the key under [pos], front-most first; null if nothing is close. */
    fun hit(pos: Offset, slop: Float): Int? {
        for (k in count - 1 downTo 0) {
            val i = order[k]
            if (!drawn[i] || alphas[i] < 0.3f) continue
            if (!mats[i].invert(inv)) continue
            pts[0] = pos.x
            pts[1] = pos.y
            inv.mapPoints(pts)
            val n = scene.nodes[i]
            if (pts[0] >= -slop && pts[0] <= n.w + slop && pts[1] >= -slop && pts[1] <= n.h + slop) return i
        }
        var best = -1
        var bd = Float.MAX_VALUE
        for (i in 0 until count) {
            if (!drawn[i] || alphas[i] < 0.3f) continue
            val d = hypot(scx[i] - pos.x, scy[i] - pos.y)
            if (d < bd) {
                bd = d
                best = i
            }
        }
        return if (best >= 0 && bd < scene.nodes[best].h * 1.3f) best else null
    }

    private fun ease(p: Float) = 1f - (1f - p).pow(3)

    private fun bootPose(style: KbBoot, n: KeyNode, b: Float, density: Float) {
        bDx = 0f; bDz = 0f; bRx = 0f; bRy = 0f; bFill = 1f; bLine = 1f
        if (style == KbBoot.NONE || b >= 1f) return
        val su = if (n.u < 0f) -1f else 1f
        val sv = if (n.v < 0f) -1f else 1f
        fun prog(delay: Float) = ((b - delay) / 0.56f).coerceIn(0f, 1f)
        when (style) {
            KbBoot.RISE -> {
                val p = prog(n.rowFrac * 0.25f + n.colFrac * 0.2f)
                val e = ease(p)
                bDz = -(1f - e) * (scene.thickness * 3f + 40f * density)
                bFill = e; bLine = e
            }
            KbBoot.ASSEMBLE -> {
                val dist = (hypot(n.u, n.v) / 1.42f).coerceIn(0f, 1f)
                val p = prog(dist * 0.42f)
                val e = ease(p)
                bDz = -(1f - e) * scene.width * 0.8f
                bRy = (1f - e) * 1.1f * su
                bRx = (1f - e) * 0.6f * sv
                bFill = (p * 1.8f).coerceAtMost(1f); bLine = bFill
            }
            KbBoot.SWEEP -> {
                val p = prog((1f - (n.v + 1f) / 2f) * 0.42f)
                bLine = (p * 3f).coerceAtMost(1f)
                bFill = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)
                bDz = -(1f - ease(p)) * 6f * density
            }
            KbBoot.UNFOLD -> {
                val p = prog(abs(n.rowFrac - 0.5f) * 0.8f)
                val e = ease(p)
                bRx = (1f - e) * (PI.toFloat() / 2f) * sv
                bFill = (p * 2.5f).coerceAtMost(1f); bLine = bFill
            }
            KbBoot.FLICKER -> {
                val p = prog(hash01(n.index * 5 + 3) * 0.42f)
                if (p < 1f) {
                    val on = hash01(n.index * 31 + floor(p * 9f).toInt()) > 0.4f
                    bFill = if (p <= 0f) 0f else if (on) p else p * 0.15f
                    bLine = bFill
                    bDx = (1f - p) * (hash01(n.index * 7 + 2) - 0.5f) * 10f * density
                }
            }
            KbBoot.CASCADE -> {
                val p = prog((1f - abs(n.u)).coerceIn(0f, 1f) * 0.42f)
                val e = ease(p)
                bDx = su * (1f - e) * scene.width * 0.5f
                bDz = (1f - e) * (scene.amp * 0.6f + 20f * density)
                bRy = -(1f - e) * 0.7f * su
                bFill = e; bLine = e
            }
            KbBoot.NONE -> Unit
        }
    }

    /** Builds [m] mapping a local w×h rect to the screen for the current key pose. */
    private fun quad(w: Float, h: Float, ox: Float, oy: Float, lzz: Float, m: android.graphics.Matrix): Boolean {
        xf.apply(ox - w / 2f, oy - h / 2f, lzz); dst[0] = xf.outX; dst[1] = xf.outY
        xf.apply(ox + w / 2f, oy - h / 2f, lzz); dst[2] = xf.outX; dst[3] = xf.outY
        xf.apply(ox + w / 2f, oy + h / 2f, lzz); dst[4] = xf.outX; dst[5] = xf.outY
        xf.apply(ox - w / 2f, oy + h / 2f, lzz); dst[6] = xf.outX; dst[7] = xf.outY
        src[0] = 0f; src[1] = 0f; src[2] = w; src[3] = 0f; src[4] = w; src[5] = h; src[6] = 0f; src[7] = h
        return m.setPolyToPoly(src, 0, dst, 0, 4)
    }

    private inline fun DrawScope.withMatrix(m: android.graphics.Matrix, block: DrawScope.() -> Unit) {
        drawIntoCanvas { c ->
            c.save()
            c.nativeCanvas.concat(m)
            block()
            c.restore()
        }
    }

    fun draw(
        scope: DrawScope,
        s: HoloSettings,
        pal: KbPalette,
        frame: KbFrame,
        press: FloatArray,
        held: BooleanArray,
        outlines: Map<Size, Outline>,
        shape: Shape,
        labels: List<TextLayoutResult?>,
        alts: List<TextLayoutResult?>,
        measurer: TextMeasurer,
        font: FontFamily,
    ) = with(scope) {
        val dens = density
        val t = frame.tilt
        val b = if (s.kbBootStyle == KbBoot.NONE) 1f else frame.boot
        xf.setGlobal(t.x, t.y, scene.camD, scene.pivotX, scene.pivotY)

        val deckA = if (s.kbBootStyle == KbBoot.NONE) 1f else (b * 2.2f).coerceAtMost(1f)
        if (s.kbDeckStyle == KbDeckStyle.FRAME && s.kbDeck > 0.01f) drawDeckMesh(s, pal, deckA)
        if (s.kbGrid) drawGrid(pal, deckA)
        if (s.kbDeckStyle == KbDeckStyle.FRAME) drawFrame(s, pal, deckA, b)

        // Pose every key, then sort far → near.
        val pressSink = s.kbPressDepth * (scene.thickness + 4f * dens)
        for (n in scene.nodes) {
            val i = n.index
            bootPose(s.kbBootStyle, n, b, dens)
            xf.setKey(n.x + bDx, n.y, n.z + bDz - press[i] * pressSink, n.rotY + bRy, n.rotX + bRx, 1f)
            xf.apply(0f, 0f, 0f)
            depth[i] = xf.outZ
            scx[i] = xf.outX
            scy[i] = xf.outY
            alphas[i] = bFill
            order[i] = i
        }
        for (a in 1 until count) {
            val v = order[a]
            var j = a - 1
            while (j >= 0 && depth[order[j]] > depth[v]) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = v
        }

        // Shadows first so they never fall across nearer keys.
        if (s.kbShadow > 0.01f) {
            for (k in 0 until count) {
                val n = scene.nodes[order[k]]
                bootPose(s.kbBootStyle, n, b, dens)
                if (bFill <= 0.01f) continue
                xf.setKey(n.x + bDx, n.y, n.z + bDz - press[n.index] * pressSink, n.rotY + bRy, n.rotX + bRx, 1f)
                val drop = scene.thickness + scene.deckDrop + (n.z - scene.surfaceZ(n.u, n.v)).coerceAtLeast(0f)
                val ox = -lx / lz * drop * 0.8f
                val oy = -ly / lz * drop * 0.8f
                val o = outlines[Size(n.w, n.h)] ?: continue
                if (!quad(n.w, n.h, ox, oy, -drop, tmp)) continue
                withMatrix(tmp) {
                    drawOutline(o, Color.Black.copy(alpha = 0.2f * s.kbShadow * bFill))
                    drawRoundRect(
                        Color.Black.copy(alpha = 0.1f * s.kbShadow * bFill),
                        topLeft = Offset(-2f * dens, -2f * dens),
                        size = Size(n.w + 4f * dens, n.h + 4f * dens),
                        cornerRadius = CornerRadius((s.kbKeyRadius + 2f) * dens * scene.fit),
                    )
                }
            }
        }

        val style = s.kbKeyStyle
        for (k in 0 until count) {
            val i = order[k]
            val n = scene.nodes[i]
            drawn[i] = false
            bootPose(s.kbBootStyle, n, b, dens)
            if (bFill <= 0.001f && bLine <= 0.001f) continue
            val p = press[i]
            xf.setKey(n.x + bDx, n.y, n.z + bDz - p * pressSink, n.rotY + bRy, n.rotX + bRx, 1f)
            val o = outlines[Size(n.w, n.h)] ?: continue

            // Extruded slab: stack the outline from the back face up to the face.
            val th = scene.thickness
            if (th > 0.5f && style != KeyStyle.WIRE && style != KeyStyle.BRACKET && bFill > 0.02f) {
                xf.apply(0f, 0f, 0f)
                val fx = xf.outX
                val fy = xf.outY
                xf.apply(0f, 0f, -th)
                val steps = (hypot(xf.outX - fx, xf.outY - fy) / 1.3f).toInt().coerceIn(2, 8)
                for (st in steps downTo 1) {
                    if (!quad(n.w, n.h, 0f, 0f, -th * st / steps, tmp)) continue
                    val c = if (st == steps) pal.sideRim else pal.side
                    withMatrix(tmp) { drawOutline(o, c.copy(alpha = 0.95f * bFill)) }
                }
            }

            if (!quad(n.w, n.h, 0f, 0f, 0f, tmp)) continue
            mats[i].set(tmp)
            drawn[i] = true
            xf.keyNormal()
            val diff = (xf.nx * lx + xf.ny * ly + xf.nz * lz).coerceAtLeast(0f)
            val spec = (xf.nx * hx + xf.ny * hy + xf.nz * hz).coerceAtLeast(0f).pow(24)
            val fade = s.kbDepthFade * (-depth[i] / (scene.amp * 0.6f + 8f * dens)).coerceIn(0f, 1f)
            val nx = xf.nx
            val ny = xf.ny
            withMatrix(tmp) {
                drawFace(
                    n, o, s, pal, style, diff, spec, nx, ny, p, held[i], bFill, bLine, fade,
                    labels[i], alts[i], frame,
                )
            }
        }

        // Key preview plate, floating in front of the pressed key.
        val pi = frame.popup
        if (s.kbPopup && pi in 0 until count && drawn[pi]) {
            val n = scene.nodes[pi]
            bootPose(s.kbBootStyle, n, b, dens)
            xf.setKey(n.x + bDx, n.y, n.z + bDz, n.rotY + bRy, n.rotX + bRx, 1f)
            val pw = max(n.w * 1.3f, 46f * dens * scene.fit)
            val ph = n.h * 1.25f
            val oy = -(n.h / 2f + scene.gap * 1.2f + ph / 2f)
            if (quad(pw, ph, 0f, oy, scene.thickness + 12f * dens, tmp)) {
                val po = shape.createOutline(Size(pw, ph), layoutDirection, this)
                withMatrix(tmp) { drawPopup(po, pw, ph, pal, s, frame.popupText, measurer, font) }
            }
        }

        if (s.kbBootStyle == KbBoot.SWEEP && b < 1f) drawScanline(pal, b)
    }

    // ------------------------------------------------------------ deck

    private fun deckPoint(u: Float, v: Float, dz: Float = 0f) {
        xf.applyWorld(scene.deckX(u), scene.deckY(v), scene.deckZ(u, v) + dz)
    }

    private fun DrawScope.drawDeckMesh(s: HoloSettings, pal: KbPalette, a: Float) {
        val nu = 22
        val nv = 4
        val fu = Scene.FRAME_U
        val fv = Scene.FRAME_V
        drawIntoCanvas { c ->
            for (i in 0 until nu) for (j in 0 until nv) {
                val u0 = -fu + 2f * fu * i / nu
                val u1 = -fu + 2f * fu * (i + 1) / nu
                val v0 = -fv + 2f * fv * j / nv
                val v1 = -fv + 2f * fv * (j + 1) / nv
                nPath.reset()
                deckPoint(u0, v0); nPath.moveTo(xf.outX, xf.outY)
                deckPoint(u1 + 0.004f, v0); nPath.lineTo(xf.outX, xf.outY)
                deckPoint(u1 + 0.004f, v1 + 0.004f); nPath.lineTo(xf.outX, xf.outY)
                deckPoint(u0, v1 + 0.004f); nPath.lineTo(xf.outX, xf.outY)
                nPath.close()
                val um = (u0 + u1) / 2f
                val vm = (v0 + v1) / 2f
                xf.worldNormal(-scene.slopeX(um, vm), -scene.slopeY(um, vm), 1f)
                val d = (xf.nx * lx + xf.ny * ly + xf.nz * lz).coerceAtLeast(0f)
                val col = lerp(pal.deck, Color.White, 0.07f * d)
                meshPaint.color = col.copy(alpha = (s.kbDeck * a).coerceIn(0f, 1f)).toArgb()
                c.nativeCanvas.drawPath(nPath, meshPaint)
            }
        }
    }

    private fun DrawScope.drawGrid(pal: KbPalette, a: Float) {
        val fu = Scene.FRAME_U
        val fv = Scene.FRAME_V
        linePaint.strokeWidth = 0.8f * density
        linePaint.color = pal.accent.copy(alpha = 0.07f * a).toArgb()
        drawIntoCanvas { c ->
            for (k in 0..8) {
                val v = -1f + k / 4f
                nPath.reset()
                for (st in 0..16) {
                    deckPoint(-fu + 2f * fu * st / 16f, v, 0.5f)
                    if (st == 0) nPath.moveTo(xf.outX, xf.outY) else nPath.lineTo(xf.outX, xf.outY)
                }
                c.nativeCanvas.drawPath(nPath, linePaint)
            }
            for (k in 0..12) {
                val u = -1f + k / 6f
                nPath.reset()
                for (st in 0..8) {
                    deckPoint(u, -fv + 2f * fv * st / 8f, 0.5f)
                    if (st == 0) nPath.moveTo(xf.outX, xf.outY) else nPath.lineTo(xf.outX, xf.outY)
                }
                c.nativeCanvas.drawPath(nPath, linePaint)
            }
        }
    }

    private fun DrawScope.drawFrame(s: HoloSettings, pal: KbPalette, a: Float, b: Float) {
        val fu = Scene.FRAME_U
        val fv = Scene.FRAME_V
        val grow = if (s.kbBootStyle == KbBoot.NONE) 1f else (b * 1.6f).coerceAtMost(1f)
        drawIntoCanvas { c ->
            // Rim: edges grow out from the top and bottom centre during boot.
            linePaint.strokeWidth = 1f * density
            linePaint.color = pal.border.copy(alpha = 0.28f * a).toArgb()
            for (sv in floatArrayOf(-1f, 1f)) {
                nPath.reset()
                for (st in 0..20) {
                    val u = (-fu + 2f * fu * st / 20f) * grow
                    deckPoint(u, sv * fv, 1f)
                    if (st == 0) nPath.moveTo(xf.outX, xf.outY) else nPath.lineTo(xf.outX, xf.outY)
                }
                c.nativeCanvas.drawPath(nPath, linePaint)
            }
            if (grow >= 1f) {
                for (su in floatArrayOf(-1f, 1f)) {
                    nPath.reset()
                    for (st in 0..10) {
                        deckPoint(su * fu, -fv + 2f * fv * st / 10f, 1f)
                        if (st == 0) nPath.moveTo(xf.outX, xf.outY) else nPath.lineTo(xf.outX, xf.outY)
                    }
                    c.nativeCanvas.drawPath(nPath, linePaint)
                }
            }
            // Glowing corner brackets, like the in-game panel frame.
            val ca = a * grow
            for (su in floatArrayOf(-1f, 1f)) for (sv in floatArrayOf(-1f, 1f)) {
                nPath.reset()
                for (st in 5 downTo 0) {
                    deckPoint(su * fu, sv * (fv - st * 0.09f), 1f)
                    if (st == 5) nPath.moveTo(xf.outX, xf.outY) else nPath.lineTo(xf.outX, xf.outY)
                }
                for (st in 1..5) {
                    deckPoint(su * (fu - st * 0.04f), sv * fv, 1f)
                    nPath.lineTo(xf.outX, xf.outY)
                }
                for ((w, al) in arrayOf(6f to 0.08f, 3.5f to 0.16f, 1.6f to 0.9f)) {
                    linePaint.strokeWidth = w * density
                    linePaint.color = pal.frameAccent.copy(alpha = al * ca).toArgb()
                    c.nativeCanvas.drawPath(nPath, linePaint)
                }
            }
            // Top-centre notch.
            deckPoint(0f, -fv, 1f)
            val tx = xf.outX
            val ty = xf.outY
            nPath.reset()
            nPath.moveTo(tx - 6f * density, ty + 3f * density)
            nPath.lineTo(tx, ty + 8f * density)
            nPath.lineTo(tx + 6f * density, ty + 3f * density)
            linePaint.strokeWidth = 1.2f * density
            linePaint.color = pal.border.copy(alpha = 0.5f * a).toArgb()
            c.nativeCanvas.drawPath(nPath, linePaint)
            // Side ticks.
            for (su in floatArrayOf(-1f, 1f)) {
                deckPoint(su * (fu + 0.025f), 0f, 1f)
                val x = xf.outX
                val y = xf.outY
                linePaint.strokeWidth = 1f * density
                linePaint.color = pal.border.copy(alpha = 0.35f * a).toArgb()
                c.nativeCanvas.drawLine(x - 3f * density, y, x + 3f * density, y, linePaint)
                c.nativeCanvas.drawLine(x, y - 3f * density, x, y + 3f * density, linePaint)
            }
        }
    }

    private fun DrawScope.drawScanline(pal: KbPalette, b: Float) {
        val fu = Scene.FRAME_U
        val fv = Scene.FRAME_V
        val v = fv - (b / 0.75f).coerceAtMost(1f) * 2f * fv
        if (b > 0.8f) return
        drawIntoCanvas { c ->
            nPath.reset()
            for (st in 0..20) {
                deckPoint(-fu + 2f * fu * st / 20f, v, scene.thickness + 8f * density)
                if (st == 0) nPath.moveTo(xf.outX, xf.outY) else nPath.lineTo(xf.outX, xf.outY)
            }
            for ((w, al) in arrayOf(14f to 0.06f, 6f to 0.15f, 1.5f to 0.9f)) {
                linePaint.strokeWidth = w * density
                linePaint.color = pal.accent.copy(alpha = al).toArgb()
                c.nativeCanvas.drawPath(nPath, linePaint)
            }
        }
    }

    // ------------------------------------------------------------ keys

    private fun DrawScope.glowOutline(o: Outline, color: Color, amount: Float, bw: Float) {
        if (amount <= 0.005f) return
        drawOutline(o, color.copy(alpha = (0.05f * amount).coerceIn(0f, 1f)), style = Stroke(bw + 8f * density))
        drawOutline(o, color.copy(alpha = (0.1f * amount).coerceIn(0f, 1f)), style = Stroke(bw + 4f * density))
        drawOutline(o, color.copy(alpha = (0.18f * amount).coerceIn(0f, 1f)), style = Stroke(bw + 2f * density))
    }

    private fun DrawScope.drawFace(
        n: KeyNode,
        o: Outline,
        s: HoloSettings,
        pal: KbPalette,
        style: KeyStyle,
        diff: Float,
        spec: Float,
        nx: Float,
        ny: Float,
        press: Float,
        held: Boolean,
        fillA: Float,
        lineA: Float,
        fade: Float,
        label: TextLayoutResult?,
        alt: TextLayoutResult?,
        frame: KbFrame,
    ) {
        val w = n.w
        val h = n.h
        val k = scene.fit
        val fn = n.spec.isFunction
        val enter = n.spec.action == KeyAction.Enter
        val accentEnter = enter && s.kbEnterAccent
        val top = if (fn) pal.fnTop else pal.keyTop
        val bottom = if (fn) pal.fnBottom else pal.keyBottom
        val op = s.kbKeyOpacity
        val shadeA = (s.kbShade * (1f - diff) * 0.55f + fade * 0.5f).coerceIn(0f, 0.9f)
        val specA = s.kbSpecular * spec
        val bw = s.kbBorderWidth * density
        val glow = s.kbBorderGlow
        val r = s.kbKeyRadius * density * k

        val sheen = Brush.linearGradient(
            0f to Color.White.copy(alpha = (0.05f + 0.25f * specA).coerceIn(0f, 1f)),
            0.5f to Color.Transparent,
            start = Offset(w * nx * 2f, h * ny * 2f),
            end = Offset(w * (1f + nx * 2f), h * (1f + ny * 2f)),
        )

        when (style) {
            KeyStyle.TILE -> {
                drawOutline(o, Brush.verticalGradient(0f to top.copy(alpha = op), 1f to bottom.copy(alpha = op), endY = h), alpha = fillA)
                if (accentEnter) drawOutline(o, pal.accent.copy(alpha = 0.22f), alpha = fillA)
                drawOutline(o, Color.Black.copy(alpha = shadeA), alpha = fillA)
                drawOutline(o, sheen, alpha = fillA)
                drawOutline(o, Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.18f), endY = h), alpha = fillA)
                drawLine(
                    Color.White.copy(alpha = ((0.1f + 0.25f * specA) * lineA).coerceIn(0f, 1f)),
                    Offset(r, 1.2f * density), Offset(w - r, 1.2f * density), 1f * density,
                )
                glowOutline(o, pal.accent, glow * lineA * (if (accentEnter) 3f else 1f), bw)
                val bc = if (accentEnter) pal.accent else pal.border
                val ba = if (accentEnter) 0.75f else 0.3f + 0.3f * specA
                drawOutline(o, bc.copy(alpha = (ba * lineA).coerceIn(0f, 1f)), style = Stroke(bw))
            }
            KeyStyle.GLASS -> {
                drawOutline(o, Brush.verticalGradient(0f to top.copy(alpha = op * 0.7f), 1f to bottom.copy(alpha = op * 0.7f), endY = h), alpha = fillA)
                if (accentEnter) drawOutline(o, pal.accent.copy(alpha = 0.2f), alpha = fillA)
                drawOutline(o, Color.Black.copy(alpha = shadeA * 0.6f), alpha = fillA)
                drawOutline(
                    o,
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = (0.08f + 0.3f * specA).coerceIn(0f, 1f)),
                        0.45f to Color.Transparent,
                        1f to Color.White.copy(alpha = 0.03f),
                        start = Offset(w * nx * 2f, h * ny * 2f),
                        end = Offset(w * (1f + nx * 2f), h * (1f + ny * 2f)),
                    ),
                    alpha = fillA,
                )
                glowOutline(o, pal.accent, glow * lineA * (if (accentEnter) 3f else 1f), bw)
                val bc = if (accentEnter) pal.accent else pal.border
                drawOutline(
                    o,
                    Brush.linearGradient(
                        0f to bc.copy(alpha = ((0.75f + specA * 0.25f) * lineA).coerceIn(0f, 1f)),
                        0.5f to bc.copy(alpha = 0.25f * lineA),
                        1f to bc.copy(alpha = 0.5f * lineA),
                        start = Offset(w * (0.5f + nx * 2f), 0f),
                        end = Offset(w * (0.5f - nx * 2f), h),
                    ),
                    style = Stroke(bw),
                )
            }
            KeyStyle.WIRE -> {
                drawOutline(o, pal.accent.copy(alpha = 0.08f * op), alpha = fillA)
                if (accentEnter) drawOutline(o, pal.accent.copy(alpha = 0.16f), alpha = fillA)
                glowOutline(o, pal.accent, (0.6f + glow) * lineA * (if (accentEnter) 1.6f else 1f), bw)
                drawOutline(o, pal.accent.copy(alpha = ((0.7f + 0.3f * specA) * lineA).coerceIn(0f, 1f)), style = Stroke(bw))
            }
            KeyStyle.SOLID -> {
                drawOutline(o, Brush.verticalGradient(0f to lerp(top, Color.White, 0.04f), 1f to bottom, endY = h), alpha = fillA * op)
                if (accentEnter) drawOutline(o, pal.accent.copy(alpha = 0.3f), alpha = fillA)
                drawOutline(o, Color.Black.copy(alpha = shadeA), alpha = fillA)
                drawOutline(o, sheen, alpha = fillA)
                drawOutline(
                    o,
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = ((0.22f + 0.3f * specA) * lineA).coerceIn(0f, 1f)),
                        0.25f to Color.Transparent,
                        0.8f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.45f * lineA),
                        endY = h,
                    ),
                    style = Stroke(bw * 1.8f),
                )
                glowOutline(o, pal.accent, glow * 0.6f * lineA * (if (accentEnter) 3f else 1f), bw)
                drawOutline(o, Color.Black.copy(alpha = 0.5f * lineA), style = Stroke(0.8f * density))
            }
            KeyStyle.BRACKET -> {
                drawOutline(o, top.copy(alpha = 0.3f * op), alpha = fillA)
                if (accentEnter) drawOutline(o, pal.accent.copy(alpha = 0.18f), alpha = fillA)
                drawOutline(o, Color.Black.copy(alpha = shadeA * 0.6f), alpha = fillA)
                drawOutline(o, pal.border.copy(alpha = 0.12f * lineA), style = Stroke(1f * density))
                val len = minOf(w, h) * 0.28f
                val sw = bw * 1.3f
                val col = pal.accent.copy(alpha = ((0.85f + 0.15f * specA) * lineA).coerceIn(0f, 1f))
                val gcol = pal.accent.copy(alpha = (0.15f * (0.5f + glow) * lineA).coerceIn(0f, 1f))
                for ((cx, cy) in arrayOf(0f to 0f, w to 0f, 0f to h, w to h)) {
                    val dx = if (cx == 0f) len else -len
                    val dy = if (cy == 0f) len else -len
                    for ((c, wd) in arrayOf(gcol to sw + 4f * density, col to sw)) {
                        drawLine(c, Offset(cx, cy), Offset(cx + dx, cy), wd, StrokeCap.Round)
                        drawLine(c, Offset(cx, cy), Offset(cx, cy + dy), wd, StrokeCap.Round)
                    }
                }
            }
        }

        // Press: wash, glow and a shockwave ring as the key springs back.
        if (press > 0.01f) {
            drawOutline(o, pal.accent.copy(alpha = 0.3f * press), alpha = fillA)
            if (s.kbPressFx) {
                glowOutline(o, pal.accent, 3f * press, bw)
                if (!held) {
                    val g = (1f - press) * 10f * density
                    drawRoundRect(
                        pal.accent.copy(alpha = 0.45f * press),
                        topLeft = Offset(-g, -g),
                        size = Size(w + g * 2f, h + g * 2f),
                        cornerRadius = CornerRadius(r + g),
                        style = Stroke(1f * density),
                    )
                }
            }
        }

        // Labels and glyphs.
        val ink0 = when {
            style == KeyStyle.WIRE -> lerp(pal.label, pal.accent, 0.45f)
            fn -> pal.labelFn
            else -> pal.label
        }
        val ink = lerp(ink0, Color.White, 0.4f * press).copy(alpha = lineA.coerceIn(0f, 1f))
        val showBadge = alt != null && s.kbAltBadges
        val c = Offset(w / 2f, h / 2f + if (showBadge && h < 60f * density) 2f * density * k else 0f)
        val iconSize = 15f * density * k * s.kbLabelScale
        when (n.spec.action) {
            KeyAction.Shift -> glyphShift(c, iconSize, ink, filled = frame.upper, caps = frame.caps)
            KeyAction.Backspace -> glyphBackspace(c, iconSize, ink)
            KeyAction.Globe -> glyphGlobe(c, iconSize * 0.95f, ink)
            KeyAction.Space -> {
                val sw = w * 0.28f
                val y = c.y + 4f * density * k
                val col = pal.labelDim.copy(alpha = 0.8f * lineA)
                val st = 1.5f * density
                drawLine(col, Offset(c.x - sw / 2f, y), Offset(c.x + sw / 2f, y), st)
                drawLine(col, Offset(c.x - sw / 2f, y), Offset(c.x - sw / 2f, y - 4f * density * k), st)
                drawLine(col, Offset(c.x + sw / 2f, y), Offset(c.x + sw / 2f, y - 4f * density * k), st)
            }
            KeyAction.Enter -> if (!frame.enterText) glyphEnter(c, iconSize, lerp(pal.label, Color.White, 0.3f).copy(alpha = lineA))
            else -> Unit
        }
        if (label != null) {
            val tl = Offset(c.x - label.size.width / 2f, c.y - label.size.height / 2f)
            if (s.kbLabelGlow > 0.01f) {
                drawText(
                    label, color = pal.accent, topLeft = tl,
                    alpha = (0.35f * s.kbLabelGlow * lineA).coerceIn(0f, 1f),
                    drawStyle = Stroke((1f + 2.5f * s.kbLabelGlow) * density),
                )
            }
            drawText(label, color = if (accentEnter) lerp(pal.label, Color.White, 0.3f) else ink0, topLeft = tl, alpha = lineA.coerceIn(0f, 1f))
        }
        if (alt != null) {
            if (showBadge) {
                val bh = 11f * density * k
                val bwid = max(bh, alt.size.width + 6f * density * k)
                val bx = 3f * density * k
                drawRoundRect(
                    pal.accent.copy(alpha = 0.55f * lineA),
                    topLeft = Offset(bx, bx), size = Size(bwid, bh),
                    cornerRadius = CornerRadius(2.5f * density * k), style = Stroke(0.8f * density),
                )
                drawText(alt, color = pal.labelFn, topLeft = Offset(bx + (bwid - alt.size.width) / 2f, bx + (bh - alt.size.height) / 2f), alpha = 0.9f * lineA)
            } else {
                drawText(alt, color = pal.labelDim, topLeft = Offset(w - alt.size.width - 4f * density * k, 2f * density * k), alpha = 0.75f * lineA)
            }
        }
    }

    private fun DrawScope.drawPopup(
        o: Outline,
        w: Float,
        h: Float,
        pal: KbPalette,
        s: HoloSettings,
        text: String,
        measurer: TextMeasurer,
        font: FontFamily,
    ) {
        drawRoundRect(Color.Black.copy(alpha = 0.35f), topLeft = Offset(-3f * density, 4f * density), size = Size(w + 6f * density, h + 6f * density), cornerRadius = CornerRadius(12f * density))
        drawOutline(
            o,
            Brush.verticalGradient(0f to lerp(pal.keyTop, pal.accent, 0.2f), 1f to lerp(pal.keyBottom, Color.Black, 0.2f), endY = h),
        )
        glowOutline(o, pal.accent, 1.6f, 1.5f * density)
        drawOutline(o, pal.accent.copy(alpha = 0.9f), style = Stroke(1.5f * density))
        val layout = measurer.measure(
            text,
            TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 30.sp * s.kbLabelScale * scene.fit),
        )
        val tl = Offset(w / 2f - layout.size.width / 2f, h / 2f - layout.size.height / 2f)
        drawText(layout, color = pal.accent, topLeft = tl, alpha = 0.5f, drawStyle = Stroke(3f * density))
        drawText(layout, color = Color.White, topLeft = tl)
    }

    // ------------------------------------------------------------ glyphs

    private fun DrawScope.strokeOf(px: Float) = Stroke(px, cap = StrokeCap.Round, join = StrokeJoin.Round)

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
        drawPath(p, color, style = strokeOf(1.6f * density))
        if (caps) drawLine(color, Offset(c.x - 0.3f * s, c.y + 0.62f * s), Offset(c.x + 0.3f * s, c.y + 0.62f * s), 1.8f * density, StrokeCap.Round)
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
        drawPath(p, color, style = strokeOf(1.6f * density))
        val x = c.x + 0.16f * s
        val d = 0.16f * s
        drawLine(color, Offset(x - d, c.y - d), Offset(x + d, c.y + d), 1.6f * density, StrokeCap.Round)
        drawLine(color, Offset(x + d, c.y - d), Offset(x - d, c.y + d), 1.6f * density, StrokeCap.Round)
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
        drawPath(p, color, style = strokeOf(1.8f * density))
    }

    private fun DrawScope.glyphGlobe(c: Offset, s: Float, color: Color) {
        val r = 0.5f * s
        val st = strokeOf(1.4f * density)
        drawCircle(color, r, c, style = st)
        drawOval(color, Offset(c.x - r * 0.42f, c.y - r), Size(r * 0.84f, r * 2f), style = st)
        drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.4f * density)
    }
}
