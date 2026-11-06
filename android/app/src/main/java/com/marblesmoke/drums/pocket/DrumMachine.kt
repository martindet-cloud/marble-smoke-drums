package com.marblesmoke.drums.pocket

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

const val BANKS = 8
const val LANES = 8
const val STEPS = 16

data class DrumState(
    val artist: Int = 0,
    val preset: Int = 0,
    val bank: Int = 0,
    val selected: Int = 0,
    val bpm: Int = 128,
    val swing: Double = 0.0,
    /** Flattened [bank][lane][step]. */
    val patterns: List<Boolean> = List(BANKS * LANES * STEPS) { false },
    val customSamples: List<String> = List(LANES) { "" },
) {
    fun step(bank: Int, lane: Int, step: Int): Boolean = patterns[(bank * LANES + lane) * STEPS + step]
}

class DrumMachine private constructor(private val context: Context) {

    companion object {
        val artists = listOf("Martin Det", "GONDII", "Xande", "Clay", "Init")
        val presets = listOf(
            listOf("Afterhours", "Glitch Vein", "Leftfield Garage"),
            listOf("Tunnel", "Runs and Hits", "Pressure Room"),
            listOf("Static Motion", "Blue Circuit", "Fragments"),
            listOf("Warm Dust", "Raw Shuffle", "Low Tide"),
            listOf("Phase One", "Offset", "Night Shift"),
        )
        val names = listOf("KICK", "SNARE", "CLAP", "C.HAT", "O.HAT", "RIM", "TOM", "PERC")
        val files = listOf("Kick", "Snare", "Clap", "ClosedHat", "OpenHat", "Rim", "Tom", "Perc")

        @Volatile private var instance: DrumMachine? = null

        fun get(context: Context): DrumMachine =
            instance ?: synchronized(this) {
                instance ?: DrumMachine(context.applicationContext).also { instance = it }
            }
    }

    var state by mutableStateOf(DrumState())
        private set
    var playing by mutableStateOf(false)
        private set
    var playStep by mutableIntStateOf(-1)
        private set
    var error by mutableStateOf<String?>(null)

    val engine = AudioEngine(context)
    private val main = Handler(Looper.getMainLooper())
    private val stateFile = File(context.filesDir, "drums-state.json")
    private val demo: Array<FloatArray?> = Array(LANES) { lane -> loadAsset(files[lane]) }

    init {
        val saved = runCatching { load() }.getOrNull()
        if (saved != null) {
            state = saved
            syncEngine()
            reloadSamples()
        } else {
            makePreset(0, 0)
        }
        engine.onStep = { step -> main.post { if (playing) playStep = step } }
    }

    // region Actions

    fun selectArtist(index: Int) = makePreset(index, 0)
    fun selectPreset(index: Int) = makePreset(state.artist, index)

    fun selectBank(index: Int) {
        state = state.copy(bank = index.coerceIn(0, BANKS - 1))
        changed()
    }

    fun selectLane(index: Int) {
        state = state.copy(selected = index.coerceIn(0, LANES - 1))
        engine.trigger(index)
        persist()
    }

    fun setBpm(value: Int) {
        state = state.copy(bpm = value.coerceIn(60, 220))
        changed()
    }

    fun setSwing(value: Double) {
        state = state.copy(swing = ((value * 10).roundToInt() / 10.0).coerceIn(0.0, 0.6))
        changed()
    }

    fun toggle(step: Int) {
        val s = state
        val i = (s.bank * LANES + s.selected) * STEPS + step
        state = s.copy(patterns = s.patterns.toMutableList().also { it[i] = !it[i] })
        changed()
    }

    fun togglePlay() {
        if (playing) stop() else start()
    }

    fun start() {
        if (playing) return
        playing = true
        playStep = -1
        engine.setPlaying(true)
    }

    fun stop() {
        playing = false
        playStep = -1
        engine.setPlaying(false)
    }

    // endregion

    private fun makePreset(artist: Int, preset: Int) {
        if (artist !in 0..4 || preset !in 0..2) return
        stop()
        val patterns = List(BANKS * LANES * STEPS) { i ->
            val b = i / (LANES * STEPS)
            val t = (i / STEPS) % LANES
            val s = i % STEPS
            val x = (s * 19 + b * 7 + artist * 13 + preset * 11) % 37
            when (t) {
                0 -> s % 4 == 0 || (artist == 2 && x == 1)
                1 -> s % 8 == 4
                3 -> s % 2 == 0 || x < 3
                4 -> s % 8 == 6
                2, 5, 7 -> x < (if (artist == 3) 8 else 4) &&
                    t == (when (artist) { 1 -> 5; 4 -> 7; else -> 2 })
                else -> false
            }
        }
        state = state.copy(
            artist = artist, preset = preset, bank = 0,
            customSamples = List(LANES) { "" }, patterns = patterns,
        )
        changed(samples = true)
    }

    private fun changed(samples: Boolean = false) {
        persist()
        syncEngine()
        if (samples) reloadSamples()
    }

    private fun syncEngine() {
        val s = state
        val base = s.bank * LANES * STEPS
        engine.update(BooleanArray(LANES * STEPS) { s.patterns[base + it] }, s.bpm, s.swing)
    }

    private fun reloadSamples() {
        engine.samples = Array(LANES) { lane -> customSample(lane) ?: demo[lane] }
    }

    private fun customSample(lane: Int): FloatArray? {
        val name = state.customSamples[lane]
        if (name.isEmpty()) return null
        val file = File(context.filesDir, name)
        if (!file.exists()) return null
        return runCatching { Wav.decode(file.readBytes(), engine.sampleRate) }.getOrNull()
    }

    private fun loadAsset(name: String): FloatArray? = runCatching {
        context.assets.open("samples/$name.wav").use { Wav.decode(it.readBytes(), engine.sampleRate) }
    }.getOrNull()

    // region Import

    fun importSample(uri: Uri) {
        val lane = state.selected
        val rate = engine.sampleRate
        Thread {
            val result: Result<FloatArray> = runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("file non leggibile")
                if (bytes.size >= 30_000_000) throw IllegalStateException("campione troppo grande (massimo 30 MB)")
                val decoded = Wav.decode(bytes, rate)
                    ?: throw IllegalStateException("formato non supportato, serve un WAV PCM o float")
                File(context.filesDir, "sample-$lane.wav").writeBytes(bytes)
                decoded
            }
            main.post {
                result.onSuccess { decoded ->
                    state = state.copy(customSamples = state.customSamples.toMutableList().also { it[lane] = "sample-$lane.wav" })
                    persist()
                    engine.samples = engine.samples.copyOf().also { it[lane] = decoded }
                    engine.trigger(lane)
                }.onFailure { error = "Caricamento WAV non riuscito: ${it.message}" }
            }
        }.start()
    }

    // endregion

    // region Export

    fun exportMidi(lane: Int?): File {
        val s = state
        val events = mutableListOf<Pair<Int, ByteArray>>()
        for (step in 0 until STEPS) for (t in 0 until LANES) {
            if ((lane == null || lane == t) && s.step(s.bank, t, step)) {
                val at = step * 240 + if (step % 2 == 0) 0 else (s.swing * 120).toInt()
                events += at to byteArrayOf(0x99.toByte(), (36 + t).toByte(), 105)
                events += (at + 110) to byteArrayOf(0x89.toByte(), (36 + t).toByte(), 0)
            }
        }
        events.sortWith(compareBy<Pair<Int, ByteArray>>({ it.first }, { it.second[0].toInt() and 0xFF }))

        val track = ByteArrayOutputStream()
        val tempo = 60_000_000 / s.bpm
        track.write(
            byteArrayOf(
                0, 0xFF.toByte(), 0x51, 3,
                ((tempo shr 16) and 255).toByte(), ((tempo shr 8) and 255).toByte(), (tempo and 255).toByte(),
            )
        )
        var previous = 0
        for ((time, message) in events) {
            track.write(varLength(time - previous))
            track.write(message)
            previous = time
        }
        track.write(varLength(3840 - previous))
        track.write(byteArrayOf(0xFF.toByte(), 0x2F, 0))
        val body = track.toByteArray()

        val file = ByteArrayOutputStream()
        file.write(
            byteArrayOf(
                0x4D, 0x54, 0x68, 0x64, 0, 0, 0, 6, 0, 0, 0, 1, 0x03, 0xC0.toByte(),
                0x4D, 0x54, 0x72, 0x6B,
                (body.size shr 24).toByte(), (body.size shr 16).toByte(), (body.size shr 8).toByte(), body.size.toByte(),
            )
        )
        file.write(body)
        return save(file.toByteArray(), lane?.let { "DRUMS_${files[it]}.mid" } ?: "DRUMS_ALL.mid")
    }

    fun exportWav(lane: Int?): File {
        val s = state
        val rate = engine.sampleRate
        val length = (16.0 * 60.0 / s.bpm / 4.0 * rate).toInt() + rate
        val mix = FloatArray(length)
        val current = engine.samples
        for (t in 0 until LANES) {
            if (lane != null && lane != t) continue
            val data = current[t] ?: continue
            for (step in 0 until STEPS) {
                if (!s.step(s.bank, t, step)) continue
                val seconds = step * 15.0 / s.bpm + if (step % 2 == 0) 0.0 else s.swing * 7.5 / s.bpm
                val offset = (seconds * rate).toInt()
                val n = minOf(data.size, length - offset)
                for (i in 0 until n) mix[offset + i] += data[i] * 0.65f
            }
        }
        return save(Wav.encodeMono16(mix, rate), lane?.let { "DRUMS_${files[it]}.wav" } ?: "DRUMS_MIX.wav")
    }

    private fun save(data: ByteArray, name: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, name).apply { writeBytes(data) }
    }

    // endregion

    // region Persistence

    private fun persist() {
        val s = state
        val json = JSONObject()
            .put("artist", s.artist)
            .put("preset", s.preset)
            .put("bank", s.bank)
            .put("selected", s.selected)
            .put("bpm", s.bpm)
            .put("swing", s.swing)
            .put("patterns", s.patterns.joinToString("") { if (it) "1" else "0" })
            .put("customSamples", JSONArray(s.customSamples))
        runCatching {
            val tmp = File(stateFile.path + ".tmp")
            tmp.writeText(json.toString())
            tmp.renameTo(stateFile)
        }
    }

    private fun load(): DrumState? {
        if (!stateFile.exists()) return null
        val o = JSONObject(stateFile.readText())
        val bits = o.getString("patterns")
        val custom = o.getJSONArray("customSamples")
        if (bits.length != BANKS * LANES * STEPS || custom.length() != LANES) return null
        return DrumState(
            artist = o.getInt("artist").coerceIn(0, 4),
            preset = o.getInt("preset").coerceIn(0, 2),
            bank = o.getInt("bank").coerceIn(0, BANKS - 1),
            selected = o.getInt("selected").coerceIn(0, LANES - 1),
            bpm = o.getInt("bpm").coerceIn(60, 220),
            swing = o.getDouble("swing").coerceIn(0.0, 0.6),
            patterns = bits.map { it == '1' },
            customSamples = List(LANES) { custom.getString(it) },
        )
    }

    // endregion
}

private fun varLength(value: Int): ByteArray {
    val bytes = ArrayList<Byte>()
    bytes.add((value and 0x7F).toByte())
    var n = value shr 7
    while (n > 0) {
        bytes.add(0, ((n and 0x7F) or 0x80).toByte())
        n = n shr 7
    }
    return bytes.toByteArray()
}
