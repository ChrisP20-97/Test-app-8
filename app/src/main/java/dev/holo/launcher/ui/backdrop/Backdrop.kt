package dev.holo.launcher.ui.backdrop

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.util.Size
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Everything behind the panels. With [useCamera], the live back-camera feed is blurred and
 * colour-graded on the GPU so the UI reads as a hologram floating in front of the real world.
 * Otherwise a painted, out-of-focus interior is shown. Both shift slightly against the tilt.
 */
@Composable
fun Backdrop(
    useCamera: Boolean,
    blur: Float,
    grade: Float,
    tint: Color,
    tilt: State<Offset>,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val t = tilt.value
                scaleX = 1.08f
                scaleY = 1.08f
                translationX = -t.x * 6.dp.toPx()
                translationY = t.y * 6.dp.toPx()
            }
    ) {
        StaticBackdrop(Modifier.fillMaxSize())
        if (useCamera) CameraBackdrop(Modifier.fillMaxSize(), blur, grade, tint)
    }
    // Keeps the real status bar readable over bright scenes.
    Box(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(Brush.verticalGradient(listOf(Color(0xB8030508), Color.Transparent)))
    )
}

private const val GRADE_AGSL = """
uniform shader content;
uniform float2 size;
uniform float strength;
uniform float3 tint;

half4 main(float2 p) {
    float4 c = content.eval(p);
    float l = dot(c.rgb, float3(0.299, 0.587, 0.114));
    float3 g = mix(c.rgb, float3(l), 0.7 * strength);
    g = mix(g, l * tint * 1.15, 0.45 * strength);
    g = mix(g, float3(0.035, 0.045, 0.06), 0.35 * strength);
    float2 uv = p / size - 0.5;
    float v = 1.0 - smoothstep(0.35, 0.95, length(uv * float2(1.1, 0.9)));
    g *= mix(1.0, 0.45 + 0.55 * v, strength);
    return half4(half3(g), 1.0);
}
"""

private class GradeEffect {
    private val shader = RuntimeShader(GRADE_AGSL)
    var blur = 0.45f
    var grade = 0.8f
    var tint = Color.White

    fun apply(view: View) {
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return
        shader.setFloatUniform("size", w.toFloat(), h.toFloat())
        shader.setFloatUniform("strength", grade)
        shader.setFloatUniform("tint", tint.red, tint.green, tint.blue)
        val r = 1f + blur * 90f
        view.setRenderEffect(
            RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(shader, "content"),
                RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP),
            )
        )
    }
}

@Composable
private fun CameraBackdrop(modifier: Modifier, blur: Float, grade: Float, tint: Color) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val effect = remember { GradeEffect() }
    val previewView = remember {
        PreviewView(context).apply {
            // TextureView-backed, so RenderEffect (blur + shader) applies to the camera image.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> effect.apply(v) }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            runCatching {
                val p = future.get()
                // The image is heavily blurred, so a small stream is plenty and saves battery.
                val selector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(960, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                    )
                    .build()
                val preview = Preview.Builder().setResolutionSelector(selector).build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                provider = p
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { provider?.unbindAll() } }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
        update = { view ->
            effect.blur = blur
            effect.grade = grade
            effect.tint = tint
            effect.apply(view)
        },
    )
}

/** Painted, out-of-focus interior used when the camera is off. */
@Composable
fun StaticBackdrop(modifier: Modifier) {
    Canvas(modifier.blur(14.dp, BlurredEdgeTreatment.Rectangle)) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF141A22), Color(0xFF0B1016), Color(0xFF07090D))))
        drawRect(Brush.radialGradient(listOf(Color(0x6B68869C), Color.Transparent), center = Offset(w * 0.5f, h * 0.34f), radius = w * 0.75f))
        drawRect(Brush.radialGradient(listOf(Color(0x614E627C), Color.Transparent), center = Offset(w * 0.5f, h * 0.64f), radius = w * 0.8f))
        drawRoundRect(Color(0xBFD8E6F6), Offset(w * 0.2f, h * 0.03f), androidx.compose.ui.geometry.Size(w * 0.54f, 8.dp.toPx()), CornerRadius(4.dp.toPx()))
        drawRoundRect(Color(0x6BBED0E6), Offset(w * 0.78f, h * 0.055f), androidx.compose.ui.geometry.Size(w * 0.28f, 5.dp.toPx()), CornerRadius(3.dp.toPx()))
        drawRect(
            Brush.horizontalGradient(listOf(Color(0xFF0C1117), Color(0xFF19222C), Color(0xFF2A3542), Color(0xFF10161D)), startX = 0f, endX = w * 0.22f),
            topLeft = Offset(0f, h * 0.12f), size = androidx.compose.ui.geometry.Size(w * 0.22f, h),
        )
        drawRect(Color(0x6B96B8DC), topLeft = Offset(w * 0.22f, h * 0.16f), size = androidx.compose.ui.geometry.Size(4.dp.toPx(), h * 0.72f))
        drawRect(
            Brush.horizontalGradient(listOf(Color(0xFF0F141A), Color(0xFF26313D), Color(0xFF18212B), Color(0xFF0C1117)), startX = w * 0.8f, endX = w),
            topLeft = Offset(w * 0.8f, h * 0.1f), size = androidx.compose.ui.geometry.Size(w * 0.2f, h),
        )
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0x4D7A92B0), Color(0x24465A74)), startY = h * 0.27f, endY = h * 0.7f),
            topLeft = Offset(w * 0.38f, h * 0.27f), size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.43f),
            cornerRadius = CornerRadius(10.dp.toPx()),
        )
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color(0x80324052)), startY = h * 0.75f, endY = h),
            topLeft = Offset(0f, h * 0.75f), size = androidx.compose.ui.geometry.Size(w, h * 0.25f),
        )
        drawCircle(Color(0xFFE3A25E), 7.dp.toPx(), Offset(w * 0.16f, h * 0.77f))
        drawCircle(Color(0xFFD8605E), 5.dp.toPx(), Offset(w * 0.94f, h * 0.38f))
        drawCircle(Color(0xFF63C39C), 5.dp.toPx(), Offset(w * 0.94f, h * 0.405f))
    }
}
