package com.droptuner.audio

import kotlinx.coroutines.flow.Flow

/** Where the tuner's audio comes from: the microphone, or a synthetic signal in debug builds. */
interface FrameSource {
    val sampleRate: Int

    /** Overlapping mono frames of [frameSize] samples in -1..1, each [hopSize] samples after the last. */
    fun frames(frameSize: Int, hopSize: Int): Flow<FloatArray>
}
