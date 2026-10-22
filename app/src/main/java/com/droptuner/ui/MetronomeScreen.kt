package com.droptuner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droptuner.metronome.MetronomeActions
import com.droptuner.metronome.MetronomeUiState
import com.droptuner.metronome.MetronomeViewModel
import kotlin.math.roundToInt

@Composable
fun MetronomeScreen(state: MetronomeUiState, actions: MetronomeActions) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Метроном",
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            color = TunerColors.Text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        BeatDots(state.beatsPerBar, state.beat)
        Spacer(Modifier.height(20.dp))
        Text("${state.bpm}", color = TunerColors.Text, fontSize = 96.sp, fontWeight = FontWeight.Bold, lineHeight = 96.sp)
        Text("${tempoName(state.bpm)} · ударов в минуту", color = TunerColors.Muted, fontSize = 15.sp)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (step in listOf(-5, -1, 1, 5)) {
                Pill(if (step < 0) "−${-step}" else "+$step", onClick = { actions.setBpm(state.bpm + step) })
            }
        }
        Slider(
            value = state.bpm.toFloat(),
            onValueChange = { actions.setBpm(it.roundToInt()) },
            valueRange = MetronomeViewModel.MIN_BPM.toFloat()..MetronomeViewModel.MAX_BPM.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = TunerColors.InTune,
                activeTrackColor = TunerColors.InTune,
                inactiveTrackColor = TunerColors.SurfaceHigh,
            ),
        )
        Spacer(Modifier.height(16.dp))
        Text("Долей в такте", color = TunerColors.Muted, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (beats in MetronomeViewModel.BEAT_CHOICES) {
                Pill("$beats", onClick = { actions.setBeatsPerBar(beats) }, selected = beats == state.beatsPerBar)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Тап", Modifier.weight(1f), TunerColors.SurfaceHigh, TunerColors.Text, actions::tap)
            BigButton(
                if (state.playing) "■ Стоп" else "▶ Старт",
                Modifier.weight(1f),
                if (state.playing) TunerColors.Off else TunerColors.InTune,
                Color.Black,
                actions::toggle,
            )
        }
    }
}

/** One dot per beat; the one being heard lights up, the bar's first beat in green. */
@Composable
private fun BeatDots(beats: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until beats) {
            val first = i == 0 && beats > 1
            val lit = i == current
            Box(
                Modifier
                    .size(if (first) 30.dp else 24.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            lit && first -> TunerColors.InTune
                            lit -> TunerColors.Text
                            else -> TunerColors.SurfaceHigh
                        },
                    )
                    .border(2.dp, if (first) TunerColors.InTune else Color.Transparent, CircleShape),
            )
        }
    }
}

@Composable
private fun BigButton(text: String, modifier: Modifier, background: Color, content: Color, onClick: () -> Unit) {
    Box(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = content, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun tempoName(bpm: Int): String = when {
    bpm < 40 -> "Grave"
    bpm < 60 -> "Largo"
    bpm < 66 -> "Larghetto"
    bpm < 76 -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 168 -> "Allegro"
    bpm < 200 -> "Presto"
    else -> "Prestissimo"
}
