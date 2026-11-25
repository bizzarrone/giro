package simplylucaapps.girocalendar.ui.gpx

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import simplylucaapps.girocalendar.R
import java.io.File
import java.util.Locale

private fun fmtDist(m: Double) = if (m >= 1000) String.format(Locale.getDefault(), "%.2f km", m / 1000) else "${m.toInt()} m"
private fun fmtDur(s: Long): String = String.format(Locale.getDefault(), "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
private fun safeName(n: String) = n.replace(Regex("[^\\p{L}\\p{N} _.-]"), "_")

/** Piccolo menu "⋮" del GPX caricato nella MAPPA: statistiche, ritaglia, inverti, semplifica, salva, condividi. */
@Composable
fun GpxToolsMenu(track: GTrack, onChange: (GTrack) -> Unit, onStartCrop: () -> Unit, onPlay: () -> Unit = {}) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var simplifyDlg by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var convertDlg by remember { mutableStateOf(false) }
    var convFmt by remember { mutableStateOf(TrackFormat.KML) }
    val savedMsg = stringResource(R.string.gpx_saved)
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) runCatching { ctx.contentResolver.openOutputStream(uri)?.use { GpxModel.write(track, it) } }
            .onSuccess { msg = savedMsg }.onFailure { msg = it.message }
    }
    val convSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) runCatching { ctx.contentResolver.openOutputStream(uri)?.use { TrackFormats.write(track, convFmt, it) } }
            .onSuccess { msg = savedMsg }.onFailure { msg = it.message }
    }
    Box {
        FilledTonalIconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.gpx_tools)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (GpxPlayback.hasTimes(track)) DropdownMenuItem(text = { Text(stringResource(R.string.pl_play)) }, onClick = { open = false; onPlay() })
            DropdownMenuItem(text = { Text(stringResource(R.string.gpx_stats)) }, onClick = { open = false; showStats = true })
            DropdownMenuItem(text = { Text(stringResource(R.string.gpx_crop)) }, onClick = { open = false; onStartCrop() })
            DropdownMenuItem(text = { Text(stringResource(R.string.gpx_reverse)) }, onClick = { open = false; onChange(track.copy(pts = GpxModel.reverse(track.pts))) })
            DropdownMenuItem(text = { Text(stringResource(R.string.gpx_simplify)) }, onClick = { open = false; simplifyDlg = true })
            DropdownMenuItem(text = { Text(stringResource(R.string.gpx_save)) }, onClick = { open = false; saveLauncher.launch(safeName(track.name) + ".gpx") })
            DropdownMenuItem(text = { Text(stringResource(R.string.gpx_convert)) }, onClick = { open = false; convertDlg = true })
            DropdownMenuItem(text = { Text(stringResource(R.string.gpx_share)) }, onClick = {
                open = false
                runCatching {
                    val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
                    val f = File(dir, safeName(track.name) + ".gpx")
                    f.outputStream().use { GpxModel.write(track, it) }
                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/gpx+xml"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { msg = it.message }
            })
        }
    }
    if (convertDlg) {
        AlertDialog(
            onDismissRequest = { convertDlg = false },
            title = { Text(stringResource(R.string.gpx_convert)) },
            text = {
                Column {
                    TrackFormat.values().forEach { f ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected = convFmt == f, onClick = { convFmt = f })
                            TextButton(onClick = { convFmt = f }) { Text(f.label + " (." + f.ext + ")") }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        convertDlg = false
                        runCatching {
                            val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
                            val f = File(dir, safeName(track.name) + "." + convFmt.ext)
                            f.outputStream().use { TrackFormats.write(track, convFmt, it) }
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = convFmt.mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.onFailure { msg = it.message }
                    }) { Text(stringResource(R.string.gpx_share)) }
                    TextButton(onClick = { convertDlg = false; convSaveLauncher.launch(safeName(track.name) + "." + convFmt.ext) }) { Text(stringResource(R.string.gpx_save)) }
                }
            },
            dismissButton = { TextButton(onClick = { convertDlg = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    if (showStats) {
        val s = remember(track) { GpxModel.stats(track.pts) }
        AlertDialog(
            onDismissRequest = { showStats = false },
            confirmButton = { TextButton(onClick = { showStats = false }) { Text(stringResource(android.R.string.ok)) } },
            title = { Text(track.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.gpx_st_dist, fmtDist(s.distM)))
                    Text(stringResource(R.string.gpx_st_points, track.pts.size))
                    Text(stringResource(R.string.gpx_st_gain, s.gain.toInt(), s.loss.toInt()))
                    if (s.minEle != null && s.maxEle != null) Text(stringResource(R.string.gpx_st_ele, s.minEle.toInt(), s.maxEle.toInt()))
                    if (s.durSec != null) Text(stringResource(R.string.gpx_st_time, fmtDur(s.durSec)))
                    if (s.avgKmh != null) Text(stringResource(R.string.gpx_st_speed, String.format(Locale.getDefault(), "%.1f", s.avgKmh)))
                }
            },
        )
    }
    if (simplifyDlg) {
        var tol by remember { mutableFloatStateOf(5f) }
        val preview = remember(track, tol) { GpxModel.simplify(track.pts, tol.toDouble()).size }
        AlertDialog(
            onDismissRequest = { simplifyDlg = false },
            title = { Text(stringResource(R.string.gpx_simplify)) },
            text = {
                Column {
                    Text(stringResource(R.string.gpx_simplify_info, tol.toInt(), track.pts.size, preview))
                    Slider(value = tol, onValueChange = { tol = it }, valueRange = 1f..50f)
                }
            },
            confirmButton = { TextButton(onClick = { onChange(track.copy(pts = GpxModel.simplify(track.pts, tol.toDouble()))); simplifyDlg = false }) { Text(stringResource(R.string.gpx_crop_apply)) } },
            dismissButton = { TextButton(onClick = { simplifyDlg = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    msg?.let { m -> AlertDialog(onDismissRequest = { msg = null }, confirmButton = { TextButton(onClick = { msg = null }) { Text(stringResource(android.R.string.ok)) } }, text = { Text(m) }) }
}

/** Pannello di ritaglio: la mappa mostra in diretta solo la parte scelta ([onPreview]); Applica la conferma. */
@Composable
fun GpxCropPanel(track: GTrack, onPreview: (List<GPt>?) -> Unit, onApply: (List<GPt>) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var range by remember { mutableStateOf(0f..1f) }
    val n = track.pts.size
    val a = (range.start * (n - 1)).toInt().coerceIn(0, n - 1)
    val b = (range.endInclusive * (n - 1)).toInt().coerceIn(a, n - 1)
    val part = remember(track, a, b) { track.pts.subList(a, b + 1) }
    LaunchedEffect(part) { onPreview(part) }
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), shadowElevation = 6.dp) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.gpx_crop_hint, fmtDist(GpxModel.stats(part).distM), part.size), style = MaterialTheme.typography.bodyMedium)
            RangeSlider(value = range, onValueChange = { range = it }, valueRange = 0f..1f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (part.size >= 2) onApply(part) else onCancel() }) { Text(stringResource(R.string.gpx_crop_apply)) }
                TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
            }
        }
    }
}
