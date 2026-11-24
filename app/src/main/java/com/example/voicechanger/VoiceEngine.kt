package com.example.voicechanger

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/** 麦克风 → 变声 → 扬声器/耳机（可选同时录成 WAV）。 */
class VoiceEngine {
    companion object {
        const val SAMPLE_RATE = 44100
        private const val FRAME = 512
    }

    val effect = VoiceEffect(SAMPLE_RATE)

    @Volatile var monitor = true
    @Volatile var onLevel: ((Float) -> Unit)? = null
    @Volatile var onError: ((String) -> Unit)? = null

    @Volatile private var running = false
    private var thread: Thread? = null
    private val lock = Any()
    private var writer: WavWriter? = null

    val isRunning: Boolean get() = running

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "VoiceEngine").also {
            it.priority = Thread.MAX_PRIORITY
            it.start()
        }
    }

    fun stop() {
        running = false
        thread?.join(800)
        thread = null
        stopRecording()
    }

    fun startRecording(file: File) {
        synchronized(lock) {
            writer?.close()
            writer = WavWriter(file, SAMPLE_RATE)
        }
    }

    fun stopRecording() {
        synchronized(lock) {
            writer?.close()
            writer = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun loop() {
        val minRec = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            max(minRec, FRAME * 8)
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            running = false
            rec.release()
            onError?.invoke("无法打开麦克风，请检查权限或是否被其他应用占用")
            return
        }
        try {
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)
        } catch (_: Throwable) {
        }

        val minPlay = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(minPlay, FRAME * 2 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        val inBuf = ShortArray(FRAME)
        val outBuf = ShortArray(FRAME)

        rec.startRecording()
        track.play()

        while (running) {
            val n = rec.read(inBuf, 0, FRAME)
            if (n <= 0) continue
            var peak = 0f
            for (i in 0 until n) {
                val y = effect.process(inBuf[i] / 32768f).coerceIn(-1f, 1f)
                if (abs(y) > peak) peak = abs(y)
                outBuf[i] = (y * 32767f).toInt().toShort()
            }
            if (monitor) track.write(outBuf, 0, n)
            synchronized(lock) { writer?.write(outBuf, n) }
            onLevel?.invoke(peak)
        }

        try {
            rec.stop()
            track.stop()
        } catch (_: Throwable) {
        }
        rec.release()
        track.release()
    }
}
