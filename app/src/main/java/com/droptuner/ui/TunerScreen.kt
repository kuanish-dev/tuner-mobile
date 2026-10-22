package com.droptuner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droptuner.music.Note
import com.droptuner.tuner.IN_TUNE_CENTS
import com.droptuner.tuner.TunerActions
import com.droptuner.tuner.TunerReading
import com.droptuner.tuner.TunerUiState
import kotlin.math.abs
import kotlin.math.roundToInt

/** Microphone permission status plus the ways to fix it. */
class MicAccess(val granted: Boolean, val request: () -> Unit, val openSettings: () -> Unit)

@Composable
fun TunerScreen(state: TunerUiState, actions: TunerActions, mic: MicAccess) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showA4 by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(state, onPickTuning = { showPicker = true }, onA4 = { showA4 = true }, onEdit = actions::toggleEditing)
        Spacer(Modifier.weight(1f))
        when {
            !mic.granted -> MicPrompt(mic)
            state.error != null -> Text(state.error, color = TunerColors.Off, fontSize = 17.sp, textAlign = TextAlign.Center)
            else -> {
                Pill(
                    if (state.strobe) "Стрелка" else "Строб",
                    onClick = actions::toggleStrobe,
                    modifier = Modifier.align(Alignment.End),
                )
                Spacer(Modifier.height(8.dp))
                if (state.strobe) StrobeDisplay(state.reading?.cents) else TuningGauge(state.reading?.cents)
                Spacer(Modifier.height(8.dp))
                Readout(state)
            }
        }
        Spacer(Modifier.weight(1f))
        if (!state.tuning.isChromatic) {
            StringRow(state, actions)
            Spacer(Modifier.height(12.dp))
            ModeLine(state, onAuto = { state.locked?.let(actions::toggleLock) })
        }
    }

    if (showPicker) {
        TuningPicker(
            current = state.tuning,
            custom = state.custom,
            onSelect = {
                actions.selectTuning(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
    if (showA4) A4Dialog(state.a4, onChange = actions::setA4, onDismiss = { showA4 = false })
}

@Composable
private fun TopBar(state: TunerUiState, onPickTuning: () -> Unit, onA4: () -> Unit, onEdit: () -> Unit) {
    val tuning = state.tuning
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onPickTuning)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                "${tuning.name} ▾",
                color = TunerColors.Text,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (tuning.isChromatic) "Любая нота" else "${tuning.strings.size} струн · ${tuning.notes}",
                color = TunerColors.Muted,
                fontSize = 14.sp,
                maxLines = 1,
            )
        }
        Pill("A4 ${state.a4}", onClick = onA4)
        if (!tuning.isChromatic) {
            Spacer(Modifier.width(8.dp))
            Pill(if (state.editing) "Готово" else "✎ Свой", onClick = onEdit, selected = state.editing)
        }
    }
}

@Composable
private fun Readout(state: TunerUiState) {
    val reading = state.reading
    val note = reading?.target ?: state.locked?.let { state.tuning.strings.getOrNull(it) }
    val color = if (reading == null) TunerColors.Muted else TunerColors.forCents(reading.cents)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(note?.name ?: "—", color = color, fontSize = 96.sp, fontWeight = FontWeight.Bold, lineHeight = 96.sp)
        Text(
            note?.octave?.toString().orEmpty(),
            Modifier.padding(start = 4.dp, bottom = 16.dp),
            color = TunerColors.Muted,
            fontSize = 28.sp,
        )
    }
    Text(reading?.let { formatCents(it.cents) } ?: " ", color = color, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Text(direction(state), color = TunerColors.Text, fontSize = 18.sp)
    Text(details(reading), color = TunerColors.Muted, fontSize = 14.sp)
}

/** Tenths of a cent near the target, whole cents further out where tenths are noise. */
private fun formatCents(cents: Double): String {
    val sign = if (cents < 0) "−" else "+"
    return when {
        abs(cents) < 0.05 -> "0.0 ¢"
        abs(cents) < 10 -> "$sign${"%.1f".format(abs(cents))} ¢"
        else -> "$sign${abs(cents).roundToInt()} ¢"
    }
}

private fun direction(state: TunerUiState): String {
    val reading = state.reading
    return when {
        state.playing != null -> "♪ Звучит ${state.tuning.strings[state.playing].label}"
        reading == null -> if (state.tuning.isChromatic) "Сыграй любую ноту" else "Сыграй открытую струну"
        abs(reading.cents) <= IN_TUNE_CENTS -> "✓ Строит"
        reading.cents < 0 -> "▲ Подтяни"
        else -> "▼ Ослабь"
    }
}

private fun details(reading: TunerReading?): String {
    if (reading == null) return " "
    val hz = "%.2f Гц".format(reading.frequency)
    return if (reading.heard == reading.target) hz else "$hz · слышу ${reading.heard.label}"
}

@Composable
private fun MicPrompt(mic: MicAccess) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Нужен доступ к микрофону —\nтюнер слушает струну через него.",
            color = TunerColors.Text,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
        )
        Pill("Разрешить", onClick = mic.request, selected = true)
        Pill("Открыть настройки", onClick = mic.openSettings)
    }
}

@Composable
private fun StringRow(state: TunerUiState, actions: TunerActions) {
    val strings = state.tuning.strings
    val accent = TunerColors.forCents(state.reading?.cents)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        strings.forEachIndexed { i, note ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.editing) ShiftButton("▲") { actions.shiftString(i, 1) }
                StringChip(
                    note = note,
                    number = strings.size - i,
                    compact = strings.size > 6,
                    active = state.reading?.stringIndex == i,
                    locked = state.locked == i,
                    tuned = i in state.tuned,
                    playing = state.playing == i,
                    accent = accent,
                    onClick = { actions.toggleLock(i) },
                    onLongClick = { actions.playReference(i) },
                )
                if (state.editing) ShiftButton("▼") { actions.shiftString(i, -1) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StringChip(
    note: Note,
    number: Int,
    compact: Boolean,
    active: Boolean,
    locked: Boolean,
    tuned: Boolean,
    playing: Boolean,
    accent: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    val borderColor = when {
        playing -> TunerColors.Close
        active -> accent
        locked -> TunerColors.Text
        else -> Color.Transparent
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(shape)
            .background(if (locked) TunerColors.SurfaceHigh else TunerColors.Surface)
            .border(2.dp, borderColor, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                note.name,
                color = if (tuned) TunerColors.InTune else TunerColors.Text,
                fontSize = if (compact) 17.sp else 21.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(note.label, color = TunerColors.Muted, fontSize = 11.sp)
        }
        Text("$number", Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 4.dp), color = TunerColors.Muted, fontSize = 10.sp)
        val badge = when {
            playing -> "♪" to TunerColors.Close
            tuned -> "✓" to TunerColors.InTune
            else -> null
        }
        if (badge != null) {
            Text(badge.first, Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 3.dp), color = badge.second, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ShiftButton(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(TunerColors.SurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = TunerColors.Text, fontSize = 14.sp)
    }
}

@Composable
private fun ModeLine(state: TunerUiState, onAuto: () -> Unit) {
    val hint = when {
        state.editing -> "Меняй ноты стрелками ▲▼ — строй сохранится как «Свой строй»"
        state.locked == null -> "Струна определяется сама. Нажми струну — зафиксировать, удержи — услышать ноту."
        else -> "Струна ${state.tuning.strings.size - state.locked} зафиксирована. Удержи струну — услышать ноту."
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Pill("Авто", onClick = onAuto, selected = state.locked == null)
        Spacer(Modifier.width(12.dp))
        Text(hint, Modifier.weight(1f), color = TunerColors.Muted, fontSize = 13.sp)
    }
}
