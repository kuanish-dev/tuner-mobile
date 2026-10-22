package com.droptuner.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.max

/** Streams overlapping mono frames from the microphone. */
class MicInput(private val context: Context) : FrameSource {

    /** 48 kHz is native on nearly every phone (no resampling); 44.1 kHz is the rate Android guarantees. */
    override val sampleRate: Int = listOf(48_000, 44_100).firstOrNull { AudioRecord.getMinBufferSize(it, CHANNEL, ENCODING) > 0 } ?: 44_100

    /** The caller must already hold RECORD_AUDIO. */
    @SuppressLint("MissingPermission")
    override fun frames(frameSize: Int, hopSize: Int): Flow<FloatArray> = flow {
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING)
        val record = AudioRecord(source(), sampleRate, CHANNEL, ENCODING, max(minBuffer, frameSize * Float.SIZE_BYTES))
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED) { "Микрофон занят другим приложением" }
            record.startRecording()
            val frame = FloatArray(frameSize)
            val hop = FloatArray(hopSize)
            while (currentCoroutineContext().isActive) {
                var filled = 0
                while (filled < hopSize) {
                    val read = record.read(hop, filled, hopSize - filled, AudioRecord.READ_BLOCKING)
                    check(read >= 0) { "Ошибка чтения микрофона ($read)" }
                    filled += read
                }
                System.arraycopy(frame, hopSize, frame, 0, frameSize - hopSize)
                System.arraycopy(hop, 0, frame, frameSize - hopSize, hopSize)
                emit(frame.copyOf())
            }
        } finally {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            record.release()
        }
    }.flowOn(Dispatchers.IO)

    /** Raw input where the phone offers it: noise suppression and AGC eat sustained notes. */
    private fun source(): Int {
        val audio = context.getSystemService(AudioManager::class.java)
        val unprocessed = audio.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    private companion object {
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
    }
}
