package com.droptuner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droptuner.music.Note

@Composable
fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    Text(
        text,
        modifier
            .clip(CircleShape)
            .background(if (selected) TunerColors.InTune else TunerColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (selected) Color.Black else TunerColors.Text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun A4Dialog(a4: Int, onChange: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TunerColors.Surface,
        titleContentColor = TunerColors.Text,
        title = { Text("Эталон A4") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Pill("−", onClick = { onChange(a4 - 1) })
                Text(
                    "$a4 Гц",
                    Modifier.padding(horizontal = 20.dp),
                    color = TunerColors.Text,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Pill("+", onClick = { onChange(a4 + 1) })
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово", color = TunerColors.InTune) }
        },
        dismissButton = {
            TextButton(onClick = { onChange(Note.A4_HZ.toInt()) }) { Text("Сбросить на 440", color = TunerColors.Muted) }
        },
    )
}
