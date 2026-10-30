package com.droptuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droptuner.audio.InputInfo
import com.droptuner.audio.InputState
import com.droptuner.audio.MicSource
import kotlin.math.roundToInt

private const val METER_MIN_DB = -90.0

private const val METER_MAX_DB = -20.0

private fun meterFraction(db: Double) = ((db - METER_MIN_DB) / (METER_MAX_DB - METER_MIN_DB)).coerceIn(0.0, 1.0).toFloat()

@Composable
fun LevelMeter(inputDb: () -> Float, gateDb: () -> Float, modifier: Modifier = Modifier, barWidth: Dp = 140.dp, onClick: (() -> Unit)? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (onClick != null) "Микрофон ›" else "Уровень", color = TunerColors.Muted, fontSize = 12.sp)
        Canvas(Modifier.width(barWidth).height(6.dp)) {
            val corner = CornerRadius(size.height / 2)
            val level = meterFraction(inputDb().toDouble())
            val gate = meterFraction(gateDb().toDouble())
            drawRoundRect(TunerColors.SurfaceHigh, cornerRadius = corner)
            drawRoundRect(
                if (level >= gate) TunerColors.Text else TunerColors.Muted,
                size = Size(size.width * level, size.height),
                cornerRadius = corner,
            )
            val x = size.width * gate
            drawLine(TunerColors.Close, Offset(x, -3.dp.toPx()), Offset(x, size.height + 3.dp.toPx()), strokeWidth = 2.dp.toPx())
        }
        DbLabel(inputDb)
    }
}

@Composable
private fun DbLabel(inputDb: () -> Float) {
    val db = inputDb()
    Text(
        if (db < METER_MIN_DB) "—" else "${db.roundToInt()} дБ".replace('-', '−'),
        Modifier.width(48.dp),
        color = TunerColors.Muted,
        fontSize = 12.sp,
    )
}

fun inputWarning(state: InputState): Pair<String, String>? = when (state) {
    InputState.SILENT -> "✗ Микрофон молчит" to "Нажми «Микрофон» ниже — там причина и выбор входа"
    InputState.TAKEN -> "✗ Микрофон занят" to "Его забрал звонок, диктофон или голосовой ассистент"
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MicSheet(
    info: InputInfo,
    inputDb: () -> Float,
    gateDb: () -> Float,
    sources: List<MicSource>,
    selected: MicSource?,
    onSelect: (MicSource?) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TunerColors.Surface,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Микрофон", color = TunerColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            InputStatus(info)
            LevelMeter(inputDb, gateDb, barWidth = 200.dp)
            Text(
                "Скажи что-нибудь или хлопни в ладоши — полоска должна прыгать. Чтобы тюнер услышал струну, " +
                    "полоска должна заходить за оранжевую отметку: электрогитару без комбика держи в 10–30 см от телефона. " +
                    "Отметка сама отходит вправо от шума комнаты — вентилятора или улицы, — и он до детектора не доходит.",
                color = TunerColors.Muted,
                fontSize = 14.sp,
            )
            if (info.state == InputState.SILENT || info.state == InputState.TAKEN) {
                Text(
                    "Полная тишина бывает, когда:\n" +
                        "• в шторке быстрых настроек выключен доступ к микрофону (Android 12+);\n" +
                        "• приложению запрещён микрофон — на Xiaomi, Redmi и POCO проверь ещё разрешения в настройках MIUI/HyperOS;\n" +
                        "• микрофон занят: звонок, диктофон, «Окей, Google».",
                    color = TunerColors.Text,
                    fontSize = 14.sp,
                )
            }
            if (sources.isNotEmpty()) {
                Text("ВХОД", color = TunerColors.InTune, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Авто", onClick = { onSelect(null) }, selected = selected == null)
                    for (source in sources) {
                        Pill(source.title, onClick = { onSelect(source) }, selected = selected == source)
                    }
                }
                Text(
                    "«Авто» начинает с обычного входа и сам пропускает те, из которых идёт полная тишина. " +
                        "Если полоска еле двигается — попробуй другой вход вручную.",
                    color = TunerColors.Muted,
                    fontSize = 13.sp,
                )
            }
            Pill("Настройки приложения", onClick = onOpenSettings)
        }
    }
}

@Composable
private fun InputStatus(info: InputInfo) {
    val rate = "${info.sampleRate / 1000.0} кГц".replace(".0 ", " ").replace('.', ',')
    val (text, color) = when (info.state) {
        InputState.STARTING -> "… Подключаюсь: ${info.sourceTitle}" to TunerColors.Muted
        InputState.OK -> "✓ Звук идёт · ${info.sourceTitle} · $rate" to TunerColors.InTune
        InputState.SILENT -> "✗ Полная тишина · ${info.sourceTitle}" to TunerColors.Off
        InputState.TAKEN -> "✗ Микрофон занят другим приложением" to TunerColors.Off
    }
    Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium)
}
