package dev.holo.launcher.ui.fx

import android.graphics.BlendMode
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import dev.holo.launcher.data.HoloSettings

/**
 * The "rendered" look, applied on the GPU to the whole composite (backdrop + panels):
 *
 *  1. Bloom: bright pixels are isolated, blurred wide and screened back over the image,
 *     so rims, text glow and the hologram bleed light like a game render.
 *  2. Lens: chromatic aberration that grows toward the screen edges.
 *  3. Film grade: gentle S-curve, cool shadows, warm highlights, and a vignette.
 */
class RenderPipeline {
    private val shader = RuntimeShader(POST_AGSL)

    fun build(width: Float, height: Float, density: Float, s: HoloSettings): RenderEffect? {
        if (!s.renderPipeline || s.lowPower || width <= 0f || height <= 0f) return null
        var effect: RenderEffect? = null

        if (s.bloom > 0.01f) {
            val gain = 2.4f
            val threshold = 0.58f
            val off = -threshold * gain * 255f
            val isolate = ColorMatrix(
                floatArrayOf(
                    gain, 0f, 0f, 0f, off,
                    0f, gain, 0f, 0f, off,
                    0f, 0f, gain, 0f, off,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
            val k = 0.35f + s.bloom * 1.4f
            val intensity = ColorMatrix(
                floatArrayOf(
                    k, 0f, 0f, 0f, 0f,
                    0f, k, 0f, 0f, 0f,
                    0f, 0f, k, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
            val radius = (6f + s.bloomRadius * 34f) * density
            val bright = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(isolate))
            val blurred = RenderEffect.createBlurEffect(radius, radius, bright, Shader.TileMode.DECAL)
            val glow = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(intensity), blurred)
            effect = RenderEffect.createBlendModeEffect(
                RenderEffect.createOffsetEffect(0f, 0f),
                glow,
                BlendMode.SCREEN,
            )
        }

        if (s.aberration > 0.01f || s.filmGrade > 0.01f || s.vignette > 0.01f) {
            shader.setFloatUniform("size", width, height)
            shader.setFloatUniform("ca", s.aberration * 3.2f * density)
            shader.setFloatUniform("vignette", s.vignette)
            shader.setFloatUniform("grade", s.filmGrade)
            val post = RenderEffect.createRuntimeShaderEffect(shader, "content")
            val inner = effect
            effect = if (inner != null) RenderEffect.createChainEffect(post, inner) else post
        }
        return effect
    }

    private companion object {
        const val POST_AGSL = """
uniform shader content;
uniform float2 size;
uniform float ca;
uniform float vignette;
uniform float grade;

half4 main(float2 p) {
    float2 d = (p - size * 0.5) / size;
    float r = length(d);
    float2 off = d * ca * 4.0 * r;
    float2 hi = size - 1.0;
    float4 base = content.eval(p);
    float red = content.eval(clamp(p + off, float2(0.0), hi)).r;
    float blue = content.eval(clamp(p - off, float2(0.0), hi)).b;
    float3 c = float3(red, base.g, blue);
    float l = dot(c, float3(0.299, 0.587, 0.114));
    float3 curve = c * c * (3.0 - 2.0 * c);
    c = mix(c, curve, 0.35 * grade);
    c += float3(-0.004, 0.006, 0.022) * grade * (1.0 - l);
    c = mix(c, c * float3(1.035, 1.0, 0.965), grade * smoothstep(0.5, 1.0, l));
    float v = smoothstep(0.32, 0.9, r * 1.15);
    c *= 1.0 - vignette * 0.6 * v;
    return half4(half3(clamp(c, 0.0, 1.0)), half(base.a));
}
"""
    }
}
