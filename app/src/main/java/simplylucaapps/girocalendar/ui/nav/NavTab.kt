package simplylucaapps.girocalendar.ui.nav

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import simplylucaapps.girocalendar.data.remote.firestore.RideEvent
import simplylucaapps.girocalendar.ui.gpx.GTrack
import simplylucaapps.girocalendar.ui.trails.EdgeToEdgeDialogWindow

/**
 * Scheda «Registra»: subito la mappa sulla posizione attuale, con APRI GPX, SEGNALA e REC. Aprendo un GPX
 * la stessa schermata diventa la navigazione; ✕ torna alla mappa libera. Il tasto ⛶ la porta a schermo intero
 * (fuori dal pager delle schede), ⤡ torna nella scheda.
 */
/** Traccia GPX aperta in Naviga: tenuta in memoria finché l'app resta aperta (sopravvive al cambio di scheda). */
internal object NavOpenTrack {
    var track by androidx.compose.runtime.mutableStateOf<GTrack?>(null)
}

@Composable
fun NavMapTab(events: List<RideEvent>, calendarCode: String) {
    // la traccia aperta vive fuori dalla composizione: cambiando scheda (o passando a Esplora) non si perde
    var track by NavOpenTrack::track
    var showPicker by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    var showMine by remember { mutableStateOf(false) }
    var showChoice by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val signedIn = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.isAnonymous == false

    val pickLocal = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "GPX"
            val l = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { simplylucaapps.girocalendar.ui.gpx.TrackFormats.parse(it, name) } }.getOrNull().orEmpty()
            if (l.isEmpty()) android.widget.Toast.makeText(ctx, ctx.getString(simplylucaapps.girocalendar.R.string.nv_bad_gpx), android.widget.Toast.LENGTH_LONG).show()
            else { NavIntro.seen.clear(); track = GTrack(0, name, l.flatMap { it.pts }) }
        }
    }

    @Composable fun Map(embedded: Boolean) {
        val tr = track
        key(tr?.name, tr?.pts?.size) {
            if (tr == null) {
                NavContent(DoubleArray(0), DoubleArray(0), null, "", calendarCode, free = true, embedded = embedded,
                    onOpenGpx = { showChoice = true }, onOpenMine = null, onToggleFull = { full = !full }, isFull = full, onDismiss = {})
            } else {
                NavContent(
                    DoubleArray(tr.pts.size) { tr.pts[it].lat }, DoubleArray(tr.pts.size) { tr.pts[it].lon },
                    if (tr.pts.any { it.ele != null }) DoubleArray(tr.pts.size) { tr.pts[it].ele ?: Double.NaN } else null,
                    tr.name, calendarCode, free = false, embedded = embedded,
                    onToggleFull = { full = !full }, isFull = full, onDismiss = { track = null; NavIntro.seen.clear() },
                )
            }
        }
    }

    if (full) {
        Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            EdgeToEdgeDialogWindow()
            Map(embedded = false)
        }
    } else {
        Map(embedded = true)
    }
    if (showChoice) androidx.compose.material3.AlertDialog(
        onDismissRequest = { showChoice = false },
        title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.nv_open_gpx)) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = { showChoice = false; pickLocal.launch(arrayOf("*/*")) }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) { androidx.compose.material3.Text("📱 " + androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.og_phone)) }
                androidx.compose.material3.OutlinedButton(onClick = { showChoice = false; showPicker = true }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) { androidx.compose.material3.Text("📅 " + androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.og_calendar)) }
                if (signedIn) androidx.compose.material3.OutlinedButton(onClick = { showChoice = false; showMine = true }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) { androidx.compose.material3.Text("☁ " + androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.menu_my_storage)) }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { showChoice = false }) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(android.R.string.cancel)) } },
    )
    if (showMine) simplylucaapps.girocalendar.ui.trails.MyStorageDialog(
        onDismiss = { showMine = false },
        onOpen = { name, bytes ->
            val l = simplylucaapps.girocalendar.ui.gpx.TrackFormats.parse(bytes, name)
            if (l.isEmpty()) android.widget.Toast.makeText(ctx, ctx.getString(simplylucaapps.girocalendar.R.string.mp_gpx_invalid), android.widget.Toast.LENGTH_LONG).show()
            else { NavIntro.seen.clear(); track = GTrack(0, name, l.flatMap { it.pts }) }
        },
    )
    if (showPicker) NavPickerDialog(
        events = events,
        onPick = { name, pts -> showPicker = false; NavIntro.seen.clear(); track = GTrack(0, name, pts) },
        onDismiss = { showPicker = false },
    )
}
