package simplylucaapps.girocalendar.ui.record

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.ui.emergency.EmergencyEngine
import simplylucaapps.girocalendar.ui.emergency.EmergencyPrefs
import simplylucaapps.girocalendar.ui.trails.SaveRecordingDialog
import simplylucaapps.girocalendar.ui.trails.TrackRecorder
import simplylucaapps.girocalendar.ui.trails.fmtDur
import simplylucaapps.girocalendar.ui.trails.rememberRecordStarter
import simplylucaapps.girocalendar.ui.trails.stopRecording
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SOS_RED = Color(0xFFC62828)

private fun buzz(c: Context, ms: Long = 70) {
    runCatching {
        @Suppress("DEPRECATION") val v = c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v.vibrate(ms)
    }
}

/**
 * Scheda «Registra», pensata per l'uso in sella: registrazione GPX con cruscotto, segnalazioni a un tocco (anche senza
 * registrare) e SOS a pressione lunga. Le segnalazioni restano private sul dispositivo (completamento: passo successivo).
 */
@Composable
fun RecordTab(events: List<simplylucaapps.girocalendar.data.remote.firestore.RideEvent> = emptyList(), calendarCode: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val prefs = remember { context.getSharedPreferences("girocalendar_boot", Context.MODE_PRIVATE) }
    var keepOn by remember { mutableStateOf(prefs.getBoolean("rec_keep_on", false)) }
    DisposableEffect(keepOn) { view.keepScreenOn = keepOn; onDispose { view.keepScreenOn = false } }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(TrackRecorder.active, TrackRecorder.paused) { while (TrackRecorder.active) { tick = System.currentTimeMillis(); delay(1000) } }
    @Suppress("UNUSED_EXPRESSION") tick

    val startRec = rememberRecordStarter()
    LaunchedEffect(Unit) { ReportStore.load(context) }
    var lastSaved by remember { mutableStateOf<Report?>(null) }

    // Segnalazione: serve la posizione; se manca il permesso lo si chiede e si ripete il tocco.
    var pendingType by remember { mutableStateOf<ReportType?>(null) }
    val trailRepo = remember { simplylucaapps.girocalendar.data.remote.firestore.TrailRepository() }
    fun report(type: ReportType) {
        scope.launch {
            val loc = ReportStore.locate(context)
            if (loc == null) { Toast.makeText(context, context.getString(R.string.rp_no_loc), Toast.LENGTH_LONG).show(); return@launch }
            val r = ReportStore.add(context, type, loc)
            simplylucaapps.girocalendar.data.remote.firestore.ReportsRepository.share(calendarCode, r)
            lastSaved = r; buzz(context, 120)
            val tr = simplylucaapps.girocalendar.ui.nav.TrailLink.attach(context, trailRepo, type, loc.latitude, loc.longitude)
            if (tr != null) Toast.makeText(context, context.getString(R.string.nv_trail_linked, tr.name), Toast.LENGTH_LONG).show()
        }
    }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val t = pendingType; pendingType = null
        if (ok && t != null) report(t) else if (!ok) Toast.makeText(context, context.getString(R.string.rec_need_loc), Toast.LENGTH_LONG).show()
    }
    fun onReport(type: ReportType) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) report(type)
        else { pendingType = type; locPerm.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Registrazione ──
        if (!TrackRecorder.active) {
            Button(onClick = startRec, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(16.dp)) {
                Text("● " + stringResource(R.string.rc_start), fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { TrackRecorder.togglePause() }, modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(stringResource(if (TrackRecorder.paused) R.string.rc_resume else R.string.rc_pause), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = { stopRecording(context) }, modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("■ " + stringResource(R.string.rc_stop), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Navigazione su un GPX ──
        var navTrack by remember { mutableStateOf<simplylucaapps.girocalendar.ui.gpx.GTrack?>(null) }
        var showPicker by remember { mutableStateOf(false) }
        Button(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White)) {
            Text("🧭 " + stringResource(R.string.nv_navigate_gpx), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        if (showPicker) simplylucaapps.girocalendar.ui.nav.NavPickerDialog(
            events = events,
            onPick = { name, pts -> showPicker = false; navTrack = simplylucaapps.girocalendar.ui.gpx.GTrack(0, name, pts) },
            onDismiss = { showPicker = false })
        navTrack?.let { tr ->
            simplylucaapps.girocalendar.ui.nav.NavScreen(
                lat = DoubleArray(tr.pts.size) { tr.pts[it].lat }, lon = DoubleArray(tr.pts.size) { tr.pts[it].lon },
                ele = if (tr.pts.any { it.ele != null }) DoubleArray(tr.pts.size) { tr.pts[it].ele ?: Double.NaN } else null,
                title = tr.name, calendarCode = calendarCode, onDismiss = { navTrack = null })
        }

        // ── Cruscotto (compatto: così le segnalazioni restano subito in vista) ──
        val active = TrackRecorder.active
        val dist = TrackRecorder.distanceM
        val movingH = TrackRecorder.movingMs / 3_600_000.0
        val avg = if (movingH > 0) dist / 1000.0 / movingH else 0.0
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(stringResource(R.string.rc_distance), if (active || dist > 0) "%.2f".format(Locale.ITALY, dist / 1000) else "–", "km", Modifier.weight(1f))
            Stat(stringResource(R.string.rc_time), if (active) fmtDur(TrackRecorder.durationMs()) else "–", if (TrackRecorder.paused) stringResource(R.string.rc_paused) else "", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Stat(stringResource(R.string.rc_altitude), TrackRecorder.eleNow.takeIf { active && !it.isNaN() }?.let { "%d".format(it.toInt()) } ?: "–", "m", Modifier.weight(1f), small = true)
            Stat(stringResource(R.string.rc_gain), if (active) "%d".format(TrackRecorder.gainM.toInt()) else "–", "m ↑", Modifier.weight(1f), small = true)
            Stat(stringResource(R.string.rc_speed), if (active && !TrackRecorder.paused) "%.1f".format(Locale.ITALY, TrackRecorder.speedMs * 3.6) else "–", "km/h", Modifier.weight(1f), small = true)
            Stat(stringResource(R.string.rc_avg), if (active && avg > 0) "%.1f".format(Locale.ITALY, avg) else "–", "km/h", Modifier.weight(1f), small = true)
        }
        HorizontalDivider()

        // ── Segnalazioni ──
        Text(stringResource(R.string.rp_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val types = ReportType.entries
        types.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    Column(
                        Modifier.weight(1f).height(92.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
                            .pointerInput(t) { detectTapGestures { onReport(t) } }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(t.emoji, fontSize = 34.sp)
                        Text(stringResource(t.labelRes), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 2)
                    }
                }
            }
        }
        lastSaved?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.rp_saved, r.n, r.type.emoji, stringResource(r.type.labelRes), SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(r.atMillis))), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    TextButton(onClick = { ReportStore.remove(context, r.n); simplylucaapps.girocalendar.data.remote.firestore.ReportsRepository.delete(calendarCode, r); lastSaved = null }) { Text(stringResource(R.string.rp_undo)) }
                }
            }
        }

        // ── SOS ──
        HorizontalDivider()
        SosButton(onTrigger = {
            if (EmergencyPrefs.contacts.isEmpty()) Toast.makeText(context, context.getString(R.string.rc_sos_no_contacts), Toast.LENGTH_LONG).show()
            else { buzz(context, 400); EmergencyEngine.trigger(context, false, context.getString(R.string.rc_sos_reason)) }
        })

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rc_keep_on), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(keepOn, { keepOn = it; prefs.edit().putBoolean("rec_keep_on", it).apply() })
        }

        // ── Elenco delle segnalazioni ──
        if (ReportStore.items.isNotEmpty()) {
            HorizontalDivider()
            Text(stringResource(R.string.rp_list, ReportStore.items.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.rp_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (calendarCode.isNotBlank()) OutlinedButton(onClick = {
                val n = simplylucaapps.girocalendar.data.remote.firestore.ReportsRepository.shareAll(calendarCode, ReportStore.items.toList())
                Toast.makeText(context, if (n > 0) context.getString(R.string.rp_shared_n, n) else context.getString(R.string.rp_share_login), Toast.LENGTH_LONG).show()
            }, modifier = Modifier.fillMaxWidth()) { Text("☁ " + stringResource(R.string.rp_share_all)) }
            val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
            ReportStore.items.take(30).forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${r.n}  ${r.type.emoji}  ${stringResource(r.type.labelRes)}\n${fmt.format(Date(r.atMillis))} · %.5f, %.5f".format(Locale.US, r.lat, r.lon), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { ReportStore.remove(context, r.n); simplylucaapps.girocalendar.data.remote.firestore.ReportsRepository.delete(calendarCode, r) }) { Icon(Icons.Default.Delete, stringResource(R.string.rp_delete)) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    TrackRecorder.finished?.let { SaveRecordingDialog(it) }
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = if (small) 6.dp else 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, fontSize = if (small) 20.sp else 36.sp, fontWeight = FontWeight.Black)
            Text(unit.ifEmpty { " " }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** SOS a pressione lunga (2 s) per evitare tocchi accidentali in tasca o sul manubrio; poi parte il conto alla rovescia annullabile. */
internal fun triggerSos(context: Context) {
    if (EmergencyPrefs.contacts.isEmpty()) Toast.makeText(context, context.getString(R.string.rc_sos_no_contacts), Toast.LENGTH_LONG).show()
    else { buzz(context, 400); EmergencyEngine.trigger(context, false, context.getString(R.string.rc_sos_reason)) }
}

@Composable
internal fun SosButton(onTrigger: () -> Unit, compact: Boolean = false) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(if (compact) 28.dp else 16.dp)
    Box(
        (if (compact) Modifier.size(56.dp) else Modifier.fillMaxWidth().height(84.dp)).clip(shape).background(if (compact) Color(0xE6FFFFFF) else SOS_RED.copy(alpha = .18f))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        val t0 = System.currentTimeMillis()
                        while (true) {
                            progress = ((System.currentTimeMillis() - t0) / 2000f).coerceAtMost(1f)
                            if (progress >= 1f) { onTrigger(); break }
                            delay(30)
                        }
                    }
                    tryAwaitRelease(); job.cancel(); progress = 0f
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(SOS_RED).align(Alignment.CenterStart))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SOS", fontSize = if (compact) 15.sp else 30.sp, fontWeight = FontWeight.Black, color = if (progress > .5f) Color.White else SOS_RED)
            if (!compact) Text(stringResource(R.string.rc_sos_hold), style = MaterialTheme.typography.labelMedium, color = if (progress > .5f) Color.White else SOS_RED)
        }
    }
}
