package com.droptuner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droptuner.tuner.IN_TUNE_CENTS
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

const val GAUGE_ASPECT = 1.8f
private const val RANGE_CENTS = 50.0
/** Needle swing to each side of vertical at ±[RANGE_CENTS]. */
private const val SWING_RAD = 65.0 * PI / 180
private val MINOR_TICKS = listOf(1, 2, 3, 4, 15, 30, 40)
private val MAJOR_TICKS = listOf(5, 10, 20, 50)

/**
 * Square-root scale: the last few cents get most of the dial, so ±1 cent is a visible
 * swing while ±50 still fits.
 */
private fun angleOf(cents: Double): Double {
    val c = cents.coerceIn(-RANGE_CENTS, RANGE_CENTS)
    return -PI / 2 + sign(c) * sqrt(abs(c) / RANGE_CENTS) * SWING_RAD
}

/** Needle meter for ±50 cents; [cents] = null parks a grey needle in the middle. */
@Composable
fun TuningGauge(cents: Double?, modifier: Modifier = Modifier) {
    val needle by animateFloatAsState(
        targetValue = (cents ?: 0.0).coerceIn(-RANGE_CENTS, RANGE_CENTS).toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "needle",
    )
    val color by animateColorAsState(TunerColors.forCents(cents), label = "needleColor")
    val labels = rememberTextMeasurer()

    Canvas(modifier.fillMaxWidth().aspectRatio(GAUGE_ASPECT)) {
        val pivot = Offset(size.width / 2, size.height - 10.dp.toPx())
        val radius = min(size.width / 2 / sin(SWING_RAD).toFloat(), pivot.y) * 0.9f

        fun at(c: Double, r: Float): Offset {
            val angle = angleOf(c)
            return pivot + Offset((cos(angle) * r).toFloat(), (sin(angle) * r).toFloat())
        }

        val zone = (angleOf(IN_TUNE_CENTS) + PI / 2) * 180 / PI
        drawArc(
            color = TunerColors.InTune.copy(alpha = 0.18f),
            startAngle = (-90 - zone).toFloat(),
            sweepAngle = (2 * zone).toFloat(),
            useCenter = true,
            topLeft = pivot - Offset(radius, radius),
            size = Size(radius * 2, radius * 2),
        )
        drawLine(TunerColors.InTune, at(0.0, radius * 0.78f), at(0.0, radius), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        for (side in listOf(-1, 1)) {
            for (c in MINOR_TICKS) {
                drawLine(
                    TunerColors.Muted.copy(alpha = 0.45f),
                    at(side * c.toDouble(), radius * 0.9f),
                    at(side * c.toDouble(), radius),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            for (c in MAJOR_TICKS) {
                drawLine(
                    TunerColors.Muted,
                    at(side * c.toDouble(), radius * 0.82f),
                    at(side * c.toDouble(), radius),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                val text = labels.measure(c.toString(), TextStyle(color = TunerColors.Muted, fontSize = 11.sp))
                val anchor = at(side * c.toDouble(), radius * 1.09f)
                drawText(text, topLeft = anchor - Offset(text.size.width / 2f, text.size.height / 2f))
            }
        }
        drawLine(color, pivot, at(needle.toDouble(), radius * 0.95f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color, radius = 8.dp.toPx(), center = pivot)
    }
}
