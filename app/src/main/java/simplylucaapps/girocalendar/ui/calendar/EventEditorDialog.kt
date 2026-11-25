package simplylucaapps.girocalendar.ui.calendar

import simplylucaapps.girocalendar.ui.common.CompactTextField
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
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
import simplylucaapps.girocalendar.data.remote.firestore.Difficulty
import simplylucaapps.girocalendar.data.remote.firestore.RideEvent
import simplylucaapps.girocalendar.ui.common.DifficultyBadge
import simplylucaapps.girocalendar.util.CardBackgroundProcessor
import simplylucaapps.girocalendar.util.GpxProcessor
import simplylucaapps.girocalendar.util.GpxResult

/**
 * Editor a schermo intero per creare/modificare una [RideEvent]. Pattern analogo a
 * MatchEditScreen di VolleyCalendar, ma con i campi del dominio "uscita in bici". Campi
 * allineati alla PWA Spaccasassi Eventi (titolo/data/ora/localita/ritrovo/proponente/
 * waProponente/difficolta/linkPercorso/link2/video/note/cardBgData); non riportati: gpx
 * (upload file, diverso da un'immagine) e partecipanti_wa (whatsapp per singolo
 * partecipante) — valutabili in futuro se richiesti.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    initial: RideEvent?,
    onClose: () -> Unit,
    /** Secondo parametro: true se l'audio è stato caricato o rimosso in questa sessione
     * dell'editor (vedi CalendarViewModel.saveEvent — l'audio è un documento separato). */
    onSave: (RideEvent, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    /** Come [onDelete] ma sposta l'evento in archivio invece di eliminarlo definitivamente.
     * Null = non mostrare l'azione (coerente con onDelete: solo in modifica di un evento
     * esistente, mai su uno nuovo o su "Rivedi proposta"/"Ripesca"). */
    onArchive: (() -> Unit)? = null,
    /** Nome suggerito per il campo Proponente su un evento nuovo (profilo Google se loggato).
     * Ignorato in modifica: [initial] ha già il suo proponente. */
    suggestedName: String = "",
    /** Rimuove un partecipante dalla lista (solo admin: es. iscrizione per errore, ospiti che
     * non possono togliersi da soli). Null = non mostrare la sezione partecipanti (evento
     * nuovo, "Rivedi proposta", "Ripesca"). */
    onRemoveParticipant: ((String) -> Unit)? = null,
    /** true per una scheda della tab "Epici": nasconde data/ora/partecipanti, mostra
     * Proponente come "Autore" e Note come "Descrizione" (stessi campi del modello,
     * etichettati diversamente perché qui non è un'uscita programmata). */
    isEpic: Boolean = false,
) {
    var titolo by remember { mutableStateOf(initial?.titolo ?: "") }
    var data by remember { mutableStateOf(initial?.data ?: "") }
    var ora by remember { mutableStateOf(initial?.ora ?: "") }
    var durataGiorni by remember { mutableStateOf((initial?.durataGiorni ?: 1).toString()) }
    var localita by remember { mutableStateOf(initial?.localita ?: "") }
    var ritrovo by remember { mutableStateOf(initial?.ritrovo ?: "") }
    var proponente by remember { mutableStateOf(initial?.proponente ?: suggestedName) }
    var waProponente by remember { mutableStateOf(initial?.waProponente ?: "") }
    var difficolta by remember { mutableStateOf(initial?.difficolta ?: "") }
    var annullata by remember { mutableStateOf(initial?.annullata ?: false) }
    var trailZone by remember { mutableStateOf(initial?.trailZone ?: "") }
    var trailZoneOn by remember { mutableStateOf(!initial?.trailZone.isNullOrBlank()) }
    var linkPercorso by remember { mutableStateOf(initial?.linkPercorso ?: "") }
    var link2 by remember { mutableStateOf(initial?.link2 ?: "") }
    var video by remember { mutableStateOf(initial?.video ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var cardBgData by remember { mutableStateOf(initial?.cardBgData ?: "") }
    var photoData by remember { mutableStateOf(initial?.photoData ?: "") }
    var gpxData by remember { mutableStateOf(initial?.gpxData ?: "") }
    var gpxFileName by remember { mutableStateOf(initial?.gpxFileName ?: "") }
    var audioData by remember { mutableStateOf(initial?.audioData ?: "") }
    var audioMimeType by remember { mutableStateOf(initial?.audioMimeType ?: "") }
    // true solo se l'utente ha toccato l'audio in QUESTA sessione dell'editor (nuovo file
    // caricato o rimozione esplicita): l'audio vive in un documento Firestore separato da
    // questo evento (vedi CalendarViewModel.saveEvent), quindi senza questo flag non si
    // potrebbe distinguere "non toccato" da "rimosso" quando entrambi lasciano audioData vuoto.
    var audioChanged by remember { mutableStateOf(false) }
    // Copia locale mutabile: la rimozione qui è ottimistica, non aspetta il round-trip
    // Firestore per sparire dai chip (la scrittura vera parte comunque via onRemoveParticipant).
    var partecipanti by remember(initial?.id) { mutableStateOf(initial?.partecipanti ?: emptyList()) }
    // Modifica/aggiunta di un partecipante: null = dialog chiuso; ("", true) = nuovo; (nome, false) = rinomina.
    var participantEdit by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var showDifficultyMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    participantEdit?.let { (oldName, isNew) ->
        var text by remember(oldName, isNew) { mutableStateOf(oldName) }
        val clean = text.trim()
        AlertDialog(
            onDismissRequest = { participantEdit = null },
            title = { Text(stringResource(if (isNew) R.string.editor_participant_new else R.string.editor_participant_edit)) },
            text = { CompactTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(enabled = clean.isNotEmpty(), onClick = {
                    partecipanti = if (isNew) (if (clean in partecipanti) partecipanti else partecipanti + clean)
                    else partecipanti.map { if (it == oldName) clean else it }.distinct()
                    participantEdit = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { participantEdit = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    val canSave = titolo.isNotBlank() && (isEpic || data.isNotBlank())

    val doSave = {
        onSave(
            RideEvent(
                id = initial?.id ?: "",
                titolo = titolo, data = data, ora = ora,
                durataGiorni = (durataGiorni.toIntOrNull() ?: 1).coerceAtLeast(1),
                localita = localita, ritrovo = ritrovo,
                proponente = proponente, waProponente = waProponente,
                difficolta = difficolta,
                linkPercorso = linkPercorso, link2 = link2, video = video,
                note = note, cardBgData = cardBgData, photoData = photoData,
                partecipanti = partecipanti,
                gpxData = gpxData, gpxFileName = gpxFileName,
                audioData = audioData, audioMimeType = audioMimeType,
                hasAudio = initial?.hasAudio ?: false,
                annullata = annullata,
                trailZone = if (trailZoneOn) trailZone else "",
            ),
            audioChanged,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) stringResource(R.string.editor_title_new) else stringResource(R.string.editor_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_close)) }
                },
                actions = {
                    IconButton(onClick = doSave, enabled = canSave) {
                        Icon(Icons.Default.Save, stringResource(R.string.action_save))
                    }
                    if (onArchive != null) {
                        IconButton(onClick = onArchive) {
                            Icon(Icons.Default.Archive, stringResource(R.string.action_archive))
                        }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(
                        onClick = doSave,
                        enabled = canSave,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.action_save)) }
                }
            }
        },
    ) { inner ->
        // Bordo non-focus più marcato del grigio tenue di default M3 (outline con alpha
        // basso): con undici campi in fila l'occhio fa più fatica a distinguerli dallo sfondo,
        // soprattutto in chiaro. onSurface a bassa opacità invece di outline dà un contorno
        // visibile anche prima di toccare il campo.
        val fieldColors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CompositionLocalProvider(
            // bodySmall (non bodyMedium) fa calcolare a Material3 un box interno più basso per
            // ogni OutlinedTextField: con undici campi in un form scrollabile, l'altezza extra
            // del default si somma in fretta. Il testo resta comunque ben leggibile.
            LocalTextStyle provides MaterialTheme.typography.bodySmall,
            LocalMinimumInteractiveComponentSize provides 24.dp,
        ) {
        Column(
            Modifier.padding(inner).padding(horizontal = 20.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            FieldWithIcon(null) {
                CompactTextField(
                    colors = fieldColors,
                    value = titolo, onValueChange = { titolo = it },
                    label = { Text(stringResource(R.string.editor_field_title)) }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.editor_field_title_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!isEpic) {
                FieldWithIcon(Icons.Default.Schedule) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        CompactTextField(
                        colors = fieldColors,
                            value = data, onValueChange = { data = it },
                            label = { Text(stringResource(R.string.editor_field_date)) }, placeholder = { Text(stringResource(R.string.editor_field_date_placeholder)) },
                            trailingIcon = {
                                IconButton(onClick = { showDatePicker = true }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.CalendarMonth, stringResource(R.string.editor_field_date), modifier = Modifier.size(18.dp))
                                }
                            },
                            singleLine = true, modifier = Modifier.weight(1f),
                        )
                        CompactTextField(
                        colors = fieldColors,
                            value = ora, onValueChange = { ora = it },
                            label = { Text(stringResource(R.string.editor_field_time)) }, placeholder = { Text(stringResource(R.string.editor_field_time_placeholder)) },
                            trailingIcon = {
                                IconButton(onClick = { showTimePicker = true }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.AccessTime, stringResource(R.string.editor_field_time), modifier = Modifier.size(18.dp))
                                }
                            },
                            singleLine = true, modifier = Modifier.weight(1f),
                        )
                    }
                }
                FieldWithIcon(Icons.Default.CalendarMonth) {
                    CompactTextField(
                        colors = fieldColors,
                        value = durataGiorni, onValueChange = { v -> durataGiorni = v.filter { it.isDigit() }.take(3) },
                        label = { Text(stringResource(R.string.editor_field_duration_days)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            FieldWithIcon(Icons.Default.Place) {
                CompactTextField(
                    colors = fieldColors,
                    value = localita, onValueChange = { localita = it },
                    label = { Text(stringResource(R.string.editor_field_location)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FieldWithIcon(Icons.Default.LocalParking) {
                CompactTextField(
                    colors = fieldColors,
                    value = ritrovo, onValueChange = { ritrovo = it },
                    label = { Text(stringResource(R.string.editor_field_meeting_point)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FieldWithIcon(Icons.Default.Person) {
                if (isEpic) {
                    CompactTextField(
                        colors = fieldColors,
                        value = proponente, onValueChange = { proponente = it },
                        label = { Text(stringResource(R.string.editor_field_author)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        CompactTextField(
                        colors = fieldColors,
                            value = proponente, onValueChange = { proponente = it },
                            label = { Text(stringResource(R.string.editor_field_organizer)) },
                            placeholder = { Text(stringResource(R.string.editor_field_organizer_placeholder)) },
                            singleLine = true, modifier = Modifier.weight(1f),
                        )
                        CompactTextField(
                        colors = fieldColors,
                            value = waProponente, onValueChange = { waProponente = it },
                            label = { Text(stringResource(R.string.editor_field_whatsapp)) }, placeholder = { Text("333...") },
                            singleLine = true, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            FieldWithIcon(null) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.editor_field_difficulty),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box {
                        OutlinedButton(onClick = { showDifficultyMenu = true }) {
                            if (difficolta.isBlank()) {
                                Text(stringResource(R.string.editor_difficulty_none))
                            } else {
                                DifficultyBadge(difficolta)
                            }
                        }
                        DropdownMenu(expanded = showDifficultyMenu, onDismissRequest = { showDifficultyMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_difficulty_none)) },
                                onClick = { difficolta = ""; showDifficultyMenu = false },
                            )
                            Difficulty.LEVELS.forEach { level ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            DifficultyBadge(level.id)
                                            Spacer(Modifier.width(8.dp))
                                            Text(Difficulty.description(level.id))
                                        }
                                    },
                                    onClick = { difficolta = level.id; showDifficultyMenu = false },
                                )
                            }
                        }
                    }
                }
            }

            if (!isEpic && initial != null) {
                Row(
                    Modifier.fillMaxWidth().clickable { annullata = !annullata },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(annullata, { annullata = it })
                    Text(stringResource(R.string.editor_field_cancelled))
                }
            }

            TrailZoneField(trailZoneOn, { trailZoneOn = it }, trailZone, { trailZone = it })

            FieldWithIcon(Icons.Default.Link) {
                val routeLabel = simplylucaapps.girocalendar.ui.common.platformNameForLink(linkPercorso)
                    ?.let { stringResource(R.string.editor_field_route_link_named, it) }
                    ?: stringResource(R.string.editor_field_route_link)
                CompactTextField(
                    colors = fieldColors,
                    value = linkPercorso, onValueChange = { linkPercorso = it },
                    label = { Text(routeLabel) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            EventGpxField(gpxData, gpxFileName) { d, n -> gpxData = d; gpxFileName = n }
            FieldWithIcon(Icons.Default.Link) {
                val link2Label = simplylucaapps.girocalendar.ui.common.platformNameForLink(link2)
                    ?: stringResource(R.string.editor_field_other_link)
                CompactTextField(
                    colors = fieldColors,
                    value = link2, onValueChange = { link2 = it },
                    label = { Text(link2Label) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FieldWithIcon(Icons.Default.Videocam) {
                val videoLabel = simplylucaapps.girocalendar.ui.common.platformNameForLink(video)
                    ?.let { stringResource(R.string.editor_field_video_named, it) }
                    ?: stringResource(R.string.editor_field_video)
                CompactTextField(
                    colors = fieldColors,
                    value = video, onValueChange = { video = it },
                    label = { Text(videoLabel) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FieldWithIcon(Icons.AutoMirrored.Filled.Notes) {
                CompactTextField(
                    colors = fieldColors,
                    value = note, onValueChange = { note = it },
                    label = { Text(stringResource(if (isEpic) R.string.editor_field_description else R.string.editor_field_notes)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                )
            }

            if (!isEpic && onRemoveParticipant != null) {
                FieldWithIcon(Icons.Default.Group) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.editor_participants_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            partecipanti.forEach { name ->
                                InputChip(
                                    selected = false,
                                    onClick = { participantEdit = name to false },
                                    label = { Text(name) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close, stringResource(R.string.editor_participant_remove, name),
                                            modifier = Modifier.size(16.dp)
                                                .clickable {
                                                    partecipanti = partecipanti - name
                                                    onRemoveParticipant(name)
                                                },
                                        )
                                    },
                                )
                            }
                            AssistChip(
                                onClick = { participantEdit = "" to true },
                                label = { Text(stringResource(R.string.editor_participant_add)) },
                                leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
            }

            EventBackgroundField(cardBgData) { c, ph -> cardBgData = c; photoData = ph }
            EventAudioField(audioData) { d, m -> audioData = d; audioMimeType = m; audioChanged = true }
            Spacer(Modifier.height(40.dp))
        }
        }
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.editor_delete_confirm_title)) },
            text = { Text(stringResource(R.string.editor_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (showDatePicker) {
        // Precompila il picker con la data già scritta nel campo (se in formato gg/mm/aaaa
        // valido), altrimenti parte da oggi.
        val initialMillis = parseDateToMillis(data) ?: System.currentTimeMillis()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { data = formatMillisToDate(it) }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }

    if (showTimePicker) {
        val (initH, initM) = parseTimeOrNull(ora) ?: (12 to 0)
        val pickerState = rememberTimePickerState(initialHour = initH, initialMinute = initM, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    ora = "%02d:%02d".format(pickerState.hour, pickerState.minute)
                    showTimePicker = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** "03/10/2026" -> epoch millis UTC (mezzanotte). Null se non parsabile come data gg/mm/aaaa. */
internal fun parseDateToMillis(ddMmYyyy: String): Long? {
    val p = ddMmYyyy.trim().split("/")
    if (p.size != 3) return null
    val d = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    val y = p[2].toIntOrNull() ?: return null
    return runCatching {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(y, m - 1, d)
        cal.timeInMillis
    }.getOrNull()
}

/** epoch millis UTC (come restituito da DatePicker) -> "03/10/2026". */
internal fun formatMillisToDate(millis: Long): String {
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = millis
    return "%02d/%02d/%04d".format(
        cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.YEAR),
    )
}

/** "09:30" -> (9, 30). Null se non parsabile come ora hh:mm. */
internal fun parseTimeOrNull(hhMm: String): Pair<Int, Int>? {
    val p = hhMm.trim().split(":")
    if (p.size != 2) return null
    val h = p[0].toIntOrNull()?.coerceIn(0, 23) ?: return null
    val m = p[1].toIntOrNull()?.coerceIn(0, 59) ?: return null
    return h to m
}

/** Nome del file da un content Uri (SAF/picker), es. "anello_monte_rosa.gpx". Null se non risolvibile. */
internal fun queryFileName(context: android.content.Context, uri: android.net.Uri): String? =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()

/**
 * Riga del form con una piccola icona grigia a sinistra (coerente con le icone sui campi
 * già mostrate in lista/dettaglio) e il/i campo/i a destra. Icona null = solo indentazione,
 * per righe senza un'icona naturale (titolo, difficoltà).
 */
@Composable
internal fun FieldWithIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(26.dp).padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}
