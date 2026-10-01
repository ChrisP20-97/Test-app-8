package dev.holo.launcher.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.holo.launcher.ui.theme.LocalHolo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Depth of each exploded layer, in phone units (the phone is 76 x 152 units). */
private enum class Layer(val z: Float) { BACK(-30f), BATTERY(-13f), BOARD(4f), FRAME(18f), GLASS(32f) }

/**
 * Exploded, slowly turning wireframe of the phone above a projector, with a light beam,
 * scan line and drifting motes. [extraTilt] adds the device-motion tilt (degrees) on top.
 */
@Composable
fun DeviceHologram(
    modifier: Modifier,
    batteryFraction: Float,
    charging: Boolean,
    extraTilt: androidx.compose.runtime.State<Offset>,
) {
    val style = LocalHolo.current
    val holo = style.holo
    val animate = !style.lowPower
    val transition = rememberInfiniteTransition(label = "hologram")
    val spin: androidx.compose.runtime.State<Float> = if (animate) {
        transition.animateFloat(
            initialValue = -38f, targetValue = 38f,
            animationSpec = infiniteRepeatable(tween(11000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "spin",
        )
    } else {
        remember { mutableFloatStateOf(-24f) }
    }
    val loop: androidx.compose.runtime.State<Float> = if (animate) {
        transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
            label = "loop",
        )
    } else {
        remember { mutableFloatStateOf(0.3f) }
    }

    BoxWithConstraints(modifier) {
        val phoneH = min(maxHeight * 0.56f, maxWidth * 1.1f)
        val phoneW = phoneH / 2f
        val phoneTop = maxHeight * 0.17f
        val projY = phoneTop + phoneH + maxHeight * 0.06f
        val angle = { spin.value + extraTilt.value.x * 3f }
        val pitch = { 14f - extraTilt.value.y * 2f }

        // Light beam from projector up to the phone (blurred so it reads as volumetric).
        Canvas(Modifier.fillMaxSize().blur(4.dp, BlurredEdgeTreatment.Unbounded)) {
            val cx = size.width / 2f
            val top = phoneTop.toPx() + 6.dp.toPx()
            val bottom = projY.toPx()
            val halfTop = phoneW.toPx() * 1.05f
            val halfBottom = phoneW.toPx() * 0.3f
            val beam = Path().apply {
                moveTo(cx - halfTop, top)
                lineTo(cx + halfTop, top)
                lineTo(cx + halfBottom, bottom)
                lineTo(cx - halfBottom, bottom)
                close()
            }
            drawPath(beam, Brush.verticalGradient(listOf(Color.Transparent, holo.copy(alpha = 0.3f)), startY = top, endY = bottom))
        }

        // Projector rings.
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = projY.toPx()
            val u = phoneW.toPx() / 76f * 2f
            fun oval(rx: Float, ry: Float): Pair<Offset, Size> =
                Pair(Offset(cx - rx * u, cy - ry * u), Size(rx * 2f * u, ry * 2f * u))
            val (o1, s1) = oval(27f, 5f)
            val (o2, s2) = oval(22f, 4f)
            val (o3, s3) = oval(13.5f, 2.5f)
            drawOval(
                holo.copy(alpha = 0.3f), o1, s1,
                style = Stroke(
                    1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()), -loop.value * 56.dp.toPx()),
                ),
            )
            drawOval(holo.copy(alpha = 0.55f), o2, s2, style = Stroke(1.2.dp.toPx()))
            drawOval(holo.copy(alpha = 0.22f), o3, s3)
            drawOval(holo.copy(alpha = 0.9f), o3, s3, style = Stroke(1.2.dp.toPx()))
            drawOval(
                Brush.radialGradient(listOf(Color(0xE6EAF4FF), Color.Transparent), center = Offset(cx, cy), radius = 6f * u),
                topLeft = Offset(cx - 6f * u, cy - 1.5f * u), size = Size(12f * u, 3f * u),
            )
        }

        // The phone, once blurred underneath for bloom, once crisp on top.
        val stackModifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = phoneTop)
            .size(phoneW, phoneH)
        PhoneStack(
            stackModifier.blur(7.dp, BlurredEdgeTreatment.Unbounded).graphicsLayer { alpha = 0.75f },
            angle, pitch, holo, batteryFraction, charging,
        )
        PhoneStack(stackModifier, angle, pitch, holo, batteryFraction, charging)

        // Scan line and motes.
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val top = phoneTop.toPx()
            val h = phoneH.toPx()
            val w = phoneW.toPx() * 1.7f
            val p = (loop.value * 1.35f) % 1f
            if (p < 0.92f) {
                val y = top + h * (p / 0.92f)
                val a = sin((p / 0.92f) * PI).toFloat()
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, holo.copy(alpha = 0.45f * a), Color.Transparent),
                        startX = cx - w / 2f, endX = cx + w / 2f,
                    ),
                    topLeft = Offset(cx - w / 2f, y - 1.dp.toPx()),
                    size = Size(w, 2.dp.toPx()),
                )
            }
            val base = projY.toPx()
            val travel = h * 0.75f
            val xs = floatArrayOf(-22f, 14f, -6f, 26f, -30f)
            for (i in xs.indices) {
                val ph = (loop.value + i * 0.21f) % 1f
                val alpha = sin(ph * PI).toFloat() * 0.9f
                drawCircle(
                    Color(0xFFE6F1FC).copy(alpha = alpha),
                    radius = (1f + (i % 3) * 0.35f).dp.toPx(),
                    center = Offset(cx + xs[i].dp.toPx(), base - ph * travel),
                )
            }
        }
    }
}

@Composable
private fun PhoneStack(
    modifier: Modifier,
    angle: () -> Float,
    pitch: () -> Float,
    holo: Color,
    batteryFraction: Float,
    charging: Boolean,
) {
    Box(modifier) {
        Layer.entries.forEach { layer ->
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val a = angle()
                        val rad = a * PI / 180.0
                        val unit = size.height / 152f
                        rotationY = a
                        rotationX = pitch()
                        translationX = (layer.z * unit * sin(rad)).toFloat()
                        translationY = (-layer.z * unit * 0.24f * cos(rad)).toFloat()
                        cameraDistance = 10f * density
                    }
            ) {
                val k = size.height / 152f
                scale(k, k, pivot = Offset.Zero) {
                    drawLayer(layer, holo, batteryFraction, charging)
                }
            }
        }
    }
}

private fun DrawScope.rr(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color, fill: Float, stroke: Float, sw: Float = 1.1f) {
    if (fill > 0f) drawRoundRect(c.copy(alpha = fill), Offset(x, y), Size(w, h), CornerRadius(r))
    if (stroke > 0f) drawRoundRect(c.copy(alpha = stroke), Offset(x, y), Size(w, h), CornerRadius(r), style = Stroke(sw))
}

private fun DrawScope.ring(x: Float, y: Float, r: Float, c: Color, a: Float = 1f) {
    drawCircle(c.copy(alpha = a), radius = r, center = Offset(x, y), style = Stroke(1.1f))
}

private fun DrawScope.drawLayer(layer: Layer, c: Color, battery: Float, charging: Boolean) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(2f, 2f))
    when (layer) {
        Layer.BACK -> {
            rr(0.75f, 0.75f, 74.5f, 150.5f, 13f, c, 0.07f, 0.6f)
            rr(7f, 7f, 24f, 42f, 8f, c, 0.12f, 0.85f)
            ring(19f, 16.5f, 5f, c)
            ring(19f, 28f, 5f, c)
            ring(19f, 39.5f, 5f, c)
            drawCircle(c.copy(alpha = 0.6f), radius = 2f, center = Offset(37f, 13f))
            ring(38f, 100f, 9f, c, 0.3f)
        }
        Layer.BATTERY -> {
            rr(12f, 58f, 52f, 84f, 6f, c, 0.08f, 0.85f)
            rr(30f, 54f, 16f, 4f, 1.5f, c, 0.5f, 0f)
            for (y in listOf(72f, 86f, 100f)) {
                drawLine(c.copy(alpha = 0.22f), Offset(16f, y), Offset(60f, y), strokeWidth = 1f)
            }
            val h = 78f * battery.coerceIn(0.03f, 1f)
            rr(15f, 139f - h, 46f, h, 3.5f, c, 0.42f, 0f)
            if (charging || battery < 0.2f) {
                val bolt = Path().apply {
                    moveTo(40.5f, 111f); lineTo(34f, 123f); lineTo(39f, 123f); lineTo(37f, 132f)
                    lineTo(44.5f, 119.5f); lineTo(39.5f, 119.5f); lineTo(42f, 111f); close()
                }
                drawPath(bolt, Color(0xEBF2F8FF))
            }
        }
        Layer.BOARD -> {
            val board = Path().apply {
                moveTo(36f, 6f); lineTo(69f, 6f); lineTo(69f, 54f); lineTo(7f, 54f); lineTo(7f, 48f); lineTo(36f, 48f); close()
            }
            drawPath(board, c.copy(alpha = 0.1f))
            drawPath(board, c.copy(alpha = 0.8f), style = Stroke(1f))
            rr(44f, 13f, 16f, 16f, 2f, c, 0.45f, 1f)
            rr(48.5f, 17.5f, 7f, 7f, 1f, Color(0xFFEEF6FF), 0.75f, 0f)
            for (y in listOf(17f, 21f, 25f)) {
                drawLine(c.copy(alpha = 0.7f), Offset(41.5f, y), Offset(44f, y), strokeWidth = 1f)
                drawLine(c.copy(alpha = 0.7f), Offset(60f, y), Offset(62.5f, y), strokeWidth = 1f)
            }
            rr(42f, 34f, 20f, 9f, 1.5f, c, 0.28f, 0.7f, 0.8f)
            drawLine(c.copy(alpha = 0.5f), Offset(12f, 52f), Offset(32f, 52f), strokeWidth = 1f, pathEffect = dash)
            drawLine(c.copy(alpha = 0.5f), Offset(64f, 54f), Offset(64f, 138f), strokeWidth = 1f, pathEffect = dash)
            rr(14f, 141f, 48f, 7f, 2f, c, 0.18f, 0.7f, 0.8f)
            rr(31f, 145f, 14f, 3f, 1.5f, c, 0f, 1f, 0.8f)
        }
        Layer.FRAME -> {
            rr(2.5f, 2.5f, 71f, 147f, 11.5f, c, 0f, 0.45f)
            rr(5f, 6f, 66f, 140f, 9f, c, 0.05f, 0.22f, 0.8f)
        }
        Layer.GLASS -> {
            drawRoundRect(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.2f),
                    0.35f to Color.Transparent,
                    0.72f to Color.Transparent,
                    1f to Color.White.copy(alpha = 0.08f),
                    start = Offset.Zero, end = Offset(76f, 152f),
                ),
                topLeft = Offset(0.75f, 0.75f), size = Size(74.5f, 150.5f), cornerRadius = CornerRadius(13f),
            )
            rr(0.75f, 0.75f, 74.5f, 150.5f, 13f, c, 0f, 0.95f, 1.2f)
            ring(38f, 9f, 2.4f, c)
            rr(9f, 18f, 58f, 11f, 3f, c, 0f, 0.35f, 0.8f)
            rr(9f, 33f, 58f, 42f, 3f, c, 0f, 0.35f, 0.8f)
            rr(9f, 79f, 58f, 17f, 3f, c, 0f, 0.35f, 0.8f)
            rr(9f, 100f, 58f, 26f, 3f, c, 0f, 0.35f, 0.8f)
            rr(9f, 131f, 58f, 10f, 5f, c, 0f, 0.35f, 0.8f)
        }
    }
}
