package com.droptuner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.droptuner.tuner.IN_TUNE_CENTS
import kotlin.math.abs

object TunerColors {
    val Background = Color(0xFF0F1115)
    val Surface = Color(0xFF1A1D23)
    val SurfaceHigh = Color(0xFF272B34)
    val Text = Color(0xFFE8EAED)
    val Muted = Color(0xFF8A9099)
    val InTune = Color(0xFF3DDC84)
    val Close = Color(0xFFFFB020)
    val Off = Color(0xFFFF5A5F)

    private const val CLOSE_CENTS = 8.0

    fun forCents(cents: Double?): Color = when {
        cents == null -> Muted
        abs(cents) <= IN_TUNE_CENTS -> InTune
        abs(cents) <= CLOSE_CENTS -> Close
        else -> Off
    }
}

@Composable
fun DropTunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TunerColors.InTune,
            onPrimary = Color.Black,
            background = TunerColors.Background,
            onBackground = TunerColors.Text,
            surface = TunerColors.Surface,
            onSurface = TunerColors.Text,
            onSurfaceVariant = TunerColors.Muted,
        ),
        content = content,
    )
}
