package com.nova.automate

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Motion and drawing pieces shared across the screens. Everything in this file
 * is presentation only — nothing here reads or writes app state.
 */

/** Scale factor for a card that dips slightly while it is held. */
@Composable
fun rememberPressScale(interactionSource: InteractionSource, pressedScale: Float = 0.975f): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "pressScale"
    )
    return scale
}

/**
 * Fades and lifts [content] into place once, [delayMillis] after it first appears.
 * Staggering a column of these makes a screen assemble instead of snapping in.
 */
@Composable
fun Reveal(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "reveal"
    )
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 20.dp.toPx()
        }
    ) {
        content()
    }
}

/**
 * A number that counts up to [value] instead of appearing at it. [format] turns
 * the running number into the string on screen, so this works for rupiah too.
 */
@Composable
fun AnimatedCount(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    color: Color = Color.Unspecified,
    format: (Int) -> String = { it.toString() }
) {
    var target by remember { mutableStateOf(0) }
    LaunchedEffect(value) { target = value }
    val shown by animateIntAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "count"
    )
    Text(
        text = format(shown),
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** One column of [NovaBarChart]. */
data class ChartBar(
    val label: String,
    val value: Long,
    val highlighted: Boolean = false
)

/**
 * A compact bar chart with no axes — just proportional columns and their labels.
 * Bars grow up from the baseline the first time the chart is laid out. Zero
 * values stay as flat stubs rather than disappearing, so the row of labels
 * always lines up with something.
 */
@Composable
fun NovaBarChart(
    bars: List<ChartBar>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.accents.chartBar,
    trackColor: Color = MaterialTheme.accents.chartTrack,
    height: Dp = 84.dp
) {
    if (bars.isEmpty()) return

    var grown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { grown = true }
    val grow by animateFloatAsState(
        targetValue = if (grown) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "barGrow"
    )

    val max = bars.maxOf { it.value }.coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val slot = size.width / bars.size
            // Gap either side of every bar, but never let one collapse to nothing.
            val barWidth = (slot * 0.54f).coerceIn(4f, 28.dp.toPx())
            val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
            val stub = barWidth.coerceAtMost(size.height)

            bars.forEachIndexed { index, bar ->
                val centerX = slot * index + slot / 2f
                val left = centerX - barWidth / 2f
                val full = size.height * (bar.value.toFloat() / max.toFloat())
                val target = if (bar.value == 0L) stub else full.coerceAtLeast(stub)
                val drawn = target * grow

                // Track behind each bar keeps the row legible when values are lopsided.
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = radius
                )
                drawRoundRect(
                    color = if (bar.value == 0L) trackColor else barColor,
                    topLeft = Offset(left, size.height - drawn),
                    size = Size(barWidth, drawn),
                    cornerRadius = radius,
                    alpha = if (bar.highlighted || bar.value == 0L) 1f else 0.55f
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            bars.forEach { bar ->
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bar.highlighted) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Circular gauge for a "how much of this is done" fraction, with [content]
 * centred inside it. [progress] is clamped to 0..1 and animates on change.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    strokeWidth: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "ring"
    )
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
            if (animated > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke
                )
            }
        }
        content()
    }
}

/**
 * A status dot that breathes while [active], so "the service is running" reads
 * at a glance without having to parse the label beside it.
 */
@Composable
fun PulsingDot(
    active: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.secondary,
    idleColor: Color = MaterialTheme.colorScheme.error,
    dotSize: Dp = 8.dp
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val halo by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "halo"
    )
    val color = if (active) activeColor else idleColor

    Box(
        modifier = modifier.size(dotSize * 2.5f),
        contentAlignment = Alignment.Center
    ) {
        if (active) {
            Canvas(modifier = Modifier.size(dotSize * 2.5f)) {
                val maxRadius = this.size.minDimension / 2f
                val minRadius = dotSize.toPx() / 2f
                drawCircle(
                    color = color,
                    radius = minRadius + (maxRadius - minRadius) * halo,
                    alpha = (1f - halo) * 0.35f
                )
            }
        }
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(RoundedCornerShape(percent = 50))
                .background(color)
        )
    }
}

/** Caption above a value, sized for two or three sitting side by side. */
@Composable
fun StatTile(
    label: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    value: @Composable () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            value()
        }
    }
}
