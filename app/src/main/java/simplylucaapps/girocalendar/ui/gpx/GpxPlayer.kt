package simplylucaapps.girocalendar.ui.gpx

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import simplylucaapps.girocalendar.R
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Riproduzione di un GPX con i tempi: un marker percorre il tracciato a velocità 1–20× (default 20×) e, se si concede
 * l'accesso alle foto, le foto scattate nell'intervallo del percorso compaiono in miniatura sulla mappa (posizione dai
 * metadati GPS, altrimenti stimata dall'orario). Lo stato è globale perché la mappa esiste due volte (scheda e schermo intero).
 */
object GpxPlayback {
    var track by mutableStateOf<GTrack?>(null)
    var t by mutableDoubleStateOf(0.0)               // secondi virtuali dall'inizio
    var playing by mutableStateOf(false)
    var speed by mutableFloatStateOf(20f)
    var posJs by mutableStateOf("")
    var trackJs by mutableStateOf("")                // tracciato completo (grigio) su cui si colora il percorso già fatto
    var showElev by mutableStateOf(true)
    var seeking by mutableStateOf(false)              // durante il trascinamento dei cursori il tempo non avanza da solo
    var follow by mutableStateOf(false)               // la mappa segue il marker solo se lo si chiede (◎)

    fun hasTimes(t: GTrack) = t.pts.count { it.time != null } >= 2
    fun start(tr: GTrack) { follow = false; t = 0.0; speed = 20f; playing = true; track = tr }
    fun stop() { playing = false; track = null; trackJs = ""; posJs = "if (window.clearPlay) clearPlay();" }
}


private fun timed(tr: GTrack) = tr.pts.filter { it.time != null }

/** Posizione del tracciato al tempo [ms] (interpolazione lineare fra i due punti vicini); null fuori intervallo. */
private fun posAt(pts: List<GPt>, ms: Long): Triple<Double, Double, Int>? {
    if (pts.size < 2) return null
    if (ms <= pts.first().time!!.toEpochMilli()) return Triple(pts.first().lat, pts.first().lon, 0)
    if (ms >= pts.last().time!!.toEpochMilli()) return Triple(pts.last().lat, pts.last().lon, pts.size - 1)
    var lo = 0; var hi = pts.size - 1
    while (hi - lo > 1) { val mid = (lo + hi) / 2; if (pts[mid].time!!.toEpochMilli() <= ms) lo = mid else hi = mid }
    val a = pts[lo]; val b = pts[hi]
    val ta = a.time!!.toEpochMilli(); val tb = b.time!!.toEpochMilli()
    val f = if (tb == ta) 0.0 else (ms - ta).toDouble() / (tb - ta)
    return Triple(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f, lo)
}

/** Va chiamato una sola volta nella scheda Mappa: muove il marker durante la riproduzione del GPX. */
@Composable
fun GpxPlaybackDriver(current: GTrack?) {
    val ctx = LocalContext.current
    val tr = GpxPlayback.track
    // Se il GPX cambia (ritaglio, rimozione, altro file) la riproduzione si ferma.
    LaunchedEffect(current, tr) { if (tr != null && current !== tr) GpxPlayback.stop() }
    DisposableEffect(Unit) { onDispose { GpxPlayback.stop() } }

    LaunchedEffect(tr) {
        if (tr == null) return@LaunchedEffect
        val ts = timed(tr)
        val stride = maxOf(1, ts.size / 3000)
        GpxPlayback.trackJs = "if (window.setPlayTrack) setPlayTrack(" + JSONArray().also { a -> ts.forEachIndexed { i, q -> if (i % stride == 0) a.put(JSONArray().put(q.lat).put(q.lon)) } } + ");"
        GpxPlayback.posJs = ts.first().let { "if (window.setPlayPos) setPlayPos(${it.lat}, ${it.lon}, false, 0);" }
    }
    // Avanzamento: ~12 aggiornamenti al secondo, tempo virtuale = tempo reale × velocità.
    LaunchedEffect(tr, GpxPlayback.playing) {
        val trk = tr ?: return@LaunchedEffect
        if (!GpxPlayback.playing) return@LaunchedEffect
        val ts = timed(trk)
        val t0 = ts.first().time!!.toEpochMilli()
        val dur = (ts.last().time!!.toEpochMilli() - t0) / 1000.0
        var last = System.nanoTime()
        while (GpxPlayback.playing && GpxPlayback.track === trk) {
            delay(80)
            val now = System.nanoTime(); val dt = (now - last) / 1e9; last = now
            if (GpxPlayback.seeking) continue
            GpxPlayback.t = (GpxPlayback.t + dt * GpxPlayback.speed).coerceAtMost(dur)
            if (GpxPlayback.t >= dur) GpxPlayback.playing = false
        }
    }
    // Posizione del marker a ogni cambio di tempo (anche quando si trascina il cursore).
    LaunchedEffect(tr, GpxPlayback.t, GpxPlayback.follow) {
        val trk = tr ?: return@LaunchedEffect
        val ts = timed(trk)
        val p = posAt(ts, ts.first().time!!.toEpochMilli() + (GpxPlayback.t * 1000).toLong()) ?: return@LaunchedEffect
        val stride = maxOf(1, ts.size / 3000)
        GpxPlayback.posJs = "if (window.setPlayPos) setPlayPos(${p.first}, ${p.second}, ${GpxPlayback.follow}, ${p.third / stride});"
    }
}

private fun fmtClock(ms: Long) = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))
private fun fmtDur(s: Double): String { val v = s.toLong(); return String.format(Locale.getDefault(), "%d:%02d:%02d", v / 3600, (v % 3600) / 60, v % 60) }

private fun eleAt(ts: List<GPt>, ms: Long): Double? {
    val p = posAtIdx(ts, ms)
    return ts.getOrNull(p)?.ele
}
private fun posAtIdx(ts: List<GPt>, ms: Long): Int {
    var lo = 0; var hi = ts.size - 1
    while (hi - lo > 1) { val m = (lo + hi) / 2; if (ts[m].time!!.toEpochMilli() <= ms) lo = m else hi = m }
    return lo
}

/** Altimetria (quota nel tempo) con la parte già percorsa colorata; toccando o trascinando si salta in quel punto. */
@Composable
private fun ElevationChart(ts: List<GPt>, t0: Long, dur: Double, modifier: Modifier = Modifier) {
    val n = 160
    val ys = remember(ts) {
        var last: Double? = ts.firstOrNull { it.ele != null }?.ele
        List(n) { i -> (eleAt(ts, t0 + (dur * 1000 * i / (n - 1)).toLong()) ?: last).also { if (it != null) last = it } }
    }
    val vals = ys.filterNotNull()
    if (vals.isEmpty()) return
    val mn = vals.min(); val mx = vals.max(); val span = (mx - mn).coerceAtLeast(10.0)
    val base = Color(0xFF9E9E9E); val done = Color(0xFFFF6D00)
    Box(modifier) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().pointerInput(dur) {
            detectTapGestures { o -> GpxPlayback.t = (o.x / size.width * dur).coerceIn(0.0, dur) }
        }.pointerInput(dur) {
            detectHorizontalDragGestures(onDragStart = { GpxPlayback.seeking = true }, onDragEnd = { GpxPlayback.seeking = false }, onDragCancel = { GpxPlayback.seeking = false }) { c, _ -> GpxPlayback.t = (c.position.x / size.width * dur).coerceIn(0.0, dur) }
        }) {
            val w = size.width; val h = size.height
            fun x(i: Int) = w * i / (n - 1)
            fun y(v: Double?) = h - ((((v ?: mn) - mn) / span).toFloat() * (h - 6f)) - 3f
            val line = androidx.compose.ui.graphics.Path().apply { ys.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
            val fill = androidx.compose.ui.graphics.Path().apply { addPath(line); lineTo(w, h); lineTo(0f, h); close() }
            drawPath(fill, base.copy(alpha = 0.25f))
            drawPath(line, base, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
            val cx = (GpxPlayback.t / dur).toFloat().coerceIn(0f, 1f) * w
            clipRect(0f, 0f, cx, h) {
                drawPath(fill, done.copy(alpha = 0.35f))
                drawPath(line, done, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            }
            drawLine(Color(0xFFE53935), androidx.compose.ui.geometry.Offset(cx, 0f), androidx.compose.ui.geometry.Offset(cx, h), strokeWidth = 2f)
        }
        Text("${mn.toInt()}–${mx.toInt()} m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.TopEnd).padding(end = 2.dp))
    }
}

/** Pannello di controllo (compatto): play/pausa, ricomincia, tempo, velocità 1–40×, altimetria nascondibile, chiudi. */
@Composable
fun GpxPlayerPanel(modifier: Modifier = Modifier) {
    val tr = GpxPlayback.track ?: return
    val ts = remember(tr) { timed(tr) }
    val t0 = ts.first().time!!.toEpochMilli()
    val dur = ((ts.last().time!!.toEpochMilli() - t0) / 1000.0).coerceAtLeast(1.0)
    val hasEle = remember(ts) { ts.any { it.ele != null } }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Surface(modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), shadowElevation = 6.dp) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (GpxPlayback.t >= dur) GpxPlayback.t = 0.0
                        GpxPlayback.playing = !GpxPlayback.playing
                    }, modifier = Modifier.size(32.dp)) { Icon(if (GpxPlayback.playing) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(R.string.pl_play), Modifier.size(22.dp)) }
                    IconButton(onClick = { GpxPlayback.t = 0.0; GpxPlayback.playing = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Replay, stringResource(R.string.pl_restart), Modifier.size(20.dp)) }
                    Text(fmtClock(t0 + (GpxPlayback.t * 1000).toLong()) + " · " + fmtDur(GpxPlayback.t) + "/" + fmtDur(dur), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f).padding(horizontal = 6.dp), maxLines = 1)
                    TextButton(onClick = { GpxPlayback.follow = !GpxPlayback.follow }, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.height(32.dp)) {
                        Text(if (GpxPlayback.follow) "◉" else "◎", style = MaterialTheme.typography.titleSmall)
                    }
                    if (hasEle) TextButton(onClick = { GpxPlayback.showElev = !GpxPlayback.showElev }, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.height(32.dp)) {
                        Text(if (GpxPlayback.showElev) "▼ " + stringResource(R.string.pl_elev) else "▲ " + stringResource(R.string.pl_elev), style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { GpxPlayback.stop() }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, stringResource(R.string.action_close), Modifier.size(20.dp)) }
                }
                Slider(value = GpxPlayback.t.toFloat(), onValueChange = { GpxPlayback.seeking = true; GpxPlayback.t = it.toDouble() }, onValueChangeFinished = { GpxPlayback.seeking = false }, valueRange = 0f..dur.toFloat(), modifier = Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pl_speed, GpxPlayback.speed.toInt()), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(34.dp))
                    Slider(value = GpxPlayback.speed, onValueChange = { GpxPlayback.speed = it.toInt().toFloat().coerceIn(1f, 60f) }, valueRange = 1f..60f, steps = 58, modifier = Modifier.weight(1f).height(24.dp))
                }
                if (hasEle && GpxPlayback.showElev) ElevationChart(ts, t0, dur, Modifier.fillMaxWidth().height(52.dp))
            }
        }
    }
}
