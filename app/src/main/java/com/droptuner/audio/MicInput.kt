package com.droptuner.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import com.droptuner.dsp.SilenceDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.max

/** Android capture presets, in the order Auto tries them. */
enum class MicSource(val title: String, val preset: Int) {
    /** What most tuners use: works on practically every phone. */
    MIC("Обычный", MediaRecorder.AudioSource.MIC),
    VOICE_RECOGNITION("Распознавание речи", MediaRecorder.AudioSource.VOICE_RECOGNITION),
    UNPROCESSED("Без обработки", MediaRecorder.AudioSource.UNPROCESSED),
    CAMCORDER("Видеокамера", MediaRecorder.AudioSource.CAMCORDER),
}

/**
 * Streams overlapping mono frames from the microphone as 16-bit PCM, the format every phone
 * records natively. In Auto mode a preset that only ever delivers exact zeros is dropped for the
 * next one, since some phones advertise presets that are not wired to a mic.
 */
class MicInput(private val context: Context) : FrameSource {

    /** 48 kHz is native on nearly every phone (no resampling); 44.1 kHz is the rate Android guarantees. */
    override val sampleRate: Int =
        listOf(48_000, 44_100).firstOrNull { AudioRecord.getMinBufferSize(it, CHANNEL, ENCODING) > 0 } ?: 44_100

    private val _info = MutableStateFlow(InputInfo("—", sampleRate, InputState.STARTING))
    override val info: StateFlow<InputInfo> = _info.asStateFlow()

    override var preferredSource: MicSource? = null

    /** Presets this phone offers, in Auto order. */
    override val availableSources: List<MicSource> by lazy {
        val audio = context.getSystemService(AudioManager::class.java)
        val unprocessed = audio.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        MicSource.entries.filter { it != MicSource.UNPROCESSED || unprocessed }
    }

    /** The caller must already hold RECORD_AUDIO. */
    override fun frames(frameSize: Int, hopSize: Int): Flow<FloatArray> = flow {
        val candidates = preferredSource?.let { listOf(it) } ?: availableSources
        for ((i, source) in candidates.withIndex()) {
            val last = i == candidates.lastIndex
            // Only the last candidate keeps listening through silence (and reports it).
            if (!capture(source, frameSize, hopSize, mayGiveUp = !last)) return@flow
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Records from [source] until cancelled and returns false. Returns true early — so the next
     * preset can be tried — only if [mayGiveUp] and the preset won't open or stays digitally silent.
     */
    @SuppressLint("MissingPermission")
    private suspend fun FlowCollector<FloatArray>.capture(source: MicSource, frameSize: Int, hopSize: Int, mayGiveUp: Boolean): Boolean {
        _info.value = InputInfo(source.title, sampleRate, InputState.STARTING)
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING)
        val record = try {
            AudioRecord(source.preset, sampleRate, CHANNEL, ENCODING, max(minBuffer, frameSize * Short.SIZE_BYTES * 2))
        } catch (e: Exception) {
            if (mayGiveUp) return true else throw e
        }
        try {
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                if (mayGiveUp) return true else error("Микрофон не открывается — возможно, он занят другим приложением")
            }
            record.startRecording()
            val silence = SilenceDetector(sampleRate)
            val pcm = ShortArray(hopSize)
            val frame = FloatArray(frameSize)
            var hops = 0
            while (currentCoroutineContext().isActive) {
                var filled = 0
                while (filled < hopSize) {
                    val read = record.read(pcm, filled, hopSize - filled)
                    check(read >= 0) { "Ошибка чтения микрофона ($read)" }
                    filled += read
                }
                if (silence.feed(pcm) && mayGiveUp) return true
                System.arraycopy(frame, hopSize, frame, 0, frameSize - hopSize)
                for (j in 0 until hopSize) frame[frameSize - hopSize + j] = pcm[j] / 32_768f
                if (hops++ % STATUS_EVERY_HOPS == 0) {
                    val state = when {
                        isTakenBySystem(record) -> InputState.TAKEN
                        silence.isSilent -> InputState.SILENT
                        silence.hearsSomething -> InputState.OK
                        else -> _info.value.state
                    }
                    _info.value = InputInfo(source.title, sampleRate, state)
                }
                emit(frame.copyOf())
            }
            return false
        } finally {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            record.release()
        }
    }

    /** Android 10+ hands the mic to calls and assistants and feeds the rest of us zeros. */
    private fun isTakenBySystem(record: AudioRecord): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && record.activeRecordingConfiguration?.isClientSilenced == true

    private companion object {
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        /** ~0.2 s at a 1024-sample hop: often enough for the status line, cheap on the audio thread. */
        const val STATUS_EVERY_HOPS = 10
    }
}
