package com.marblesmoke.drums.pocket

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Minimal RIFF/WAVE reader and writer. */
object Wav {

    /**
     * Decodes a WAV file (PCM 8/16/24/32 bit or float 32/64 bit, any channel count)
     * into mono floats resampled to [targetRate]. Longer files are cut at [maxSeconds].
     * Returns null when the file is not a supported WAV.
     */
    fun decode(bytes: ByteArray, targetRate: Int, maxSeconds: Int = 30): FloatArray? {
        if (bytes.size < 12 || tag(bytes, 0) != "RIFF" || tag(bytes, 8) != "WAVE") return null
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var format = -1
        var channels = 0
        var rate = 0
        var bits = 0
        var dataStart = -1
        var dataLen = 0
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = tag(bytes, pos)
            val len = b.getInt(pos + 4).toLong() and 0xFFFFFFFFL
            val body = pos + 8
            if (id == "fmt ") {
                if (body + 16 > bytes.size) return null
                format = b.getShort(body).toInt() and 0xFFFF
                channels = b.getShort(body + 2).toInt() and 0xFFFF
                rate = b.getInt(body + 4)
                bits = b.getShort(body + 14).toInt() and 0xFFFF
                if (format == 0xFFFE && len >= 26 && body + 26 <= bytes.size) {
                    format = b.getShort(body + 24).toInt() and 0xFFFF
                }
            } else if (id == "data") {
                dataStart = body
                dataLen = minOf(len, (bytes.size - body).toLong()).toInt()
            }
            val next = body.toLong() + len + (len and 1L)
            if (next > bytes.size) break
            pos = next.toInt()
        }
        if (dataStart < 0 || channels !in 1..16 || rate !in 4000..384000) return null
        val bytesPer = bits / 8
        if (bytesPer !in 1..8) return null
        val frameSize = bytesPer * channels
        val frames = minOf(dataLen / frameSize, rate * maxSeconds)
        if (frames <= 0) return null

        val mono = FloatArray(frames)
        for (f in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) {
                val o = dataStart + f * frameSize + c * bytesPer
                sum += when {
                    format == 1 && bits == 8 -> ((bytes[o].toInt() and 0xFF) - 128) / 128f
                    format == 1 && bits == 16 -> b.getShort(o).toInt() / 32768f
                    format == 1 && bits == 24 -> ((bytes[o].toInt() and 0xFF) or
                        ((bytes[o + 1].toInt() and 0xFF) shl 8) or
                        (bytes[o + 2].toInt() shl 16)) / 8388608f
                    format == 1 && bits == 32 -> (b.getInt(o) / 2147483648.0).toFloat()
                    format == 3 && bits == 32 -> b.getFloat(o)
                    format == 3 && bits == 64 -> b.getDouble(o).toFloat()
                    else -> return null
                }
            }
            mono[f] = sum / channels
        }
        return resample(mono, rate, targetRate)
    }

    /** Linear-interpolation resampler. */
    fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || input.isEmpty()) return input
        val outLen = (input.size.toLong() * to / from).toInt().coerceAtLeast(1)
        val ratio = from.toDouble() / to
        val last = input.size - 1
        return FloatArray(outLen) { i ->
            val x = i * ratio
            val j = x.toInt()
            val frac = (x - j).toFloat()
            val a = input[minOf(j, last)]
            val c = input[minOf(j + 1, last)]
            a + (c - a) * frac
        }
    }

    /** 16-bit mono PCM WAV. */
    fun encodeMono16(samples: FloatArray, rate: Int): ByteArray {
        val dataBytes = samples.size * 2
        val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + dataBytes)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
        buf.putShort(1.toShort()).putShort(1.toShort()).putInt(rate).putInt(rate * 2)
        buf.putShort(2.toShort()).putShort(16.toShort())
        buf.put("data".toByteArray(Charsets.US_ASCII)).putInt(dataBytes)
        for (s in samples) buf.putShort((s.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
        return buf.array()
    }

    private fun tag(bytes: ByteArray, at: Int): String =
        if (at + 4 <= bytes.size) String(bytes, at, 4, Charsets.US_ASCII) else ""
}
