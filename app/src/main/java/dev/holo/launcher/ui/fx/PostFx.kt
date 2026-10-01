package dev.holo.launcher.ui.fx

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Random

/**
 * Screen-space finishing pass: occasional soft light sweep, vignette and fine film grain.
 * No scanlines. Purely visual; never intercepts touches.
 */
@Composable
fun PostFxOverlay(strength: Float, animate: Boolean) {
    val noise = remember { noiseBitmap(128) }
    val grain = remember(noise) { ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)) }
    val frame = remember { mutableIntStateOf(0) }
    if (animate) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(90)
                frame.intValue = frame.intValue + 1
            }
        }
    }
    val sweep: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "fx").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
            label = "sweep",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Canvas(Modifier.fillMaxSize()) {
        val s = sweep.value
        if (s < 0.55f) {
            val p = s / 0.55f
            val band = 90.dp.toPx()
            val x = -band * 2f + p * (size.width + band * 4f)
            rotate(16f, pivot = Offset(x, size.height / 2f)) {
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0x0DDCEBFA), Color.Transparent),
                        startX = x - band / 2f, endX = x + band / 2f,
                    ),
                    topLeft = Offset(x - band / 2f, -size.height * 0.2f),
                    size = Size(band, size.height * 1.4f),
                    alpha = strength,
                )
            }
        }
        drawRect(
            Brush.radialGradient(
                0.58f to Color.Transparent,
                1f to Color(0x8C000308),
                center = Offset(size.width / 2f, size.height * 0.48f),
                radius = size.maxDimension * 0.62f,
            ),
            alpha = strength,
        )
        val f = frame.intValue
        val ox = ((f * 37) % 128).toFloat()
        val oy = ((f * 61) % 128).toFloat()
        translate(-ox, -oy) {
            drawRect(
                grain,
                topLeft = Offset.Zero,
                size = Size(size.width + 128f, size.height + 128f),
                alpha = 0.10f * strength,
                blendMode = BlendMode.Overlay,
            )
        }
    }
}

private fun noiseBitmap(n: Int): ImageBitmap {
    val rnd = Random(7)
    val px = IntArray(n * n) {
        val v = rnd.nextInt(256)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}
