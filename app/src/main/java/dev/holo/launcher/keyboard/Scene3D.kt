package dev.holo.launcher.keyboard

import dev.holo.launcher.data.HoloSettings
import dev.holo.launcher.data.KbSurface
import dev.holo.launcher.data.KeyDepthProfile
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * A tiny 3D scene for the keyboard. World space: x right, y down, z toward the viewer, origin
 * at the centre of the key field (the pivot). One camera looks down -z from [Scene.camD], so
 * every key is projected through the same perspective, like a real render.
 */

/** One key placed on the surface. Sizes and positions are in px, already fitted to the screen. */
class KeyNode(
    val index: Int,
    val spec: KeySpec,
    val row: Int,
    val rowFrac: Float,
    val colFrac: Float,
    /** Normalised position on the field, -1..1. */
    val u: Float,
    val v: Float,
    /** 0..1 float value from the depth profile. */
    val float: Float,
) {
    var x = 0f
    var y = 0f
    var z = 0f
    var w = 0f
    var h = 0f
    var rotY = 0f
    var rotX = 0f
}

class Scene(
    val nodes: List<KeyNode>,
    val width: Float,
    val height: Float,
    val pivotX: Float,
    val pivotY: Float,
    /** Half extents of the key field before fitting. */
    val halfW: Float,
    val halfH: Float,
    val fit: Float,
    val amp: Float,
    val surface: KbSurface,
    val camD: Float,
    /** Key slab thickness. */
    val thickness: Float,
    val gap: Float,
    /** How far below the surface the deck plate sits. */
    val deckDrop: Float,
) {
    fun surfaceZ(u: Float, v: Float): Float = surfaceZ(surface, amp, u, v)

    /** World position of a point on the deck plate at (u, v). */
    fun deckX(u: Float) = u * halfW * fit
    fun deckY(v: Float) = v * halfH * fit
    fun deckZ(u: Float, v: Float) = surfaceZ(u, v) - thickness - deckDrop

    /** Surface slope in world units, for lighting the deck. */
    fun slopeX(u: Float, v: Float) = surfaceDu(surface, amp, u, v) / (halfW * fit)
    fun slopeY(u: Float, v: Float) = surfaceDv(surface, amp, u, v) / (halfH * fit)

    companion object {
        const val FRAME_U = 1.07f
        const val FRAME_V = 1.16f
    }
}

private const val V_WEIGHT = 0.35f

fun surfaceZ(s: KbSurface, a: Float, u: Float, v: Float): Float = when (s) {
    // Centre far, sides near: an inverted dome the keys wrap around.
    KbSurface.BOWL -> a * (u * u + V_WEIGHT * v * v) - a * 0.45f
    KbSurface.CYLINDER -> a * u * u - a * 0.4f
    KbSurface.FLAT -> 0f
    KbSurface.DOME -> -(a * (u * u + V_WEIGHT * v * v) - a * 0.45f)
    // A raked console: bottom rows nearer.
    KbSurface.SLOPE -> a * 0.7f * v
}

private fun surfaceDu(s: KbSurface, a: Float, u: Float, v: Float): Float = when (s) {
    KbSurface.BOWL, KbSurface.CYLINDER -> a * 2f * u
    KbSurface.DOME -> -a * 2f * u
    else -> 0f
}

private fun surfaceDv(s: KbSurface, a: Float, u: Float, v: Float): Float = when (s) {
    KbSurface.BOWL -> a * V_WEIGHT * 2f * v
    KbSurface.DOME -> -a * V_WEIGHT * 2f * v
    KbSurface.SLOPE -> a * 0.7f
    else -> 0f
}

private fun profileFloat(p: KeyDepthProfile, k: KeySpec, idx: Int, rowFrac: Float): Float = when (p) {
    KeyDepthProfile.UNIFORM -> 0.5f
    KeyDepthProfile.RAKE -> rowFrac
    KeyDepthProfile.ISLANDS -> when {
        k.action == KeyAction.Space -> 0.7f
        k.isFunction -> 1f
        else -> 0.2f
    }
    KeyDepthProfile.SCATTER -> hash01(idx * 3 + 1)
}

fun hash01(i: Int): Float {
    val v = sin(i * 12.9898f + 4.1414f) * 43758.547f
    return v - floor(v)
}

/** Field padding above and below the keys, in dp. */
const val FIELD_PAD_TOP = 16f
const val FIELD_PAD_BOTTOM = 14f

fun buildScene(rows: List<KeyRow>, width: Float, density: Float, s: HoloSettings): Scene {
    val g = s.kbKeyGap * density
    val kh = s.kbKeyHeight * density
    val padTop = FIELD_PAD_TOP * density
    val padBottom = FIELD_PAD_BOTTOM * density
    val fieldH = rows.size * (kh + g) + g
    val height = padTop + fieldH + padBottom
    val halfW = width / 2f
    val halfH = fieldH / 2f
    val pivotX = width / 2f
    val pivotY = padTop + fieldH / 2f

    // Flat layout first, then wrap it onto the surface.
    val side = g
    val unit = (width - side * 2f) / 10f
    val nRows = rows.size
    val nodes = ArrayList<KeyNode>()
    val flat = ArrayList<FloatArray>() // cx, cy, w, h relative to the pivot
    rows.forEachIndexed { r, row ->
        val units = row.lead + row.trail + row.keys.sumOf { it.width.toDouble() }.toFloat()
        var x = side + (10f - units) / 2f * unit + row.lead * unit
        val top = g + r * (kh + g)
        row.keys.forEach { k ->
            val w = k.width * unit
            val cx = x + w / 2f - halfW
            val cy = top + kh / 2f - halfH
            val idx = nodes.size
            val rowFrac = if (nRows > 1) r / (nRows - 1f) else 0f
            val colFrac = ((cx + halfW) / width).coerceIn(0f, 1f)
            nodes += KeyNode(
                index = idx, spec = k, row = r, rowFrac = rowFrac, colFrac = colFrac,
                u = cx / halfW, v = cy / halfH,
                float = profileFloat(s.kbDepthProfile, k, idx, rowFrac),
            )
            flat += floatArrayOf(cx, cy, w - g, kh)
            x += w
        }
    }

    val amp = s.kbCurve * width * 0.16f
    val camD = width * 2.2f / s.kbLens.coerceAtLeast(0.2f)
    val thickness = s.kbThickness * 9f * density
    val floatPx = s.kbDepth * 16f * density
    val deckDrop = 5f * density

    var fit = 1f
    val xf = Xf()
    var lastMinY = 0f
    var lastMaxY = height
    repeat(4) {
        nodes.forEachIndexed { i, n ->
            val f = flat[i]
            n.x = f[0] * fit
            n.y = f[1] * fit
            n.w = f[2] * fit
            n.h = f[3] * fit
            n.z = surfaceZ(s.kbSurface, amp, n.u, n.v) + floatPx * n.float
            n.rotY = atan(surfaceDu(s.kbSurface, amp, n.u, n.v) / (halfW * fit)) * s.kbKeyAngle
            n.rotX = atan(surfaceDv(s.kbSurface, amp, n.u, n.v) / (halfH * fit)) * s.kbKeyAngle
        }
        // Measure the projected bounds at rest and shrink until everything fits.
        xf.setGlobal(0f, 0f, camD, pivotX, pivotY)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        fun acc() {
            minX = min(minX, xf.outX); maxX = max(maxX, xf.outX)
            minY = min(minY, xf.outY); maxY = max(maxY, xf.outY)
        }
        nodes.forEach { n ->
            xf.setKey(n.x, n.y, n.z, n.rotY, n.rotX, 1f)
            for (cx in floatArrayOf(-0.5f, 0.5f)) for (cy in floatArrayOf(-0.5f, 0.5f)) {
                xf.apply(cx * n.w, cy * n.h, 0f)
                acc()
            }
        }
        for (k in 0..8) {
            val t = -1f + k / 4f
            for ((u, v) in listOf(t * Scene.FRAME_U to -Scene.FRAME_V, t * Scene.FRAME_U to Scene.FRAME_V,
                -Scene.FRAME_U to t * Scene.FRAME_V, Scene.FRAME_U to t * Scene.FRAME_V)) {
                xf.applyWorld(u * halfW * fit, v * halfH * fit, surfaceZ(s.kbSurface, amp, u, v) - thickness - deckDrop)
                acc()
            }
        }
        lastMinY = minY
        lastMaxY = maxY
        val margin = 3f * density
        val needW = maxX - minX
        val needH = maxY - minY
        val kx = (width - margin * 2f) / needW
        val ky = (height - margin) / needH
        val k = min(1f, min(kx, ky))
        if (abs(1f - k) < 0.003f) return@repeat
        fit *= k
    }

    // Re-centre vertically: sloped or domed shapes project lopsided.
    val centredPivotY = pivotY + (height / 2f - (lastMinY + lastMaxY) / 2f) * 0.9f
    return Scene(
        nodes = nodes, width = width, height = height, pivotX = pivotX, pivotY = centredPivotY,
        halfW = halfW, halfH = halfH, fit = fit, amp = amp, surface = s.kbSurface, camD = camD,
        thickness = thickness, gap = g * fit, deckDrop = deckDrop,
    )
}

/**
 * Reusable transform: a key's own pose (scale, yaw, pitch, position) followed by the global tilt
 * and the perspective projection. Results land in [outX], [outY] (screen px) and [outZ] (depth).
 */
class Xf {
    private var px = 0f
    private var py = 0f
    private var pz = 0f
    private var cY = 1f
    private var sY = 0f
    private var cX = 1f
    private var sX = 0f
    private var sc = 1f
    private var cg = 1f
    private var sg = 0f
    private var cp = 1f
    private var sp = 0f
    private var d = 1000f
    private var ox = 0f
    private var oy = 0f
    var outX = 0f
    var outY = 0f
    var outZ = 0f
    var nx = 0f
    var ny = 0f
    var nz = 1f

    fun setGlobal(yawDeg: Float, pitchDeg: Float, camD: Float, pivotX: Float, pivotY: Float) {
        val g = Math.toRadians(yawDeg.toDouble()).toFloat()
        val p = Math.toRadians(pitchDeg.toDouble()).toFloat()
        cg = cos(g); sg = sin(g); cp = cos(p); sp = sin(p)
        d = camD; ox = pivotX; oy = pivotY
    }

    fun setKey(x: Float, y: Float, z: Float, rotY: Float, rotX: Float, scale: Float) {
        px = x; py = y; pz = z
        cY = cos(rotY); sY = sin(rotY); cX = cos(rotX); sX = sin(rotX)
        sc = scale
    }

    /** Local key point → screen. */
    fun apply(lx: Float, ly: Float, lz: Float) {
        val x = lx * sc
        val y = ly * sc
        val z = lz * sc
        val x1 = x * cY - z * sY
        val z1 = x * sY + z * cY
        val y2 = y * cX - z1 * sX
        val z2 = y * sX + z1 * cX
        applyWorld(x1 + px, y2 + py, z2 + pz)
    }

    /** World point → screen. */
    fun applyWorld(x: Float, y: Float, z: Float) {
        val x1 = x * cg + z * sg
        val z1 = -x * sg + z * cg
        val y1 = y * cp - z1 * sp
        val z2 = y * sp + z1 * cp
        val s = d / max(d - z2, d * 0.05f)
        outX = ox + x1 * s
        outY = oy + y1 * s
        outZ = z2
    }

    /** Face normal of the current key after all rotations, into [nx], [ny], [nz]. */
    fun keyNormal() {
        // Local (0,0,1) through yaw, pitch, then the global rotation.
        val x1 = -sY
        val z1 = cY
        val y2 = -z1 * sX
        val z2 = z1 * cX
        worldNormal(x1, y2, z2)
    }

    fun worldNormal(x: Float, y: Float, z: Float) {
        val x1 = x * cg + z * sg
        val z1 = -x * sg + z * cg
        val y1 = y * cp - z1 * sp
        val z2 = y * sp + z1 * cp
        val len = kotlin.math.sqrt(x1 * x1 + y1 * y1 + z2 * z2).coerceAtLeast(1e-4f)
        nx = x1 / len; ny = y1 / len; nz = z2 / len
    }
}
