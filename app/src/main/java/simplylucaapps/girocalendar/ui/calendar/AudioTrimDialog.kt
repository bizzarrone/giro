package simplylucaapps.girocalendar.ui.calendar

import androidx.compose.ui.res.stringResource

import simplylucaapps.girocalendar.R

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import simplylucaapps.girocalendar.util.AudioEditor
import simplylucaapps.girocalendar.util.SourceAudio
import kotlin.math.roundToLong

/** Massima durata del clip risultante, in millisecondi. L'audio vive in un documento Firestore
 * separato dall'evento (limite di 1 MiB indipendente da foto/GPX): a 128 kbps stereo (qualità
 * applicata da AudioEditor) 45s pesano circa 720 KB binari, entro AudioProcessor.MAX_BYTES. */
private const val MAX_CLIP_MS = 45_000L

/**
 * Editor del file audio scelto per l'evento: taglio (inizio/fine) e fade-in/fade-out
 * opzionali, con anteprima riproducibile prima di confermare. Alla conferma esporta un nuovo
 * file M4A con [AudioEditor] e lo restituisce tramite [onApply].
 */
@UnstableApi
@Composable
fun AudioTrimDialog(
    source: SourceAudio,
    onDismiss: () -> Unit,
    onApply: (java.io.File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val totalMs = source.durationMs

    var startMs by remember { mutableStateOf(0f) }
    var endMs by remember { mutableStateOf(minOf(totalMs, MAX_CLIP_MS).toFloat()) }
    var fadeIn by remember { mutableStateOf(false) }
    var fadeOut by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Player di anteprima: riproduce solo la porzione [startMs, endMs] selezionata, per
    // sentire davvero cosa verrà salvato prima di applicare il taglio definitivo.
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    DisposableEffect(source.uri) {
        onDispose { player?.release() }
    }

    fun stopPreview() {
        player?.stop()
        player?.release()
        player = null
        isPlaying = false
    }

    fun playPreview() {
        stopPreview()
        // setDataSource()/prepare() possono fare I/O di rete per URI di provider come Nextcloud:
        // vanno fuori dal main thread (NetworkOnMainThreadException altrimenti), come già per
        // AudioProcessor.durationOf.
        scope.launch {
            val mp = withContext(Dispatchers.IO) {
                MediaPlayer().apply {
                    setDataSource(context, source.uri)
                    prepare()
                }
            }
            if (!isActive) { mp.release(); return@launch }
            mp.setOnCompletionListener { isPlaying = false }
            mp.seekTo(startMs.toInt())
            mp.start()
            player = mp
            isPlaying = true
            // Ferma da sola alla fine del segmento selezionato (non solo alla fine del file).
            kotlinx.coroutines.delay((endMs - startMs).toLong())
            if (player === mp) stopPreview()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                val msgExpFail = stringResource(R.string.t_export_failed)
                val msgExpFailFmt = stringResource(R.string.t_export_failed_msg, "%s")
                Text(stringResource(R.string.t_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.t_duration, "%.1f".format(java.util.Locale.getDefault(), (endMs - startMs) / 1000f)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                Text("Intervallo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RangeSlider(
                    value = startMs..endMs,
                    onValueChange = { range ->
                        val newStart = range.start
                        val newEnd = range.endInclusive
                        // Vincola la selezione a non superare i 30s, spostando l'estremo
                        // opposto a quello che l'utente sta trascinando invece di bloccarlo.
                        if (newEnd - newStart <= MAX_CLIP_MS) {
                            startMs = newStart; endMs = newEnd
                        } else if (newStart != startMs) {
                            startMs = newStart; endMs = minOf(totalMs.toFloat(), newStart + MAX_CLIP_MS)
                        } else {
                            endMs = newEnd; startMs = maxOf(0f, newEnd - MAX_CLIP_MS)
                        }
                    },
                    valueRange = 0f..totalMs.toFloat(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMs(startMs.roundToLong()), style = MaterialTheme.typography.bodySmall)
                    Text(formatMs(endMs.roundToLong()), style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.t_fade_in), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = fadeIn, onCheckedChange = { fadeIn = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.t_fade_out), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = fadeOut, onCheckedChange = { fadeOut = it })
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { if (isPlaying) stopPreview() else playPreview() }) {
                        Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, "Anteprima")
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { stopPreview(); onDismiss() }) { Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.action_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = !exporting,
                        onClick = {
                            stopPreview()
                            exporting = true
                            error = null
                            scope.launch {
                                try {
                                    val clipMs = (endMs - startMs).toLong()
                                    val fadeInMs = if (fadeIn) minOf(2000L, clipMs / 3) else 0L
                                    val fadeOutMs = if (fadeOut) minOf(2000L, clipMs / 3) else 0L
                                    val file = AudioEditor.trimWithFade(
                                        context = context,
                                        sourceUri = source.uri,
                                        startMs = startMs.toLong(),
                                        endMs = endMs.toLong(),
                                        fadeInMs = fadeInMs,
                                        fadeOutMs = fadeOutMs,
                                    )
                                    exporting = false
                                    if (file != null) onApply(file) else error = msgExpFail
                                } catch (e: Exception) {
                                    exporting = false
                                    error = msgExpFailFmt.replace("%s", e.message ?: "")
                                }
                            }
                        },
                    ) {
                        if (exporting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Applica")
                        }
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
