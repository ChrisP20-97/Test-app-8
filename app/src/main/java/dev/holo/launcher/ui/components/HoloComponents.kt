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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
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
import dev.holo.launcher.ui.theme.HoloType
import dev.holo.launcher.ui.theme.LocalHolo
import kotlinx.coroutines.delay

/** Soft light bleeding out around a rounded shape. Draw it before clipping. */
fun Modifier.holoGlow(radius: Dp, color: Color = HoloColors.Glow, blur: Dp = 14.dp): Modifier = drawBehind {
    val paint = Paint()
    val fp = paint.asFrameworkPaint()
    fp.isAntiAlias = true
    fp.color = android.graphics.Color.TRANSPARENT
    fp.setShadowLayer(blur.toPx(), 0f, 0f, color.toArgb())
    val r = radius.toPx()
    drawIntoCanvas { it.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint) }
}

/** The main glass panel: luminous gradient rim, dark translucent fill, sheen and a bottom light catch. */
@Composable
fun HoloCard(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    padding: Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalHolo.current.glass
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .holoGlow(radius)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF1F2936).copy(alpha = glass),
                    0.55f to Color(0xFF121922).copy(alpha = glass),
                    1f to Color(0xFF0D121A).copy(alpha = glass),
                )
            )
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.045f),
                        0.3f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to Color.White.copy(alpha = 0.02f),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                )
                val w = size.width
                val y = size.height - 1.dp.toPx()
                drawLine(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0x99D6E8FA), Color.Transparent),
                        startX = w * 0.22f, endX = w * 0.78f,
                    ),
                    Offset(w * 0.22f, y), Offset(w * 0.78f, y),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
            .border(
                1.5.dp,
                Brush.verticalGradient(
                    0f to Color(0x94C6DAF0),
                    0.18f to Color(0x4D829CBC),
                    0.74f to Color(0x3868809E),
                    1f to Color(0x6BA4BEDC),
                ),
                shape,
            )
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** Dark capsule header with icon, spaced caps title and optional trailing controls. */
@Composable
fun HeaderPill(
    title: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xDB090D13), Color(0xDB070A0F))))
            .border(1.dp, Color(0x4296B2D4), shape)
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
                .background(Brush.radialGradient(listOf(Color(0xF2425268), Color(0xF21C2532))))
                .border(1.2.dp, Color(0x80B2CAE6), CircleShape),
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
    val shape = RoundedCornerShape(radius)
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
fun Modifier.dotGrid(color: Color = Color(0x26B0C8E4), spacing: Dp = 13.dp, radius: Dp = 0.8.dp): Modifier =
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
        onDrawBehind { drawPath(path, color) }
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
            drawCircle(
                HoloColors.Track, radius = d / 2f, center = center, style = Stroke(sw),
            )
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
fun PillButton(text: String, color: Color = LocalHolo.current.holo, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
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
