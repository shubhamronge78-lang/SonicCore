package com.soniccore.app.ui.audio

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccore.app.loudness.Loudness
import com.soniccore.app.loudness.WavPcm
import com.soniccore.app.ui.design.SonicCoreColors

/**
 * The offline loudness measurement, as one reusable card.
 *
 * It lives here — outside any single screen — because it is *analysis*, not
 * processing, and both the Audio Console and Audio Diagnostics show it. One
 * implementation means the two screens cannot drift apart in what they measure
 * or in how a refusal is worded.
 *
 * A picker, a bounded read and a report: nothing here starts playback, joins
 * the playback path or adds a stage to the live chain. Every outcome is a
 * sentence — a refused format, a truncated read and a measured report read the
 * same way — so an unavailable measurement can never look like a silent one.
 */
@Composable
fun LoudnessAnalysisSection(
    modifier: Modifier = Modifier,
    description: String? = null
) {
    val context = LocalContext.current
    // Kept as lines so a refusal reads exactly like a measurement: both are
    // sentences the card shows as they are.
    var lines by remember { mutableStateOf<List<String>?>(null) }
    val pickWavFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) lines = measureWavFile(context, uri)
    }

    AudioSection(
        title = "Loudness (offline)",
        modifier = modifier,
        description = description
            ?: "Integrated loudness, loudness range and true peak of one 16-bit PCM WAV file. " +
            "Measured after the fact, in the module's own native library — never during playback."
    ) {
        Text(
            Loudness.SCOPE,
            color = SonicCoreColors.SecondaryText,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        AudioActionRow(
            actions = listOf(
                AudioAction(
                    label = "ANALYZE A .WAV FILE",
                    onClick = {
                        lines = listOf(
                            "Reading the file you pick (up to ${WavPcm.MAX_FILE_BYTES / (1024 * 1024)} MB)…"
                        )
                        pickWavFile.launch(arrayOf("audio/wav", "audio/x-wav", "audio/*"))
                    }
                )
            ),
            modifier = Modifier.padding(top = 8.dp)
        )
        lines?.forEach { line ->
            Text(
                "• $line",
                color = SonicCoreColors.PrimaryText,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
        if (lines == null) {
            Text(
                "No file measured in this session yet.",
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/**
 * Reads the picked `.wav` (bounded by [WavPcm.MAX_FILE_BYTES]), measures it and
 * returns the lines to show. Every outcome — a refused format, a truncated file,
 * a module that is not on this device — is a sentence, never a missing row.
 */
internal fun measureWavFile(context: Context, uri: Uri): List<String> {
    val bytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0
            while (total < WavPcm.MAX_FILE_BYTES) {
                val read = input.read(chunk, 0, minOf(chunk.size, WavPcm.MAX_FILE_BYTES - total))
                if (read <= 0) break
                out.write(chunk, 0, read)
                total += read
            }
            out.toByteArray()
        }
    }.getOrNull() ?: return listOf("Not measured: that file could not be read.")

    val capNote = if (bytes.size >= WavPcm.MAX_FILE_BYTES) {
        listOf(
            "Read the first ${WavPcm.MAX_FILE_BYTES / (1024 * 1024)} MB of that file — " +
                "the picker's own limit."
        )
    } else {
        emptyList()
    }

    val parsed = runCatching { WavPcm.read(bytes) }.getOrElse {
        return listOf("Not measured: that file could not be read (${it.message ?: it.toString()}).")
    }
    return when (parsed) {
        is WavPcm.Read.Refused -> listOf("Not measured: ${parsed.reason}")
        is WavPcm.Read.Ok -> runCatching {
            Loudness.reportLines(Loudness.analyze(parsed.pcm, WavPcm.SOURCE)) + parsed.notes + capNote
        }.getOrElse {
            listOf("Not measured: the measurement failed (${it.message ?: it.toString()}).")
        }
    }
}
