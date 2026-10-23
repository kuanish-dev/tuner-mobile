package com.droptuner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.droptuner.audio.AudioOut
import com.droptuner.audio.MicInput
import com.droptuner.metronome.MetronomePrefs
import com.droptuner.metronome.MetronomeViewModel
import com.droptuner.tuner.TunerPrefs
import com.droptuner.tuner.TunerViewModel
import com.droptuner.ui.DropTunerTheme
import com.droptuner.ui.MetronomeScreen
import com.droptuner.ui.MicAccess
import com.droptuner.ui.TunerColors
import com.droptuner.ui.TunerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always-dark UI: light status/navigation icons regardless of the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // Tuning takes both hands: don't let the screen go dark mid-string.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            DropTunerTheme { App() }
        }
    }
}

private enum class Tab(val title: String) { TUNER("Тюнер"), METRONOME("Метроном") }

@Composable
private fun App() {
    val app = LocalContext.current.applicationContext
    val metronome = viewModel { MetronomeViewModel(MetronomePrefs(app), AudioOut()) }
    val metronomeState by metronome.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.TUNER) }

    // The metronome keeps going across tabs but not in the background.
    LifecycleStartEffect(metronome) {
        onStopOrDispose { metronome.stop() }
    }

    Column(Modifier.fillMaxSize().background(TunerColors.Background)) {
        Box(Modifier.weight(1f).statusBarsPadding()) {
            when (tab) {
                Tab.TUNER -> TunerTab()
                Tab.METRONOME -> MetronomeScreen(metronomeState, metronome)
            }
        }
        BottomTabs(tab, metronomeOn = metronomeState.playing, onSelect = { tab = it })
    }
}

@Composable
private fun TunerTab() {
    val context = LocalContext.current
    val vm = viewModel {
        val app = context.applicationContext
        TunerViewModel(TunerPrefs(app), demoSource(context.findActivity().intent) ?: MicInput(app), AudioOut())
    }
    var granted by remember { mutableStateOf(context.hasMicPermission()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) request.launch(Manifest.permission.RECORD_AUDIO)
    }
    // Listen only while this tab is on screen; re-check on resume in case access was granted in Settings.
    LifecycleResumeEffect(granted) {
        granted = context.hasMicPermission()
        if (granted) vm.startListening()
        onPauseOrDispose { vm.stopListening() }
    }

    val state by vm.state.collectAsStateWithLifecycle()
    val inputDb = vm.inputDb.collectAsStateWithLifecycle()
    TunerScreen(
        state = state,
        actions = vm,
        inputDb = { inputDb.value },
        mic = MicAccess(
            granted = granted,
            request = { request.launch(Manifest.permission.RECORD_AUDIO) },
            openSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
        ),
    )
}

@Composable
private fun BottomTabs(selected: Tab, metronomeOn: Boolean, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(TunerColors.Surface)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (tab in Tab.entries) {
            val active = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) TunerColors.SurfaceHigh else Color.Transparent)
                    .clickable { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (tab == Tab.METRONOME && metronomeOn) "${tab.title} ●" else tab.title,
                    color = if (active) TunerColors.Text else TunerColors.Muted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No activity for $this")
}

private fun Context.hasMicPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
