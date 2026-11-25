package simplylucaapps.girocalendar.ui.calendar

import simplylucaapps.girocalendar.ui.common.CompactTextField
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WbSunny
import simplylucaapps.girocalendar.ui.common.iconForLink
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.data.remote.firestore.RideEvent
import simplylucaapps.girocalendar.ui.common.DifficultyBadge
import simplylucaapps.girocalendar.ui.common.RichText
import simplylucaapps.girocalendar.util.EventExport
import simplylucaapps.girocalendar.util.GpxTrack

/**
 * Dettaglio evento a schermo intero (non più un bottom sheet): stesso pattern di
 * [EventEditScreen], apre "sopra" il calendario con un vero back invece di uno swipe-down.
 * Se l'evento ha [RideEvent.cardBgData], il banner in alto è cliccabile e apre
 * [PhotoViewerDialog] con la foto a tutto schermo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(
    event: RideEvent,
    canEdit: Boolean,
    /** Codice del calendario, usato per costruire il link diretto alla scheda evento sul
     * sito web (girocalendar.pages.dev) incluso nella condivisione. */
    calendarCode: String,
    showFieldIcons: Boolean = true,
    /** Se attivo (Preferenze), l'audio dell'evento (se presente) parte da solo all'apertura
     * della scheda, senza dover toccare il pulsante Play. */
    audioAutoplay: Boolean = true,
    /** Nome suggerito per "Partecipa": quello del profilo Google se loggato, altrimenti
     * l'ultimo nome salvato localmente (ospite senza login, come i cookie di Spaccasassi). */
    suggestedName: String = "",
    /** Carica la clip audio dell'evento (documento Firestore separato, vedi
     * CalendarViewModel.loadEventAudio) — chiamato solo se [RideEvent.hasAudio] è true. */
    loadAudio: suspend () -> simplylucaapps.girocalendar.data.remote.firestore.EventAudio? = { null },
    /** Non null se l'utente è loggato e ha almeno un altro calendario dove è editor/owner:
     * mostra l'icona "Clona in un mio calendario" nella barra in alto. */
    onClone: (() -> Unit)? = null,
    /** Mappa del percorso completa (layer, occhi, posizione); se null resta la mappa semplice. */
    routeMap: (@Composable (simplylucaapps.girocalendar.util.GpxTrack, String, () -> Unit) -> Unit)? = null,
    /** Non null (solo per eventi normali/archiviati, mai per una scheda Epici stessa) per
     * mostrare l'icona "Aggiungi a Epici" nella barra in alto. */
    onAddToEpics: (() -> Unit)? = null,
    /** Non null solo per una scheda Epici (isEpic=true): mostra l'icona "Pianifica" per
     * aprire l'editor di un NUOVO evento precompilato con questi dati, data/ora da riempire. */
    onPlan: (() -> Unit)? = null,
    onEdit: () -> Unit,
    /** Solo per un evento collegato da un altro calendario e se si è editor: toglie il collegamento (l'evento originale non si tocca). */
    onUnlink: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onParticipate: (String, String) -> Unit = { _, _ -> },
    onRemoveParticipant: (String) -> Unit = {},
    /** true per una scheda della tab "Epici": nasconde data/ora/partecipanti/aggiungi-a-calendario,
     * mostra il proponente come "Autore" e le note come "Descrizione". */
    isEpic: Boolean = false,
    /** Commenti sulla scheda: visibili e scrivibili solo da utenti loggati ([canComment]). */
    canComment: Boolean = false,
    comments: List<simplylucaapps.girocalendar.data.remote.firestore.EventComment> = emptyList(),
    myUid: String? = null,
    /** Owner/collaboratori del calendario: possono eliminare qualsiasi commento. */
    canModerate: Boolean = false,
    onAddComment: (String) -> Unit = {},
    onEditComment: (String, String) -> Unit = { _, _ -> },
    onDeleteComment: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPhoto by remember { mutableStateOf(false) }
    var showShareChoice by remember { mutableStateOf(false) }
    // L'audio non è più nel documento evento (limite 1 MiB condiviso con foto/GPX): caricato a
    // parte solo se hasAudio è true, per non fare una lettura extra su ogni evento senza audio.
    var loadedAudio by remember(event.id) { mutableStateOf<simplylucaapps.girocalendar.data.remote.firestore.EventAudio?>(null) }
    LaunchedEffect(event.id, event.hasAudio) {
        loadedAudio = if (event.hasAudio) loadAudio() else null
    }
    val effectiveAudioData = loadedAudio?.audioData ?: event.audioData
    val effectiveAudioMimeType = loadedAudio?.audioMimeType ?: event.audioMimeType
    // null = dialog chiuso; "yes" / "maybe" = risposta che il dialog sta raccogliendo.
    var showParticipate by remember { mutableStateOf<String?>(null) }
    var showWeather by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    // Parcheggio: app di navigazione scelta nelle Preferenze ("ask" = chiede ogni volta con questa finestra).
    var showNavChoice by remember { mutableStateOf(false) }
    var rememberNav by remember { mutableStateOf(false) }
    fun launchParking(app: String) {
        scope.launch {
            val dest = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { simplylucaapps.girocalendar.util.NavLauncher.resolve(event.ritrovo) }
            val ok = simplylucaapps.girocalendar.util.NavLauncher.open(context, app, dest)
            if (!ok) android.widget.Toast.makeText(context, context.getString(R.string.d_coords_missing), android.widget.Toast.LENGTH_LONG).show()
        }
    }
    fun openParking() {
        val pref = simplylucaapps.girocalendar.data.settings.BootPrefs.navApp(context)
        if (pref == simplylucaapps.girocalendar.util.NavLauncher.ASK) showNavChoice = true else launchParking(pref)
    }
    if (showNavChoice) {
        AlertDialog(
            onDismissRequest = { showNavChoice = false },
            title = { Text(stringResource(R.string.d_open_parking)) },
            text = {
                Column {
                    listOf(
                        simplylucaapps.girocalendar.util.NavLauncher.MAPS to "Google Maps",
                        simplylucaapps.girocalendar.util.NavLauncher.WAZE to "Waze",
                        simplylucaapps.girocalendar.util.NavLauncher.OTHER to stringResource(R.string.d_other_app),
                    ).forEach { (id, label) ->
                        TextButton(
                            onClick = {
                                showNavChoice = false
                                if (rememberNav) simplylucaapps.girocalendar.data.settings.BootPrefs.setNavApp(context, id)
                                launchParking(id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(label, modifier = Modifier.fillMaxWidth()) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Checkbox(checked = rememberNav, onCheckedChange = { rememberNav = it })
                        Text(stringResource(R.string.d_remember_nav), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showNavChoice = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    var showGpxChoice by remember { mutableStateOf(false) }
    val saveGpxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri -> uri?.let { simplylucaapps.girocalendar.util.EventExport.saveGpxTo(context, event, it) } }

    // Parsing GPX una sola volta per evento aperto (decompressione + calcolo statistiche):
    // null se non c'è traccia o se il file non è un GPX valido.
    val gpxTrack = remember(event.id) {
        event.gpxData.takeIf { it.isNotBlank() }
            ?.let { simplylucaapps.girocalendar.util.GpxProcessor.decompress(it) }
            ?.let { simplylucaapps.girocalendar.util.GpxTrackParser.parse(it) }
    }

    // Meteo caricato solo su richiesta (tap sull'icona accanto a Località), non a ogni
    // apertura della scheda: evita una chiamata di rete automatica per ogni evento visto.
    // Lista: l'ora dell'uscita più le due prima e le due dopo.
    var weather by remember(event.id) { mutableStateOf<List<simplylucaapps.girocalendar.data.remote.weather.WeatherForecast>>(emptyList()) }
    var weatherLoading by remember(event.id) { mutableStateOf(false) }
    var weatherFailed by remember(event.id) { mutableStateOf(false) }
    fun loadWeather() {
        if (weather.isNotEmpty() || weatherLoading) return
        showWeather = true
        weatherLoading = true
        weatherFailed = false
        scope.launch {
            val coords = simplylucaapps.girocalendar.data.remote.weather.WeatherRepository.geocode(event.localita)
            weather = coords?.let {
                simplylucaapps.girocalendar.data.remote.weather.WeatherRepository
                    .forecastFor(it.first, it.second, event.data, event.ora)
            } ?: emptyList()
            weatherFailed = weather.isEmpty()
            weatherLoading = false
        }
    }

    // photoData = foto intera, non ritagliata; fallback a cardBgData (versione 16:9) per gli
    // eventi importati prima che esistesse questo secondo campo.
    val fullImageData = event.photoData.ifBlank { event.cardBgData }
    val bmp = remember(fullImageData) {
        if (fullImageData.isBlank()) null else runCatching {
            val bytes = android.util.Base64.decode(fullImageData, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    Scaffold(
        topBar = {
            // Il titolo compare solo una volta, grande, nel corpo sotto la foto — qui la barra
            // resta senza titolo per non ripeterlo.
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_close)) }
                },
                actions = {
                    if (onClone != null) {
                        IconButton(onClick = onClone) {
                            Icon(Icons.Default.ContentCopy, stringResource(R.string.action_clone_event))
                        }
                    }
                    if (onAddToEpics != null) {
                        IconButton(onClick = onAddToEpics) {
                            Icon(Icons.Default.Star, stringResource(R.string.action_add_to_epics))
                        }
                    }
                    if (onPlan != null) {
                        IconButton(onClick = onPlan) {
                            Icon(Icons.Default.EventAvailable, stringResource(R.string.action_plan_epic))
                        }
                    }
                    if (canEdit) {
                        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.action_edit)) }
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        ) {
            if (bmp != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Image(
                        bmp, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clickable { showPhoto = true },
                    )
                    if (effectiveAudioData.isNotBlank()) {
                        // Autoplay qui (apertura scheda), non nel viewer a tutto schermo: se
                        // l'utente apre poi anche la foto, il viewer ha autoplay=false per non
                        // far ripartire una seconda riproduzione sovrapposta a questa.
                        AudioPlayButton(
                            audioData = effectiveAudioData,
                            audioMimeType = effectiveAudioMimeType,
                            autoplay = audioAutoplay,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            } else {
                Spacer(Modifier.height(16.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    event.titolo.ifBlank { stringResource(R.string.default_event_title) },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (event.difficolta.isNotBlank()) {
                    DifficultyBadge(event.difficolta)
                }
            }
            if (event.fromCode.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val linkedInfo = rememberLinkedCalInfo(event.fromCode)
                        simplylucaapps.girocalendar.ui.common.CalendarLogo(event.fromCode, linkedInfo?.logoColor.orEmpty(), linkedInfo?.logoData.orEmpty(), size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.lk_from, event.fromName.ifBlank { event.fromCode }), Modifier.weight(1f),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        if (onUnlink != null) TextButton(onClick = onUnlink) { Text(stringResource(R.string.lk_unlink)) }
                    }
                }
            }
            if (!isEpic) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (showFieldIcons) {
                        Icon(Icons.Default.Schedule, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        "${event.data}${simplylucaapps.girocalendar.util.EventTime.weekdayLabel(event.data)?.let { " $it" } ?: ""}${if (event.ora.isNotBlank()) "  ·  ${event.ora}" else ""}${if (event.durataGiorni > 1) "  ·  ${stringResource(R.string.d_duration_days, event.durataGiorni)}" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { addToDeviceCalendar(context, event) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Default.EventAvailable, stringResource(R.string.action_add_to_calendar), modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }
            // Ordine: Luogo, Parcheggio, Mappa, Percorso, Altro link, Video, Note, Proponente
            // (spostato in fondo — le info logistiche dell'uscita vengono prima di chi la propone).
            if (event.localita.isNotBlank()) {
                DetailRow(
                    Icons.Default.Place, event.localita,
                    showIcon = showFieldIcons,
                    // Meteo non disponibile per una scheda Epici: non ha una data propria per
                    // cui chiedere una previsione (vedi anche isEpic, non solo event.data).
                    trailing = if (!isEpic && event.data.isNotBlank()) {
                        {
                            IconButton(onClick = { loadWeather() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.WbSunny, stringResource(R.string.weather_title), modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else null,
                )
            }
            if (event.ritrovo.isNotBlank()) {
                val isRitrovoLink = event.ritrovo.startsWith("http")
                DetailRow(
                    Icons.Default.LocalParking,
                    if (isRitrovoLink) stringResource(R.string.field_parking) else event.ritrovo,
                    showIcon = showFieldIcons,
                    onClick = if (isRitrovoLink) { { openParking() } } else null,
                    // Icona "indicazioni stradali" a destra, stesso trattamento del meteo su
                    // Località: azione secondaria ben visibile invece di solo il testo cliccabile.
                    trailing = if (isRitrovoLink) {
                        {
                            IconButton(
                                onClick = { openParking() },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Default.Directions, stringResource(R.string.field_parking), modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else null,
                )
            }
            if (gpxTrack != null) {
                DetailRow(
                    Icons.Default.Map, stringResource(R.string.field_map),
                    showIcon = showFieldIcons,
                    onClick = { showMap = true },
                    // Icona mappa più grande a destra: bersaglio di tocco più ampio e più
                    // facile da colpire rispetto al solo testo della riga.
                    trailing = {
                        IconButton(onClick = { showMap = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Map, stringResource(R.string.field_map), modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
                DetailRow(
                    Icons.Default.Download, stringResource(R.string.field_gpx_download),
                    showIcon = showFieldIcons,
                    onClick = { showGpxChoice = true },
                    trailing = {
                        IconButton(onClick = { showGpxChoice = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Download, stringResource(R.string.field_gpx_download), modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
            }
            if (event.linkPercorso.isNotBlank()) {
                val routeLabel = simplylucaapps.girocalendar.ui.common.platformNameForLink(event.linkPercorso)
                    ?.let { stringResource(R.string.editor_field_route_link_named, it) }
                    ?: stringResource(R.string.field_route)
                DetailRow(
                    iconForLink(event.linkPercorso), routeLabel,
                    showIcon = showFieldIcons,
                    onClick = { simplylucaapps.girocalendar.util.openWebLink(context, event.linkPercorso) },
                )
            }
            if (event.link2.isNotBlank()) {
                DetailRow(
                    iconForLink(event.link2),
                    simplylucaapps.girocalendar.ui.common.platformNameForLink(event.link2)
                        ?: stringResource(R.string.field_other_link),
                    showIcon = showFieldIcons,
                    onClick = { simplylucaapps.girocalendar.util.openWebLink(context, event.link2) },
                )
            }
            if (event.video.isNotBlank()) {
                val videoLabel = simplylucaapps.girocalendar.ui.common.platformNameForLink(event.video)
                    ?.let { stringResource(R.string.editor_field_video_named, it) }
                    ?: stringResource(R.string.field_video)
                DetailRow(
                    Icons.Default.Videocam, videoLabel,
                    showIcon = showFieldIcons,
                    onClick = { simplylucaapps.girocalendar.util.openWebLink(context, event.video) },
                )
            }
            if (event.trailZone.isNotBlank()) TrailZoneStatus(event.trailZone)
            if (event.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                if (isEpic) {
                    Text(
                        stringResource(R.string.editor_field_description),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Row {
                    if (showFieldIcons && !isEpic) {
                        Icon(Icons.AutoMirrored.Filled.Notes, null, modifier = Modifier.size(20.dp).padding(top = 2.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                    }
                    RichText(event.note, modifier = Modifier.weight(1f))
                }
            }
            if (event.proponente.isNotBlank()) {
                DetailRow(
                    Icons.Default.Person,
                    if (isEpic) stringResource(R.string.editor_field_author) + ": " + event.proponente
                    else stringResource(R.string.proposal_by, event.proponente),
                    showIcon = showFieldIcons,
                    onClick = if (!isEpic && event.waProponente.isNotBlank()) {
                        {
                            val phone = event.waProponente.filter { it.isDigit() || it == '+' }
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone"))) }
                        }
                    } else null,
                )
            }

            if (!isEpic) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                // partecipanti e «forse» (nomi a chip) con i pulsanti in fondo al riquadro, prima dei commenti
                ParticipantsPanel(
                    event = event, suggestedName = suggestedName,
                    onParticipate = onParticipate, onRemove = onRemoveParticipant,
                    askName = { showParticipate = it },
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                EventCommentsSection(canComment, comments, myUid, canModerate, onAddComment, onEditComment, onDeleteComment)
            }

            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = { showShareChoice = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_share))
            }
        }
    }

    if (showShareChoice) {
        AlertDialog(
            onDismissRequest = { showShareChoice = false },
            title = { Text(stringResource(R.string.d_share_as)) },
            text = {
                Column {
                    TextButton(onClick = { showShareChoice = false; shareEventText(context, event, calendarCode) },
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.d_text)) }
                    TextButton(onClick = { showShareChoice = false; shareEventLink(context, event, calendarCode) },
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.d_web_link)) }
                }
            },
            confirmButton = { TextButton(onClick = { showShareChoice = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (showPhoto && bmp != null) {
        PhotoViewerDialog(
            bitmap = bmp,
            imageData = fullImageData,
            audioData = effectiveAudioData,
            audioMimeType = effectiveAudioMimeType,
            // false fisso (non audioAutoplay): l'autoplay è già partito aprendo la scheda
            // (vedi sopra), niente doppia riproduzione se l'utente apre anche la foto.
            audioAutoplay = false,
            onDismiss = { showPhoto = false },
        )
    }

    showParticipate?.let { status ->
        ParticipateDialog(
            suggestedName = suggestedName,
            maybe = status == "maybe",
            onDismiss = { showParticipate = null },
            onConfirm = { name -> showParticipate = null; onParticipate(name, status) },
        )
    }

    if (showWeather) {
        WeatherDialog(
            localita = event.localita,
            data = event.data,
            ora = event.ora,
            weather = weather,
            loading = weatherLoading,
            failed = weatherFailed,
            onDismiss = { showWeather = false },
        )
    }

    if (showMap && gpxTrack != null) {
        if (routeMap != null) routeMap(gpxTrack, event.titolo) { showMap = false }
        else GpxMapDialog(track = gpxTrack, titolo = event.titolo, onDismiss = { showMap = false })
    }

    if (showGpxChoice) {
        AlertDialog(
            onDismissRequest = { showGpxChoice = false },
            title = { Text(stringResource(R.string.field_gpx_download)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showGpxChoice = false
                            saveGpxLauncher.launch(EventExport.gpxFileNameOf(event))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.gpx_action_save), modifier = Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = { showGpxChoice = false; EventExport.shareGpx(context, event) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.gpx_action_share), modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showGpxChoice = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Apre l'app Calendario di sistema con un evento precompilato (via Intent, nessun permesso richiesto). */
private fun addToDeviceCalendar(context: android.content.Context, event: RideEvent) {
    val startMillis = simplylucaapps.girocalendar.util.EventTime.startMillis(event)
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = android.provider.CalendarContract.Events.CONTENT_URI
        putExtra(android.provider.CalendarContract.Events.TITLE, event.titolo.ifBlank { context.getString(R.string.d_default_title) })
        val loc = listOfNotNull(event.localita.takeIf { it.isNotBlank() }, event.ritrovo.takeIf { it.isNotBlank() })
            .joinToString(" — ")
        if (loc.isNotBlank()) putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, loc)
        val desc = listOfNotNull(
            event.proponente.takeIf { it.isNotBlank() }?.let { context.getString(R.string.d_proposed_by_cal, it) },
            event.note.takeIf { it.isNotBlank() },
        ).joinToString("\n\n")
        if (desc.isNotBlank()) putExtra(android.provider.CalendarContract.Events.DESCRIPTION, desc)
        if (startMillis != null) {
            putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 3 * 60 * 60 * 1000)
        }
    }
    runCatching { context.startActivity(intent) }
}

/** Condivide i dettagli dell'uscita come testo semplice (share sheet di sistema), con in
 * fondo il link diretto alla stessa scheda sul sito web (utile per chi non ha l'app, es. iOS). */
private fun shareEventText(context: android.content.Context, event: RideEvent, calendarCode: String, withLink: Boolean = true) {
    val text = buildString {
        append(event.titolo.ifBlank { context.getString(R.string.d_default_title) })
        append("\n")
        if (event.data.isNotBlank()) {
            append(event.data)
            if (event.ora.isNotBlank()) append("  ·  ${event.ora}")
            if (event.durataGiorni > 1) append("  ·  ${context.getString(R.string.d_duration_days, event.durataGiorni)}")
            append("\n")
        }
        if (event.localita.isNotBlank()) append("📍 ${event.localita}\n")
        if (event.ritrovo.isNotBlank()) append(context.getString(R.string.d_meeting_line, event.ritrovo) + "\n")
        if (event.proponente.isNotBlank()) append(context.getString(R.string.d_proposed_line, event.proponente) + "\n")
        if (event.linkPercorso.isNotBlank()) append("🔗 ${event.linkPercorso}\n")
        if (event.link2.isNotBlank()) append("🔗 ${event.link2}\n")
        if (event.note.isNotBlank()) append("\n${event.note}\n")
        if (withLink) append("\n${eventWebUrl(calendarCode, event)}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text.trim())
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.d_share_ride)))
}

/** Riquadro «Chi partecipa»: azioni (Partecipa / Forse) e nomi a chip, con i «forse» in una riga a parte più tenue. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParticipantsPanel(
    event: simplylucaapps.girocalendar.data.remote.firestore.RideEvent,
    suggestedName: String,
    onParticipate: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    askName: (String) -> Unit,
) {
    val alreadyIn = suggestedName.isNotBlank() && event.partecipanti.contains(suggestedName)
    val isMaybe = suggestedName.isNotBlank() && event.forse.contains(suggestedName)
    fun answer(status: String) { if (suggestedName.isNotBlank()) onParticipate(suggestedName, status) else askName(status) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (event.partecipanti.isEmpty() && event.forse.isEmpty()) {
                Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.ev_nobody), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (event.partecipanti.isNotEmpty()) {
                Text((stringResource(R.string.participants_label) + " (${event.partecipanti.size})").uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    event.partecipanti.forEach { NameRow(it, MaterialTheme.colorScheme.onSurface, event.partecipanti.size > 1) }
                }
            }
            if (event.forse.isNotEmpty()) {
                Text((stringResource(R.string.action_maybe) + " (${event.forse.size})").uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    event.forse.forEach { NameRow(it, MaterialTheme.colorScheme.onSurfaceVariant, event.forse.size > 1) }
                }
            }
            // azioni: due pulsanti uguali (stesso stile), su una riga
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val yesLabel = stringResource(if (alreadyIn) R.string.action_leave else R.string.action_participate).uppercase()
                val maybeLabel = stringResource(if (isMaybe) R.string.action_not_maybe else R.string.action_maybe).uppercase()
                FilledTonalButton(
                    onClick = { if (alreadyIn) onRemove(suggestedName) else answer("yes") },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    if (!alreadyIn) { Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)) }
                    Text(yesLabel, maxLines = 2, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                FilledTonalButton(
                    onClick = { if (isMaybe) onRemove(suggestedName) else answer("maybe") },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) { Text(maybeLabel, maxLines = 2, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }
    }
}

/** Un nome per riga, senza decorazioni. */
@Composable
private fun NameRow(name: String, color: androidx.compose.ui.graphics.Color, bullet: Boolean) {
    Text((if (bullet) "•  " else "") + name, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
}

/** Dialog "Partecipa": nome precompilato (profilo Google o ultimo nome usato), modificabile. */
@Composable
private fun ParticipateDialog(
    suggestedName: String,
    maybe: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(suggestedName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (maybe) R.string.action_maybe else R.string.action_participate)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.participate_prompt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                CompactTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.your_name)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Salva l'immagine originale (base64) in Immagini/GiroCalendar tramite MediaStore. */
private fun saveEventImage(ctx: android.content.Context, imageData: String): android.net.Uri? {
    return runCatching {
        val bytes = android.util.Base64.decode(imageData, android.util.Base64.DEFAULT)
        val mime = when {
            bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png"
            bytes.size > 12 && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() -> "image/webp"
            else -> "image/jpeg"
        }
        val ext = mime.substringAfter('/').replace("jpeg", "jpg")
        val cv = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "GiroCalendar_${System.currentTimeMillis()}.$ext")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, mime)
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/GiroCalendar")
        }
        val uri = ctx.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)!!
        ctx.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        uri
    }.getOrNull()
}

/**
 * Foto a schermo intero su sfondo nero, con pinch-to-zoom/pan e chiusura sia dalla X sia con
 * un tap sull'immagine — quest'ultimo solo quando non si è zoomati (a scala 1x), per non
 * chiudere accidentalmente durante un pan con lo zoom attivo.
 */
@Composable
private fun PhotoViewerDialog(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    imageData: String,
    audioData: String,
    audioMimeType: String,
    audioAutoplay: Boolean = false,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offsetX += panChange.x
        offsetY += panChange.y
    }

    val ctx = LocalContext.current
    var savedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).transformable(transformState)
                .then(
                    // Tap singolo chiude solo a scala 1x; doppio tap alterna 1x ↔ 2.5x.
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { if (scale <= 1f) onDismiss() },
                            onDoubleTap = {
                                if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f } else scale = 2.5f
                            },
                        )
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap, contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale, scaleY = scale,
                    translationX = offsetX, translationY = offsetY,
                ),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
            ) {
                Icon(Icons.Default.Close, stringResource(R.string.action_close), tint = Color.White)
            }
            IconButton(
                onClick = {
                    val u = saveEventImage(ctx, imageData)
                    if (u != null) savedUri = u
                    else android.widget.Toast.makeText(ctx, ctx.getString(R.string.d_image_save_failed), android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
            ) {
                Icon(Icons.Default.Download, stringResource(R.string.d_save_image), tint = Color.White)
            }
            savedUri?.let { u ->
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.d_image_saved), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(u, "image/*")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            }
                        }) { Text(stringResource(R.string.d_open_image)) }
                    }
                }
            }
            if (audioData.isNotBlank()) {
                AudioPlayButton(
                    audioData = audioData,
                    audioMimeType = audioMimeType,
                    autoplay = audioAutoplay,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                    size = 72.dp,
                    iconSize = 40.dp,
                )
            }
        }
    }
}

/**
 * Bottone play/stop per l'audio dell'evento, in un cerchio semi-trasparente da mettere in
 * sovraimpressione su un'immagine. Riutilizzato sia dalla miniatura nella scheda evento
 * (piccolo, in basso a destra) sia dal viewer a tutto schermo (grande, in basso al centro).
 * Niente autoplay: su alcuni browser/versioni web il file può essere WebM/Opus e un avvio
 * automatico silenzioso rendeva impossibile capire se stesse suonando. MediaPlayer richiede
 * un file/URI, non un byte array diretto: il base64 viene scritto una volta in un file
 * temporaneo nella cache al primo play.
 */
@Composable
private fun AudioPlayButton(
    audioData: String,
    audioMimeType: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
    /** Avvia la riproduzione una sola volta alla prima composizione del bottone (Preferenze
     * > Autoplay). Non ha effetto sui riavvii successivi (es. ricomposizione del genitore). */
    autoplay: Boolean = false,
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    var focusRequest by remember { mutableStateOf<android.media.AudioFocusRequest?>(null) }
    fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }
    fun stopAudio() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        isPlaying = false
        abandonFocus()
    }
    fun toggleAudio() {
        if (isPlaying) { stopAudio(); return }
        runCatching {
            // AudioAttributes espliciti (USAGE_MEDIA + richiesta di audio focus): senza,
            // MediaPlayer usa attributi generici che su alcuni head-unit auto (Bluetooth A2DP,
            // Android Auto) non vengono instradati correttamente se un'altra sorgente audio
            // (navigatore, musica) ha già l'output — il suono non si sente pur "riproducendo".
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .build()
            audioManager.requestAudioFocus(request)
            focusRequest = request

            val bytes = android.util.Base64.decode(audioData, android.util.Base64.DEFAULT)
            val ext = when {
                audioMimeType.contains("webm") -> "webm"
                audioMimeType.contains("mp4") || audioMimeType.contains("m4a") -> "m4a"
                audioMimeType.contains("ogg") -> "ogg"
                audioMimeType.contains("wav") -> "wav"
                else -> "mp3"
            }
            val tempFile = java.io.File.createTempFile("event_audio", ".$ext", context.cacheDir)
            tempFile.deleteOnExit()
            tempFile.writeBytes(bytes)
            android.media.MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener { stopAudio() }
                prepare()
                start()
            }
        }.onSuccess { player = it; isPlaying = true }
            .onFailure { abandonFocus() }
    }
    LaunchedEffect(Unit) {
        if (autoplay) toggleAudio()
    }
    DisposableEffect(Unit) {
        onDispose { stopAudio() }
    }
    IconButton(
        onClick = { toggleAudio() },
        modifier = modifier.size(size).background(Color.Black.copy(alpha = 0.5f), CircleShape),
    ) {
        Icon(
            if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
            stringResource(if (isPlaying) R.string.audio_stop else R.string.audio_play),
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Popup con la previsione meteo, aperto dall'icona accanto a Località: caricato solo su richiesta. */
@Composable
private fun WeatherDialog(
    localita: String,
    data: String,
    ora: String,
    weather: List<simplylucaapps.girocalendar.data.remote.weather.WeatherForecast>,
    loading: Boolean,
    failed: Boolean,
    onDismiss: () -> Unit,
) {
    val targetHour = ora.split(":").firstOrNull()?.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weather_dialog_title, localita)) },
        text = {
            when {
                loading -> Text(stringResource(R.string.weather_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                failed -> Text(
                    stringResource(R.string.weather_unavailable),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                weather.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (data.isNotBlank() || ora.isNotBlank()) {
                        Text(
                            listOf(data, ora).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    weather.forEach { w ->
                        val isTarget = w.hour == targetHour
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "%02d:00".format(w.hour),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isTarget) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                color = if (isTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(52.dp),
                            )
                            Text(
                                "${w.emoji} ${w.temperatureC.toInt()}°C  💧${w.precipitationProbability}%  💨${w.windSpeedKmh.toInt()}km/h",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isTarget) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                            )
                        }
                    }
                }
                else -> {}
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    showIcon: Boolean = true,
    onClick: (() -> Unit)? = null,
    /** Azione secondaria a destra della riga (es. l'icona meteo accanto a Località). */
    trailing: (@Composable () -> Unit)? = null,
) {
    val rowMod = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Row(rowMod, verticalAlignment = Alignment.CenterVertically) {
        if (showIcon) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

private fun eventWebUrl(calendarCode: String, event: RideEvent) =
    "https://girocalendar.pages.dev/#/c/$calendarCode/${event.id}"

private fun shareEventLink(context: android.content.Context, event: RideEvent, calendarCode: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, eventWebUrl(calendarCode, event))
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.d_share_ride)))
}


/** Commenti sulla scheda di un'uscita (prossima o passata): solo utenti loggati leggono e scrivono. */
@Composable
private fun EventCommentsSection(
    canComment: Boolean,
    comments: List<simplylucaapps.girocalendar.data.remote.firestore.EventComment>,
    myUid: String?,
    canModerate: Boolean,
    onAdd: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Text(
        stringResource(R.string.ev_comments_title) + if (comments.isNotEmpty() && canComment) " (${comments.size})" else "",
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.height(6.dp))
    if (!canComment) {
        Text(stringResource(R.string.ev_comments_signin), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    var text by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<simplylucaapps.girocalendar.data.remote.firestore.EventComment?>(null) }
    var editText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<simplylucaapps.girocalendar.data.remote.firestore.EventComment?>(null) }
    val df = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT) }
    if (comments.isEmpty()) {
        Text(stringResource(R.string.ev_comments_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            comments.forEach { c ->
                val mine = myUid != null && c.authorUid == myUid
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            c.authorName.ifBlank { "?" } + "  " + df.format(java.util.Date(c.createdAtMillis)) +
                                if (c.editedAtMillis > 0) "  · " + stringResource(R.string.ev_comment_edited) else "",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (mine) IconButton(onClick = { editing = c; editText = c.text }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, stringResource(R.string.action_edit), modifier = Modifier.size(16.dp))
                        }
                        if (mine || canModerate) IconButton(onClick = { deleting = c }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, stringResource(R.string.action_delete), modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(c.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    CompactTextField(
        value = text, onValueChange = { text = it.take(1000) },
        placeholder = { Text(stringResource(R.string.ev_comment_placeholder)) },
        modifier = Modifier.fillMaxWidth(), minLines = 2,
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(onClick = { onAdd(text); text = "" }, enabled = text.isNotBlank()) { Text(stringResource(R.string.ev_comment_send)) }
    }

    editing?.let { c ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.ev_comment_edit)) },
            text = { CompactTextField(value = editText, onValueChange = { editText = it.take(1000) }, modifier = Modifier.fillMaxWidth(), minLines = 3) },
            confirmButton = {
                TextButton(enabled = editText.isNotBlank(), onClick = { onEdit(c.id, editText); editing = null }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.ev_comment_delete_q)) },
            confirmButton = { TextButton(onClick = { onDelete(c.id); deleting = null }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
