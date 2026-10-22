package com.droptuner.metronome

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droptuner.audio.AudioOut
import com.droptuner.dsp.ClickTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class MetronomeUiState(
    val bpm: Int,
    val beatsPerBar: Int,
    val playing: Boolean = false,
    /** Beat being heard right now (0 = the accented first beat), -1 when stopped. */
    val beat: Int = -1,
)

interface MetronomeActions {
    fun toggle()
    fun setBpm(bpm: Int)
    fun setBeatsPerBar(beats: Int)
    fun tap()
}

class MetronomePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("metronome", Context.MODE_PRIVATE)

    var bpm: Int
        get() = prefs.getInt("bpm", 100)
        set(value) = prefs.edit { putInt("bpm", value) }

    var beatsPerBar: Int
        get() = prefs.getInt("beats", 4)
        set(value) = prefs.edit { putInt("beats", value) }
}

class MetronomeViewModel(
    private val prefs: MetronomePrefs,
    private val speaker: AudioOut,
) : ViewModel(), MetronomeActions {

    private val _state = MutableStateFlow(MetronomeUiState(bpm = prefs.bpm, beatsPerBar = prefs.beatsPerBar))
    val state: StateFlow<MetronomeUiState> = _state.asStateFlow()

    @Volatile private var clicks: ClickTrack? = null
    private var playback: Job? = null
    private val taps = ArrayDeque<Long>()

    override fun toggle() = if (playback?.isActive == true) stop() else start()

    fun stop() {
        playback?.cancel()
        playback = null
        clicks = null
        _state.update { it.copy(playing = false, beat = -1) }
    }

    private fun start() {
        val track = ClickTrack(AudioOut.SAMPLE_RATE).apply {
            bpm = _state.value.bpm
            beatsPerBar = _state.value.beatsPerBar
        }
        clicks = track
        _state.update { it.copy(playing = true) }
        playback = viewModelScope.launch {
            // Beats still in the speaker's buffer after stop() must not light the dots again.
            speaker.playClicks(track) { beat -> if (clicks === track) _state.update { it.copy(beat = beat) } }
        }
    }

    override fun setBpm(bpm: Int) {
        val value = bpm.coerceIn(MIN_BPM, MAX_BPM)
        prefs.bpm = value
        clicks?.bpm = value
        _state.update { it.copy(bpm = value) }
    }

    override fun setBeatsPerBar(beats: Int) {
        prefs.beatsPerBar = beats
        clicks?.beatsPerBar = beats
        _state.update { it.copy(beatsPerBar = beats) }
    }

    /** Tap tempo: the average of the last few intervals; a long pause starts a new count. */
    override fun tap() {
        val now = SystemClock.elapsedRealtime()
        if (taps.isNotEmpty() && now - taps.last() > TAP_RESET_MS) taps.clear()
        taps.addLast(now)
        if (taps.size > MAX_TAPS) taps.removeFirst()
        if (taps.size >= 2) {
            val interval = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
            setBpm((60_000 / interval).roundToInt())
        }
    }

    companion object {
        const val MIN_BPM = 20
        const val MAX_BPM = 300
        val BEAT_CHOICES = (1..7).toList()
        private const val TAP_RESET_MS = 2_000L
        private const val MAX_TAPS = 5
    }
}
