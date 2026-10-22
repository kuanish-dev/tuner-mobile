package com.droptuner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droptuner.music.Tuning
import com.droptuner.music.Tunings

/** Bottom sheet with every tuning, split into 6/7/8-string tabs and grouped by family. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuningPicker(current: Tuning, custom: Tuning?, onSelect: (Tuning) -> Unit, onDismiss: () -> Unit) {
    var strings by rememberSaveable { mutableIntStateOf(current.strings.size.takeIf { it in Tunings.STRING_COUNTS } ?: 6) }
    val groups = remember(strings, custom) {
        (listOfNotNull(custom) + Tunings.all).filter { it.strings.size == strings }.groupBy { it.family }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TunerColors.Surface,
    ) {
        TuningRow(Tunings.CHROMATIC, selected = current.isChromatic, onClick = { onSelect(Tunings.CHROMATIC) }, subtitle = "Любая нота — для других инструментов")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (count in Tunings.STRING_COUNTS) {
                Pill("$count струн", onClick = { strings = count }, selected = count == strings)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            groups.forEach { (family, tunings) ->
                item(key = "header-${family.name}") { GroupHeader(family.title) }
                items(tunings, key = { it.id }) { tuning ->
                    TuningRow(tuning, selected = tuning.id == current.id, onClick = { onSelect(tuning) })
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        title.uppercase(),
        Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp),
        color = TunerColors.InTune,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun TuningRow(tuning: Tuning, selected: Boolean, onClick: () -> Unit, subtitle: String = tuning.notes) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) TunerColors.SurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tuning.name, color = TunerColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TunerColors.Muted, fontSize = 14.sp)
        }
        if (selected) Text("✓", color = TunerColors.InTune, fontSize = 18.sp)
    }
}
