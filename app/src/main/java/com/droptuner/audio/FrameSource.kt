package com.droptuner.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Where the tuner's audio comes from: the microphone, or a synthetic signal in debug builds. */
interface FrameSource {
    val sampleRate: Int

    /** What the input is doing right now, for the status line and the mic sheet. */
    val info: StateFlow<InputInfo>

    /** Capture presets this device offers (empty when the input isn't a microphone). */
    val availableSources: List<MicSource>

    /** A capture preset to use instead of trying them in turn; applies from the next [frames] call. */
    var preferredSource: MicSource?

    /** Overlapping mono frames of [frameSize] samples in -1..1, each [hopSize] samples after the last. */
    fun frames(frameSize: Int, hopSize: Int): Flow<FloatArray>
}

enum class InputState {
    STARTING,
    /** Sound is coming in (it may still be too quiet — that's what the level meter is for). */
    OK,
    /** Nothing but exact zeros: the mic is blocked, switched off or not routed. */
    SILENT,
    /** Android reports that another app (a call, the assistant, a recorder) has taken the mic. */
    TAKEN,
}

data class InputInfo(val sourceTitle: String, val sampleRate: Int, val state: InputState)
