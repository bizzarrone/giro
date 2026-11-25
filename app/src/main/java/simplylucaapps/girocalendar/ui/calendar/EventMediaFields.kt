package simplylucaapps.girocalendar.ui.calendar

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.util.AudioProcessor
import simplylucaapps.girocalendar.util.AudioResult
import simplylucaapps.girocalendar.util.CardBackgroundProcessor
import simplylucaapps.girocalendar.util.GpxProcessor
import simplylucaapps.girocalendar.util.GpxResult
import simplylucaapps.girocalendar.util.SourceAudio

/*
 * Campi allegato (traccia GPX, immagine di sfondo, audio) condivisi da [EventEditScreen] e da
 * [ProposeEventScreen]: stessa UI, stessi processor (compressione/ritaglio/taglio audio).
 */

/** Traccia GPX: [onChange] riceve (gpxData compresso, nome file); entrambi vuoti = rimossa. */
@Composable
internal fun EventGpxField(gpxData: String, gpxFileName: String, onChange: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var tooLargeKb by remember { mutableStateOf<Int?>(null) }
    val pickGpx = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = queryFileName(context, uri) ?: "traccia.gpx"
        uploading = true
        tooLargeKb = null
        scope.launch {
            when (val result = GpxProcessor.load(context, uri, name)) {
                is GpxResult.Ok -> onChange(result.gpxData, result.fileName)
                is GpxResult.TooLarge -> tooLargeKb = result.originalSizeKb
                GpxResult.ReadError -> tooLargeKb = null
            }
            uploading = false
        }
    }
    FieldWithIcon(Icons.Default.Route) {
        Column(Modifier.fillMaxWidth()) {
            if (gpxData.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Route, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        gpxFileName.ifBlank { stringResource(R.string.editor_gpx_default_name) },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onChange("", "") }) {
                        Icon(Icons.Default.Close, stringResource(R.string.editor_gpx_remove))
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { pickGpx.launch("*/*") },
                    enabled = !uploading,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                ) {
                    Text(if (uploading) stringResource(R.string.editor_gpx_uploading) else stringResource(R.string.editor_gpx_upload), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (tooLargeKb != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.editor_gpx_too_large, tooLargeKb ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Immagine di sfondo: [onChange] riceve (cardBgData ritagliata 16:9, photoData intera); vuoti = rimossa. */
@Composable
internal fun EventBackgroundField(cardBgData: String, onChange: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    val pickBgImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            val photo = CardBackgroundProcessor.compressToBase64(context, uri)
            uploading = false
            if (photo != null) onChange(photo.cardBgData, photo.photoData)
        }
    }
    FieldWithIcon(Icons.Default.Image) {
        Column(Modifier.fillMaxWidth()) {
            if (cardBgData.isNotBlank()) {
                val bmp = remember(cardBgData) {
                    runCatching {
                        val bytes = Base64.decode(cardBgData, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }.getOrNull()
                }
                Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp))) {
                    if (bmp != null) {
                        Image(bmp, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize())
                    }
                    IconButton(
                        onClick = { onChange("", "") },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    ) {
                        Icon(Icons.Default.Close, stringResource(R.string.editor_bg_remove), tint = Color.White)
                    }
                    OutlinedButton(
                        onClick = { pickBgImage.launch("image/*") },
                        enabled = !uploading,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                    ) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(if (uploading) stringResource(R.string.editor_gpx_uploading) else stringResource(R.string.editor_bg_change), color = Color.White)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { pickBgImage.launch("image/*") },
                    enabled = !uploading,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                ) {
                    Text(if (uploading) stringResource(R.string.editor_gpx_uploading) else stringResource(R.string.editor_bg_choose), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * Audio: il file scelto apre subito l'editor di taglio (AudioTrimDialog), non si salva mai il
 * sorgente as-is. [onChange] riceve (audioData, mimeType); vuoti = rimosso.
 */
@Composable
internal fun EventAudioField(audioData: String, onChange: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var audioError by remember { mutableStateOf<String?>(null) }
    var pendingAudioSource by remember { mutableStateOf<SourceAudio?>(null) }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Provider come quello di Nextcloud fanno I/O di rete per aprire il file descriptor:
        // openSource() deve girare fuori dal main thread (NetworkOnMainThreadException altrimenti).
        scope.launch {
            val source = AudioProcessor.openSource(context, uri)
            if (source == null) {
                audioError = context.getString(R.string.editor_audio_read_error)
            } else {
                audioError = null
                pendingAudioSource = source
            }
        }
    }
    FieldWithIcon(Icons.AutoMirrored.Filled.VolumeUp) {
        Column(Modifier.fillMaxWidth()) {
            if (audioData.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.editor_audio_default_name),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onChange("", "") }) {
                        Icon(Icons.Default.Close, stringResource(R.string.editor_audio_remove))
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { pickAudio.launch("audio/*") },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                ) {
                    Text(stringResource(R.string.editor_audio_upload), style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    stringResource(R.string.editor_audio_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
            if (audioError != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    audioError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    pendingAudioSource?.let { source ->
        AudioTrimDialog(
            source = source,
            onDismiss = { pendingAudioSource = null },
            onApply = { file ->
                scope.launch {
                    when (val result = AudioProcessor.loadEdited(file, "audio/mp4")) {
                        is AudioResult.Ok -> { onChange(result.audioData, result.mimeType); audioError = null }
                        is AudioResult.TooLarge ->
                            audioError = context.getString(R.string.editor_audio_too_large, result.sizeKb)
                        AudioResult.ReadError ->
                            audioError = context.getString(R.string.editor_audio_read_error)
                    }
                    pendingAudioSource = null
                }
            },
        )
    }
}
