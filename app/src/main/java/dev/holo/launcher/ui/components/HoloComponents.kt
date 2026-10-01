package dev.holo.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.holo.launcher.ui.theme.HoloColors
import dev.holo.launcher.ui.theme.HoloMetrics
import dev.holo.launcher.ui.theme.HoloType
import dev.holo.launcher.ui.theme.LocalTilt
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Card / tile outline following the user's corner style and radius. */
fun holoShape(radius: Dp): Shape =
    if (HoloMetrics.cut) CutCornerShape(radius * 0.6f) else RoundedCornerShape(radius)

/** Inset radius scaled with the user's corner-radius setting. */
fun scaledRadius(base: Dp): Dp = base * (HoloMetrics.radius / 16f)

/** Tilt normalised to -1..1 against the configured maximum. */
fun normTilt(t: Offset): Offset {
    val m = HoloMetrics.maxTilt.coerceAtLeast(1f)
    return Offset((t.x / m).coerceIn(-1f, 1f), (t.y / m).coerceIn(-1f, 1f))
}

/** Cast shadow plus soft rim glow, drawn only outside the shape so the glass stays clean. */
fun Modifier.cardLighting(shape: Shape, tilt: State<Offset>): Modifier = drawBehind {
    val glow = HoloMetrics.glow
    val shadow = HoloMetrics.shadow
    if (glow <= 0.01f && shadow <= 0.01f) return@drawBehind
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val n = if (HoloMetrics.dynamicLight) normTilt(tilt.value) else Offset.Zero
    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.clipPath(path, ClipOp.Difference)
        if (shadow > 0.01f) {
            val p = Paint()
            val fp = p.asFrameworkPaint()
            fp.isAntiAlias = true
            fp.color = android.graphics.Color.TRANSPARENT
            fp.setShadowLayer(
                22.dp.toPx(),
                -n.x * 10.dp.toPx(),
                8.dp.toPx() + n.y * 8.dp.toPx(),
                Color.Black.copy(alpha = 0.6f * shadow).toArgb(),
            )
            canvas.drawPath(path, p)
        }
        if (glow > 0.01f) {
            val p = Paint()
            val fp = p.asFrameworkPaint()
            fp.isAntiAlias = true
            fp.color = android.graphics.Color.TRANSPARENT
            fp.setShadowLayer((8f + 14f * glow).dp.toPx(), 0f, 0f, HoloColors.Glow.toArgb())
            canvas.drawPath(path, p)
        }
        canvas.restore()
    }
}

/** Kept for callers that want a glow without the full card treatment. */
fun Modifier.holoGlow(radius: Dp, color: Color = HoloColors.Glow, blur: Dp = 14.dp): Modifier = drawBehind {
    val paint = Paint()
    val fp = paint.asFrameworkPaint()
    fp.isAntiAlias = true
    fp.color = android.graphics.Color.TRANSPARENT
    fp.setShadowLayer(blur.toPx(), 0f, 0f, color.toArgb())
    val r = radius.toPx()
    drawIntoCanvas { it.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint) }
}

/**
 * The main glass panel. Sits at [depth] (0 = far, 1 = near) for parallax, catches light that
 * moves with the tilt, casts a soft shadow and glows at the rim.
 */
@Composable
fun HoloCard(
    modifier: Modifier = Modifier,
    radius: Dp? = null,
    padding: Dp = 6.dp,
    depth: Float = 0.5f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tilt = LocalTilt.current
    val r = radius ?: HoloMetrics.radius.dp
    val shape = holoShape(r)
    Column(
        modifier
            .graphicsLayer {
                val t = tilt.value
                val k = HoloMetrics.depth * depth * 1.6.dp.toPx()
                translationX = t.x * k
                translationY = -t.y * k
            }
            .cardLighting(shape, tilt)
            .clip(shape)
            .drawWithContent {
                val g = HoloMetrics.glass
                drawRect(
                    Brush.verticalGradient(
                        0f to HoloColors.PanelTop.copy(alpha = g),
                        0.55f to HoloColors.PanelMid.copy(alpha = g),
                        1f to HoloColors.PanelBottom.copy(alpha = g),
                    )
                )
                drawContent()
                val n = if (HoloMetrics.dynamicLight) normTilt(tilt.value) else Offset.Zero
                val w = size.width
                val h = size.height
                drawRect(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.045f + 0.05f * abs(n.x)),
                        0.3f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to Color.White.copy(alpha = 0.02f + 0.03f * abs(n.y)),
                        start = Offset(w * (-0.1f + n.x * 0.5f), h * (n.y * 0.4f)),
                        end = Offset(w * (0.9f + n.x * 0.5f), h * (1f + n.y * 0.4f)),
                    )
                )
                val bw = HoloMetrics.borderWidth.dp.toPx()
                if (bw > 0.05f) {
                    val b = HoloColors.Border
                    val br = HoloMetrics.borderBrightness
                    drawOutline(
                        shape.createOutline(size, layoutDirection, this),
                        Brush.linearGradient(
                            0f to b.copy(alpha = (0.95f * br).coerceIn(0f, 1f)),
                            0.2f to b.copy(alpha = (0.5f * br).coerceIn(0f, 1f)),
                            0.74f to b.copy(alpha = (0.36f * br).coerceIn(0f, 1f)),
                            1f to b.copy(alpha = (0.7f * br).coerceIn(0f, 1f)),
                            start = Offset(w * (0.5f - n.x * 0.7f), 0f),
                            end = Offset(w * (0.5f + n.x * 0.7f), h),
                        ),
                        style = Stroke(bw * 2f),
                    )
                }
                val y = h - 1.dp.toPx()
                val cx = w * (0.5f + n.x * 0.2f)
                drawLine(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, HoloColors.Border.copy(alpha = (0.6f * HoloMetrics.borderBrightness).coerceIn(0f, 1f)), Color.Transparent),
                        startX = cx - w * 0.28f, endX = cx + w * 0.28f,
                    ),
                    Offset(cx - w * 0.28f, y), Offset(cx + w * 0.28f, y),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** Header row: a dark capsule (or a plain titled rule) with icon, title and trailing controls. */
@Composable
fun HeaderPill(
    title: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val shape = holoShape(scaledRadius(11.dp))
    val capsule = HoloMetrics.headerCapsules
    val chrome = if (capsule) {
        Modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(HoloColors.HeaderFill.copy(alpha = 0.86f), lerp(HoloColors.HeaderFill, Color.Black, 0.2f).copy(alpha = 0.86f))
                )
            )
            .border(1.dp, HoloColors.Border.copy(alpha = 0.26f), shape)
    } else {
        Modifier.drawBehind {
            drawLine(
                HoloColors.Border.copy(alpha = 0.22f),
                Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .then(chrome)
            .padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = HoloColors.Text, modifier = Modifier.size(15.dp))
        Text(
            title, style = HoloType.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** Round outlined button. The touch area is a bit larger than the drawn circle. */
@Composable
fun CircleButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp = 24.dp,
    iconSize: Dp = 11.dp,
    tint: Color = HoloColors.Text,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size + 8.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            lerp(HoloColors.PanelTop, Color.White, 0.18f).copy(alpha = 0.95f),
                            lerp(HoloColors.PanelMid, Color.Black, 0.1f).copy(alpha = 0.95f),
                        )
                    )
                )
                .border(1.2.dp, HoloColors.Border.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

/** Lighter inset tile used inside cards. */
@Composable
fun SubPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 10.dp,
    fill: Color = HoloColors.SubFill,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = holoShape(scaledRadius(radius))
    Box(
        modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, HoloColors.SubBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** Faint dot grid, like the hologram bays in the reference UI. */
fun Modifier.dotGrid(spacing: Dp = 13.dp, radius: Dp = 0.8.dp): Modifier =
    drawWithCache {
        val sp = spacing.toPx()
        val r = radius.toPx()
        val path = Path()
        var y = sp / 2f
        while (y < size.height) {
            var x = sp / 2f
            while (x < size.width) {
                path.addOval(Rect(Offset(x, y), r))
                x += sp
            }
            y += sp
        }
        onDrawBehind { drawPath(path, HoloColors.Border.copy(alpha = 0.15f)) }
    }

/** Arc with a soft glow, drawn inside the current bounds. Angles in degrees, 0 = 3 o'clock. */
fun DrawScope.glowArc(
    color: Color,
    startAngle: Float,
    sweep: Float,
    stroke: Float,
    inset: Float,
    glow: Float,
    cap: StrokeCap = StrokeCap.Round,
) {
    val d = size.minDimension - inset * 2f
    val left = (size.width - d) / 2f
    val top = (size.height - d) / 2f
    val paint = Paint()
    paint.color = color
    paint.style = PaintingStyle.Stroke
    paint.strokeWidth = stroke
    paint.strokeCap = cap
    paint.isAntiAlias = true
    if (glow > 0f) paint.asFrameworkPaint().setShadowLayer(glow, 0f, 0f, color.copy(alpha = 0.7f).toArgb())
    drawIntoCanvas { it.drawArc(left, top, left + d, top + d, startAngle, sweep, false, paint) }
}

/** Ring gauge with a glowing value arc and anything in the middle. */
@Composable
fun RingGauge(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 2.6.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier.drawBehind {
            val sw = stroke.toPx()
            val inset = sw / 2f + 1.dp.toPx()
            val d = size.minDimension - inset * 2f
            drawCircle(HoloColors.Track, radius = d / 2f, center = center, style = Stroke(sw))
            if (fraction > 0.005f) {
                glowArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), sw, inset, 3.dp.toPx())
            }
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Small caps label with the vertical tick used in the reference panels. */
@Composable
fun TickLabel(text: String, color: Color = HoloColors.TextMid) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.width(2.dp).height(9.dp).background(color))
        Text(text, style = HoloType.label.copy(color = color), maxLines = 1)
    }
}

@Composable
fun ThinBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.height(3.dp)) {
        val r = size.height / 2f
        drawRoundRect(HoloColors.Track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w > 0f) {
            drawRoundRect(
                color,
                size = androidx.compose.ui.geometry.Size(w, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
            )
        }
    }
}

/** Little red count badge. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(HoloColors.BadgeRed)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            style = HoloType.value.copy(fontSize = 9.5.sp, letterSpacing = 0.em, color = Color.White),
        )
    }
}

/** Compact text button, e.g. GRANT / SET. */
@Composable
fun PillButton(text: String, color: Color = HoloColors.Holo, onClick: () -> Unit) {
    val shape = holoShape(scaledRadius(8.dp))
    Box(
        Modifier
            .height(28.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.6f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HoloType.tab.copy(color = color, fontSize = 10.5.sp))
    }
}

/** Panels materialise: fade, rise and a brief flicker. Replays whenever [trigger] changes. */
@Composable
fun Modifier.bootIn(index: Int, trigger: Int, enabled: Boolean = true): Modifier {
    val anim = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(trigger, enabled) {
        if (!enabled) {
            anim.snapTo(1f)
            return@LaunchedEffect
        }
        anim.snapTo(0f)
        delay(index * 60L)
        anim.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        val v = anim.value
        val flicker = if (v in 0.55f..0.68f) 0.8f else 1f
        alpha = (v * 1.7f).coerceAtMost(1f) * flicker
        translationY = (1f - v) * 10.dp.toPx()
        scaleX = 0.985f + 0.015f * v
        scaleY = 0.985f + 0.015f * v
    }
}

/** Centered message used for empty or offline states. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    SubPanel(modifier) {
        Column(
            Modifier.align(Alignment.Center).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = HoloType.title.copy(fontSize = 11.sp))
            Text(message, style = HoloType.body.copy(color = HoloColors.Hint, fontSize = 11.5.sp))
            if (action != null) PillButton(action, onClick = onAction)
        }
    }
}
