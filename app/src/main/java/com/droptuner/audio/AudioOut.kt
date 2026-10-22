package com.droptuner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.droptuner.dsp.ClickTrack
import com.droptuner.dsp.ToneSynth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Speaker output: reference notes for the tuner and the metronome's click stream. */
class AudioOut {

    /** Plays one reference note and returns when it has finished (or is cancelled). */
    suspend fun playNote(frequency: Double) {
        val samples = withContext(Dispatchers.Default) { ToneSynth.pluck(frequency, SAMPLE_RATE, NOTE_SECONDS) }
        val track = track(AudioTrack.MODE_STATIC, samples.size * Float.SIZE_BYTES)
        try {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            track.play()
            delay((NOTE_SECONDS * 1000).toLong())
        } finally {
            release(track)
        }
    }

    /**
     * Streams [clicks] until cancelled. [onBeat] gets each beat's index in the bar once the
     * speaker reaches it (not when it is queued), so the flashing light matches the sound.
     */
    suspend fun playClicks(clicks: ClickTrack, onBeat: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = track(AudioTrack.MODE_STREAM, minBuffer, lowLatency = true)
        val chunk = FloatArray(CLICK_CHUNK)
        val pending = ArrayDeque<ClickTrack.Beat>()
        try {
            track.play()
            while (isActive) {
                pending += clicks.fill(chunk)
                track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                val heard = track.playbackHeadPosition.toLong()
                while (pending.isNotEmpty() && pending.first().frame <= heard) onBeat(pending.removeFirst().index)
            }
        } finally {
            release(track)
        }
    }

    private fun track(mode: Int, bufferBytes: Int, lowLatency: Boolean = false): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(mode)
            .setBufferSizeInBytes(bufferBytes)
            .setPerformanceMode(if (lowLatency) AudioTrack.PERFORMANCE_MODE_LOW_LATENCY else AudioTrack.PERFORMANCE_MODE_NONE)
            .build()

    private fun release(track: AudioTrack) {
        if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
        track.release()
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        private const val NOTE_SECONDS = 2.5
        /** ~5 ms: how finely the beat callbacks follow the playback position. */
        private const val CLICK_CHUNK = 256
    }
}
