package com.droptuner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/** Stripe width of the coarsest band; each band below halves it. */
private const val STRIPE_DP = 48f
private const val BANDS = 4
/** Drift speed per cent of error: 0.1 cent is a slow but visible creep. */
private const val DP_PER_SECOND_PER_CENT = 24f
/** Beyond this the bands would only blur, so they stop speeding up. */
private const val MAX_CENTS = 20.0

/**
 * Strobe tuner: bands drift right when sharp, left when flat, and stand still in tune.
 * Speed is proportional to the error, so it shows fractions of a cent that a needle can't.
 */
@Composable
fun StrobeDisplay(cents: Double?, modifier: Modifier = Modifier) {
    val error by rememberUpdatedState(cents)
    var offsetDp by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val seconds = (now - last) / 1e9f
                last = now
                val c = (error ?: 0.0).coerceIn(-MAX_CENTS, MAX_CENTS).toFloat()
                // Wrap at a multiple of every band's period so the pattern never jumps.
                offsetDp = (offsetDp + c * DP_PER_SECOND_PER_CENT * seconds).mod(STRIPE_DP * 2)
            }
        }
    }
    val color by animateColorAsState(
        if (cents == null) TunerColors.Muted.copy(alpha = 0.35f) else TunerColors.forCents(cents),
        label = "strobeColor",
    )

    Canvas(modifier.fillMaxWidth().aspectRatio(GAUGE_ASPECT)) {
        val gap = 6.dp.toPx()
        val bandHeight = (size.height - gap * (BANDS - 1)) / BANDS
        val corner = CornerRadius(4.dp.toPx())
        for (band in 0 until BANDS) {
            val top = band * (bandHeight + gap)
            drawRoundRect(TunerColors.Surface, Offset(0f, top), Size(size.width, bandHeight), corner)
            val period = (STRIPE_DP / (1 shl band)).dp.toPx()
            val shift = offsetDp.dp.toPx().mod(period)
            var x = shift - period
            while (x < size.width) {
                val left = x.coerceAtLeast(0f)
                val right = (x + period / 2).coerceAtMost(size.width)
                if (right > left) drawRect(color, Offset(left, top), Size(right - left, bandHeight))
                x += period
            }
        }
    }
}
