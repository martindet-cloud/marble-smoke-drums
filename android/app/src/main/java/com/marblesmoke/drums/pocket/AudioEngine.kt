package com.marblesmoke.drums.pocket

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Low-latency mono mixer on its own audio thread.
 * The sequencer is clocked in audio frames, so steps and swing are sample-accurate
 * instead of depending on a UI timer.
 */
class AudioEngine(context: Context) {

    val sampleRate: Int = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000

    /** Called on the audio thread when a sequencer step starts. */
    @Volatile var onStep: ((Int) -> Unit)? = null

    /** One decoded sample per lane, at [sampleRate]. */
    @Volatile var samples: Array<FloatArray?> = arrayOfNulls(LANES)

    @Volatile private var pattern = BooleanArray(LANES * STEPS)
    @Volatile private var bpm = 128
    @Volatile private var swing = 0.0
    @Volatile private var playRequested = false
    @Volatile private var running = false

    private class Voice(val data: FloatArray, var pos: Int)

    private val pending = ConcurrentLinkedQueue<Int>()
    private val voices = ArrayList<Voice>()   // touched only by the audio thread
    private var thread: Thread? = null

    /** [pattern] is lane * 16 + step for the bank that is playing. */
    fun update(pattern: BooleanArray, bpm: Int, swing: Double) {
        this.pattern = pattern
        this.bpm = bpm
        this.swing = swing
    }

    fun trigger(lane: Int) {
        pending.add(lane)
    }

    fun setPlaying(on: Boolean) {
        playRequested = on
    }

    @Synchronized
    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "DrumsAudio").also { it.start() }
    }

    @Synchronized
    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val chunk = 128
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBytes, chunk * 4 * 2))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val out = FloatArray(chunk)
        var clock = 0L
        var wasPlaying = false
        var nextGrid = 0.0
        var nextStep = 0
        track.play()
        try {
            while (running) {
                out.fill(0f)
                while (true) {
                    val lane = pending.poll() ?: break
                    startVoice(lane, 0)
                }
                val playing = playRequested
                if (playing && !wasPlaying) {
                    nextGrid = clock.toDouble()
                    nextStep = 0
                }
                wasPlaying = playing
                if (playing) {
                    val end = clock + chunk
                    while (true) {
                        val stepFrames = sampleRate * 60.0 / bpm / 4.0
                        val swingDelay = if (nextStep % 2 == 1) swing * stepFrames / 2.0 else 0.0
                        val at = nextGrid + swingDelay
                        if (at >= end) break
                        val offset = (at - clock).toInt().coerceIn(0, chunk - 1)
                        val p = pattern
                        for (lane in 0 until LANES) {
                            if (p[lane * STEPS + nextStep]) startVoice(lane, offset)
                        }
                        onStep?.invoke(nextStep)
                        nextGrid += stepFrames
                        nextStep = (nextStep + 1) % STEPS
                    }
                }
                mix(out)
                track.write(out, 0, chunk, AudioTrack.WRITE_BLOCKING)
                clock += chunk
            }
        } finally {
            track.pause()
            track.flush()
            track.release()
            voices.clear()
        }
    }

    private fun startVoice(lane: Int, offset: Int) {
        val data = samples.getOrNull(lane) ?: return
        if (voices.size >= 32) voices.removeAt(0)
        voices.add(Voice(data, -offset))
    }

    private fun mix(out: FloatArray) {
        val iterator = voices.iterator()
        while (iterator.hasNext()) {
            val v = iterator.next()
            for (i in out.indices) {
                val idx = v.pos + i
                if (idx >= 0 && idx < v.data.size) out[i] += v.data[idx] * GAIN
            }
            v.pos += out.size
            if (v.pos >= v.data.size) iterator.remove()
        }
        for (i in out.indices) out[i] = out[i].coerceIn(-1f, 1f)
    }

    private companion object {
        const val GAIN = 0.8f
    }
}
