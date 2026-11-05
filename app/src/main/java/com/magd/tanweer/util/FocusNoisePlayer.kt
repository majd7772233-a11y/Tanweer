package com.magd.tanweer.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Small, generated low-volume focus ambience. No network or bundled audio is used.
 * The player owns a single streaming AudioTrack and releases it on stop.
 */
class FocusNoisePlayer {
    private val lock = Any()

    @Volatile
    private var running = false

    private var audioTrack: AudioTrack? = null
    private var worker: Thread? = null

    fun start(): Boolean = synchronized(lock) {
        if (running && audioTrack?.state == AudioTrack.STATE_INITIALIZED) return true

        val sampleRate = 22_050
        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferBytes <= 0) return false

        val bufferBytes = maxOf(minBufferBytes, 8_192)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .build()
        } catch (_: Exception) {
            return false
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return false
        }

        try {
            track.play()
        } catch (_: Exception) {
            track.release()
            return false
        }

        audioTrack = track
        running = true

        val samples = ShortArray(maxOf(1_024, bufferBytes / 2))
        val activeTrack = track
        worker = Thread({
            var lowPass = 0.0
            var sampleIndex = 0L
            val wavePeriod = sampleRate * 9.0

            try {
                while (running && !Thread.currentThread().isInterrupted) {
                    for (index in samples.indices) {
                        val whiteNoise = Random.Default.nextDouble(-1.0, 1.0)
                        lowPass = lowPass * 0.84 + whiteNoise * 0.16
                        val waveEnvelope = 0.78 + 0.22 * sin((sampleIndex / wavePeriod) * 2.0 * PI)
                        val value = (lowPass * waveEnvelope * 0.22 * Short.MAX_VALUE)
                            .toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        samples[index] = value.toShort()
                        sampleIndex++
                    }

                    val written = activeTrack.write(
                        samples,
                        0,
                        samples.size,
                        AudioTrack.WRITE_BLOCKING
                    )
                    if (written < 0) break
                }
            } catch (_: Exception) {
                // stop() can interrupt a blocking write while releasing the track.
            }
        }, "Tanweer-Focus-Ambience").apply {
            isDaemon = true
            start()
        }

        true
    }

    fun stop() = synchronized(lock) {
        running = false
        val activeTrack = audioTrack

        try {
            activeTrack?.pause()
            activeTrack?.flush()
            activeTrack?.stop()
        } catch (_: Exception) {
            // The track may already have stopped after an audio-device change.
        }

        worker?.interrupt()
        try {
            worker?.join(150)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        try {
            activeTrack?.release()
        } catch (_: Exception) {
            // Release is best-effort; the track reference is cleared below.
        }

        worker = null
        audioTrack = null
    }
}
