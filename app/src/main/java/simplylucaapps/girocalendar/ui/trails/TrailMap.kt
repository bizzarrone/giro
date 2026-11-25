package simplylucaapps.girocalendar.ui.trails

import simplylucaapps.girocalendar.ui.common.CompactTextField
import androidx.compose.ui.res.stringResource

import simplylucaapps.girocalendar.R

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.EditOff
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONArray
import org.json.JSONObject
import simplylucaapps.girocalendar.data.remote.firestore.Difficulty
import simplylucaapps.girocalendar.data.remote.firestore.Poi
import simplylucaapps.girocalendar.data.remote.firestore.Trail
import simplylucaapps.girocalendar.data.remote.firestore.TrailZone
import simplylucaapps.girocalendar.util.GpxTrackParser
import simplylucaapps.girocalendar.util.LatLon
import simplylucaapps.girocalendar.util.Polyline

private const val TAG = "TrailMap"

/** Vista (centro e zoom) condivisa tra la mappa nella tab e quella a schermo intero: aprendo/chiudendo lo schermo
 * intero non si deve perdere zoom e posizione. [syncToken] cambia alla chiusura dello schermo intero. */
object MapViewState { var view: Triple<Double, Double, Double>? = null; var syncToken by mutableIntStateOf(0) }

/** Pulsante "?": spiega come usare la mappa (occhi, modifica trail e zone, "Tuo GPX" e la sua etichetta). */
@Composable
fun MapHelpButton(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    SmallFloatingActionButton(onClick = { open = true }, containerColor = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.mh_btn))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.mh_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapHelpText.sections().forEach { (title, body) ->
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

object MapHelpText {
    @Composable
    fun sections() = listOf(
        stringResource(R.string.mh_t1) to stringResource(R.string.mh_b1),
        stringResource(R.string.mh_tS) to stringResource(R.string.mh_bS),
        stringResource(R.string.mh_t2) to stringResource(R.string.mh_b2),
        stringResource(R.string.mh_t3) to stringResource(R.string.mh_b3),
        stringResource(R.string.mh_tPoi) to stringResource(R.string.mh_bPoi),
        stringResource(R.string.mh_t4) to stringResource(R.string.mh_b4),
        stringResource(R.string.mh_tG) to stringResource(R.string.mh_bG),
        stringResource(R.string.mh_tP) to stringResource(R.string.mh_bP),
        stringResource(R.string.mh_t5) to stringResource(R.string.mh_b5),
    )
}

/** Modalità trail: occhio = sola lettura (di default a ogni avvio), matita = modifica (per ogni utente con login). */
object TrailEdit { var on by mutableStateOf(false); var loaded = false; var blocked by mutableStateOf(false) }

/** Pulsante occhio/matita: alterna lettura e modifica. Visibile solo con il login. */
@Composable
fun TrailEditToggle(signedIn: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!signedIn) return
    SmallFloatingActionButton(
        onClick = { if (TrailEdit.blocked) Toast.makeText(context, context.getString(R.string.mp_edit_denied), Toast.LENGTH_SHORT).show() else TrailEdit.on = !TrailEdit.on },
        containerColor = if (TrailEdit.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) { Icon(if (TrailEdit.on) Icons.Default.Edit else Icons.Default.EditOff, stringResource(if (TrailEdit.on) R.string.mp_edit_on else R.string.mp_edit_off)) }
}

/** La mia posizione: acceso = pallino blu con cerchio di precisione sulla mappa (serve il permesso di posizione). */
object MyLoc { var on by mutableStateOf(false); var pos by mutableStateOf<Triple<Double, Double, Float>?>(null); var centerTick by mutableStateOf(0) }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MyLocationButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) MyLoc.on = true
        else Toast.makeText(context, context.getString(R.string.mp_perm_denied), Toast.LENGTH_LONG).show()
    }
    val onTap: () -> Unit = {
        if (MyLoc.on) { MyLoc.centerTick++ }  // già acceso: ricentra la mappa sul pallino (lo spegnimento è sul tocco lungo)
        else {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) MyLoc.on = true
            else perm.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    Surface(
        modifier = modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onTap, onLongClick = { if (MyLoc.on) { MyLoc.on = false; Toast.makeText(context, context.getString(R.string.mp_hide_pos), Toast.LENGTH_SHORT).show() } }),
        shape = RoundedCornerShape(12.dp), shadowElevation = 6.dp,
        color = if (MyLoc.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
    ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MyLocation, stringResource(if (MyLoc.on) R.string.mp_hide_pos else R.string.mp_show_pos)) } }
}

/** Ascolta GPS/rete finché "La mia posizione" è acceso e aggiorna [MyLoc.pos]. */
@Composable
fun MyLocationEffect() {
    val context = LocalContext.current
    DisposableEffect(MyLoc.on) {
        if (!MyLoc.on) { MyLoc.pos = null; return@DisposableEffect onDispose { } }
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(l: android.location.Location) { MyLoc.pos = Triple(l.latitude, l.longitude, l.accuracy) }
            @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
        }
        runCatching {
            lm.getProviders(true).filter { it == android.location.LocationManager.GPS_PROVIDER || it == android.location.LocationManager.NETWORK_PROVIDER }.forEach { prov ->
                lm.requestLocationUpdates(prov, 2000L, 3f, listener, android.os.Looper.getMainLooper())
                lm.getLastKnownLocation(prov)?.let { if (MyLoc.pos == null) MyLoc.pos = Triple(it.latitude, it.longitude, it.accuracy) }
            }
        }.onFailure { Toast.makeText(context, context.getString(R.string.mp_pos_unavail), Toast.LENGTH_LONG).show() }
        onDispose { runCatching { lm.removeUpdates(listener) } }
    }
}

/** Occhio "Z": mostra/nasconde i perimetri delle zone (acceso di default). */
/** Spessore base delle linee del GPX (Preferenze → Spessore traccia GPX): 2 molto sottile … 8 molto spesso; default 3. */
object GpxWidth {
    var w by mutableIntStateOf(3)
    private var loaded = false
    fun load(ctx: android.content.Context) { if (loaded) return; loaded = true; w = ctx.getSharedPreferences("map_eyes", android.content.Context.MODE_PRIVATE).getInt("gpx_w", 3) }
    fun set(ctx: android.content.Context, v: Int) { w = v; ctx.getSharedPreferences("map_eyes", android.content.Context.MODE_PRIVATE).edit().putInt("gpx_w", v).apply() }
}

/** Preferenze della mappa: opacità del colore delle zone (%) e nome dei trail "cerchiato" (riquadro con bordo) on/off. */
object MapLook {
    var zoneOp by mutableIntStateOf(22)
    var nameBox by mutableStateOf(true)
    private var loaded = false
    private fun sp(ctx: android.content.Context) = ctx.getSharedPreferences("map_eyes", android.content.Context.MODE_PRIVATE)
    fun load(ctx: android.content.Context) { if (loaded) return; loaded = true; zoneOp = sp(ctx).getInt("zone_op", 22); nameBox = sp(ctx).getBoolean("name_box", true) }
    fun setZoneOp(ctx: android.content.Context, v: Int) { zoneOp = v; sp(ctx).edit().putInt("zone_op", v).apply() }
    fun setNameBox(ctx: android.content.Context, v: Boolean) { nameBox = v; sp(ctx).edit().putBoolean("name_box", v).apply() }
}

/** Gli occhi Z, L, T, N, G, F si ricordano tra una visita e l'altra (SharedPreferences "map_eyes"). */
object MapEyes {
    private var loaded = false
    fun load(ctx: android.content.Context) {
        if (loaded) return
        loaded = true
        val p = ctx.getSharedPreferences("map_eyes", android.content.Context.MODE_PRIVATE)
        if (p.contains("z")) MapZones.on = p.getBoolean("z", true)
        if (p.contains("l")) MapBuilders.on = p.getBoolean("l", true)
        if (p.contains("t")) MapTrails.on = p.getBoolean("t", true)
        if (p.contains("n")) MapNames.on = p.getBoolean("n", false)
        if (p.contains("p")) MapPois.on = p.getBoolean("p", true)
        if (p.contains("g")) MapMyGpx.visible = p.getBoolean("g", true)
    }
    fun save(ctx: android.content.Context) {
        ctx.getSharedPreferences("map_eyes", android.content.Context.MODE_PRIVATE).edit()
            .putBoolean("z", MapZones.on).putBoolean("l", MapBuilders.on).putBoolean("t", MapTrails.on).putBoolean("n", MapNames.on).putBoolean("p", MapPois.on)
            .putBoolean("g", MapMyGpx.visible).apply()
    }
}

object MapZones { var on by mutableStateOf(true) }

@Composable
fun MapZonesToggle(modifier: Modifier = Modifier) {
    val eyeCtx = LocalContext.current
    SmallFloatingActionButton(
        onClick = { MapZones.on = !MapZones.on; MapEyes.save(eyeCtx) },
        containerColor = if (MapZones.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (MapZones.on) Icons.Default.Visibility else Icons.Default.VisibilityOff, stringResource(if (MapZones.on) R.string.mp_hide_zones else R.string.mp_show_zones), Modifier.size(20.dp))
            Text("Z", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Punti di interesse (POI): lista condivisa, eventi dalla mappa (aggiungi/modifica/elimina) e occhio "P". */
object MapPois {
    var on by mutableStateOf(true)
    var list by mutableStateOf<List<Poi>>(emptyList())
    var addAt by mutableStateOf<Pair<Double, Double>?>(null)
    var editId by mutableStateOf<String?>(null)
    var deleteId by mutableStateOf<String?>(null)
    var navId by mutableStateOf<String?>(null)
}

/** Elenco dei POI come JSON (per la mappa 3D). */
fun poisJson(): String = JSONArray().also { a -> MapPois.list.forEach { p -> a.put(JSONObject().put("id", p.id).put("name", p.name).put("desc", p.desc).put("type", p.type).put("lat", p.lat).put("lon", p.lon).put("by", p.createdByName).put("upd", p.updatedByName)) } }.toString()

@Composable
fun MapPoisToggle(modifier: Modifier = Modifier) {
    val eyeCtx = LocalContext.current
    SmallFloatingActionButton(
        onClick = { MapPois.on = !MapPois.on; MapEyes.save(eyeCtx) },
        containerColor = if (MapPois.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (MapPois.on) Icons.Default.Visibility else Icons.Default.VisibilityOff, stringResource(if (MapPois.on) R.string.mp_hide_poi else R.string.mp_show_poi), Modifier.size(20.dp))
            Text("P", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Occhio "L": mostra/nasconde i loghi dei costruttori sulla mappa (acceso di default). */
object MapBuilders { var on by mutableStateOf(true) }

@Composable
fun MapBuildersToggle(modifier: Modifier = Modifier) {
    val eyeCtx = LocalContext.current
    SmallFloatingActionButton(
        onClick = { MapBuilders.on = !MapBuilders.on; MapEyes.save(eyeCtx) },
        containerColor = if (MapBuilders.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (MapBuilders.on) Icons.Default.Visibility else Icons.Default.VisibilityOff, stringResource(if (MapBuilders.on) R.string.mp_hide_logos else R.string.mp_show_logos), Modifier.size(20.dp))
            Text("L", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Occhio "N": mostra/nasconde i nomi (etichette) dei trail sulla mappa (spenti di default: con molti trail rallentano). */
object MapNames { var on by mutableStateOf(false) }

@Composable
fun MapNamesToggle(modifier: Modifier = Modifier) {
    val eyeCtx = LocalContext.current
    SmallFloatingActionButton(
        onClick = { MapNames.on = !MapNames.on; MapEyes.save(eyeCtx) },
        containerColor = if (MapNames.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (MapNames.on) Icons.Default.Visibility else Icons.Default.VisibilityOff, stringResource(if (MapNames.on) R.string.mp_hide_names else R.string.mp_show_names), Modifier.size(20.dp))
            Text("N", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Occhio "G": mostra/nasconde il GPX personale caricato (senza eliminarlo). */
object MapMyGpx { var visible by mutableStateOf(true) }

/** Il GPX caricato resta quando si cambia scheda (e la mappa non si risposta su di esso: si adatta solo la prima volta). */
object MapGpxStore {
    var track by mutableStateOf<simplylucaapps.girocalendar.ui.gpx.GTrack?>(null)
    var name by mutableStateOf("")
    var fitId by mutableLongStateOf(-1L)
}

@Composable
fun MapMyGpxToggle(modifier: Modifier = Modifier) {
    val eyeCtx = LocalContext.current
    SmallFloatingActionButton(
        onClick = { MapMyGpx.visible = !MapMyGpx.visible; MapEyes.save(eyeCtx) },
        containerColor = if (MapMyGpx.visible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (MapMyGpx.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, stringResource(if (MapMyGpx.visible) R.string.mp_hide_gpx else R.string.mp_show_gpx), Modifier.size(20.dp))
            Text("G", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Tasto "3D" fisso: la zona che stai guardando in 3D (trail colorati per difficoltà e zone). */
@Composable
fun Map3DAreaButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    SmallFloatingActionButton(onClick = onClick, containerColor = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Text("3D", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

/** Occhio "T": mostra/nasconde i tracciati (GPX) dei trail sulla mappa (acceso di default). */
object MapTrails { var on by mutableStateOf(true) }

@Composable
fun MapTrailsToggle(modifier: Modifier = Modifier) {
    val eyeCtx = LocalContext.current
    SmallFloatingActionButton(
        onClick = { MapTrails.on = !MapTrails.on; MapEyes.save(eyeCtx) },
        containerColor = if (MapTrails.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (MapTrails.on) Icons.Default.Visibility else Icons.Default.VisibilityOff, stringResource(if (MapTrails.on) R.string.mp_hide_tracks else R.string.mp_show_tracks), Modifier.size(20.dp))
            Text("T", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Richiesta "vedi sulla mappa" dal dettaglio di un trail: la tab MAPPA centra e evidenzia quel trail. */
object MapFocus { var trailId by mutableStateOf<String?>(null) }

/** Tracciato arrivato da un'altra app (nome senza estensione, contenuto): la Mappa lo mostra come "Tuo GPX". */
object IncomingGpx { var pending by mutableStateOf<Pair<String, ByteArray>?>(null) }

private fun androidx.compose.ui.graphics.Color.hex(): String =
    "#%02X%02X%02X".format((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

/** Marcatori/tracciati dei trail posizionati: nome, colore stato, colore difficoltà, eventuale path. */
/** Costruisce i marker (decodifica di tutte le polyline) fuori dal thread UI. */
@Composable
internal fun rememberMarkers(trails: List<Trail>): JSONArray {
    val m by produceState(JSONArray(), trails) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { markersJson(trails) }
    }
    return m
}

private fun markersJson(trails: List<Trail>, exceptId: String? = null): JSONArray = JSONArray().also { arr ->
    trails.filter { it.lat != null && it.lon != null && it.id != exceptId }.forEach { t ->
        arr.put(JSONObject().apply {
            put("id", t.id); put("name", t.name); put("lat", t.lat); put("lon", t.lon)
            put("status", t.status.color.hex())
            put("diff", Difficulty.of(t.difficulty)?.color?.hex() ?: "#9E9E9E")
            put("closed", t.status == simplylucaapps.girocalendar.data.remote.firestore.TrailStatus.CLOSED)
            if (t.path.isNotEmpty()) put("path", JSONArray().also { p ->
                Polyline.decode(t.path).forEach { (la, lo) -> p.put(JSONArray().put(la).put(lo)) }
            })
        })
    }
}

/** Legge un GPX da [uri] e ne ricava il tracciato semplificato (null se illeggibile / <2 punti). */
private fun readGpx(context: android.content.Context, uri: Uri): List<LatLon>? = runCatching {
    val xml = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return null
    val pts = GpxTrackParser.parse(xml)?.points?.map { it.lat to it.lon } ?: return null
    Polyline.simplify(pts).takeIf { it.size >= 2 }
}.getOrNull()

private fun readTrack(context: android.content.Context, uri: Uri, name: String): simplylucaapps.girocalendar.ui.gpx.GTrack? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { simplylucaapps.girocalendar.ui.gpx.TrackFormats.parse(it, name) }
        ?.let { l -> if (l.isEmpty()) null else simplylucaapps.girocalendar.ui.gpx.GTrack(l[0].id, name, l.flatMap { it.pts }) }
}.getOrNull()

private fun displayName(context: android.content.Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: (uri.lastPathSegment ?: "")

// ── abbinamento nome file ↔ trail ──────────────────────────────────────────────
private fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

private fun similarity(a: String, b: String): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    if (a == b) return 1.0
    if (a.contains(b) || b.contains(a)) return 0.85
    val d = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) d[i][0] = i
    for (j in 0..b.length) d[0][j] = j
    for (i in 1..a.length) for (j in 1..b.length)
        d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
    return 1.0 - d[a.length][b.length].toDouble() / maxOf(a.length, b.length)
}

private data class GpxImportItem(val fileName: String, val points: List<LatLon>, var trailId: String?)

/** Tab MAPPA: base OpenStreetMap con tracciati/etichette dei trail; tap → dettaglio; import GPX multiplo. */
@Composable
fun TrailMapTab(vm: TrailViewModel, events: List<simplylucaapps.girocalendar.data.remote.firestore.RideEvent> = emptyList()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    remember { MapEyes.load(context) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    // Anche rientrando nella scheda (dopo aver cambiato scheda o essere stati in Naviga) il WebView in cache resta vuoto:
    // all'ingresso se ne crea uno nuovo; zoom e centro si ripristinano da MapViewState.
    remember { MapTabCache.discard = true }
    // Dopo lo schermo intero il WebView della scheda (in cache) resta vuoto: all'uscita lo si ricrea da zero.
    var mapEpoch by remember { mutableIntStateOf(0) }
    var wasFull by remember { mutableStateOf(fullscreen) }
    LaunchedEffect(fullscreen) {
        if (wasFull && !fullscreen) { MapTabCache.discard = true; mapEpoch++ }
        wasFull = fullscreen
    }
    var trace by remember { mutableStateOf<TraceResult?>(null) }
    var newZone by remember { mutableStateOf<List<LatLon>?>(null) }
    var buildersZone by remember { mutableStateOf<String?>(null) }
    val zoneCenters = remember(state.trails) {
        state.trails.filter { it.lat != null && it.lon != null && it.zone.isNotBlank() }.groupBy { it.zone.trim() }
            .mapValues { (_, l) -> l.map { it.lat!! }.average() to l.map { it.lon!! }.average() }
    }
    var tappedZone by remember { mutableStateOf<TrailZone?>(null) }
    MyLocationEffect()
    // GPX personale: tracciato completo (quota/tempi) + eventuale anteprima di ritaglio; sulla mappa va la versione alleggerita.
    var myTrack: simplylucaapps.girocalendar.ui.gpx.GTrack? by MapGpxStore::track   // resta anche cambiando scheda
    var cropping by remember { mutableStateOf(false) }
    var cropPreview by remember { mutableStateOf<List<simplylucaapps.girocalendar.ui.gpx.GPt>?>(null) }
    val myGpx: List<LatLon>? = remember(myTrack, cropPreview) {
        val src = cropPreview ?: myTrack?.pts
        src?.let { simplylucaapps.girocalendar.ui.gpx.GpxModel.simplify(it, 2.0).map { p -> p.lat to p.lon } }
    }
    var myGpxName: String by MapGpxStore::name
    val pickMine = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name0 = displayName(context, uri).substringBeforeLast('.')
        val pts = readTrack(context, uri, name0)
        if (pts == null) Toast.makeText(context, context.getString(R.string.mp_gpx_invalid), Toast.LENGTH_LONG).show()
        else { myTrack = pts; cropping = false; cropPreview = null; MapMyGpx.visible = true; myGpxName = name0 }
    }
    var showMine by remember { mutableStateOf(false) }
    var gpxMenu by remember { mutableStateOf(false) }
    var showChoice by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    if (showMine) MyStorageDialog(
        onDismiss = { showMine = false },
        onOpen = { name, bytes ->
            val l = simplylucaapps.girocalendar.ui.gpx.TrackFormats.parse(bytes, name)
            if (l.isEmpty()) Toast.makeText(context, context.getString(R.string.mp_gpx_invalid), Toast.LENGTH_LONG).show()
            else { myTrack = simplylucaapps.girocalendar.ui.gpx.GTrack(l[0].id, name, l.flatMap { it.pts }); cropping = false; cropPreview = null; MapMyGpx.visible = true; myGpxName = name }
        },
    )
    LaunchedEffect(IncomingGpx.pending) {
        val inc = IncomingGpx.pending ?: return@LaunchedEffect
        // Si aspetta che la pagina della mappa sia caricata: il suo avvio azzera il GPX personale e cancellerebbe quello appena arrivato.
        kotlinx.coroutines.delay(2000)
        IncomingGpx.pending = null
        val l = simplylucaapps.girocalendar.ui.gpx.TrackFormats.parse(inc.second, inc.first)
        if (l.isEmpty()) Toast.makeText(context, context.getString(R.string.mp_gpx_invalid), Toast.LENGTH_LONG).show()
        else { myTrack = simplylucaapps.girocalendar.ui.gpx.GTrack(l[0].id, inc.first, l.flatMap { it.pts }); cropping = false; cropPreview = null; MapMyGpx.visible = true; myGpxName = inc.first }
    }
    simplylucaapps.girocalendar.ui.gpx.GpxPlaybackDriver(myTrack)
    LaunchedEffect(myTrack) { val t = myTrack; if (t != null) { kotlinx.coroutines.delay(1500); MapGpxStore.fitId = t.id } }
    var importItems by remember { mutableStateOf<List<GpxImportItem>?>(null) }
    val markers = rememberMarkers(state.trails)
    val focusId = MapFocus.trailId

    val pickGpx = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val candidates = state.trails.map { it to norm(it.name) }
        val items = uris.mapNotNull { uri ->
            val pts = readGpx(context, uri) ?: return@mapNotNull null
            val base = displayName(context, uri).substringBeforeLast('.')
            val n = norm(base)
            val best = candidates.maxByOrNull { similarity(n, it.second) }
            val ok = best != null && similarity(n, best.second) >= 0.6
            GpxImportItem(base, pts, if (ok) best!!.first.id else null)
        }
        if (items.size < uris.size) Toast.makeText(context, context.getString(R.string.mp_gpx_unreadable, uris.size - items.size), Toast.LENGTH_LONG).show()
        if (items.isNotEmpty()) importItems = items
    }

    // LOG delle modifiche anche sotto la mappa (visibile ai soli admin come nella scheda Trail): la mappa lascia una fascia in basso per poter scorrere.
    var log by remember { mutableStateOf<List<simplylucaapps.girocalendar.data.remote.firestore.TrailLogEntry>>(emptyList()) }
    LaunchedEffect(state.isAdmin) {
        if (state.isAdmin) vm.observeLog().collect { log = it } else log = emptyList()
    }
    var showLog by remember { mutableStateOf(false) }
    // Il pulsante LOG sta nella riga in basso con Esplora/Naviga (MapNavTab) e chiede l'apertura da qui.
    // si apre solo per una pressione NUOVA: se la scheda viene ricreata (es. si torna da Naviga) il contatore vecchio non riapre il LOG
    var logHandled by remember { mutableIntStateOf(MapLogButton.tick) }
    LaunchedEffect(MapLogButton.tick) { if (MapLogButton.tick != logHandled) { logHandled = MapLogButton.tick; showLog = true } }
    var showArea by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        key(mapEpoch) {
        TrailWebMap(markers = markers, captureGestures = false, focusId = focusId, syncToken = MapViewState.syncToken, onTrail = { detailId = it }, myGpx = myGpx, myGpxNoFit = cropping || (myTrack != null && MapGpxStore.fitId == myTrack!!.id), myGpxName = myGpxName, onGpxCleared = { myTrack = null; cropping = false; cropPreview = null },
            zones = state.zones, zoneCenters = zoneCenters, onZoneBuilders = { id -> buildersZone = state.zones.firstOrNull { z -> z.id == id }?.zone }, onZone = { newZone = it }, onZoneTap = { id -> tappedZone = state.zones.firstOrNull { z -> z.id == id } },
            onEditSave = { id, pts, kind ->
                if (kind == "zone") state.zones.firstOrNull { it.id == id }?.let { z -> vm.setZone(z.zone, pts) { ok, e -> Toast.makeText(context, if (ok) context.getString(R.string.mp_zone_saved, z.zone) else (e ?: context.getString(R.string.tr_error)), Toast.LENGTH_LONG).show() } }
                else vm.setPath(id, pts) { ok, e -> Toast.makeText(context, if (ok) context.getString(R.string.mp_track_saved) else (e ?: context.getString(R.string.tr_error)), Toast.LENGTH_LONG).show() }
            },
            onEditDelete = { id, kind ->
                if (kind == "zone") state.zones.firstOrNull { it.id == id }?.let { z -> vm.deleteZone(z) { ok, e -> Toast.makeText(context, if (ok) context.getString(R.string.mp_perimeter_deleted) else (e ?: context.getString(R.string.tr_error)), Toast.LENGTH_LONG).show() } }
                else vm.setPath(id, emptyList()) { ok, e -> Toast.makeText(context, if (ok) context.getString(R.string.mp_track_removed) else (e ?: context.getString(R.string.tr_error)), Toast.LENGTH_LONG).show() }
            })
        }
        MapTrailsToggle(Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = 84.dp))
        // Stesso pulsante APRI GPX della modalità Naviga: sul telefono / dal calendario / Il mio spazio.
        Button(
            onClick = { showChoice = true },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 60.dp).height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF3B7D3B), contentColor = androidx.compose.ui.graphics.Color.White),
        ) { Text("🧭 " + androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.nv_open_gpx), fontWeight = FontWeight.Bold) }
        if (showChoice) AlertDialog(
            onDismissRequest = { showChoice = false },
            title = { Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.nv_open_gpx)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showChoice = false; pickMine.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("📱 " + androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.og_phone)) }
                    if (events.isNotEmpty()) OutlinedButton(onClick = { showChoice = false; showPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("📅 " + androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.og_calendar)) }
                    if (state.signedIn) OutlinedButton(onClick = { showChoice = false; showMine = true }, modifier = Modifier.fillMaxWidth()) { Text("☁ " + androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.menu_my_storage)) }
                    if (TrailEdit.on) OutlinedButton(onClick = { showChoice = false; pickGpx.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mp_import_gpx_menu)) }
                }
            },
            confirmButton = { TextButton(onClick = { showChoice = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
        if (showPicker) simplylucaapps.girocalendar.ui.nav.NavPickerDialog(
            events = events,
            onPick = { name, pts -> showPicker = false; myTrack = simplylucaapps.girocalendar.ui.gpx.GTrack(0, name, pts); cropping = false; cropPreview = null; MapMyGpx.visible = true; myGpxName = name },
            onDismiss = { showPicker = false },
        )
        MapNamesToggle(Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = 124.dp))
        MapPoisToggle(Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = 164.dp))
        MapBuildersToggle(Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = 44.dp))
        MapZonesToggle(Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = 4.dp))
        MyLocationButton(Modifier.align(Alignment.BottomEnd).padding(bottom = 186.dp, end = 14.dp))
        // Avviso al centro della mappa con l'icona schermo intero (tocca per aprirlo); resta anche il tasto in alto a sinistra.
        Surface(
            Modifier.align(Alignment.Center).padding(horizontal = 56.dp).clickable { fullscreen = true },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            shadowElevation = 4.dp,
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Fullscreen, stringResource(R.string.mp_fullscreen), Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.mp_fs_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        TrailEditToggle(state.signedIn, Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = 204.dp))
        MapHelpButton(Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = if (state.signedIn) 244.dp else 204.dp))
        Map3DAreaButton(Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = if (state.signedIn) 284.dp else 244.dp)) { showArea = true }
        Row(Modifier.align(Alignment.TopStart).padding(top = 64.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // X sul pulsante: elimina il GPX personale caricato (compare solo quando c'è)
            if (myGpx != null) {
                Spacer(Modifier.width(2.dp))
                MapMyGpxToggle()
                Spacer(Modifier.width(2.dp))
                FilledTonalIconButton(onClick = { myTrack = null; cropping = false; cropPreview = null }) { Icon(Icons.Default.Close, stringResource(R.string.mp_delete_gpx)) }
                myTrack?.let { t -> Spacer(Modifier.width(2.dp)); simplylucaapps.girocalendar.ui.gpx.GpxToolsMenu(t, onChange = { myTrack = it; cropPreview = null }, onStartCrop = { cropping = true }, onPlay = { simplylucaapps.girocalendar.ui.gpx.GpxPlayback.start(t) }) }
            }
        }
        if (cropping) myTrack?.let { t -> simplylucaapps.girocalendar.ui.gpx.GpxCropPanel(t, onPreview = { cropPreview = it }, onApply = { myTrack = t.copy(pts = it); cropping = false; cropPreview = null }, onCancel = { cropping = false; cropPreview = null }, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)) }
        if (!cropping) simplylucaapps.girocalendar.ui.gpx.GpxPlayerPanel(Modifier.align(Alignment.BottomCenter).padding(12.dp))
        if (state.trails.none { it.lat != null && it.lon != null }) {
            Surface(
                Modifier.align(Alignment.TopCenter).padding(12.dp).padding(end = 48.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 4.dp,
            ) {
                Text(
                    stringResource(R.string.mp_no_trails),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        // Tasto LOG piccolo in basso a sinistra (solo admin, non a schermo intero): apre il registro delle modifiche.
    }
    // 3D del GPX: stessi livelli della mappa 2D (zone, trail, nomi, punti di interesse), così gli occhi Z/T/N/P ci sono tutti
    if (showArea) {
        val zl = state.zones.map { z -> z.zone to Polyline.decode(z.polygon) }
        val zb = state.zones.associate { it.zone to it.builders }
        // Con un GPX caricato il 3D mostra il tuo GPX sopra trail e zone; altrimenti la zona.
        val t = myTrack
        if (t != null) simplylucaapps.girocalendar.ui.gpx.Flight3DDialog(t, markers, zl, zoneBuilders = zb) { showArea = false }
        else simplylucaapps.girocalendar.ui.gpx.Flight3DAreaDialog(markers, MapViewState.view, zl, zoneBuilders = zb) { showArea = false }
    }
    if (showLog) androidx.compose.ui.window.Dialog(onDismissRequest = { showLog = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("LOG", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showLog = false }) { Icon(Icons.Default.Close, stringResource(R.string.action_close)) }
                }
                Column(Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    TrailLogSection(log, state.isAdmin, onUndo = { e, force, cb -> vm.undoLog(e, force, cb) }, startExpanded = true)
                }
            }
        }
    }

    if (fullscreen) {
        Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            EdgeToEdgeDialogWindow()
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
                    val sv = remember { MapViewState.view }
                    DisposableEffect(Unit) { onDispose { MapViewState.syncToken++ } }
                    TrailWebMap(markers = markers, captureGestures = true, trace = TrailEdit.on, focusId = focusId, startView = sv, onTrail = { detailId = it }, onTrace = { json -> parseTrace(json)?.let { trace = it } }, myGpx = myGpx, myGpxNoFit = cropping || (myTrack != null && MapGpxStore.fitId == myTrack!!.id), myGpxName = myGpxName, onGpxCleared = { myTrack = null; cropping = false; cropPreview = null },
            zones = state.zones, zoneCenters = zoneCenters, onZoneBuilders = { id -> buildersZone = state.zones.firstOrNull { z -> z.id == id }?.zone }, onZone = { newZone = it }, onZoneTap = { id -> tappedZone = state.zones.firstOrNull { z -> z.id == id } },
            onEditSave = { id, pts, kind ->
                if (kind == "zone") state.zones.firstOrNull { it.id == id }?.let { z -> vm.setZone(z.zone, pts) { ok, e -> Toast.makeText(context, if (ok) context.getString(R.string.mp_zone_saved, z.zone) else (e ?: context.getString(R.string.tr_error)), Toast.LENGTH_LONG).show() } }
                else vm.setPath(id, pts) { ok, e -> Toast.makeText(context, if (ok) context.getString(R.string.mp_track_saved) else (e ?: context.getString(R.string.tr_error)), Toast.LENGTH_LONG).show() }
            },
            onEditDelete = { id, kind ->
                if (kind == "zone") state.zones.firstOrNull { it.id == id }?.let { z -> vm.deleteZone(z) { ok, e -> Toast.makeText(context, if (ok) context.getString(R.string.mp_perimeter_deleted) else (e ?: context.getString(R.string.tr_error)), Toast.LENGTH_LONG).show() } }
                else vm.setPath(id, emptyList()) { ok, e -> Toast.makeText(context, if (ok) context.getString(R.string.mp_track_removed) else (e ?: context.getString(R.string.tr_error)), Toast.LENGTH_LONG).show() }
            })
                    // La mappa copre tutto lo schermo (anche sotto barra di stato e navigazione); i controlli restano nell'area sicura.
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                    SmallFloatingActionButton(
                        onClick = { fullscreen = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp),
                    ) { Icon(Icons.Default.Close, androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.action_close)) }
                    MapTrailsToggle(Modifier.align(Alignment.TopStart).padding(top = 4.dp, start = 84.dp))
        MapNamesToggle(Modifier.align(Alignment.TopStart).padding(top = 4.dp, start = 124.dp))
        MapPoisToggle(Modifier.align(Alignment.TopStart).padding(top = 4.dp, start = 164.dp))
        MapBuildersToggle(Modifier.align(Alignment.TopStart).padding(top = 4.dp, start = 44.dp))
                    MapZonesToggle(Modifier.align(Alignment.TopStart).padding(top = 4.dp, start = 4.dp))
                    MyLocationButton(Modifier.align(Alignment.BottomEnd).padding(bottom = 136.dp, end = 14.dp))
                    Button(
                        onClick = { showChoice = true },
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 14.dp).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF3B7D3B), contentColor = androidx.compose.ui.graphics.Color.White),
                    ) { Text("🧭 " + androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.nv_open_gpx), fontWeight = FontWeight.Bold) }
        TrailEditToggle(state.signedIn, Modifier.align(Alignment.TopStart).padding(top = 4.dp, start = 204.dp))
        MapHelpButton(Modifier.align(Alignment.TopStart).padding(top = 4.dp, start = if (state.signedIn) 244.dp else 204.dp))
        Map3DAreaButton(Modifier.align(Alignment.TopStart).padding(top = 4.dp, start = if (state.signedIn) 284.dp else 244.dp)) { showArea = true }
                    Row(Modifier.align(Alignment.TopStart).padding(top = 56.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        // X sul pulsante: elimina il GPX personale caricato (compare solo quando c'è)
                        if (myGpx != null) {
                            Spacer(Modifier.width(2.dp))
                            MapMyGpxToggle()
                            Spacer(Modifier.width(2.dp))
                            FilledTonalIconButton(onClick = { myTrack = null; cropping = false; cropPreview = null }) { Icon(Icons.Default.Close, stringResource(R.string.mp_delete_gpx)) }
                myTrack?.let { t -> Spacer(Modifier.width(2.dp)); simplylucaapps.girocalendar.ui.gpx.GpxToolsMenu(t, onChange = { myTrack = it; cropPreview = null }, onStartCrop = { cropping = true }, onPlay = { simplylucaapps.girocalendar.ui.gpx.GpxPlayback.start(t) }) }
                        }
                    }
                    if (cropping) myTrack?.let { t -> simplylucaapps.girocalendar.ui.gpx.GpxCropPanel(t, onPreview = { cropPreview = it }, onApply = { myTrack = t.copy(pts = it); cropping = false; cropPreview = null }, onCancel = { cropping = false; cropPreview = null }, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)) }
        if (!cropping) simplylucaapps.girocalendar.ui.gpx.GpxPlayerPanel(Modifier.align(Alignment.BottomCenter).padding(12.dp))
                    }
                }
            }
        }
    }

    buildersZone?.let { zn ->
        BuildersDialog(
            zone = zn, existing = state.zones.firstOrNull { it.zone == zn }?.builders.orEmpty(),
            onSave = { list -> vm.setZoneBuilders(zn, list) { ok, e -> Toast.makeText(context, if (ok) context.getString(R.string.tr_builders_saved) else (e ?: context.getString(R.string.tr_error)), Toast.LENGTH_LONG).show() }; buildersZone = null },
            onDismiss = { buildersZone = null },
        )
    }
    newZone?.let { pts ->
        ZonePickDialog(
            zones = state.trails.map { it.zone.trim() }.filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase() },
            withPolygon = state.zones.map { it.zone }.toSet(),
            onPick = { z -> vm.setZone(z, pts) { ok, e -> Toast.makeText(context, if (ok) context.getString(R.string.mp_zone_saved, z) else (e ?: context.getString(R.string.tr_error)), Toast.LENGTH_LONG).show() }; newZone = null },
            onDismiss = { newZone = null },
        )
    }
    LaunchedEffect(state.pois) { MapPois.list = state.pois }
    MapPois.addAt?.let { (la, lo) ->
        PoiDialog(initial = null, onDismiss = { MapPois.addAt = null }) { name, desc, type ->
            vm.savePoi(Poi(name = name, desc = desc, type = type, lat = la, lon = lo)) { ok, e -> Toast.makeText(context, if (ok) context.getString(R.string.poi_saved) else (e ?: context.getString(R.string.tr_error)), Toast.LENGTH_LONG).show() }
            MapPois.addAt = null
        }
    }
    MapPois.editId?.let { id ->
        val p = state.pois.firstOrNull { it.id == id }
        if (p == null) MapPois.editId = null
        else PoiDialog(initial = p, onDismiss = { MapPois.editId = null }) { name, desc, type ->
            vm.savePoi(p.copy(name = name, desc = desc, type = type)) { ok, e -> Toast.makeText(context, if (ok) context.getString(R.string.poi_saved) else (e ?: context.getString(R.string.tr_error)), Toast.LENGTH_LONG).show() }
            MapPois.editId = null
        }
    }
    MapPois.navId?.let { id ->
        val p = state.pois.firstOrNull { it.id == id }
        if (p == null) MapPois.navId = null
        else PoiNavDialog(p) { MapPois.navId = null }
    }
    MapPois.deleteId?.let { id ->
        val p = state.pois.firstOrNull { it.id == id }
        if (p == null) MapPois.deleteId = null
        else AlertDialog(
            onDismissRequest = { MapPois.deleteId = null },
            title = { Text(stringResource(R.string.poi_delete_title, p.name)) },
            confirmButton = { TextButton(onClick = {
                vm.deletePoi(id, p.name) { ok, e -> Toast.makeText(context, if (ok) context.getString(R.string.poi_deleted) else (e ?: context.getString(R.string.tr_error)), Toast.LENGTH_LONG).show() }
                MapPois.deleteId = null
            }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { MapPois.deleteId = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    tappedZone?.let { z ->
        AlertDialog(
            onDismissRequest = { tappedZone = null },
            title = { Text(stringResource(R.string.mp_zone_title, z.zone)) },
            text = { Text(stringResource(R.string.mp_del_perimeter_q)) },
            confirmButton = { TextButton(onClick = { vm.deleteZone(z) { _, _ -> }; tappedZone = null }) { Text(stringResource(R.string.mp_del_perimeter), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { tappedZone = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }

    trace?.let { tr ->
        TraceSaveDialog(
            trace = tr, trails = state.trails, vm = vm,
            onDismiss = { trace = null },
        )
    }

    importItems?.let { items ->
        GpxImportDialog(
            items = items, trails = state.trails,
            onDismiss = { importItems = null },
            onConfirm = { chosen ->
                importItems = null
                vm.setPaths(chosen) { ok ->
                    Toast.makeText(context, context.getString(R.string.mp_imported, ok), Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    state.trails.firstOrNull { it.id == detailId }?.let { fresh ->
        TrailDetailDialog(
            trail = fresh, vm = vm, signedIn = state.signedIn, myUid = state.myUid,
            canDelete = state.myUid == fresh.createdByUid || state.isAdmin,
            onDismiss = { detailId = null }, onDeleted = { detailId = null },
        )
    }
}

@Composable
private fun GpxImportDialog(
    items: List<GpxImportItem>, trails: List<Trail>,
    onDismiss: () -> Unit, onConfirm: (List<Pair<String, List<LatLon>>>) -> Unit,
) {
    val chosen = remember(items) { mutableStateListOf<String?>().apply { addAll(items.map { it.trailId }) } }
    var pickingFor by remember { mutableStateOf<Int?>(null) }

    pickingFor?.let { i ->
        TrailPickerDialog(
            trails = trails, initialQuery = items[i].fileName, current = chosen[i],
            onPick = { chosen[i] = it; pickingFor = null },
            onDismiss = { pickingFor = null },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mp_import_n, items.size)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items.indices.toList()) { i ->
                    val t = trails.firstOrNull { x -> x.id == chosen[i] }
                    Column {
                        Text(items[i].fileName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { pickingFor = i }, modifier = Modifier.fillMaxWidth()) {
                            Text(t?.let { x -> "${x.name} (${x.zone})" } ?: stringResource(R.string.mp_no_trail_tap), maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            val n = chosen.count { it != null }
            TextButton(enabled = n > 0, onClick = {
                onConfirm(items.indices.mapNotNull { i -> chosen[i]?.let { it to items[i].points } })
            }) { Text(stringResource(R.string.mp_import_count, n)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Scelta del trail con ricerca: parte dal nome del file, ordina per somiglianza, filtra mentre scrivi. */
@Composable
private fun TrailPickerDialog(
    trails: List<Trail>, initialQuery: String, current: String?,
    onPick: (String?) -> Unit, onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery) }
    val q = norm(query)
    val results = remember(q, trails) {
        val words = q.split(' ').filter { it.isNotEmpty() }
        trails.map { it to norm(it.name + " " + it.zone) }
            .filter { (_, n) -> words.all { w -> n.contains(w) } || q.isEmpty() || similarity(q, n) >= 0.5 }
            .sortedByDescending { (t, n) -> similarity(q, norm(t.name)).coerceAtLeast(similarity(q, n)) }
            .take(40).map { it.first }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mp_pick_trail)) },
        text = {
            Column {
                CompactTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.mp_search_name_zone)) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(results, key = { it.id }) { t ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(t.id) }.padding(vertical = 8.dp)) {
                            Text(t.name, fontWeight = if (t.id == current) FontWeight.Bold else FontWeight.Normal)
                            Text(t.zone, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                    if (results.isEmpty()) item { Text(stringResource(R.string.mp_no_result), modifier = Modifier.padding(8.dp)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.mp_skip_file)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Funzioni che la pagina Leaflet richiama: stanno in un oggetto aggiornato a ogni composizione, così il WebView tenuto in cache
 *  ([MapTabCache]) parla sempre con la schermata attuale e non con una composizione ormai chiusa. */
private class MapLive {
    var trail: (String) -> Unit = {}
    var trace: (String) -> Unit = {}
    var gpxCleared: () -> Unit = {}
    var zone: (String) -> Unit = {}
    var zoneTap: (String) -> Unit = {}
    var editSave: (String) -> Unit = {}
    var editDelete: (String) -> Unit = {}
    var zoneBuilders: (String) -> Unit = {}
    var onPageFinished: (WebView) -> Unit = {}
    var html: String = ""
}

/** La mappa della scheda MAPPA è un WebView con Leaflet: ricostruirlo a ogni cambio di scheda costava un caricamento intero.
 *  Ne teniamo uno vivo finché si resta nel calendario (la mappa a schermo intero ne usa uno proprio). */
private object MapTabCache {
    var view: WebView? = null
    val live = MapLive()
    /** Se true, il prossimo [take] butta il WebView in cache e ne fa creare uno nuovo (dopo lo schermo intero resta vuoto). */
    var discard = false
    fun take(context: android.content.Context): WebView? {
        if (discard) {
            discard = false
            val old = view; view = null
            old?.let { runCatching { (it.parent as? ViewGroup)?.removeView(it); it.destroy() } }
            return null
        }
        val v = view ?: return null
        if (v.context !== context) { release(); return null }
        (v.parent as? ViewGroup)?.removeView(v)
        return v
    }
    fun release() { view?.let { runCatching { (it.parent as? ViewGroup)?.removeView(it); it.destroy() } }; view = null }
}

/** Da chiamare quando si lascia il calendario: libera il WebView della scheda MAPPA. */
fun releaseMapTabCache() = MapTabCache.release()

/** Richiesta di aprire il LOG delle modifiche dalla riga in basso della scheda Mappa. */
object MapLogButton { var tick by androidx.compose.runtime.mutableIntStateOf(0) }

private class MapBridge(val onTrail: (String) -> Unit, val onTrace: (String) -> Unit, val onReload: () -> Unit, val onGpxCleared: () -> Unit,
    val onZone: (String) -> Unit = {}, val onZoneTap: (String) -> Unit = {},
    val onEditSave: (String) -> Unit = {}, val onEditDelete: (String) -> Unit = {}, val onView: (String) -> Unit = {},
    val onOpenUrl: (String) -> Unit = {}, val onZoneBuilders: (String) -> Unit = {}) {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    @JavascriptInterface fun trail(id: String) { main.post { onTrail(id) } }
    @JavascriptInterface fun trace(json: String) { main.post { onTrace(json) } }
    @JavascriptInterface fun reload() { main.post { onReload() } }
    @JavascriptInterface fun gpxCleared() { main.post { onGpxCleared() } }
    @JavascriptInterface fun zone(json: String) { main.post { onZone(json) } }
    @JavascriptInterface fun zoneTap(id: String) { main.post { onZoneTap(id) } }
    @JavascriptInterface fun editSave(json: String) { main.post { onEditSave(json) } }
    @JavascriptInterface fun editDelete(id: String) { main.post { onEditDelete(id) } }
    @JavascriptInterface fun view(json: String) { main.post { onView(json) } }
    @JavascriptInterface fun openUrl(url: String) { main.post { onOpenUrl(url) } }
    @JavascriptInterface fun zoneBuilders(id: String) { main.post { onZoneBuilders(id) } }
    @JavascriptInterface fun poiAdd(lat: Double, lon: Double) { main.post { MapPois.addAt = lat to lon } }
    @JavascriptInterface fun poiEdit(id: String) { main.post { MapPois.editId = id } }
    @JavascriptInterface fun poiDelete(id: String) { main.post { MapPois.deleteId = id } }
    @JavascriptInterface fun poiNav(id: String) { main.post { MapPois.navId = id } }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TrailWebMap(
    markers: JSONArray, captureGestures: Boolean, trace: Boolean = false, focusId: String? = null, onTrail: (String) -> Unit,
    onTrace: (String) -> Unit = {}, onWebView: (WebView) -> Unit = {},
    myGpx: List<LatLon>? = null, myGpxNoFit: Boolean = false, myGpxName: String = "", onGpxCleared: () -> Unit = {},
    zones: List<TrailZone> = emptyList(), onZone: (List<LatLon>) -> Unit = {}, onZoneTap: (String) -> Unit = {},
    onEditSave: (String, List<LatLon>, String) -> Unit = { _, _, _ -> }, onEditDelete: (String, String) -> Unit = { _, _ -> },
    startView: Triple<Double, Double, Double>? = null, syncToken: Int = 0,
    zoneCenters: Map<String, Pair<Double, Double>> = emptyMap(), onZoneBuilders: (String) -> Unit = {},
    routeMarkers: Boolean = false,
) {
    val context = LocalContext.current
    // Se la pagina viene ricostruita (es. dopo aver salvato un tracciato i trail cambiano) si riparte dall'ultima
    // vista (centro e zoom) invece che dall'inquadratura iniziale, così la mappa non "salta". Con un nuovo focus
    // ("Vedi sulla mappa") vale invece il focus.
    // Ultima vista salvata (anche tra una sessione e l'altra): al primo caricamento la mappa riparte da lì.
    if (MapViewState.view == null) MapViewState.view = simplylucaapps.girocalendar.data.settings.BootPrefs.mapView(context)
    val built = remember { booleanArrayOf(false) }
    val lastFocus = remember { arrayOfNulls<String>(1) }
    GpxWidth.load(context); MapLook.load(context)
    val html = remember(markers, captureGestures, focusId, GpxWidth.w, MapLook.zoneOp, MapLook.nameBox) {
        val sv = if (!built[0]) (startView ?: if (focusId == null) MapViewState.view else null) else if (focusId == lastFocus[0]) MapViewState.view else null
        built[0] = true; lastFocus[0] = focusId
        // trailtrace.js serve sempre (anche "Vedi tuo GPX su mappa"); l'interfaccia di tracciamento solo con trace.
        buildTrailMapHtml(markers, focusId, context.assets.open("trailtrace.js").bufferedReader().use { it.readText() }, captureGestures, sv, context.resources.configuration.locales[0].language, routeMarkers)
    }
    // Identifica il contenuto della pagina: la vista iniziale (centro/zoom) non conta, altrimenti ogni rientro nella scheda ricaricherebbe la mappa.
    val pageKey = remember(html) { html.replaceFirst(Regex("var startView = [^;]*;"), "").hashCode() }
    // GPX personale da mostrare: applicato al caricamento della pagina e a ogni cambio.
    val mineJs0 = remember(myGpx, myGpxName, myGpxNoFit) {
        if (myGpx == null) "if (window.clearMyGpx) clearMyGpx();"
        else "if (window.showMyGpx) showMyGpx(" + JSONArray().also { a -> myGpx.forEach { (la, lo) -> a.put(JSONArray().put(la).put(lo)) } } + "," + JSONObject.quote(myGpxName) + "," + myGpxNoFit + ");" +
            (if (routeMarkers) " (function(){ try { if (window.__rm) window.__rm.remove(); var p = " + JSONArray().also { a -> myGpx.forEach { (la, lo) -> a.put(JSONArray().put(la).put(lo)) } } +
                "; window.__rm = L.layerGroup([L.circleMarker(p[0], {radius:8,color:'#fff',weight:2,fillColor:'#27AE60',fillOpacity:1}), L.circleMarker(p[p.length-1], {radius:8,color:'#fff',weight:2,fillColor:'#E74C3C',fillOpacity:1})]).addTo(map); } catch (e) {} })();" else "")
    }
    // Nomi e traccia col dito si applicano a parte dal GPX personale: cambiare occhio/matita non deve
    // rimostrare il GPX già rimosso né spostare la mappa su di esso.
    val myPos = MyLoc.pos
    // A schermo intero la mappa passa sotto la barra di navigazione: alza + / − sopra di essa.
    val navPx = if (captureGestures) WindowInsets.navigationBars.getBottom(androidx.compose.ui.platform.LocalDensity.current) else 0
    val navDp = navPx / androidx.compose.ui.platform.LocalDensity.current.density
    val insetJs = "document.querySelectorAll('.leaflet-bottom.leaflet-right').forEach(function(e){ e.style.marginBottom = '${navDp + (if (captureGestures) 0f else 50f)}px'; }); "
    val zonesJson = remember(zones, zoneCenters) {
        JSONArray().also { a ->
            zones.forEach { z ->
                val o = JSONObject().put("id", z.id).put("zone", z.zone)
                    .put("pts", JSONArray().also { p -> Polyline.decode(z.polygon).forEach { (la, lo) -> p.put(JSONArray().put(la).put(lo)) } })
                    .put("builders", JSONArray().also { b -> z.builders.forEach { x -> b.put(JSONObject().put("name", x.name).put("logo", x.logo).put("url", x.url)) } })
                zoneCenters[z.zone]?.let { o.put("center", JSONArray().put(it.first).put(it.second)) }
                a.put(o)
            }
        }.toString()
    }
    val poisJson = remember(MapPois.list) {
        poisJson()
    }
    val posJs = if (myPos == null) "if (window.clearMyPos) clearMyPos();" else "if (window.setMyPos) setMyPos(${myPos.first}, ${myPos.second}, ${myPos.third});" + (if (MyLoc.centerTick > 0) " if (window.centerMyPos) centerMyPos(); /*${MyLoc.centerTick}*/" else "")
    val stateJs = insetJs + "if (window.setLabels) setLabels(" + MapNames.on + "); if (window.setLines) setLines(" + MapTrails.on + "); if (window.setTrace) setTrace(" + trace + "); if (window.setZones) { setZones(" + zonesJson + "); setZoneEdit(" + trace + "); setZonesVisible(" + MapZones.on + "); setBuildersVisible(" + MapBuilders.on + "); } if (window.setPois) { setPois(" + poisJson + "); setPoisVisible(" + MapPois.on + "); } " + posJs
    // Percorso in registrazione: linea rossa semitrasparente che cresce (applicata a parte, senza rifare zone e trail).
    val recJs = if (!TrackRecorder.active && TrackRecorder.finished == null) "if (window.clearRec) clearRec();"
        else "if (window.setRec) setRec(" + JSONArray().also { a -> TrackRecorder.snapshot().forEach { (la, lo) -> a.put(JSONArray().put(la).put(lo)) } } + ");"
    val recJsNow by rememberUpdatedState(recJs)
    var appliedRec by remember { mutableStateOf<String?>(null) }
    val stateJsNow by rememberUpdatedState(stateJs)
    val mineJsNow by rememberUpdatedState(mineJs0)
    // Occhio G: visibilità del GPX personale, applicata dopo il disegno del GPX (non lo ricarica né sposta la mappa).
    val visJs = "if (window.setMyGpxVisible) setMyGpxVisible(" + MapMyGpx.visible + ");"
    val visJsNow by rememberUpdatedState(visJs)
    var appliedVis by remember { mutableStateOf<String?>(null) }
    // Riproduzione GPX: marker in movimento (stato globale in GpxPlayback).
    val playJs = simplylucaapps.girocalendar.ui.gpx.GpxPlayback.posJs
    val trackJs = simplylucaapps.girocalendar.ui.gpx.GpxPlayback.trackJs
    val trackJsNow by rememberUpdatedState(trackJs)
    var appliedTrack by remember { mutableStateOf<String?>(null) }
    val playJsNow by rememberUpdatedState(playJs)
    var appliedPlay by remember { mutableStateOf<String?>(null) }
    val live = remember { if (captureGestures) MapLive() else MapTabCache.live }
    SideEffect {
        live.trail = onTrail
        live.trace = onTrace
        live.gpxCleared = onGpxCleared
        live.zone = { json -> parsePoints(json)?.let { onZone(it) } }
        live.zoneTap = onZoneTap
        live.editSave = { json -> runCatching { JSONObject(json) }.getOrNull()?.let { o -> parsePoints2(o.getJSONArray("pts").toString())?.let { onEditSave(o.getString("id"), it, o.optString("kind", "trail")) } } }
        live.editDelete = { json -> runCatching { JSONObject(json) }.getOrNull()?.let { o -> onEditDelete(o.getString("id"), o.optString("kind", "trail")) } }
        live.zoneBuilders = onZoneBuilders
        live.html = html
        live.onPageFinished = { view ->
            view.evaluateJavascript(stateJsNow, null)
            view.evaluateJavascript(mineJsNow, null)
            view.evaluateJavascript(visJsNow, null)
            view.evaluateJavascript(recJsNow, null)
            if (trackJsNow.isNotEmpty()) view.evaluateJavascript(trackJsNow, null)
            if (playJsNow.isNotEmpty()) view.evaluateJavascript(playJsNow, null)
        }
    }
    var appliedToken by remember { mutableIntStateOf(syncToken) }
    var appliedState by remember { mutableStateOf<String?>(null) }
    var appliedMine by remember { mutableStateOf<String?>(null) }
    val touchWv = remember { arrayOfNulls<WebView>(1) }
    CancelTouchesOnLifecycle { touchWv[0] }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            MapTabCache.take(context).takeIf { !captureGestures }?.also { it.onResume(); onWebView(it) } ?: WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(MapBridge({ live.trail(it) }, { live.trace(it) }, { loadDataWithBaseURL("https://unpkg.com/", live.html, "text/html", "utf-8", null) }, { live.gpxCleared() },
                    { json -> live.zone(json) }, { id -> live.zoneTap(id) },
                    { json -> live.editSave(json) },
                    { json -> live.editDelete(json) },
                    { json -> runCatching { JSONArray(json) }.getOrNull()?.let { a ->
                        val v = Triple(a.getDouble(0), a.getDouble(1), a.getDouble(2))
                        MapViewState.view = v
                        simplylucaapps.girocalendar.data.settings.BootPrefs.setMapView(context, v)
                    } },
                    { url -> runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                    { id -> live.zoneBuilders(id) }), "Android")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) = live.onPageFinished(view)
                    // Tile OSM e file di Leaflet passano dalla cache su disco (vedi MapTileCache).
                    override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest) =
                        simplylucaapps.girocalendar.util.MapTileCache.intercept(context, request.url, request.method)
                }
                // A tutto schermo la mappa riceve tutti i gesti; nella tab, invece, lo swipe tra le schede
                // ha la priorità (altrimenti non si potrebbe più cambiare scheda trascinando).
                if (captureGestures) setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                        Log.d(TAG, "console: ${msg.message()}"); return true
                    }
                }
                tag = pageKey
                onWebView(this)
                if (!captureGestures) MapTabCache.view = this
                loadDataWithBaseURL("https://unpkg.com/", html, "text/html", "utf-8", null)
            }
        },
        // La mappa a schermo intero si distrugge; quella della scheda resta in cache (vedi MapTabCache) e si ferma soltanto.
        onRelease = { runCatching { if (captureGestures) it.destroy() else { it.onPause(); (it.parent as? ViewGroup)?.removeView(it) } } },
        // Ricarica solo se i contenuti sono davvero cambiati (altrimenti si perderebbero zoom/pan).
        update = {
            touchWv[0] = it
            if (it.tag != pageKey) {
                it.tag = pageKey; appliedState = null; appliedRec = null; appliedMine = null; appliedVis = null; appliedPlay = null; appliedTrack = null
                // Pagina ricostruita (es. un trail modificato cambia nome/stato/difficoltà): si riparte dalla vista ATTUALE
                // (centro e zoom letti dalla pagina che sta per essere sostituita), se il focus è lo stesso.
                val wv = it; val newFocus = focusId
                wv.evaluateJavascript("(function(){try{var c=map.getCenter();return JSON.stringify([c.lat,c.lng,map.getZoom(),focusId])}catch(e){return null}})()") { r ->
                    val live = runCatching { JSONArray(JSONObject.quote("x").let { _ -> org.json.JSONTokener(r).nextValue() as String }) }.getOrNull()
                    val ok = live != null && live.length() == 4 && (if (live.isNull(3)) newFocus == null else live.optString(3) == newFocus)
                    val h = if (ok) {
                        MapViewState.view = Triple(live!!.getDouble(0), live.getDouble(1), live.getDouble(2))
                        html.replaceFirst(Regex("var startView = [^;]*;"), "var startView = [" + live.getDouble(0) + ", " + live.getDouble(1) + ", " + live.getDouble(2) + "];")
                    } else html
                    wv.loadDataWithBaseURL("https://unpkg.com/", h, "text/html", "utf-8", null)
                }
            }
            else {
                if (appliedToken != syncToken) {
                    appliedToken = syncToken
                    MapViewState.view?.let { v -> it.evaluateJavascript("if (window.setMapView) setMapView(${v.first}, ${v.second}, ${v.third});", null) }
                }
                if (appliedState != stateJs) { appliedState = stateJs; it.evaluateJavascript(stateJs, null) }
                if (appliedMine != mineJs0) { appliedMine = mineJs0; it.evaluateJavascript(mineJs0, null); appliedVis = null }
                if (appliedVis != visJs) { appliedVis = visJs; it.evaluateJavascript(visJs, null) }
                if (appliedTrack != trackJs) { appliedTrack = trackJs; if (trackJs.isNotEmpty()) it.evaluateJavascript(trackJs, null) }
                if (appliedPlay != playJs) { appliedPlay = playJs; if (playJs.isNotEmpty()) it.evaluateJavascript(playJs, null) }
                if (appliedRec != recJs) { appliedRec = recJs; it.evaluateJavascript(recJs, null) }
            }
        },
    )
}

private fun buildTrailMapHtml(markers: JSONArray, focusId: String?, traceJs: String, trace: Boolean, startView: Triple<Double, Double, Double>? = null, lang: String = "it", fresh: Boolean = false): String {
    fun safe(s: String) = s.replace("</", "<\\/")
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <script>var TTFRESH = $fresh; window.TT_GPX_W = ${GpxWidth.w}; window.TT_ZONE_OP = ${MapLook.zoneOp}; document.documentElement.className = "${if (MapLook.nameBox) "" else "tl-plain"}"; window.TT_LANG = "$lang"; function TTL(it, en) { return window.TT_LANG === "en" ? en : it; }</script>
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
          <style>
            html, body { height: 100%; margin: 0; padding: 0; }
            .tl { display: inline-flex; align-items: center; gap: 4px; background: rgba(255,255,255,0.93);
                  border: 2px solid #999; border-radius: 12px; padding: 1px 7px 1px 4px; white-space: nowrap;
                  font: 600 12px sans-serif; color: #222; box-shadow: 0 1px 3px rgba(0,0,0,.35); transform: translate(-8px,-50%); }
            .leaflet-control-zoom a { width: 40px !important; height: 40px !important; line-height: 40px !important; font-size: 24px !important; }
            .tl-plain .tl { background: none !important; border: 0 !important; box-shadow: none !important; text-shadow: 0 0 3px #fff, 0 0 3px #fff, 0 0 3px #fff; }
            .tl i { display: inline-block; width: 10px; height: 10px; border-radius: 50%; border: 1px solid rgba(0,0,0,.4); }
            .leaflet-top.leaflet-right { margin-top: ${if (fresh) 64 else 94}px; }
            ${if (!trace && !fresh) ".leaflet-bottom.leaflet-left { margin-bottom: 44px; }" else ""}
          </style>
        </head>
        <body>
          <div id="map"></div>
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <script>
            // Se Leaflet non si carica (rete assente/lenta la prima volta) mostra un messaggio con "Riprova".
            if (typeof L === 'undefined') {
              document.body.innerHTML = '<div style="font:600 15px sans-serif;color:#222;padding:24px;text-align:center">' + TTL("Mappa non disponibile.<br>Controlla la connessione.", "Map not available.<br>Check your connection.") + '<br><br><button style="padding:10px 18px;font:700 15px sans-serif" onclick="Android.reload()">' + TTL("Riprova", "Retry") + '</button></div>';
              throw new Error('leaflet non caricato');
            }
            var mapDiv = document.getElementById('map');
            // Dimensioni in pixel (le percentuali a catena risolvono a 0 in alcuni WebView). Il WebView può essere
            // ancora a 0x0 quando lo script parte (pager/dialog che non hanno ancora fatto il layout): si
            // ricalcola a ogni resize e ripetutamente nei primi secondi, rifacendo la vista iniziale appena
            // le dimensioni diventano valide — era la causa della mappa vuota "ogni tanto".
            var sized = false;
            function fit() {
              var h = window.innerHeight || document.documentElement.clientHeight, w = window.innerWidth || document.documentElement.clientWidth;
              if (h > 0 && w > 0) {
                mapDiv.style.height = h + 'px'; mapDiv.style.width = w + 'px';
                if (typeof map !== 'undefined') { map.invalidateSize(); if (!sized) { sized = true; initView(); } }
              }
            }
            window.addEventListener('resize', fit);
            var markers = ${safe(markers.toString())};
            var focusId = ${if (focusId != null) JSONObject.quote(focusId) else "null"};
            var startView = ${if (startView != null) "[${startView.first}, ${startView.second}, ${startView.third}]" else "null"};
            var viewReady = false;
            var map = L.map('map', { zoomControl: false });
            L.control.zoom({ position: 'bottomright', zoomInText: '+', zoomOutText: '−' }).addTo(map);

            // Sfondi: OpenStreetMap, Topografica (OpenTopoMap, curve di livello), Ciclismo (CyclOSM).
            // Overlay opzionale: percorsi MTB di Waymarked Trails. La scelta si ricorda (localStorage).
            var bases = {
              'OpenStreetMap': L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; OpenStreetMap contributors', maxZoom: 19 }),
              [TTL('Topografica', 'Topographic')]: L.tileLayer('https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; OpenStreetMap contributors, SRTM, OpenTopoMap (CC-BY-SA)', maxZoom: 17, subdomains: 'abc' }),
              // Sfondo chiaro senza chiavi API: le stesse tile OpenStreetMap, schiarite e in grigio dal filtro .tt-light.
              [TTL('Chiara', 'Light')]: L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; OpenStreetMap contributors', maxZoom: 19, className: 'tt-light' }),
              [TTL('Ciclismo (CyclOSM)', 'Cycling (CyclOSM)')]: L.tileLayer('https://{s}.tile-cyclosm.openstreetmap.fr/cyclosm/{z}/{x}/{y}.png', {
                attribution: '&copy; OpenStreetMap contributors, CyclOSM', maxZoom: 20, subdomains: 'abc' }),
              // Satellite (immagini Esri World Imagery): ideale con il rilievo del volo 3D.
              [TTL('Satellite', 'Satellite')]: L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
                attribution: 'Tiles &copy; Esri, Maxar, Earthstar Geographics', maxZoom: 19 }),
            };
            var ovName = TTL('Percorsi MTB', 'MTB routes');
            var labels = L.layerGroup();
            var lines = L.layerGroup().addTo(map);  // tracciati dei trail (occhio T)
            window.setLines = function (on) { if (on) lines.addTo(map); else map.removeLayer(lines); };
            var overlays = {};
            overlays[ovName] = L.tileLayer('https://tile.waymarkedtrails.org/mtb/{z}/{x}/{y}.png', {
              attribution: '&copy; waymarkedtrails.org', maxZoom: 18, opacity: 0.85 });
            var saved = null, savedOv = false;
            if (!TTFRESH) { try { saved = localStorage.getItem('base'); savedOv = localStorage.getItem('mtbov') === '1'; } catch (e) {} }
            (bases[saved] || bases['OpenStreetMap']).addTo(map);
            if (savedOv) overlays[ovName].addTo(map);
            // I nomi dei trail sono spenti di default (rallentano la mappa); li accende l'occhio (setLabels).
            window.setLabels = function (on) { if (on) labels.addTo(map); else map.removeLayer(labels); };
            // La mia posizione: pallino blu + cerchio di precisione; al primo fix centra la mappa.
            var posMarker = null, posCircle = null, posShown = false;
            window.setMyPos = function (la, lo, acc) {
              if (!posMarker) {
                posCircle = L.circle([la, lo], { radius: acc, color: '#1E6BFF', weight: 1, fillOpacity: .12, interactive: false }).addTo(map);
                posMarker = L.circleMarker([la, lo], { radius: 8, color: '#fff', weight: 3, fillColor: '#1E6BFF', fillOpacity: 1, interactive: false }).addTo(map);
              } else { posMarker.setLatLng([la, lo]); posCircle.setLatLng([la, lo]); posCircle.setRadius(acc); }
              if (!posShown) { posShown = true; map.setView([la, lo], Math.max(map.getZoom(), 15)); }
            };
            var recLine = null;
            window.setRec = function (pts) {
              if (!recLine) recLine = L.polyline(pts, { color: '#e53935', weight: 7, opacity: .5, lineCap: 'round', lineJoin: 'round', interactive: false }).addTo(map);
              else recLine.setLatLngs(pts);
            };
            window.clearRec = function () { if (recLine) { map.removeLayer(recLine); recLine = null; } };
            window.centerMyPos = function () { if (posMarker) map.setView(posMarker.getLatLng(), Math.max(map.getZoom(), 15)); };
            window.clearMyPos = function () { if (posMarker) { map.removeLayer(posMarker); map.removeLayer(posCircle); posMarker = posCircle = null; } posShown = false; };
            var layersCtl = L.control.layers(bases, overlays, { position: 'topright', collapsed: true }).addTo(map);
            if (!TTFRESH) map.on('baselayerchange', function(e) { try { localStorage.setItem('base', e.name); } catch (x) {} });
            if (!TTFRESH) map.on('overlayadd', function(e) { if (e.name === ovName) { try { localStorage.setItem('mtbov', '1'); } catch (x) {} } });
            if (!TTFRESH) map.on('overlayremove', function(e) { if (e.name === ovName) { try { localStorage.setItem('mtbov', '0'); } catch (x) {} } });

            var pts = [];
            markers.forEach(function(m) {
              var open = function() { if (window.TrailTrace && TrailTrace.busy) return; Android.trail(m.id); };
              if (m.path && m.path.length > 1) {
                L.polyline(m.path, { color: '#FFFFFF', weight: 8, opacity: 0.85 }).addTo(lines);
                // Il segmento ha il colore della difficoltà (S0 grigio scuro per vedersi sul bianco); tratteggiato se il trail è chiuso.
                var lineColor = (m.diff || '#9E9E9E').toUpperCase() === '#E0E0E0' ? '#9E9E9E' : (m.diff || '#9E9E9E');
                var pl = L.polyline(m.path, { color: lineColor, weight: 5, dashArray: m.closed ? '3 9' : null }).addTo(lines);
                pl.on('click', function (e) { TrailTrace.tapTrail(map, { id: m.id, name: m.name, path: m.path }, e.latlng, open); });
                pl.on('contextmenu', function () { if (window.onTrailLongPress) onTrailLongPress({ id: m.id, name: m.name, path: m.path }); });
                m.path.forEach(function(p) { pts.push(p); });
              } else { pts.push([m.lat, m.lon]); }
              var el = document.createElement('div');
              el.className = 'tl';
              el.style.borderColor = m.diff;
              var dot = document.createElement('i');
              dot.style.background = m.status;
              var txt = document.createElement('span');
              txt.textContent = m.name;
              el.appendChild(dot); el.appendChild(txt);
              var mk = L.marker([m.lat, m.lon], { icon: L.divIcon({ html: el, className: '', iconSize: null }) }).addTo(labels);
              mk.on('click', function () { TrailTrace.tapTrail(map, { id: m.id, name: m.name, path: m.path }, [m.lat, m.lon], open); });
              mk.on('contextmenu', function () { if (m.path && window.onTrailLongPress) onTrailLongPress({ id: m.id, name: m.name, path: m.path }); });
            });

            function initView() {
              // Aprendo lo schermo intero si riparte dalla vista della mappa nella tab (stesso centro e zoom).
              if (startView) { map.setView([startView[0], startView[1]], startView[2], { animate: false }); viewReady = true; return; }
              var focus = pts;
              var fm = focusId && markers.filter(function (m) { return m.id === focusId; })[0];
              if (fm) focus = (fm.path && fm.path.length > 1) ? fm.path : [[fm.lat, fm.lon]];
              if (focus.length > 1) map.fitBounds(L.latLngBounds(focus), { padding: [40, 40], maxZoom: 16 });
              else if (focus.length === 1) map.setView(focus[0], 14);
              else map.setView([45.64, 8.35], 10);
              viewReady = true;
            }
            map.on('moveend', function () {
              if (!viewReady) return;
              var c = map.getCenter(); Android.view(JSON.stringify([c.lat, c.lng, map.getZoom()]));
            });
            window.setMapView = function (la, lo, z) { map.setView([la, lo], z, { animate: false }); };
            // Vista provvisoria (il layout può non esserci ancora), poi quella definitiva da fit().
            map.setView([45.64, 8.35], 10);
            fit();
            var tries = 0, iv = setInterval(function () { fit(); if (++tries > 30) clearInterval(iv); }, 200);
          </script>
        <script>${traceJs.replace("</", "<\\/")}</script>
        <script>${MY_GPX_JS}${if (trace) TRACE_UI_JS else ""}TrailTrace.decorateLayers(layersCtl); TrailTrace.attachSearch(map);</script>
        </body>
        </html>
    """.trimIndent()
}

/** Collega l'interfaccia di tracciamento a dito (da trailtrace.js, solo mappa a schermo intero): "Avanti"
 * consegna il risultato all'app con Android.trace(json). */
/** "Vedi tuo GPX su mappa": collega pannello e overlay (vedi attachMyGpx in trailtrace.js). */
private const val MY_GPX_JS = "var playApi = TrailTrace.attachPlayback(map, function (id) { Android.openUrl('gcphoto:' + id); }); window.setPlayPos = playApi.setPos; window.setPlayTrack = playApi.setTrack; window.setPhotosVisible = playApi.setPhotosVisible; window.clearPlay = playApi.clear; window.setPhotos = playApi.setPhotos; var myGpx = TrailTrace.attachMyGpx(map, layersCtl, markers, function (id) { Android.trail(id); }, function () { Android.gpxCleared(); }); " +
    "var zonesApi = TrailTrace.attachZones(map, layersCtl, { onOpenUrl: function (u) { Android.openUrl(u); }, onZoneTap: function (id) { var z = zonesApi.get(id); if (z) editApi.openMenu({ kind: 'zone', id: z.id, name: z.zone, path: z.pts }); } }); window.setZones = function (z) { zonesApi.set(z); }; var poisApi = TrailTrace.attachPois(map, { onEdit: function (id) { Android.poiEdit(id); }, onDelete: function (id) { Android.poiDelete(id); }, onNavigate: function (id) { Android.poiNav(id); } }); window.setPois = function (p) { poisApi.set(p); }; window.setPoisVisible = function (on) { poisApi.setVisible(on); }; var editApi = TrailTrace.attachEdit(map, { onBuilders: function (id) { Android.zoneBuilders(id); }, onSave: function (id, pts, kind) { Android.editSave(JSON.stringify({ id: id, pts: pts, kind: kind })); }, onDelete: function (id, kind) { Android.editDelete(JSON.stringify({ id: id, kind: kind })); } }); window.onTrailLongPress = function (t) { editApi.openMenu(t); }; window.setZoneEdit = function (on) { TrailTrace.editOn = !!on; zonesApi.setEdit(on); poisApi.setEdit(on); editApi.setEnabled(on); }; window.setZonesVisible = function (on) { zonesApi.setVisible(on); }; window.setBuildersVisible = function (on) { zonesApi.setBuildersVisible(on); }; " +
    "window.showMyGpx = function (pts, name, nofit) { myGpx.show(pts, name, nofit); }; window.clearMyGpx = function () { myGpx.clear(); }; window.setMyGpxVisible = function (on) { myGpx.setVisible(on); }; "
private const val TRACE_UI_JS = "var traceUi = TrailTrace.attachUi(map, function (r) { Android.trace(JSON.stringify(r)); }, 1200, function (pts) { Android.zone(JSON.stringify(pts)); }, function (la, lo) { Android.poiAdd(la, lo); }); traceUi.hide(); " +
    "window.setTrace = function (on) { if (on) traceUi.show(); else traceUi.hide(); }; "

// ── tracciamento a dito: risultato dell'aggancio, abbinamento al trail, salvataggio ────────────────
private data class TraceResult(
    val points: List<LatLon>, val snapped: Boolean, val lengthM: Double,
    val names: List<String>, val mtb: String?,
)

private fun parseTrace(json: String): TraceResult? = runCatching {
    val o = JSONObject(json)
    val a = o.getJSONArray("points")
    val pts = (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getDouble(0) to p.getDouble(1) } }
    val n = o.optJSONArray("names")
    val names = if (n == null) emptyList() else (0 until n.length()).map { n.getJSONArray(it).getString(0) }
    TraceResult(pts, o.optBoolean("snapped"), o.optDouble("lengthM"), names, if (o.isNull("mtb")) null else o.optString("mtb"))
}.getOrNull().takeIf { it != null && it.points.size >= 2 }

/** Distanza in metri da [p] alla polilinea [poly]. */
private fun distToPolyM(p: LatLon, poly: List<LatLon>): Double {
    if (poly.isEmpty()) return Double.MAX_VALUE
    val kx = 111320.0 * Math.cos(Math.toRadians(p.first)); val ky = 110540.0
    if (poly.size == 1) return Math.hypot((poly[0].second - p.second) * kx, (poly[0].first - p.first) * ky)
    var best = Double.MAX_VALUE
    for (i in 0 until poly.size - 1) {
        val ax = (poly[i].second - p.second) * kx; val ay = (poly[i].first - p.first) * ky
        val bx = (poly[i + 1].second - p.second) * kx; val by = (poly[i + 1].first - p.first) * ky
        val dx = bx - ax; val dy = by - ay; val l2 = dx * dx + dy * dy
        val t = if (l2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / l2).coerceIn(0.0, 1.0)
        best = minOf(best, Math.hypot(ax + t * dx, ay + t * dy))
    }
    return best
}

/** Frazione dei punti della traccia entro 30 m da un trail già tracciato (0..1). */
private fun overlapWith(route: List<LatLon>, trail: Trail): Double {
    if (trail.path.isEmpty()) return 0.0
    val poly = Polyline.decode(trail.path)
    val sample = Polyline.simplify(route, 1.0, 150)
    if (sample.isEmpty()) return 0.0
    return sample.count { distToPolyM(it, poly) <= 30.0 }.toDouble() / sample.size
}

/** Salva il GPX in Download/GiroCalendar (Android 10+; prima non fa nulla). Restituisce true se scritto. */
private fun saveGpxToDownloads(context: android.content.Context, name: String, points: List<LatLon>): Boolean {
    if (android.os.Build.VERSION.SDK_INT < 29) return false
    val safe = name.replace(Regex("[^A-Za-z0-9 _.-]"), "_").ifBlank { "tracciato" }
    val gpx = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"GiroCalendar\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
        append("<trk><name>").append(name.replace("&", "&amp;").replace("<", "&lt;")).append("</name><trkseg>\n")
        points.forEach { append("<trkpt lat=\"%.6f\" lon=\"%.6f\"/>\n".format(java.util.Locale.US, it.first, it.second)) }
        append("</trkseg></trk></gpx>\n")
    }
    return runCatching {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "$safe.gpx")
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/gpx+xml")
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/GiroCalendar")
        }
        val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { it.write(gpx.toByteArray()) }
        true
    }.getOrDefault(false)
}

/** Dopo il tracciamento: sceglie il trail (suggerimenti per sovrapposizione e nome OSM, ricerca) o ne crea uno nuovo. */
@Composable
private fun TraceSaveDialog(trace: TraceResult, trails: List<Trail>, vm: TrailViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var reversed by remember { mutableStateOf(false) }
    val route = remember(trace, reversed) { if (reversed) trace.points.reversed() else trace.points }
    var query by remember { mutableStateOf(trace.names.firstOrNull().orEmpty()) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(trace.names.firstOrNull().orEmpty()) }
    var zone by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf(trace.mtb?.let { "s$it" }.orEmpty()) }
    var saveGpx by remember { mutableStateOf(true) }

    val overlaps = remember(trace, trails) { trails.associate { it.id to overlapWith(trace.points, it) } }
    val q = norm(query)
    val osmNames = remember(trace) { trace.names.map { norm(it) } }
    val ranked = remember(q, trails, overlaps) {
        trails.map { t ->
            val nm = norm(t.name)
            val sim = maxOf(similarity(q, nm), osmNames.maxOfOrNull { similarity(it, nm) } ?: 0.0)
            Triple(t, overlaps[t.id] ?: 0.0, sim)
        }.filter { (t, ov, sim) -> ov >= 0.3 || q.isEmpty() || sim >= 0.5 || norm(t.name + " " + t.zone).contains(q) }
            .sortedByDescending { (_, ov, sim) -> ov * 2 + sim }.take(30)
    }
    val zones = remember(trails) { trails.map { it.zone.trim() }.filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase() } }

    fun done(label: String, ok: Boolean, err: String?, gpxName: String) {
        if (ok) {
            val saved = if (saveGpx) saveGpxToDownloads(context, gpxName, route) else false
            Toast.makeText(context, if (saved) context.getString(R.string.mp_saved_gpx, label) else context.getString(R.string.mp_saved, label), Toast.LENGTH_LONG).show()
        } else Toast.makeText(context, err ?: context.getString(R.string.tr_error), Toast.LENGTH_LONG).show()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (creating) R.string.mp_new_trail else R.string.mp_save_track)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                Text(
                    stringResource(R.string.mp_track_info, trace.lengthM.toInt(), route.size, stringResource(if (trace.snapped) R.string.mp_snapped else R.string.mp_by_hand)),
                    style = MaterialTheme.typography.labelMedium,
                )
                TextButton(onClick = { reversed = !reversed }) { Text(stringResource(if (reversed) R.string.mp_reversed else R.string.mp_reverse)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saveGpx, onCheckedChange = { saveGpx = it })
                    Text(stringResource(R.string.mp_save_gpx_too), style = MaterialTheme.typography.bodySmall)
                }
                if (creating) {
                    CompactTextField(newName, { newName = it }, label = { Text(stringResource(R.string.mp_trail_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    CompactTextField(zone, { zone = it }, label = { Text(stringResource(R.string.mp_zone)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.heightIn(max = 90.dp)) {
                        items(zones.filter { zone.isBlank() || it.contains(zone, true) }.take(6)) { z ->
                            Text(z, Modifier.fillMaxWidth().clickable { zone = z }.padding(vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(stringResource(R.string.mp_difficulty), style = MaterialTheme.typography.labelMedium)
                    // Riquadri colorati tutti uguali (come nella scheda Trail): con i chip «S4» andava a capo ed era più alto degli altri
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Difficulty.LEVELS.forEach { level ->
                            val sel = difficulty == level.id
                            Box(
                                Modifier.weight(1f).height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) level.color else level.color.copy(alpha = 0.35f))
                                    .clickable { difficulty = if (sel) "" else level.id },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(level.label, style = MaterialTheme.typography.labelLarge, color = level.onColor, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                } else {
                    CompactTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        label = { Text(stringResource(R.string.mp_search_trail)) }, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(ranked, key = { it.first.id }) { (t, ov, _) ->
                            Column(
                                Modifier.fillMaxWidth().clickable {
                                    vm.setPath(t.id, route) { ok, e -> done(t.name, ok, e, t.name) }
                                }.padding(vertical = 8.dp)
                            ) {
                                Text(t.name, fontWeight = FontWeight.Bold)
                                Text(
                                    t.zone + if (ov >= 0.3) stringResource(R.string.mp_overlaps, (ov * 100).toInt()) else "",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                        }
                        item("new") {
                            Text(
                                stringResource(R.string.mp_create_new_trail) + if (query.isNotBlank()) " «${query.trim()}»" else "",
                                Modifier.fillMaxWidth().clickable { newName = query.trim().ifEmpty { newName }; creating = true }.padding(vertical = 12.dp),
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) TextButton(enabled = newName.isNotBlank(), onClick = {
                vm.createTrailWithPath(newName.trim(), zone.trim(), difficulty, route) { ok, e -> done(newName.trim(), ok, e, newName.trim()) }
            }) { Text(stringResource(R.string.mp_create_save)) }
        },
        dismissButton = {
            TextButton(onClick = { if (creating) creating = false else onDismiss() }) { Text(stringResource(if (creating) R.string.action_back else R.string.action_cancel)) }
        },
    )
}

private fun parsePoints2(json: String): List<LatLon>? = runCatching {
    val a = JSONArray(json)
    (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getDouble(0) to p.getDouble(1) } }
}.getOrNull()?.takeIf { it.size >= 2 }

private fun parsePoints(json: String): List<LatLon>? = runCatching {
    val a = JSONArray(json)
    (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getDouble(0) to p.getDouble(1) } }
}.getOrNull()?.takeIf { it.size >= 3 }

/** Dopo aver disegnato un poligono: lo si associa a una zona esistente (il campo "zona" dei trail). */
@Composable
private fun ZonePickDialog(zones: List<String>, withPolygon: Set<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q, zones) { zones.filter { q.isBlank() || it.contains(q, true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mp_which_zone)) },
        text = {
            Column {
                CompactTextField(q, { q = it }, singleLine = true, label = { Text(stringResource(R.string.mp_search_zone)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(shown, key = { it }) { z ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(z) }.padding(vertical = 10.dp)) {
                            Text(z, fontWeight = FontWeight.Bold)
                            if (z in withPolygon) Text(stringResource(R.string.mp_has_perimeter), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                    // Nome non trovato: si può creare una zona nuova con quel nome.
                    if (q.isNotBlank() && zones.none { it.equals(q.trim(), true) }) item {
                        Column(Modifier.fillMaxWidth().clickable { onPick(q.trim()) }.padding(vertical = 10.dp)) {
                            Text(stringResource(R.string.mp_create_zone, q.trim()), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (shown.isEmpty() && q.isBlank()) item { Text(stringResource(R.string.mp_no_zones), Modifier.padding(8.dp)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun dataUrlToBitmap(url: String): android.graphics.Bitmap? = runCatching {
    val b = android.util.Base64.decode(url.substringAfter("base64,"), android.util.Base64.DEFAULT)
    android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)
}.getOrNull()

/** Immagine scelta → quadrato centrale 96x96 → data URL (WebP, o PNG se non disponibile). */
private fun uriToLogoDataUrl(context: android.content.Context, uri: Uri): String? = runCatching {
    val src = context.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }!!
    val sz = minOf(src.width, src.height)
    val sq = android.graphics.Bitmap.createBitmap(src, (src.width - sz) / 2, (src.height - sz) / 2, sz, sz)
    val bmp = android.graphics.Bitmap.createScaledBitmap(sq, 96, 96, true)
    val out = java.io.ByteArrayOutputStream()
    @Suppress("DEPRECATION")
    val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) android.graphics.Bitmap.CompressFormat.WEBP_LOSSY else android.graphics.Bitmap.CompressFormat.WEBP
    bmp.compress(fmt, 85, out)
    "data:image/webp;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
}.getOrNull()

/** Costruttori dei trail di una zona: uno o più loghi, ognuno con un link social; compaiono sulla mappa con la zona attiva. */
@Composable
fun BuildersDialog(zone: String, existing: List<simplylucaapps.girocalendar.data.remote.firestore.ZoneBuilder>, onSave: (List<simplylucaapps.girocalendar.data.remote.firestore.ZoneBuilder>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val items = remember { mutableStateListOf<simplylucaapps.girocalendar.data.remote.firestore.ZoneBuilder>().apply { addAll(existing) } }
    var logo by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    val msgImgBad = stringResource(R.string.mp_img_unreadable)
    val msgNeedLogo = stringResource(R.string.mp_need_logo)
    val msgBadLink = stringResource(R.string.mp_bad_link)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uriToLogoDataUrl(context, uri)?.let { logo = it } ?: run { err = msgImgBad }
    }
    // Aggiunge alla lista il costruttore in compilazione (se c'è); false = dati incompleti/non validi (err impostato).
    fun addPending(): Boolean {
        err = ""
        var u = url.trim()
        if (logo.isEmpty()) { err = msgNeedLogo; return false }
        if (u.isNotEmpty() && !u.startsWith("http", true)) u = "https://$u"
        if (u.isNotEmpty() && runCatching { Uri.parse(u).host.isNullOrBlank() }.getOrDefault(true)) { err = msgBadLink; return false }
        items.add(simplylucaapps.girocalendar.data.remote.firestore.ZoneBuilder(name.trim(), logo, u))
        return true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mp_builders_title, zone)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.mp_builders_hint), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                if (items.isEmpty()) Text(stringResource(R.string.mp_no_builders), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                items.forEachIndexed { i, b ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        dataUrlToBitmap(b.logo)?.let { bm -> androidx.compose.foundation.Image(bm.asImageBitmap(), null, Modifier.size(40.dp).clip(CircleShape)) }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.name.ifBlank { "—" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(b.url, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { logo = b.logo; name = b.name; url = b.url; err = ""; items.removeAt(i) }) { Icon(Icons.Default.Edit, stringResource(R.string.action_edit)) }
                        IconButton(onClick = { items.removeAt(i) }) { Icon(Icons.Default.Delete, stringResource(R.string.tr_remove), tint = MaterialTheme.colorScheme.error) }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(if (name.isNotEmpty() || logo.isNotEmpty()) R.string.mp_add_edit else R.string.action_add), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (logo.isNotEmpty()) dataUrlToBitmap(logo)?.let { bm -> androidx.compose.foundation.Image(bm.asImageBitmap(), null, Modifier.size(44.dp).clip(CircleShape)); Spacer(Modifier.width(8.dp)) }
                    OutlinedButton(onClick = { pick.launch("image/*") }) { Text(stringResource(if (logo.isEmpty()) R.string.mp_pick_logo else R.string.i_change_logo)) }
                }
                CompactTextField(name, { name = it }, label = { Text(stringResource(R.string.mp_builder_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CompactTextField(url, { url = it }, label = { Text(stringResource(R.string.mp_builder_link)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { if (addPending()) { logo = ""; name = ""; url = "" } }) { Text(stringResource(R.string.mp_plus_add)) }
            }
        },
        confirmButton = { TextButton(onClick = {
            // Se c'è un costruttore compilato ma non ancora aggiunto con "+ Aggiungi", lo aggiunge qui.
            if (logo.isNotEmpty() || name.isNotBlank() || url.isNotBlank()) { if (!addPending()) return@TextButton }
            onSave(items.toList())
        }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}


/**
 * Mappa del percorso di un'uscita (apertura da "Percorso" nella scheda evento): la stessa mappa della scheda
 * MAPPA — sfondi/layer, occhi Z/L/T/N, la mia posizione — con la traccia dell'uscita al posto del "Tuo GPX",
 * partenza (verde) e arrivo (rosso) e il riepilogo di distanza e dislivello.
 */
@Composable
fun EventRouteMap(vm: TrailViewModel, track: simplylucaapps.girocalendar.util.GpxTrack, titolo: String, onDismiss: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val markers = rememberMarkers(state.trails)
    val zoneCenters = remember(state.trails) {
        state.trails.filter { it.lat != null && it.lon != null && it.zone.isNotBlank() }.groupBy { it.zone.trim() }
            .mapValues { (_, l) -> l.map { it.lat!! }.average() to l.map { it.lon!! }.average() }
    }
    val pts = remember(track) { track.points.map { it.lat to it.lon } }
    MyLocationEffect()
    // Ogni apertura riparte da zero (occhi Z/L/T/N spenti, sfondo OpenStreetMap, niente percorsi MTB) senza cambiare
    // le scelte della scheda MAPPA: i valori vengono rimessi com'erano alla chiusura.
    val eyeCtx2 = LocalContext.current
    DisposableEffect(Unit) {
        val z = MapZones.on; val b = MapBuilders.on; val t = MapTrails.on; val n = MapNames.on; val g = MapMyGpx.visible
        MapZones.on = false; MapBuilders.on = false; MapTrails.on = false; MapNames.on = false; MapMyGpx.visible = true
        onDispose { MapZones.on = z; MapBuilders.on = b; MapTrails.on = t; MapNames.on = n; MapMyGpx.visible = g; MapEyes.save(eyeCtx2) }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                TrailWebMap(
                    markers = markers, captureGestures = true, onTrail = {}, myGpx = pts, myGpxName = titolo,
                    zones = state.zones, zoneCenters = zoneCenters, routeMarkers = true,
                )
                Row(Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    MapZonesToggle(); Spacer(Modifier.width(4.dp))
                    MapBuildersToggle(); Spacer(Modifier.width(4.dp))
                    MapTrailsToggle(); Spacer(Modifier.width(4.dp))
                    MapNamesToggle(); Spacer(Modifier.width(4.dp))
                    MapMyGpxToggle()
                }
                Row(Modifier.align(Alignment.TopStart).padding(top = 64.dp, start = 4.dp, end = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), shadowElevation = 4.dp) {
                        Text(titolo.ifBlank { stringResource(R.string.d_route) }, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f), CircleShape),
                ) { Icon(Icons.Default.Close, stringResource(R.string.action_close), tint = androidx.compose.ui.graphics.Color.White) }
                MyLocationButton(Modifier.align(Alignment.BottomEnd).padding(bottom = 104.dp, end = 14.dp))
                var showNav by remember { mutableStateOf(false) }
                Button(onClick = { showNav = true }, modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 14.dp)) {
                    Text("🧭 " + stringResource(R.string.nv_navigate), fontWeight = FontWeight.Bold)
                }
                if (showNav) simplylucaapps.girocalendar.ui.nav.NavScreen(
                    lat = DoubleArray(track.points.size) { track.points[it].lat }, lon = DoubleArray(track.points.size) { track.points[it].lon },
                    ele = if (track.points.any { it.eleM != null }) DoubleArray(track.points.size) { track.points[it].eleM ?: Double.NaN } else null,
                    title = titolo, onDismiss = { showNav = false })
            }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), shadowElevation = 6.dp,
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        @Composable fun stat(e: String, v: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(e, style = MaterialTheme.typography.titleMedium); Text(v, style = MaterialTheme.typography.labelMedium)
                        }
                        stat("📏", "%.1f km".format(track.distanceKm))
                        if (track.minEleM != null && track.maxEleM != null) stat("⛰️", "${track.minEleM.toInt()}–${track.maxEleM.toInt()} m")
                        stat("🔺", "+${track.ascentM.toInt()} m"); stat("🔻", "-${track.descentM.toInt()} m")
                    }
                }
          }
        }
    }
}


/** Finestra del Dialog a schermo intero vero: sotto barra di stato e di navigazione, barre trasparenti (di default con edge-to-edge) con icone scure. */
@Composable
fun EdgeToEdgeDialogWindow() {
    val view = LocalView.current
    SideEffect {
        val w = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
        WindowCompat.setDecorFitsSystemWindows(w, false)
        if (android.os.Build.VERSION.SDK_INT >= 28) w.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = true; isAppearanceLightNavigationBars = true }
    }
}


/** Nome + descrizione + tipo di un POI (nuovo o esistente). */
@Composable
private fun PoiDialog(initial: Poi?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var desc by remember { mutableStateOf(initial?.desc ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "ritrovo") }
    val types = listOf("ritrovo" to R.string.poi_t_ritrovo, "vetta" to R.string.poi_t_vetta, "picnic" to R.string.poi_t_picnic, "altro" to R.string.poi_t_altro)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.poi_new else R.string.poi_edit)) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactTextField(name, { name = it.take(100) }, label = { Text(stringResource(R.string.poi_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CompactTextField(desc, { desc = it.take(2000) }, label = { Text(stringResource(R.string.poi_desc)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.poi_type), style = MaterialTheme.typography.labelMedium)
                types.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (id, res) ->
                            FilterChip(selected = type == id, onClick = { type = id }, label = { Text(stringResource(res)) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), desc.trim(), type) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}


/** Un tocco su un POI propone di arrivarci col navigatore: usa l'app scelta nelle Preferenze, altrimenti chiede. */
@Composable
private fun PoiNavDialog(p: Poi, onDone: () -> Unit) {
    val context = LocalContext.current
    var remember by remember { mutableStateOf(false) }
    val dest = simplylucaapps.girocalendar.util.NavLauncher.Dest("https://www.google.com/maps/dir/?api=1&destination=${p.lat},${p.lon}", p.lat, p.lon)
    fun go(app: String) { simplylucaapps.girocalendar.util.NavLauncher.open(context, app, dest, p.name); onDone() }
    val pref = simplylucaapps.girocalendar.data.settings.BootPrefs.navApp(context)
    if (pref != simplylucaapps.girocalendar.util.NavLauncher.ASK) { LaunchedEffect(p.id) { go(pref) }; return }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.poi_nav_title, p.name)) },
        text = {
            Column {
                listOf(
                    simplylucaapps.girocalendar.util.NavLauncher.MAPS to "Google Maps",
                    simplylucaapps.girocalendar.util.NavLauncher.WAZE to "Waze",
                    simplylucaapps.girocalendar.util.NavLauncher.OTHER to stringResource(R.string.d_other_app),
                ).forEach { (id, label) ->
                    TextButton(onClick = {
                        if (remember) simplylucaapps.girocalendar.data.settings.BootPrefs.setNavApp(context, id)
                        go(id)
                    }, modifier = Modifier.fillMaxWidth()) { Text(label, modifier = Modifier.fillMaxWidth()) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Checkbox(checked = remember, onCheckedChange = { remember = it })
                    Text(stringResource(R.string.d_remember_nav), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.action_cancel)) } },
    )
}
