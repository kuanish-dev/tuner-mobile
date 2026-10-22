package com.droptuner

import android.content.Intent
import com.droptuner.audio.FrameSource

/** Release builds always listen to the microphone; the synthetic source exists only in debug. */
@Suppress("UNUSED_PARAMETER")
fun demoSource(intent: Intent): FrameSource? = null
