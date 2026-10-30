package com.droptuner.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface FrameSource {
    val sampleRate: Int

    val info: StateFlow<InputInfo>

    val availableSources: List<MicSource>

    var preferredSource: MicSource?

    fun frames(frameSize: Int, hopSize: Int): Flow<FloatArray>
}

enum class InputState {
    STARTING,
    OK,
    SILENT,
    TAKEN,
}

data class InputInfo(val sourceTitle: String, val sampleRate: Int, val state: InputState)
