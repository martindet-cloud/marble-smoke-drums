package com.marblesmoke.drums.pocket

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import kotlin.math.roundToInt

private val Background = Color(0xFF131514)
private val PadBackground = Color(0xFF2B302E)
private val SequencerBackground = Color(0xFF1F2421)
private val StepOff = Color(0xFF61635C)
private val Accents = listOf(
    Color(0xFFE0F54A), // lime
    Color(0xFFF07D3B), // copper
    Color(0xFF66A8FC), // blue
    Color(0xFFDEB06E), // amber
    Color(0xFFD66EE8), // violet
)

class MainActivity : ComponentActivity() {
    private lateinit var machine: DrumMachine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        machine = DrumMachine.get(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DrumScreen(machine)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        machine.engine.start()
    }

    override fun onStop() {
        machine.stop()
        machine.engine.stop()
        super.onStop()
    }
}

@Composable
private fun DrumScreen(m: DrumMachine) {
    val s = m.state
    val accent = Accents[s.artist]
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<File?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) m.importSample(uri)
    }
    val onSaved: (android.net.Uri?) -> Unit = { uri ->
        val file = pendingExport
        pendingExport = null
        if (uri != null && file != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(file.readBytes()) }
            }.onFailure { m.error = "Salvataggio non riuscito: ${it.message}" }
        }
    }
    val midiSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/midi"), onSaved)
    val wavSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav"), onSaved)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // Tablet: everything a bit bigger.
        val f = when {
            maxHeight > 700.dp -> 1.5f
            maxHeight > 480.dp -> 1.25f
            else -> 1f
        }
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy((8 * f).dp),
        ) {
            Header(m, s, accent, f)
            PadRow(m, s, accent, f, Modifier.weight(1f))
            Controls(m, s, accent, f)
            Sequencer(m, s, accent, f, Modifier.weight(1.1f))
            Footer(
                m, s, accent, f,
                onImport = { importer.launch(arrayOf("audio/wav", "audio/x-wav", "audio/wave", "audio/*")) },
                onExport = { pendingExport = it },
            )
        }
    }

    pendingExport?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingExport = null },
            title = { Text(file.name) },
            text = { Text("Vuoi salvare il file sul dispositivo o condividerlo con un'altra app?") },
            confirmButton = {
                TextButton(onClick = {
                    if (file.name.endsWith(".mid")) midiSaver.launch(file.name) else wavSaver.launch(file.name)
                }) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingExport = null
                    share(context, file)
                }) { Text("Condividi") }
            },
        )
    }

    m.error?.let { message ->
        AlertDialog(
            onDismissRequest = { m.error = null },
            title = { Text("DRUMS") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { m.error = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun Mono(
    text: String,
    size: Float,
    color: Color = Color.White,
    weight: FontWeight = FontWeight.Bold,
    modifier: Modifier = Modifier,
) {
    Text(
        text, modifier,
        color = color, fontSize = size.sp, fontWeight = weight, fontFamily = FontFamily.Monospace,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Header(m: DrumMachine, s: DrumState, accent: Color, f: Float) {
    Row(
        Modifier.fillMaxWidth().height((44 * f).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width((181 * f).dp)) {
            Text(
                "MARBLE SMOKE", color = Color.White, fontSize = (17 * f).sp,
                fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1,
            )
            Mono("DRUMS · POCKET EDITION", 9 * f, accent)
        }
        Spacer(Modifier.weight(1f))
        Selector("ARTIST", DrumMachine.artists[s.artist], DrumMachine.artists, accent, f) { m.selectArtist(it) }
        Selector("PRESET", DrumMachine.presets[s.artist][s.preset], DrumMachine.presets[s.artist], accent, f) {
            m.selectPreset(it)
        }
        Text(
            DrumMachine.artists[s.artist],
            modifier = Modifier.widthIn(max = (130 * f).dp),
            color = accent, fontSize = (14 * f).sp, fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold,
            maxLines = 1, textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun Selector(
    title: String,
    value: String,
    options: List<String>,
    accent: Color,
    f: Float,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .width((152 * f).dp)
                .height((42 * f).dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                .clickable { open = true }
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Mono(title, 9 * f, Color.Gray)
            Mono(value.uppercase() + " ▾", 11 * f, accent)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onPick(index) })
            }
        }
    }
}

@Composable
private fun PadRow(m: DrumMachine, s: DrumState, accent: Color, f: Float, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((5 * f).dp)) {
        for (lane in 0 until LANES) {
            val selected = lane == s.selected
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(PadBackground)
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) accent else Color.Gray.copy(alpha = 0.35f),
                        RoundedCornerShape(6.dp),
                    )
                    // Fire on touch-down, not on release: lower latency when playing the pads.
                    .pointerInput(lane) { detectTapGestures(onPress = { m.selectLane(lane) }) }
                    .padding((7 * f).dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mono("%02d".format(lane + 1), 9 * f, Color.Gray)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size((5 * f).dp).clip(CircleShape)
                            .background(if (selected) accent else Color.Gray)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    DrumMachine.names[lane], color = Color.White, fontSize = (11 * f).sp,
                    fontWeight = FontWeight.ExtraBold, maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().height(3.dp).clip(CircleShape)
                        .background(if (selected) accent else accent.copy(alpha = 0.35f))
                )
            }
        }
    }
}

@Composable
private fun Controls(m: DrumMachine, s: DrumState, accent: Color, f: Float) {
    Row(
        Modifier.fillMaxWidth().height((30 * f).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((9 * f).dp),
    ) {
        Mono("PATTERN", 10 * f, Color.Gray)
        for (bank in 0 until BANKS) {
            val on = bank == s.bank
            Box(
                Modifier
                    .size((25 * f).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (on) accent else Color.White.copy(alpha = 0.13f))
                    .clickable { m.selectBank(bank) },
                contentAlignment = Alignment.Center,
            ) {
                Mono(('A' + bank).toString(), 11 * f, if (on) Color.Black else Color.White, FontWeight.Black)
            }
        }
        Spacer(Modifier.weight(1f))
        // Tap: ±1 BPM. Long press: ±10 BPM.
        Mono(
            "−", 20 * f, Color.White,
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { m.setBpm(m.state.bpm - 1) },
                        onLongPress = { m.setBpm(m.state.bpm - 10) },
                    )
                }
                .padding(horizontal = 10.dp),
        )
        Mono("${s.bpm} BPM", 12 * f, accent)
        Mono(
            "+", 20 * f, Color.White,
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { m.setBpm(m.state.bpm + 1) },
                        onLongPress = { m.setBpm(m.state.bpm + 10) },
                    )
                }
                .padding(horizontal = 10.dp),
        )
        Spacer(Modifier.weight(1f))
        Mono(
            "SWING ${(s.swing * 100).roundToInt()}%", 10 * f, Color.White,
            modifier = Modifier
                .clickable { m.setSwing(if (s.swing >= 0.6) 0.0 else s.swing + 0.1) }
                .padding(6.dp),
        )
    }
}

@Composable
private fun Sequencer(m: DrumMachine, s: DrumState, accent: Color, f: Float, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SequencerBackground)
            .padding((8 * f).dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row {
            Mono("STEP SEQUENCER / ${DrumMachine.names[s.selected]}", 9 * f, Color.Gray)
            Spacer(Modifier.weight(1f))
            Mono("16 STEPS · 1 BAR", 9 * f, Color.Gray)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy((4 * f).dp)) {
            for (step in 0 until STEPS) {
                val active = s.step(s.bank, s.selected, step)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(step) { detectTapGestures(onPress = { m.toggle(step) }) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Mono("%02d".format(step + 1), 8 * f, if (step % 4 == 0) Color.White else Color.Gray)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (active) accent else StepOff)
                            .border(
                                2.dp,
                                if (step == m.playStep) Color.White else Color.Transparent,
                                RoundedCornerShape(3.dp),
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun Footer(
    m: DrumMachine,
    s: DrumState,
    accent: Color,
    f: Float,
    onImport: () -> Unit,
    onExport: (File) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height((38 * f).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((10 * f).dp),
    ) {
        Box(
            Modifier
                .height((35 * f).dp)
                .clip(RoundedCornerShape(5.dp))
                .background(accent)
                .clickable { m.togglePlay() }
                .padding(horizontal = (18 * f).dp),
            contentAlignment = Alignment.Center,
        ) {
            Mono(if (m.playing) "■ STOP" else "▶ PLAY", 11 * f, Color.Black, FontWeight.ExtraBold)
        }
        Mono("LOAD WAV", 11 * f, Color.White, FontWeight.ExtraBold, Modifier.clickable(onClick = onImport).padding(8.dp))
        Spacer(Modifier.weight(1f))
        val laneName = DrumMachine.names[s.selected]
        ExportMenu("EXPORT MIDI ▾", "Pattern completo", "Solo $laneName", f) { all ->
            onExport(m.exportMidi(if (all) null else s.selected))
        }
        ExportMenu("EXPORT WAV ▾", "Mix completo", "Solo $laneName", f) { all ->
            onExport(m.exportWav(if (all) null else s.selected))
        }
    }
}

@Composable
private fun ExportMenu(title: String, allLabel: String, laneLabel: String, f: Float, onPick: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Mono(title, 11 * f, Color.White, FontWeight.ExtraBold, Modifier.clickable { open = true }.padding(8.dp))
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { open = false; onPick(true) })
            DropdownMenuItem(text = { Text(laneLabel) }, onClick = { open = false; onPick(false) })
        }
    }
}

private fun share(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val mime = if (file.name.endsWith(".mid")) "audio/midi" else "audio/wav"
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, file.name))
}
