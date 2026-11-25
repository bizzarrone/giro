package simplylucaapps.girocalendar.ui.trails

import simplylucaapps.girocalendar.ui.common.CompactTextField
import androidx.compose.ui.res.stringResource

import simplylucaapps.girocalendar.R

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import simplylucaapps.girocalendar.util.LatLon
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Un punto registrato: lat, lon, quota (NaN se assente), istante (ms). */
data class RecPoint(val lat: Double, val lon: Double, val ele: Double, val t: Long)

/** Stato della registrazione GPX (condiviso tra servizio e interfaccia). I punti sono anche salvati su file per non perderli se l'app muore. */
object TrackRecorder {
    var active by mutableStateOf(false); private set
    var startMillis by mutableLongStateOf(0L); private set
    var distanceM by mutableDoubleStateOf(0.0); private set
    var gainM by mutableDoubleStateOf(0.0); private set
    var count by mutableIntStateOf(0); private set
    /** Cruscotto: tempo in movimento, velocità istantanea (m/s), quota attuale (media degli ultimi punti) e pausa. */
    var movingMs by mutableLongStateOf(0L); private set
    var speedMs by mutableDoubleStateOf(0.0); private set
    var eleNow by mutableDoubleStateOf(Double.NaN); private set
    var paused by mutableStateOf(false); private set
    private var pauseStart = 0L
    private var pausedAccum = 0L
    private val recentEle = ArrayDeque<Double>()
    /** Registrazione conclusa in attesa di essere salvata/scartata. */
    var finished by mutableStateOf<List<RecPoint>?>(null)
    private val pts = ArrayList<RecPoint>()
    private var lastEle = Double.NaN
    private var file: File? = null
    private val fileIo = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "rec-file").apply { isDaemon = true } }
    private var appCtx: Context? = null
    /** Dove è stato salvato in automatico il GPX appena concluso (per mostrarlo all'utente). */
    var savedPath by mutableStateOf<String?>(null)
    var savedUri by mutableStateOf<android.net.Uri?>(null)

    fun begin(context: Context) {
        pts.clear(); distanceM = 0.0; gainM = 0.0; count = 0; lastEle = Double.NaN
        movingMs = 0L; speedMs = 0.0; eleNow = Double.NaN; paused = false; pausedAccum = 0L; recentEle.clear()
        startMillis = System.currentTimeMillis(); active = true; finished = null; savedPath = null; savedUri = null
        appCtx = context.applicationContext
        file = File(context.filesDir, "recording.tmp").also { it.writeText("") }
    }

    fun add(l: Location) {
        if (active) MyLoc.pos = Triple(l.latitude, l.longitude, l.accuracy)  // il pallino blu segue la registrazione
        if (!active || paused || l.hasAccuracy() && l.accuracy > 40f) return
        speedMs = if (l.hasSpeed()) l.speed.toDouble() else 0.0
        if (l.hasAltitude()) { recentEle.addLast(l.altitude); if (recentEle.size > 5) recentEle.removeFirst(); eleNow = recentEle.average() }
        val p = RecPoint(l.latitude, l.longitude, if (l.hasAltitude()) l.altitude else Double.NaN, l.time.takeIf { it > 0 } ?: System.currentTimeMillis())
        pts.lastOrNull()?.let {
            val d = dist(it.lat, it.lon, p.lat, p.lon)
            if (d < 3.0) return  // fermo / rumore GPS
            distanceM += d
            movingMs += minOf(p.t - it.t, 10_000L).coerceAtLeast(0L)
        }
        if (!p.ele.isNaN()) {
            if (!lastEle.isNaN() && p.ele - lastEle >= 3.0) { gainM += p.ele - lastEle; lastEle = p.ele }
            else if (lastEle.isNaN() || lastEle - p.ele >= 3.0) lastEle = p.ele
        }
        pts.add(p); count = pts.size
        val f = file; val line = "${p.lat},${p.lon},${p.ele},${p.t}\n"
        fileIo.execute { runCatching { f?.appendText(line) } }   // scrittura su disco fuori dal thread principale (un fix al secondo)
    }

    /** Punti registrati finora (diradati oltre ~1500) per il disegno live sulla mappa. */
    fun snapshot(): List<LatLon> {
        val n = count; val step = maxOf(1, pts.size / 1500)
        return pts.filterIndexed { i, _ -> i % step == 0 || i == pts.size - 1 }.map { it.lat to it.lon }.also { @Suppress("UNUSED_EXPRESSION") n }
    }

    fun stop() {
        if (!active) return
        if (paused) { pausedAccum += System.currentTimeMillis() - pauseStart; paused = false }
        active = false
        finished = pts.toList().takeIf { it.size >= 2 }
        finished?.let { f -> appCtx?.let { autoSave(it, f) } }
        // registrazione chiusa e salvata: il file temporaneo non serve più (resta solo se il salvataggio è fallito)
        if (savedPath != null || finished == null) runCatching { file?.delete() }
    }

    // ── registrazione interrotta (app chiusa/uccisa a metà): riprendere, salvare a parte o scartare ──
    private fun tmpFile(c: Context) = File(c.filesDir, "recording.tmp")

    /** Punti di una registrazione rimasta a metà (file recente, almeno 2 punti), o null. */
    fun interrupted(c: Context): List<RecPoint>? {
        if (active) return null
        val f = tmpFile(c)
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 24L * 3600_000L) return null
        val out = ArrayList<RecPoint>()
        runCatching {
            f.forEachLine { l ->
                val a = l.split(',')
                if (a.size == 4) runCatching { out.add(RecPoint(a[0].toDouble(), a[1].toDouble(), a[2].toDouble(), a[3].toLong())) }
            }
        }
        return out.takeIf { it.size >= 2 }
    }

    private fun rebuild(points: List<RecPoint>) {
        distanceM = 0.0; gainM = 0.0; movingMs = 0L; lastEle = Double.NaN
        for (i in points.indices) {
            val p = points[i]
            if (i > 0) { val q = points[i - 1]; distanceM += dist(q.lat, q.lon, p.lat, p.lon); movingMs += minOf(p.t - q.t, 10_000L).coerceAtLeast(0L) }
            if (!p.ele.isNaN()) {
                if (!lastEle.isNaN() && p.ele - lastEle >= 3.0) { gainM += p.ele - lastEle; lastEle = p.ele }
                else if (lastEle.isNaN() || lastEle - p.ele >= 3.0) lastEle = p.ele
            }
        }
    }

    /** Riprende la registrazione interrotta: ricarica i punti e continua ad aggiungere sulla stessa traccia. */
    fun resume(c: Context): Boolean {
        val points = interrupted(c) ?: return false
        pts.clear(); pts.addAll(points); rebuild(points); count = pts.size
        speedMs = 0.0; eleNow = Double.NaN; paused = false; pausedAccum = 0L; recentEle.clear()
        // il tempo trascorso tra l'interruzione e la ripresa non conta: la durata riparte da dove si era fermata
        startMillis = System.currentTimeMillis() - (points.last().t - points.first().t).coerceAtLeast(0L)
        active = true; finished = null; savedPath = null; savedUri = null
        appCtx = c.applicationContext; file = tmpFile(c)
        return true
    }

    /** Salva a parte (come GPX in Download/Giro) la registrazione interrotta e libera il file temporaneo. */
    fun saveInterrupted(c: Context) {
        val points = interrupted(c) ?: return
        appCtx = c.applicationContext; rebuild(points)
        autoSave(c, points)
        runCatching { tmpFile(c).delete() }
        pts.clear(); distanceM = 0.0; gainM = 0.0; movingMs = 0L; count = 0
    }

    fun dropInterrupted(c: Context) { runCatching { tmpFile(c).delete() } }

    /** Salva subito il GPX in Download/Giro (nessun permesso su Android 10+): la traccia non si perde più. */
    private fun autoSave(context: Context, points: List<RecPoint>) {
        runCatching {
            val name = context.getString(R.string.rec_default_name, SimpleDateFormat("dd-MM-yyyy HH.mm", Locale.getDefault()).format(Date(points.first().t)))
            val bytes = toGpx(points, name).toByteArray()
            runCatching { LastRide.save(context, name, points, distanceM, movingMs) }
            val fname = name.replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".gpx"
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fname)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/Giro")
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
                context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
                savedUri = uri; savedPath = "Download/Giro/$fname"
            } else {
                val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Giro")
                val f = runCatching { dir.mkdirs(); File(dir, fname).also { it.writeBytes(bytes) } }.getOrElse {
                    File(context.getExternalFilesDir(null), "Giro").also { d -> d.mkdirs() }.let { d -> File(d, fname).also { it.writeBytes(bytes) } }
                }
                savedUri = android.net.Uri.fromFile(f); savedPath = f.absolutePath
            }
        }
    }

    fun deleteSaved(context: Context) {
        savedUri?.let { u -> runCatching { if (u.scheme == "file") File(u.path!!).delete() else context.contentResolver.delete(u, null, null) } }
        savedUri = null; savedPath = null
    }

    fun openFolder(context: Context) {
        val tries = listOf(
            Intent(Intent.ACTION_VIEW).setDataAndType(android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download/Giro"), "vnd.android.document/directory"),
            Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS),
        )
        for (i in tries) if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        Toast.makeText(context, savedPath ?: "Download/Giro", Toast.LENGTH_LONG).show()
    }

    fun discard(context: Context) { finished = null; savedPath = null; savedUri = null; pts.clear(); runCatching { File(context.filesDir, "recording.tmp").delete() } }

    /** Pausa/ripresa: in pausa i punti GPS non vengono aggiunti e il tempo trascorso non conta. */
    fun togglePause() {
        if (!active) return
        if (paused) { pausedAccum += System.currentTimeMillis() - pauseStart; paused = false; speedMs = 0.0 }
        else { pauseStart = System.currentTimeMillis(); paused = true; speedMs = 0.0 }
    }

    fun durationMs(): Long =
        if (active) System.currentTimeMillis() - startMillis - pausedAccum - (if (paused) System.currentTimeMillis() - pauseStart else 0L)
        else (pts.lastOrNull()?.t ?: startMillis) - startMillis - pausedAccum

    fun dist(a: Double, b: Double, c: Double, d: Double): Double =
        simplylucaapps.girocalendar.ui.gpx.GpxModel.haversineM(a, b, c, d)

    fun toGpx(points: List<RecPoint>, name: String): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val esc = name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"GiroCalendar\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("<metadata><name>").append(esc).append("</name><time>").append(iso.format(Date(points.first().t))).append("</time></metadata>\n")
        sb.append("<trk><name>").append(esc).append("</name><trkseg>\n")
        points.forEach { p ->
            sb.append("<trkpt lat=\"").append("%.6f".format(Locale.US, p.lat)).append("\" lon=\"").append("%.6f".format(Locale.US, p.lon)).append("\">")
            if (!p.ele.isNaN()) sb.append("<ele>").append("%.1f".format(Locale.US, p.ele)).append("</ele>")
            sb.append("<time>").append(iso.format(Date(p.t))).append("</time></trkpt>\n")
        }
        sb.append("</trkseg></trk>\n</gpx>\n")
        return sb.toString()
    }
}

/** Servizio in primo piano: tiene viva la registrazione con lo schermo spento. */
class RecordingService : Service() {
    private var listener: LocationListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { finish(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.rec_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle(getString(R.string.rec_notif_title))
            .setContentText(getString(R.string.rec_notif_text)).setOngoing(true).setContentIntent(open).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(NOTIF_ID, n)
        if (listener == null) {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val l = object : LocationListener {
                override fun onLocationChanged(loc: Location) { TrackRecorder.add(loc) }
                @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {}
            }
            listener = l
            runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, l, Looper.getMainLooper()) }
                .onFailure { TrackRecorder.stop(); stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun finish() { TrackRecorder.stop(); stopSelf() }

    override fun onDestroy() {
        listener?.let { runCatching { (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(it) } }
        listener = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "recording"; const val NOTIF_ID = 4711; const val ACTION_STOP = "stop"
    }
}

internal fun fmtDur(ms: Long): String { val s = ms / 1000; return "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) }
internal fun fmtKm(m: Double): String = "%.2f km".format(Locale.ITALY, m / 1000)

/** Avvio della registrazione con i permessi necessari (posizione, poi notifiche). Restituisce la funzione da chiamare. */
@Composable
fun rememberRecordStarter(): () -> Unit {
    val context = LocalContext.current
    var oldRec by remember { mutableStateOf<List<RecPoint>?>(null) }
    fun go(resume: Boolean) {
        if (!(resume && TrackRecorder.resume(context))) TrackRecorder.begin(context)
        MyLoc.on = true
        ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
    }
    fun start() {
        val old = TrackRecorder.interrupted(context)
        if (old != null) oldRec = old else go(false)
    }
    oldRec?.let { o ->
        val km = o.zipWithNext().sumOf { (a, b) -> TrackRecorder.dist(a.lat, a.lon, b.lat, b.lon) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { oldRec = null },
            title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.rec_interrupted)) },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.rec_found, o.size, km / 1000, SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(o.first().t))))
                    androidx.compose.material3.Button(onClick = { oldRec = null; go(true) }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.rec_resume)) }
                    androidx.compose.material3.OutlinedButton(onClick = { oldRec = null; TrackRecorder.saveInterrupted(context); go(false) }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.rec_save_new)) }
                    androidx.compose.material3.TextButton(onClick = { oldRec = null; TrackRecorder.dropInterrupted(context); go(false) }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.rec_drop_new)) }
                }
            },
            confirmButton = {},
            dismissButton = { androidx.compose.material3.TextButton(onClick = { oldRec = null }) { androidx.compose.material3.Text(android.R.string.cancel.let { context.getString(it) }) } },
        )
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) { Toast.makeText(context, context.getString(R.string.rec_need_loc), Toast.LENGTH_LONG).show(); return@rememberLauncherForActivityResult }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) else start()
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) else start()
        } else locPerm.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

/** Ferma la registrazione (servizio incluso). */
fun stopRecording(context: Context) { context.stopService(Intent(context, RecordingService::class.java)); TrackRecorder.stop() }

@Composable
internal fun SaveRecordingDialog(points: List<RecPoint>) {
    val context = LocalContext.current
    val start = points.first().t
    val defaultName = remember(start) { context.getString(R.string.rec_default_name, SimpleDateFormat("dd-MM-yyyy HH.mm", Locale.getDefault()).format(Date(start))) }
    var name by remember(start) { mutableStateOf(defaultName) }
    var saved by remember(start) { mutableStateOf(false) }
    val gpxName = name.trim().ifBlank { defaultName }
    // L'utente sceglie cartella (e può cambiare il nome del file) con il selettore di sistema.
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(TrackRecorder.toGpx(points, gpxName).toByteArray()) } }
            .onSuccess { saved = true; Toast.makeText(context, context.getString(R.string.rec_gpx_saved), Toast.LENGTH_LONG).show() }
            .onFailure { Toast.makeText(context, context.getString(R.string.rec_save_failed), Toast.LENGTH_LONG).show() }
    }
    val ele = points.mapNotNull { it.ele.takeIf { e -> !e.isNaN() } }
    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.rec_done)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.rec_summary, fmtKm(TrackRecorder.distanceM), fmtDur(TrackRecorder.durationMs())) +
                    (if (TrackRecorder.gainM > 0) stringResource(R.string.rec_gain, TrackRecorder.gainM.toInt()) else "") + stringResource(R.string.rec_points, points.size), style = MaterialTheme.typography.bodyMedium)
                TrackRecorder.savedPath?.let { path ->
                    Text(stringResource(R.string.rec_saved_in, path), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = { TrackRecorder.openFolder(context) }, modifier = Modifier.fillMaxWidth()) { Text("📂 " + stringResource(R.string.rec_open_folder)) }
                }
                CompactTextField(name, { name = it }, label = { Text(stringResource(R.string.rec_track_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default)
                Button(onClick = { create.launch(gpxName.replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".gpx") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(if (saved) R.string.rec_save_again else R.string.rec_save_gpx), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        },
        confirmButton = { TextButton(onClick = { TrackRecorder.discard(context) }) { Text(stringResource(if (saved || TrackRecorder.savedPath != null) R.string.action_close else R.string.rec_discard), color = if (saved || TrackRecorder.savedPath != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) } },
        dismissButton = if (TrackRecorder.savedPath != null) ({ TextButton(onClick = { TrackRecorder.deleteSaved(context); TrackRecorder.discard(context) }) { Text(stringResource(R.string.rec_delete_saved), color = MaterialTheme.colorScheme.error) } }) else null,
    )
}
