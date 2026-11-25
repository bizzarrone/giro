package simplylucaapps.girocalendar.ui.nav

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.foundation.layout.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.ui.record.ReportStore
import simplylucaapps.girocalendar.ui.record.ReportType
import simplylucaapps.girocalendar.ui.trails.EdgeToEdgeDialogWindow
import simplylucaapps.girocalendar.ui.trails.SaveRecordingDialog
import simplylucaapps.girocalendar.ui.trails.TrackRecorder
import simplylucaapps.girocalendar.ui.trails.stopRecording
import simplylucaapps.girocalendar.ui.trails.rememberRecordStarter
import java.util.Locale

private val RED = Color(0xFFD32F2F)

/**
 * Navigazione su un percorso: mappa a Canvas (niente WebView, leggera anche sui telefoni lenti) con frecce grosse
 * sulle svolte, cruscotto (distanza, %, quota, D+) e pulsante SEGNALA. Schermo sempre acceso.
 */
/** Segnalazione vicina al percorso, con la distanza (m) lungo il percorso. */
internal class NearRep(val r: simplylucaapps.girocalendar.data.remote.firestore.SharedReport, val distM: Float)

/** Percorsi per cui la proposta «mappa offline + trail» è già stata mostrata (finché non si chiude il GPX). */
internal object NavIntro { val seen = HashSet<String>() }

@Composable
fun NavScreen(lat: DoubleArray, lon: DoubleArray, ele: DoubleArray?, title: String, calendarCode: String = "", onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        EdgeToEdgeDialogWindow()
        NavContent(lat, lon, ele, title, calendarCode, onDismiss = onDismiss)
    }
}

@Composable
internal fun NavContent(lat: DoubleArray, lon: DoubleArray, ele: DoubleArray?, title: String, calendarCode: String, free: Boolean = false, embedded: Boolean = false, onOpenGpx: () -> Unit = {}, onOpenMine: (() -> Unit)? = null, onToggleFull: (() -> Unit)? = null, isFull: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var route by remember { mutableStateOf<NavRoute?>(null) }
    var failed by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<NavStats?>(null) }
    var follow by remember { mutableStateOf(false) }
    var headingUp by remember { mutableStateOf(true) }
    val prefs = remember { context.getSharedPreferences("girocalendar_boot", Context.MODE_PRIVATE) }
    var layer by remember { mutableStateOf(NavLayer.entries.firstOrNull { !it.overlay && it.id == prefs.getString("nav_layer", "") }) }
    var mtb by remember { mutableStateOf(prefs.getBoolean("nav_mtb", false)) }
    var showLayers by remember { mutableStateOf(false) }
    var simSpeed by remember { mutableStateOf(0) } // 0 = spenta, poi 1×, 2×, 4×, 8×, 16×
    val sim = simSpeed > 0
    var showReport by remember { mutableStateOf(false) }
    var reportFix by remember { mutableStateOf<Location?>(null) } // posizione al momento della pressione di SEGNALA
    var confirmStop by remember { mutableStateOf(false) }
    val ride = remember { RideTracker() }
    var summary by remember { mutableStateOf(false) }
    var summaryShown by remember { mutableStateOf(false) }
    var leaveAfter by remember { mutableStateOf(false) }
    val trailRepo = remember { simplylucaapps.girocalendar.data.remote.firestore.TrailRepository() }
    var showMenu by remember { mutableStateOf(false) }
    // l'occhio «T» è lo stesso di Esplora (stesso stato): mostra/nasconde i trail anche in Naviga
    val showTrails = simplylucaapps.girocalendar.ui.trails.MapTrails.on
    var voiceOn by remember { mutableStateOf(prefs.getBoolean("nav_voice", false)) }
    var routeTrails by remember { mutableStateOf<List<Triple<String, simplylucaapps.girocalendar.data.remote.firestore.TrailStatus, Float>>>(emptyList()) }
    val voiceState = remember { NavVoiceState() }
    var routeJunctions by remember { mutableStateOf<List<NavJunctions.Junction>>(emptyList()) }
    var voice by remember { mutableStateOf<NavVoice?>(null) }
    var assignFor by remember { mutableStateOf<simplylucaapps.girocalendar.ui.record.Report?>(null) }
    var assignList by remember { mutableStateOf<List<Pair<simplylucaapps.girocalendar.data.remote.firestore.Trail, Double>>?>(null) }
    LaunchedEffect(assignFor) { val r = assignFor; assignList = if (r == null) null else TrailLink.nearby(trailRepo, r.lat, r.lon) }
    // appena caricato un GPX: proposta mappa offline per il layer in uso + trail lungo il percorso
    var intro by remember { mutableStateOf<Triple<List<Triple<Int, Int, Int>>, Int, List<simplylucaapps.girocalendar.data.remote.firestore.Trail>>?>(null) }
    var introMissing by remember { mutableStateOf(0) }
    var battery by remember { mutableStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val rx = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (lv >= 0 && sc > 0) battery = lv * 100 / sc
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        val sticky = ContextCompat.registerReceiver(context, rx, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        sticky?.let { rx.onReceive(context, it) }
        onDispose { runCatching { context.unregisterReceiver(rx) } }
    }
    var lastLoc by remember { mutableStateOf<Location?>(null) }

    // testi (seguono la lingua dell'app)
    val texts = remember {
        fun g(id: Int, vararg a: Any) = context.getString(id, *a)
        NavTexts(
            waiting = g(R.string.nv_waiting), summary = { l, n -> g(R.string.nv_summary, l, n) }, far = g(R.string.nv_far),
            farDetail = { a, b -> g(R.string.nv_far_detail, a, b) }, off = g(R.string.nv_off), offDetail = { a -> g(R.string.nv_off_detail, a) },
            arrival = g(R.string.nv_arrival), carryOn = g(R.string.nv_carry_on), remaining = { a -> g(R.string.nv_remaining, a) },
            turn = { t ->
                val r = t.right
                g(when (t.kind) {
                    0 -> R.string.nv_uturn
                    1 -> if (r) R.string.nv_sharp_r else R.string.nv_sharp_l
                    2 -> if (r) R.string.nv_turn_r else R.string.nv_turn_l
                    else -> if (r) R.string.nv_slight_r else R.string.nv_slight_l
                })
            },
        )
    }

    val nav = remember { NavView(context).also { it.free = free; it.keepScreenOn = !free || prefs.getBoolean("rec_keep_on", false) } }
    // snapshotFlow legge lo stato fuori dalla composizione: un nuovo punto GPS aggiorna la traccia senza ricomporre tutta la schermata
    LaunchedEffect(free) {
        snapshotFlow { Pair(free && TrackRecorder.active, TrackRecorder.count) }.collect { (rec, _) ->
            nav.trail = if (rec) TrackRecorder.snapshot() else emptyList(); nav.invalidate()
        }
    }
    nav.texts = texts
    fun voiceTick(st: NavStats) {
        val v = voice ?: return
        val r = route ?: return
        if (!voiceOn || free || !st.hasFix) return
        val done = st.doneM; val off = st.offRouteM
        if (done < voiceState.lastDone - 200f) voiceState.reset()
        voiceState.lastDone = done
        if (!voiceState.off && off > 50f && off < 1500f) { voiceState.off = true; v.speak(context.getString(R.string.nv_v_off)); return }
        if (voiceState.off) { if (off < 30f) { voiceState.off = false; v.speak(context.getString(R.string.nv_v_back)) }; return }
        // solo quando sei davanti alla svolta o al bivio (niente «tra cento metri»)
        r.turns.firstOrNull { it.dist > done - 5f }?.let { t ->
            if (t.dist - done <= 40f && t.idx !in voiceState.nearSaid) {
                voiceState.nearSaid.add(t.idx)
                v.speak(if (t.kind == 2) context.getString(if (t.right) R.string.nv_v_right else R.string.nv_v_left) else texts.turn(t)); return
            }
        }
        routeJunctions.firstOrNull { it.distM > done - 5f }?.let { j ->
            if (j.distM - done <= 40f && j.distM.toInt() !in voiceState.forkSaid) {
                voiceState.forkSaid.add(j.distM.toInt())
                v.speak(context.getString(when (j.kind) { 1 -> R.string.nv_v_fork_l; 2 -> R.string.nv_v_fork_r; else -> R.string.nv_v_fork_s })); return
            }
        }
        routeTrails.forEachIndexed { i, (name, status, start) ->
            if (i !in voiceState.trailSaid && done > start - 200f && done < start + 10f) {
                voiceState.trailSaid.add(i)
                v.speak(if (status == simplylucaapps.girocalendar.data.remote.firestore.TrailStatus.OPEN) context.getString(R.string.nv_v_trail, name)
                    else context.getString(R.string.nv_v_trail_st, name, status.label))
                return
            }
        }
    }
    nav.onStats = { stats = it; voiceTick(it) }
    // voce: il motore si crea solo quando serve
    LaunchedEffect(voiceOn) {
        if (voiceOn && voice == null) voice = NavVoice(context) { ok ->
            if (!ok) { voiceOn = false; prefs.edit().putBoolean("nav_voice", false).apply(); Toast.makeText(context, context.getString(R.string.nv_v_no_tts), Toast.LENGTH_LONG).show() }
        }
    }
    DisposableEffect(Unit) { onDispose { voice?.shutdown(); voice = null } }
    // occhietto: trail sulla mappa
    LaunchedEffect(showTrails) {
        if (!showTrails) { nav.trailLines = emptyList(); return@LaunchedEffect }
        val all = TrailLink.all(trailRepo)
        nav.trailLines = withContext(Dispatchers.Default) {
            all.mapNotNull { t ->
                val pts = TrailLink.points(t)
                if (pts.size < 2) null else TrailLine(t.name, t.status.color.toArgb(), DoubleArray(pts.size) { pts[it].first }, DoubleArray(pts.size) { pts[it].second })
            }
        }
    }
    // segnalazioni dell'utente sulla mappa (anche quelle fatte ora con SEGNALA)
    LaunchedEffect(Unit) { ReportStore.load(context) }
    // segnalazioni condivise del calendario (di tutti) + le mie locali non ancora arrivate sul server
    var shared by remember { mutableStateOf<List<simplylucaapps.girocalendar.data.remote.firestore.SharedReport>>(emptyList()) }
    DisposableEffect(calendarCode) {
        val reg = runCatching { simplylucaapps.girocalendar.data.remote.firestore.ReportsRepository.listen(calendarCode) { shared = it } }.getOrNull()
        onDispose { reg?.remove() }
    }
    // voti, vicinanza al percorso, avviso e dettaglio delle segnalazioni condivise
    val RR = simplylucaapps.girocalendar.data.remote.firestore.ReportsRepository
    var votes by remember { mutableStateOf<Map<String, List<simplylucaapps.girocalendar.data.remote.firestore.ReportVote>>>(emptyMap()) }
    var nearReps by remember { mutableStateOf<List<NearRep>>(emptyList()) }
    var alertRep by remember { mutableStateOf<NearRep?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    val alerted = remember { HashSet<String>() }
    val me = ReportStore.uid()
    val allReports = run {
        val keys = shared.map { it.id }.toSet()
        shared + ReportStore.items.filter { RR.keyOf(me, it.atMillis) !in keys }
            .map { simplylucaapps.girocalendar.data.remote.firestore.SharedReport(RR.keyOf(me, it.atMillis), it.type, it.lat, it.lon, it.atMillis, me, "", it.trailId, it.note) }
    }
    fun activeOf(r: simplylucaapps.girocalendar.data.remote.firestore.SharedReport) = RR.stateOf(r, votes[r.id] ?: emptyList()).active
    val activeReports = allReports.filter { activeOf(it) }
    LaunchedEffect(activeReports.map { it.id }) {
        nav.marks = activeReports.map { Triple(it.lat, it.lon, it.type.emoji) }
        nav.markIds = activeReports.map { it.id }
    }
    nav.onMarkTap = { detailId = it }
    // segnalazioni a meno di 40 m dal percorso, con la distanza lungo il percorso
    LaunchedEffect(allReports.map { it.id }, route) {
        val r = route
        if (free || r == null) { nearReps = emptyList(); return@LaunchedEffect }
        nearReps = withContext(Dispatchers.Default) {
            allReports.mapNotNull { s ->
                val x = r.xOf(s.lon); val y = r.yOf(s.lat)
                var bi = -1; var bd = Float.MAX_VALUE
                for (i in 0 until r.n) { val d = kotlin.math.hypot(r.x[i] - x, r.y[i] - y); if (d < bd) { bd = d; bi = i } }
                if (bd <= 40f) NearRep(s, r.cum[bi]) else null
            }.sortedBy { it.distM }.take(60)
        }
    }
    // voti: solo per quelle vicine al percorso e per quella aperta nel dettaglio
    DisposableEffect(nearReps.map { it.r.id }, detailId, calendarCode) {
        val ids = (nearReps.map { it.r.id } + listOfNotNull(detailId)).distinct()
        val regs = ids.mapNotNull { id -> runCatching { RR.listenVotes(calendarCode, id) { l -> votes = votes + (id to l) } }.getOrNull() }
        onDispose { regs.forEach { it.remove() } }
    }
    // avviso: quando sei a meno di 80 m da un pericolo ancora valido sul percorso
    LaunchedEffect(stats?.doneM, nearReps) {
        val st = stats ?: return@LaunchedEffect
        if (free || !st.hasFix || st.offRouteM > 60f) return@LaunchedEffect
        val done = st.doneM
        alertRep?.let { if (done > it.distM + 60f) alertRep = null }
        val n = nearReps.firstOrNull { it.distM > done - 15f && it.distM - done <= 80f && it.r.id !in alerted && activeOf(it.r) } ?: return@LaunchedEffect
        alerted.add(n.r.id); alertRep = n
        if (voiceOn) voice?.speak(context.getString(R.string.nv_ra_voice, context.getString(n.r.type.labelRes)))
        @Suppress("DEPRECATION") val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        runCatching { vib?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 150), -1)) }
    }
    fun castVote(rep: simplylucaapps.girocalendar.data.remote.firestore.SharedReport, confirm: Boolean) {
        val u = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (u == null || !RR.vote(calendarCode, rep.id, confirm)) { Toast.makeText(context, context.getString(R.string.rp_share_login), Toast.LENGTH_LONG).show(); return }
        val mine = simplylucaapps.girocalendar.data.remote.firestore.ReportVote(u.uid, confirm, System.currentTimeMillis(), "")
        val nv = (votes[rep.id] ?: emptyList()).filter { it.uid != u.uid } + mine
        votes = votes + (rep.id to nv)
        alertRep = null
        if (confirm) { Toast.makeText(context, context.getString(R.string.nv_ra_thanks), Toast.LENGTH_SHORT).show(); return }
        if (RR.stateOf(rep, nv).active) { Toast.makeText(context, context.getString(R.string.nv_ra_wait), Toast.LENGTH_LONG).show(); return }
        scope.launch {
            val trail = TrailLink.trailOf(trailRepo, rep.trailId, rep.lat, rep.lon)
            val otherActive = trail != null && allReports.any { it.id != rep.id && it.trailId == trail.id && activeOf(it) }
            val reopened = trail != null && !otherActive &&
                TrailLink.reopen(trailRepo, trail, context.getString(R.string.nv_ra_reopen, context.getString(rep.type.labelRes)))
            Toast.makeText(context, if (reopened) context.getString(R.string.nv_ra_gone_trail, trail!!.name) else context.getString(R.string.nv_ra_gone), Toast.LENGTH_LONG).show()
        }
    }
    // avviso fuori percorso: vibrazione lunga quando ti allontani oltre 50 m, breve quando rientri (sotto 30 m)
    var wasOff by remember { mutableStateOf(false) }
    LaunchedEffect(stats?.offRouteM, stats?.hasFix) {
        val st = stats ?: return@LaunchedEffect
        if (!st.hasFix || st.offRouteM.isNaN()) return@LaunchedEffect
        @Suppress("DEPRECATION") val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        if (!wasOff && st.offRouteM > 50f && st.offRouteM < 1500f) {
            wasOff = true
            runCatching { vib?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1)) }
        } else if (wasOff && st.offRouteM < 30f) {
            wasOff = false
            runCatching { vib?.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE)) }
        }
    }
    nav.onFollowChanged = { follow = it }
    val topPx = if (embedded) 0 else WindowInsets.statusBars.getTop(density)
    val dashH = if (free) 0.dp else 46.dp
    val dashPx = with(density) { dashH.roundToPx() }
    LaunchedEffect(topPx, dashPx) { nav.setInsets(topPx + dashPx) }
    LaunchedEffect(layer, mtb) { nav.layer = layer; nav.mtb = mtb; prefs.edit().putString("nav_layer", layer?.id ?: "").putBoolean("nav_mtb", mtb).apply() }

    LaunchedEffect(Unit) {
        if (free) return@LaunchedEffect
        val r = withContext(Dispatchers.Default) { runCatching { NavRoute(lat, lon, ele) }.getOrNull() }
        if (r == null) failed = true else { nav.route = r; nav.emitInitial(); route = r }
    }
    DisposableEffect(Unit) { onDispose { nav.release() } }

    // GPS
    fun hasPerm() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    var permOk by remember { mutableStateOf(hasPerm()) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permOk = ok
        if (!ok) Toast.makeText(context, context.getString(R.string.nv_no_perm), Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(route) { if ((route != null || free) && !permOk) permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
    DisposableEffect(route, permOk) {
        if ((route == null && !free) || !permOk) return@DisposableEffect onDispose {}
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val ll = object : LocationListener {
            override fun onLocationChanged(l: Location) {
                if (simSpeed > 0 || l.hasAccuracy() && l.accuracy > (if (free) 600f else 80f)) return
                lastLoc = l
                // da fermi il GPS «balla» di qualche metro: il punto resta fermo; intanto si aggiornano le statistiche del giro
                val p = ride.accept(l) ?: return
                nav.fix(p.latitude, p.longitude, if (p.hasBearing() && p.speed > 1.2f) p.bearing else null, if (p.hasAltitude()) p.altitude else null)
            }
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
            @Deprecated("API < 30") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        }
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, ll)
            // mappa libera: subito la posizione di rete e l'ultima nota (anche vecchia), poi il GPS la affina
            if (free && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f, ll)
            if (free) {
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                    .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
                    ?.let { lastLoc = it; nav.fix(it.latitude, it.longitude, null, if (it.hasAltitude()) it.altitude else null) }
            } else
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.takeIf { System.currentTimeMillis() - it.time < 120_000 }?.let { lastLoc = it; nav.fix(it.latitude, it.longitude, null, if (it.hasAltitude()) it.altitude else null) }
        }
        onDispose { runCatching { lm.removeUpdates(ll) } }
    }

    // prova senza uscire di casa
    LaunchedEffect(sim) {
        var d = 0f
        while (sim) {
            // passo di al più 12 m per posizione (così le istruzioni vocali non vengono saltate): a velocità alte si accorcia l'attesa
            val want = 4f * simSpeed; val step = minOf(want, 12f)
            d += step
            if (d >= nav.routeLength - 5f) d = 0f
            nav.pointAt(d)?.let { (la, lo, b) ->
                lastLoc = Location("sim").apply { latitude = la; longitude = lo; accuracy = 5f; time = System.currentTimeMillis() }
                nav.fix(la, lo, b)
            }
            delay((300f * step / want).toLong().coerceAtLeast(40L))
        }
    }

    val startRec = rememberRecordStarter()
    var bottomPx by remember { mutableStateOf(0) }
    var rootPx by remember { mutableStateOf(0) }
    var offPlan by remember { mutableStateOf<Pair<Int, List<Triple<Int, Int, Int>>>?>(null) }
    var offProg by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var offStop by remember { mutableStateOf(false) }
    var offTick by remember { mutableStateOf(0) }
    val bannerDp = with(density) { topPx.toDp() } + (if (free) 8.dp else 120.dp + dashH)

    Box(Modifier.fillMaxSize().background(Color(0xFFF1EFE7)).onSizeChanged { rootPx = it.height }) {
        AndroidView(factory = { nav }, update = { nav.touchEnabled = !(embedded && onToggleFull != null) }, modifier = Modifier.fillMaxSize())
        // Nella scheda lo swipe cambia scheda; per usare la mappa col tocco si passa a schermo intero.
        if (embedded && onToggleFull != null) Surface(
            Modifier.align(Alignment.Center).padding(horizontal = 56.dp).clickable { onToggleFull() },
            shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), shadowElevation = 4.dp,
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.material.icons.Icons.Default.Fullscreen, stringResource(R.string.mp_fullscreen), Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.mp_fs_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        if (free) {
            if (stats?.hasFix != true) Text(stringResource(R.string.nv_waiting), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).background(Color(0xE6000000)).padding(16.dp))
        } else if (route == null) {
            Text(if (failed) stringResource(R.string.nv_error) else stringResource(R.string.nv_loading), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).background(Color(0xE6000000)).padding(16.dp))
        }

        // stessa fila di pulsanti di Esplora: T (trail) e ? (aiuto)
        Row(Modifier.align(Alignment.TopStart).padding(top = bannerDp, start = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            simplylucaapps.girocalendar.ui.trails.MapTrailsToggle()
            simplylucaapps.girocalendar.ui.trails.MapHelpButton()
        }

        // batteria, in alto a sinistra sotto il banner
        if (battery >= 0) Surface(shape = RoundedCornerShape(12.dp), color = Color(0xCC212121),
            modifier = Modifier.align(Alignment.TopStart).padding(top = bannerDp + 52.dp, start = 8.dp)) {
            Text((if (charging) "⚡ " else "🔋 ") + battery + "%", color = if (battery <= 15 && !charging) Color(0xFFFF5252) else Color.White,
                fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        }

        // comandi: a sinistra (sotto la batteria) menu/schermo intero/strati/prova, a destra zoom e orientamento; mai sotto il cruscotto
        val keysMaxDp = with(density) { (rootPx - bottomPx).toDp() } - bannerDp - 56.dp
        Column(Modifier.align(Alignment.TopStart).padding(top = bannerDp + 52.dp + (if (battery >= 0) 40.dp else 0.dp), start = 8.dp).heightIn(max = maxOf(96.dp, keysMaxDp - (if (battery >= 0) 40.dp else 0.dp))).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!free) RoundKey("✕", Color(0xCC212121)) { if (ride.started) { leaveAfter = true; summary = true } else onDismiss() }
            RoundKey("☰") { showMenu = true }
            // lo schermo intero si attiva dal riquadro al centro; l'icona resta solo per tornare dalla vista a schermo intero
            if (onToggleFull != null && isFull) RoundKey("⤡", size = 56.dp) { onToggleFull() }
            simplylucaapps.girocalendar.ui.record.SosButton(onTrigger = { simplylucaapps.girocalendar.ui.record.triggerSos(context) }, compact = true)
            if (!free) RoundKey(if (simSpeed == 0) "▶" else "${simSpeed}×", if (sim) RED else Color(0xCC212121)) { simSpeed = when (simSpeed) { 0 -> 1; 1 -> 2; 2 -> 4; 4 -> 8; 8 -> 16; else -> 0 } }
        }
        Column(Modifier.align(Alignment.TopEnd).padding(top = bannerDp, end = 8.dp).heightIn(max = maxOf(96.dp, keysMaxDp)).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                Box(Modifier.size(46.dp).shadow(3.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Color.White).clickable { showLayers = true }, contentAlignment = Alignment.Center) {
                    LayersGlyph(if (layer != null || mtb) Color(0xFF1565C0) else Color(0xFF222222))
                }
                DropdownMenu(expanded = showLayers, onDismissRequest = { showLayers = false }) {
                    @Composable fun item(label: String, on: Boolean, click: () -> Unit) = DropdownMenuItem(
                        text = { Text((if (on) "✓  " else "     ") + label, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) },
                        onClick = click)
                    item(stringResource(R.string.nv_layer_none), layer == null) { layer = null; showLayers = false }
                    item("OpenStreetMap", layer == NavLayer.OSM) { layer = NavLayer.OSM; showLayers = false }
                    item(stringResource(R.string.nv_layer_topo), layer == NavLayer.TOPO) { layer = NavLayer.TOPO; showLayers = false }
                    item("CyclOSM", layer == NavLayer.CYCLE) { layer = NavLayer.CYCLE; showLayers = false }
                    item(stringResource(R.string.nv_layer_sat), layer == NavLayer.SAT) { layer = NavLayer.SAT; showLayers = false }
                    HorizontalDivider()
                    item(stringResource(R.string.nv_layer_mtb), mtb) { mtb = !mtb }
                }
            }
            RoundKey(if (headingUp) "↑" else "N") { headingUp = nav.toggleHeadingUp() }
            if (!free) RoundKey(if (voiceOn) "🔊" else "🔇", if (voiceOn) Color(0xFF2E7D32) else Color(0xCC212121)) { voiceOn = !voiceOn; prefs.edit().putBoolean("nav_voice", voiceOn).apply(); if (!voiceOn) voice?.shutdown().also { voice = null } }
        }

        // ◎ sopra e ＋ − uniti in un riquadro bianco, in basso a destra sopra i pulsanti: stesso aspetto della mappa Esplora
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = with(density) { bottomPx.toDp() } + 8.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(46.dp).shadow(3.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Color.White).clickable { nav.setFollow(true) }, contentAlignment = Alignment.Center) {
                Text("◎", color = if (follow) Color(0xFF1565C0) else Color(0xFF222222), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.shadow(3.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Color.White)) {
                Box(Modifier.size(46.dp).clickable { nav.zoom(0.7f) }, contentAlignment = Alignment.Center) { Text("+", color = Color(0xFF222222), fontSize = 26.sp, fontWeight = FontWeight.Bold) }
                Box(Modifier.width(46.dp).height(1.dp).background(Color(0xFFCCCCCC)))
                Box(Modifier.size(46.dp).clickable { nav.zoom(1.4f) }, contentAlignment = Alignment.Center) { Text("−", color = Color(0xFF222222), fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            }
        }

        // cruscotto in alto, su una riga: km fatti/totali (%), quota attuale, dislivello fatto/totale
        if (!free) {
            val s = stats
            fun k1(m: Float) = if (m.isNaN()) "–" else "%.1f".format(Locale.ITALY, m / 1000f)
            fun m0(m: Float) = if (m.isNaN()) "–" else "%d".format(m.toInt())
            val tot = if (s == null || s.gainDone.isNaN() || s.gainLeft.isNaN()) Float.NaN else s.gainDone + s.gainLeft
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = with(density) { topPx.toDp() }).height(dashH).padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(k1(s?.doneM ?: 0f) + "/" + k1(s?.totalM ?: Float.NaN) + "km" + (if (s?.hasFix == true) " (${s.pct}%)" else ""), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
                    Text(m0(s?.eleM ?: Float.NaN) + "m△", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
                    Text(m0(s?.gainDone ?: Float.NaN) + "/" + m0(tot) + "m↑", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
                }
                LinearProgressIndicator(progress = { ((s?.pct ?: 0) / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp), color = Color(0xFFC2185B), trackColor = Color(0x55FFFFFF))
            }
        }

        // pericolo in arrivo: riquadro con conferma / «rimosso»
        alertRep?.let { n ->
            val v = votes[n.r.id] ?: emptyList()
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = with(density) { bottomPx.toDp() } + 8.dp),
                shape = RoundedCornerShape(16.dp), color = Color(0xF2B71C1C), shadowElevation = 8.dp) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(n.r.type.emoji + "  " + stringResource(n.r.type.labelRes) + " · " + kotlin.math.max(0, (n.distM - (stats?.doneM ?: 0f)).toInt()) + " m", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.nv_ra_by, n.r.byName.ifBlank { "—" }, v.count { it.confirm }), color = Color(0xFFFFCDD2), fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { castVote(n.r, true) }, modifier = Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB71C1C))) { Text(stringResource(R.string.nv_ra_yes), fontWeight = FontWeight.Black, maxLines = 1, softWrap = false) }
                        Button(onClick = { castVote(n.r, false) }, modifier = Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White)) { Text(stringResource(R.string.nv_ra_no), fontWeight = FontWeight.Black, maxLines = 1, softWrap = false) }
                    }
                }
            }
        }

        // cruscotto + SEGNALA in basso
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { bottomPx = it.height }.then(if (embedded) Modifier else Modifier.navigationBarsPadding()).padding(8.dp)
            // in scheda Mappa i chip Esplora/Naviga stanno sopra la mappa, in basso: i pulsanti si alzano per lasciarli liberi
            .then(if (embedded) Modifier.padding(bottom = 44.dp) else Modifier), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (free && TrackRecorder.active) RecStatsPanel()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (free) Button(onClick = onOpenGpx, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White)) {
                    Text("🧭 " + stringResource(R.string.nv_open_gpx), fontSize = if (onOpenMine != null) 12.sp else 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
                // solo icona e larghezza fissa: con un quarto pulsante a pari larghezza gli altri testi venivano tagliati
                if (free && onOpenMine != null) Button(onClick = onOpenMine, modifier = Modifier.width(48.dp).height(56.dp).semantics { contentDescription = context.getString(R.string.menu_my_storage) }, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0), contentColor = Color.White)) {
                    Text("☁", fontSize = 24.sp)
                }
                Button(onClick = { reportFix = lastLoc; showReport = true }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(14.dp), contentPadding = if (free) PaddingValues(horizontal = 4.dp) else ButtonDefaults.ContentPadding,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00))) {
                    Text("⚠ " + stringResource(R.string.nv_report), fontSize = if (free) (if (onOpenMine != null) 12.sp else 15.sp) else 20.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
                }
                if (TrackRecorder.active) {
                    Button(onClick = { confirmStop = true }, modifier = (if (free) Modifier.weight(1f) else Modifier).height(56.dp), shape = RoundedCornerShape(14.dp), contentPadding = if (free) PaddingValues(horizontal = 4.dp) else ButtonDefaults.ContentPadding,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))) {
                        Text("■ " + stringResource(R.string.rc_stop), fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                } else {
                    Button(onClick = startRec, modifier = (if (free) Modifier.weight(1f) else Modifier).height(56.dp), shape = RoundedCornerShape(14.dp), contentPadding = if (free) PaddingValues(horizontal = 4.dp) else ButtonDefaults.ContentPadding,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xEE212121))) {
                        Text("●", color = RED, fontSize = 20.sp); Spacer(Modifier.width(4.dp)); Text("REC", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    val offLayers = listOfNotNull(layer ?: NavLayer.OSM, if (mtb) NavLayer.MTB else null)
    fun startOffline(tiles: List<Triple<Int, Int, Int>>) {
        offStop = false; offProg = 0 to tiles.size * offLayers.size
        scope.launch {
            val n = withContext(Dispatchers.IO) { OfflineMaps.download(context, offLayers, tiles, { offStop }) { d, t -> offProg = d to t } }
            offProg = null; offPlan = null; offTick++
            Toast.makeText(context, context.getString(R.string.of_done, n), Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(route) {
        val r = route
        if (free || r == null) return@LaunchedEffect
        // la proposta si mostra una sola volta per percorso: passando da scheda a schermo intero la schermata si ricrea ma non si ripete
        val firstTime = NavIntro.seen.add("$title|${lat.size}|${lat.firstOrNull()}|${lon.firstOrNull()}")
        val pl = withContext(Dispatchers.Default) { OfflineMaps.plan(lat, lon, 16) }
        val layersNow = listOfNotNull(layer ?: NavLayer.OSM, if (mtb) NavLayer.MTB else null)
        introMissing = withContext(Dispatchers.IO) { pl.second.count { (z, x, y) -> layersNow.any { l -> java.io.File(OfflineMaps.dir(context, l), "$z/$x/$y.png").let { !it.exists() || it.length() == 0L } } } }
        val trd = runCatching { TrailLink.alongRoute(trailRepo, lat, lon) }.getOrDefault(emptyList())
        routeTrails = trd.map { Triple(it.first.name, it.first.status, it.second) }
        routeJunctions = withContext(Dispatchers.IO) { runCatching { NavJunctions.find(context, r, "$title|${lat.size}|${lat.firstOrNull()}|${lon.firstOrNull()}|${r.length.toInt()}") }.getOrDefault(emptyList()) }
        if (firstTime) intro = Triple(pl.second, pl.first, trd.map { it.first })
    }
    intro?.let { (tiles, maxZ, trails) ->
        AlertDialog(
            onDismissRequest = { intro = null },
            title = { Text(stringResource(R.string.nv_intro_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val lname = offLayers.joinToString(" + ") { it.id }
                    if (introMissing == 0) Text(stringResource(R.string.nv_intro_off_have, lname))
                    else Text(stringResource(R.string.nv_intro_off, lname, introMissing * 1, OfflineMaps.estimateMb(introMissing), OfflineMaps.MIN_Z, maxZ))
                    HorizontalDivider()
                    if (trails.isEmpty()) Text(stringResource(R.string.nv_intro_notrail))
                    else {
                        Text(stringResource(R.string.nv_intro_trails, trails.size), fontWeight = FontWeight.Bold)
                        trails.forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(t.status.color))
                                Spacer(Modifier.width(8.dp))
                                Text(t.name.ifBlank { "—" }, modifier = Modifier.weight(1f), maxLines = 2)
                                Text(t.status.label, fontSize = 13.sp, color = t.status.color, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (introMissing > 0) TextButton(onClick = { intro = null; startOffline(tiles) }) { Text(stringResource(R.string.of_go)) }
                else TextButton(onClick = { intro = null }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { if (introMissing > 0) TextButton(onClick = { intro = null }) { Text(stringResource(R.string.nv_intro_later)) } },
        )
    }
    offPlan?.let { (maxZ, tiles) ->
        if (offProg == null) AlertDialog(
            onDismissRequest = { offPlan = null },
            title = { Text(stringResource(R.string.of_title)) },
            text = { Text(stringResource(R.string.of_confirm, tiles.size * offLayers.size, OfflineMaps.estimateMb(tiles.size * offLayers.size), OfflineMaps.MIN_Z, maxZ, offLayers.joinToString(" + ") { it.id })) },
            confirmButton = {
                TextButton(onClick = { startOffline(tiles) }) { Text(stringResource(R.string.of_go)) }
            },
            dismissButton = { TextButton(onClick = { offPlan = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    // avanzamento dello scarico: visibile sempre (anche quando parte dalla proposta all'apertura del GPX)
    offProg?.let { (d, t) ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.of_title)) },
            text = { Column { Text(stringResource(R.string.of_progress, d, t)); LinearProgressIndicator(progress = { if (t == 0) 0f else d / t.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) } },
            confirmButton = { TextButton(onClick = { offStop = true }) { Text(stringResource(R.string.of_stop)) } },
        )
    }

    detailId?.let { id ->
        val rep = allReports.firstOrNull { it.id == id }
        if (rep == null) detailId = null else {
            val v = votes[id] ?: emptyList()
            val st = RR.stateOf(rep, v)
            val fmt = remember { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
            AlertDialog(
                onDismissRequest = { detailId = null },
                title = { Text(rep.type.emoji + "  " + stringResource(rep.type.labelRes), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.nv_rd_by, rep.byName.ifBlank { stringResource(R.string.nv_rd_me) }, fmt.format(java.util.Date(rep.atMillis))))
                        if (rep.note.isNotBlank()) Text(rep.note)
                        Text(stringResource(R.string.nv_rd_votes, v.count { it.confirm }, st.removed), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!st.active) Text(stringResource(R.string.nv_rd_inactive), fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                },
                confirmButton = { TextButton(onClick = { detailId = null }) { Text(stringResource(R.string.action_close)) } },
                dismissButton = {
                    Row {
                        TextButton(onClick = { castVote(rep, true); detailId = null }) { Text(stringResource(R.string.nv_ra_yes)) }
                        TextButton(onClick = { castVote(rep, false); detailId = null }) { Text(stringResource(R.string.nv_ra_no)) }
                    }
                },
            )
        }
    }

    if (showMenu) AlertDialog(
        onDismissRequest = { showMenu = false },
        title = { Text(stringResource(R.string.nv_menu_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                simplylucaapps.girocalendar.ui.record.SosButton(onTrigger = {
                    if (simplylucaapps.girocalendar.ui.emergency.EmergencyPrefs.contacts.isEmpty()) Toast.makeText(context, context.getString(R.string.rc_sos_no_contacts), Toast.LENGTH_LONG).show()
                    else { showMenu = false; simplylucaapps.girocalendar.ui.emergency.EmergencyEngine.trigger(context, false, context.getString(R.string.rc_sos_reason)) }
                })
                var keepOn by remember { mutableStateOf(prefs.getBoolean("rec_keep_on", false)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.rc_keep_on), modifier = Modifier.weight(1f))
                    Switch(keepOn, { keepOn = it; prefs.edit().putBoolean("rec_keep_on", it).apply(); if (free) nav.keepScreenOn = it })
                }
                if (!free && route != null) {
                    OutlinedButton(onClick = {
                        showMenu = false
                        scope.launch { offPlan = withContext(Dispatchers.Default) { OfflineMaps.plan(lat, lon, 16) } }
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.of_btn), fontSize = 13.sp) }
                }
                run {
                    val mb = remember(showMenu, offTick) { OfflineMaps.sizeMb(context) }
                    if (mb > 0.05) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.of_saved, "%.1f".format(Locale.ITALY, mb)), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { OfflineMaps.clear(context); offTick++ }) { Text(stringResource(R.string.of_clear), fontSize = 13.sp) }
                    }
                }
                run {
                    val cdir = java.io.File(context.cacheDir, "tiles")
                    val cmb = remember(showMenu, offTick) { cdir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1_048_576.0 }
                    if (cmb > 0.05) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.of_cache, "%.1f".format(Locale.ITALY, cmb)), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { cdir.deleteRecursively(); offTick++ }) { Text(stringResource(R.string.of_cache_clear), fontSize = 13.sp) }
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.rp_list, ReportStore.items.size), fontWeight = FontWeight.Bold)
                if (ReportStore.items.isNotEmpty() && calendarCode.isNotBlank()) OutlinedButton(onClick = {
                    val n = simplylucaapps.girocalendar.data.remote.firestore.ReportsRepository.shareAll(calendarCode, ReportStore.items.toList())
                    Toast.makeText(context, if (n > 0) context.getString(R.string.rp_shared_n, n) else context.getString(R.string.rp_share_login), Toast.LENGTH_LONG).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("☁ " + stringResource(R.string.rp_share_all)) }
                val fmt = remember { java.text.SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
                ReportStore.items.take(30).forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("#${r.n}  ${r.type.emoji}  ${stringResource(r.type.labelRes)} · ${fmt.format(java.util.Date(r.atMillis))}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                (if (r.shared) stringResource(R.string.rp_status_sent) else stringResource(R.string.rp_status_local)) + "  ·  " +
                                    (if (r.trailName.isNotBlank()) stringResource(R.string.rp_status_trail, r.trailName) else stringResource(R.string.rp_status_notrail)),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (r.trailName.isBlank()) TextButton(onClick = { assignFor = r }, contentPadding = PaddingValues(0.dp)) { Text("🧭 " + stringResource(R.string.rp_assign), fontSize = 13.sp) }
                        }
                        TextButton(onClick = { ReportStore.remove(context, r.n); simplylucaapps.girocalendar.data.remote.firestore.ReportsRepository.delete(calendarCode, r) }) { Text("🗑") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showMenu = false }) { Text(stringResource(R.string.action_close)) } },
    )
    assignFor?.let { rep ->
        AlertDialog(
            onDismissRequest = { assignFor = null },
            title = { Text(stringResource(R.string.rp_assign_title)) },
            text = {
                val list = assignList
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("${rep.type.emoji} ${stringResource(rep.type.labelRes)}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    when {
                        list == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        list.isEmpty() -> Text(stringResource(R.string.rp_assign_none))
                        else -> list.forEach { (tr, dist) ->
                            Column(Modifier.fillMaxWidth().clickable {
                                assignFor = null
                                scope.launch {
                                    if (TrailLink.attachTo(context, trailRepo, tr, rep.type)) {
                                        ReportStore.update(context, rep.n) { it.copy(trailId = tr.id, trailName = tr.name) }
                                        Toast.makeText(context, context.getString(R.string.rp_assigned, tr.name), Toast.LENGTH_LONG).show()
                                    } else Toast.makeText(context, context.getString(R.string.rp_assign_login), Toast.LENGTH_LONG).show()
                                }
                            }.padding(vertical = 8.dp)) {
                                Text(tr.name, fontWeight = FontWeight.SemiBold)
                                Text((if (tr.zone.isNotBlank()) tr.zone + " · " else "") + (if (dist == Double.MAX_VALUE) "–" else if (dist >= 1000) "%.1f km".format(Locale.ITALY, dist / 1000) else "${dist.toInt()} m"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { assignFor = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    // arrivo: riepilogo del giro (una volta)
    LaunchedEffect(stats?.doneM) {
        val st = stats ?: return@LaunchedEffect
        if (!free && !sim && ride.started && !summaryShown && st.totalM > 0 && st.doneM >= st.totalM - 40f) { summaryShown = true; summary = true }
    }
    if (summary) {
        val gain = stats?.gainDone?.takeIf { !it.isNaN() }
        AlertDialog(
            onDismissRequest = { summary = false; if (leaveAfter) onDismiss() },
            title = { Text(stringResource(R.string.nv_sum_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    @Composable fun row(l: String, v: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(l); Text(v, fontWeight = FontWeight.Bold)
                    }
                    row(stringResource(R.string.nv_sum_dist), "%.2f km".format(ride.distM / 1000.0))
                    row(stringResource(R.string.nv_sum_total), simplylucaapps.girocalendar.ui.trails.fmtDur(ride.totalMs))
                    row(stringResource(R.string.nv_sum_moving), simplylucaapps.girocalendar.ui.trails.fmtDur(ride.movingMs))
                    row(stringResource(R.string.nv_sum_avg), "%.1f km/h".format(ride.avgKmh))
                    row(stringResource(R.string.nv_sum_max), "%.1f km/h".format(ride.maxKmh))
                    if (gain != null) row(stringResource(R.string.nv_sum_gain), "%d m".format(gain.toInt()))
                }
            },
            confirmButton = { TextButton(onClick = { summary = false; if (leaveAfter) onDismiss() }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text(stringResource(R.string.nv_stop_rec_title)) },
        confirmButton = { TextButton(onClick = { confirmStop = false; stopRecording(context) }) { Text(stringResource(R.string.rc_stop), color = Color(0xFFB71C1C)) } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(android.R.string.cancel)) } },
    )
    TrackRecorder.finished?.let { SaveRecordingDialog(it) }
    if (showReport) {
        AlertDialog(
            onDismissRequest = { showReport = false },
            title = { Text(stringResource(R.string.rp_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportType.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { t ->
                                Column(
                                    Modifier.weight(1f).height(78.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
                                        .clickable {
                                            showReport = false
                                            scope.launch {
                                                val loc = reportFix ?: lastLoc ?: ReportStore.locate(context)
                                                reportFix = null
                                                if (loc == null) Toast.makeText(context, context.getString(R.string.rp_no_loc), Toast.LENGTH_LONG).show()
                                                else {
                                                    var r = ReportStore.add(context, t, loc)
                                                    if (simplylucaapps.girocalendar.data.remote.firestore.ReportsRepository.share(calendarCode, r)) ReportStore.update(context, r.n) { it.copy(shared = true) }
                                                    Toast.makeText(context, context.getString(R.string.nv_report_saved, r.n, t.emoji, context.getString(t.labelRes)), Toast.LENGTH_SHORT).show()
                                                    val tr = TrailLink.attach(context, trailRepo, t, loc.latitude, loc.longitude)
                                                    if (tr != null) {
                                                        ReportStore.update(context, r.n) { it.copy(trailId = tr.id, trailName = tr.name) }
                                                        Toast.makeText(context, context.getString(R.string.nv_trail_linked, tr.name), Toast.LENGTH_LONG).show()
                                                    } else Toast.makeText(context, context.getString(R.string.nv_trail_none), Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }.padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(t.emoji, fontSize = 28.sp)
                                    Text(stringResource(t.labelRes), fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showReport = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

/** Icona «livelli» (rombi sovrapposti), come il pulsante livelli di Esplora. */
@Composable
private fun LayersGlyph(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(28.dp)) {
        val w = size.width; val h = size.height
        val top = androidx.compose.ui.graphics.Path().apply { moveTo(w / 2, h * 0.08f); lineTo(w * 0.96f, h * 0.36f); lineTo(w / 2, h * 0.64f); lineTo(w * 0.04f, h * 0.36f); close() }
        drawPath(top, color)
        val st = androidx.compose.ui.graphics.drawscope.Stroke(width = h * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.04f, h * 0.55f); lineTo(w / 2, h * 0.83f); lineTo(w * 0.96f, h * 0.55f) }, color, style = st)
        drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.04f, h * 0.74f); lineTo(w / 2, h * 0.96f); lineTo(w * 0.96f, h * 0.74f) }, color, style = st)
    }
}

@Composable
private fun RoundKey(label: String, bg: Color = Color(0xCC212121), size: androidx.compose.ui.unit.Dp = 42.dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** Cruscotto della registrazione: ha il suo orologio (1 s) e legge i dati qui dentro, così gli aggiornamenti restano locali. */
@Composable
private fun RecStatsPanel() {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick = System.currentTimeMillis() } }
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xEE212121), shadowElevation = 6.dp) {
        tick.hashCode()
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(stringResource(R.string.nv_done), "%.2f".format(Locale.ITALY, TrackRecorder.distanceM / 1000), "km", Modifier.weight(1f))
            Stat(stringResource(R.string.nv_time), simplylucaapps.girocalendar.ui.trails.fmtDur(TrackRecorder.durationMs()), "", Modifier.weight(1.3f))
            Stat(stringResource(R.string.nv_alt), if (TrackRecorder.eleNow.isNaN()) "–" else "%d".format(TrackRecorder.eleNow.toInt()), "m", Modifier.weight(1f))
            Stat(stringResource(R.string.nv_gain_done), "%d".format(TrackRecorder.gainM.toInt()), "m ↑", Modifier.weight(1f))
            Stat(stringResource(R.string.nv_speed), "%.1f".format(Locale.ITALY, TrackRecorder.speedMs * 3.6), "km/h", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (unit.isEmpty() || unit == label) label else "$label · $unit", color = Color(0xFFBDBDBD), fontSize = 10.sp, maxLines = 1, softWrap = false)
        Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}
