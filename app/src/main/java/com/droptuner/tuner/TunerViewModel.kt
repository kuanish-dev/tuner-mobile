package com.droptuner.tuner

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droptuner.audio.AudioOut
import com.droptuner.audio.FrameSource
import com.droptuner.dsp.PitchTracker
import com.droptuner.dsp.YinPitchDetector
import com.droptuner.music.Note
import com.droptuner.music.Tuning
import com.droptuner.music.TuningFamily
import com.droptuner.music.Tunings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

class TunerViewModel(
    private val prefs: TunerPrefs,
    private val input: FrameSource,
    private val speaker: AudioOut,
) : ViewModel(), TunerActions {

    private val _state = MutableStateFlow(
        TunerUiState(tuning = prefs.tuning(), custom = prefs.custom(), a4 = prefs.a4, strobe = prefs.strobe),
    )
    val state: StateFlow<TunerUiState> = _state.asStateFlow()

    private val matcher = StringMatcher()
    private var listening: Job? = null
    private var reference: Job? = null
    private var lastPitchAt = 0L
    private var streakString: Int? = null
    private var streakSince = 0L

    fun startListening() {
        if (listening?.isActive == true) return
        val tracker = PitchTracker(YinPitchDetector(input.sampleRate, FRAME_SIZE))
        _state.update { it.copy(error = null) }
        listening = viewModelScope.launch {
            input.frames(FRAME_SIZE, HOP_SIZE)
                .map { tracker.process(it) }
                .flowOn(Dispatchers.Default)
                .catch { e -> _state.update { it.copy(error = e.message ?: "Микрофон недоступен") } }
                .collect { onPitch(it) }
        }
    }

    fun stopListening() {
        listening?.cancel()
        listening = null
    }

    private fun onPitch(frequency: Double?) {
        val s = _state.value
        val now = SystemClock.elapsedRealtime()
        // While the reference note plays the mic hears the phone itself: that must not count.
        if (frequency == null || s.playing != null) {
            streakString = null
            if (s.reading != null && now - lastPitchAt > HOLD_MS) _state.value = s.copy(reading = null)
            return
        }
        lastPitchAt = now
        val reading = matcher.match(frequency, s.tuning, s.a4.toDouble(), s.locked)
        val string = reading.stringIndex
        if (abs(reading.cents) > IN_TUNE_CENTS || string != streakString) {
            streakString = if (abs(reading.cents) <= IN_TUNE_CENTS) string else null
            streakSince = now
        }
        val tuned = when {
            string == null -> s.tuned
            streakString == string && now - streakSince >= CONFIRM_MS -> s.tuned + string
            abs(reading.cents) > DETUNED_CENTS -> s.tuned - string
            else -> s.tuned
        }
        _state.value = s.copy(reading = reading, tuned = tuned)
    }

    override fun selectTuning(tuning: Tuning) {
        prefs.tuningId = tuning.id
        retune { it.copy(tuning = tuning, locked = null, editing = false) }
    }

    override fun toggleLock(string: Int) =
        _state.update { it.copy(locked = if (it.locked == string) null else string) }

    override fun playReference(string: Int) {
        val s = _state.value
        val frequency = s.tuning.strings[string].frequency(s.a4.toDouble())
        reference?.cancel()
        reference = viewModelScope.launch {
            _state.update { it.copy(playing = string, reading = null) }
            try {
                speaker.playNote(frequency)
                delay(ECHO_MS)
            } finally {
                _state.update { it.copy(playing = null) }
            }
        }
    }

    override fun shiftString(string: Int, semitones: Int) {
        val s = _state.value
        val strings = s.tuning.strings.toMutableList()
        strings[string] = Note((strings[string].midi + semitones).coerceIn(LOWEST_MIDI, HIGHEST_MIDI))
        val custom = Tunings.custom(strings)
        saveCustom(custom)
        _state.value = s.copy(tuning = custom, custom = custom, tuned = s.tuned - string)
    }

    override fun toggleEditing() {
        val s = _state.value
        if (s.editing) {
            _state.value = s.copy(editing = false)
            return
        }
        if (s.tuning.isChromatic) return
        // Editing always works on the custom slot, seeded from whatever tuning is on screen.
        val custom = if (s.tuning.family == TuningFamily.CUSTOM) s.tuning else Tunings.custom(s.tuning.strings)
        saveCustom(custom)
        retune { it.copy(tuning = custom, custom = custom, editing = true) }
    }

    override fun toggleStrobe() {
        val strobe = !_state.value.strobe
        prefs.strobe = strobe
        _state.update { it.copy(strobe = strobe) }
    }

    override fun setA4(hz: Int) {
        val a4 = hz.coerceIn(MIN_A4, MAX_A4)
        prefs.a4 = a4
        retune { it.copy(a4 = a4) }
    }

    private fun saveCustom(custom: Tuning) {
        prefs.saveCustom(custom)
        prefs.tuningId = Tunings.CUSTOM_ID
    }

    /** Applies a change that makes what was measured so far meaningless. */
    private fun retune(change: (TunerUiState) -> TunerUiState) {
        matcher.reset()
        streakString = null
        _state.update { change(it).copy(reading = null, tuned = emptySet()) }
    }

    private companion object {
        /** ~85 ms at 48 kHz: two periods of the lowest string (C#1 on an 8-string). */
        const val FRAME_SIZE = 4096
        /** ~47 readings a second, which the tracker averages while a note holds. */
        const val HOP_SIZE = 1024
        /** Keep showing the last note this long after the sound fades. */
        const val HOLD_MS = 1_200L
        /** How long a string must stay in tune before it gets its check mark. */
        const val CONFIRM_MS = 500L
        /** A checked string loses its mark once it drifts this far. */
        const val DETUNED_CENTS = 8.0
        /** Room echo after the reference note that the mic shouldn't take for a string. */
        const val ECHO_MS = 250L
        const val LOWEST_MIDI = 24  // C1
        const val HIGHEST_MIDI = 76 // E5
        const val MIN_A4 = 415
        const val MAX_A4 = 466
    }
}
