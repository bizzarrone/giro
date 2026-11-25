package simplylucaapps.girocalendar.ui.calendar

import simplylucaapps.girocalendar.ui.common.CompactTextField
import androidx.compose.ui.res.stringResource

import simplylucaapps.girocalendar.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import simplylucaapps.girocalendar.data.remote.firestore.Difficulty
import simplylucaapps.girocalendar.data.remote.firestore.RideProposal
import simplylucaapps.girocalendar.ui.common.DifficultyBadge

/**
 * Form pubblico "Proponi evento": chiunque può inviare una proposta di uscita, anche senza
 * login (come il form del sito PHP di Spaccasassi). La proposta finisce in coda
 * (`calendars/{code}/proposals/`) in attesa che owner/editor la approvino o rifiutino —
 * non crea direttamente un [simplylucaapps.girocalendar.data.remote.firestore.RideEvent].
 * Stesso layout compatto (densità campi, date/time picker) di [EventEditScreen], con
 * gli stessi allegati opzionali (traccia GPX, immagine di sfondo, audio).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposeEventScreen(
    onClose: () -> Unit,
    onSubmit: (RideProposal) -> Unit,
    /** Nome suggerito (profilo Google se loggato, altrimenti l'ultimo usato per "Partecipa"). */
    suggestedName: String = "",
) {
    var titolo by remember { mutableStateOf("") }
    var data by remember { mutableStateOf("") }
    var ora by remember { mutableStateOf("") }
    var localita by remember { mutableStateOf("") }
    var ritrovo by remember { mutableStateOf("") }
    var proponente by remember { mutableStateOf(suggestedName) }
    var waProponente by remember { mutableStateOf("") }
    var difficolta by remember { mutableStateOf("") }
    var linkPercorso by remember { mutableStateOf("") }
    var link2 by remember { mutableStateOf("") }
    var video by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var cardBgData by remember { mutableStateOf("") }
    var photoData by remember { mutableStateOf("") }
    var gpxData by remember { mutableStateOf("") }
    var gpxFileName by remember { mutableStateOf("") }
    var audioData by remember { mutableStateOf("") }
    var audioMimeType by remember { mutableStateOf("") }
    var showDifficultyMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val canSubmit = titolo.isNotBlank() && data.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pe_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_close)) }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(
                        onClick = {
                            onSubmit(
                                RideProposal(
                                    titolo = titolo, data = data, ora = ora,
                                    localita = localita, ritrovo = ritrovo,
                                    proponente = proponente, waProponente = waProponente,
                                    difficolta = difficolta,
                                    linkPercorso = linkPercorso, link2 = link2, video = video,
                                    note = note,
                                    cardBgData = cardBgData, photoData = photoData,
                                    gpxData = gpxData, gpxFileName = gpxFileName,
                                    audioData = audioData, audioMimeType = audioMimeType,
                                )
                            )
                        },
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.pe_send)) }
                }
            }
        },
    ) { inner ->
        val fieldColors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CompositionLocalProvider(
            // Stessa densità di EventEditScreen: bodySmall + altezza minima ridotta, così il
            // form resta compatto anche con undici campi in scroll.
            LocalTextStyle provides MaterialTheme.typography.bodySmall,
            LocalMinimumInteractiveComponentSize provides 24.dp,
        ) {
        Column(
            Modifier.padding(inner).padding(horizontal = 20.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                stringResource(R.string.pe_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            FieldWithIcon(null) {
                CompactTextField(
                    colors = fieldColors,
                    value = titolo, onValueChange = { titolo = it },
                    label = { Text(stringResource(R.string.pe_title_f)) }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.pe_title_ph)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FieldWithIcon(Icons.Default.Schedule) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactTextField(
                        colors = fieldColors,
                        value = data, onValueChange = { data = it },
                        label = { Text(stringResource(R.string.pe_date_f)) }, placeholder = { Text(stringResource(R.string.pe_date_ph)) },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.CalendarMonth, stringResource(R.string.pe_date), modifier = Modifier.size(18.dp))
                            }
                        },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    CompactTextField(
                        colors = fieldColors,
                        value = ora, onValueChange = { ora = it },
                        label = { Text(stringResource(R.string.pe_time_f)) }, placeholder = { Text("hh:mm") },
                        trailingIcon = {
                            IconButton(onClick = { showTimePicker = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.AccessTime, stringResource(R.string.pe_time_f), modifier = Modifier.size(18.dp))
                            }
                        },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
            }
            FieldWithIcon(Icons.Default.Place) {
                CompactTextField(
                    colors = fieldColors,
                    value = localita, onValueChange = { localita = it },
                    label = { Text(stringResource(R.string.pe_place)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FieldWithIcon(Icons.Default.LocalParking) {
                CompactTextField(
                    colors = fieldColors,
                    value = ritrovo, onValueChange = { ritrovo = it },
                    label = { Text(stringResource(R.string.pe_meet)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FieldWithIcon(Icons.Default.Person) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactTextField(
                        colors = fieldColors,
                        value = proponente, onValueChange = { proponente = it },
                        label = { Text(stringResource(R.string.pe_name)) },
                        placeholder = { Text(stringResource(R.string.pe_name_ph)) },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    CompactTextField(
                        colors = fieldColors,
                        value = waProponente, onValueChange = { waProponente = it },
                        label = { Text("WhatsApp") }, placeholder = { Text("333...") },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
            }

            FieldWithIcon(null) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.pe_diff),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box {
                        OutlinedButton(onClick = { showDifficultyMenu = true }) {
                            if (difficolta.isBlank()) {
                                Text(stringResource(R.string.pe_none))
                            } else {
                                DifficultyBadge(difficolta)
                            }
                        }
                        DropdownMenu(expanded = showDifficultyMenu, onDismissRequest = { showDifficultyMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pe_none)) },
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

            FieldWithIcon(Icons.Default.Link) {
                CompactTextField(
                    colors = fieldColors,
                    value = linkPercorso, onValueChange = { linkPercorso = it },
                    label = { Text(stringResource(R.string.pe_route)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            EventGpxField(gpxData, gpxFileName) { d, n -> gpxData = d; gpxFileName = n }
            FieldWithIcon(Icons.Default.Link) {
                CompactTextField(
                    colors = fieldColors,
                    value = link2, onValueChange = { link2 = it },
                    label = { Text(stringResource(R.string.pe_link2)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FieldWithIcon(Icons.Default.Videocam) {
                CompactTextField(
                    colors = fieldColors,
                    value = video, onValueChange = { video = it },
                    label = { Text(stringResource(R.string.pe_video)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FieldWithIcon(Icons.AutoMirrored.Filled.Notes) {
                CompactTextField(
                    colors = fieldColors,
                    value = note, onValueChange = { note = it },
                    label = { Text(stringResource(R.string.pe_notes)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                )
            }
            EventBackgroundField(cardBgData) { c, ph -> cardBgData = c; photoData = ph }
            EventAudioField(audioData) { d, m -> audioData = d; audioMimeType = m }
            Spacer(Modifier.height(40.dp))
        }
        }
    }

    if (showDatePicker) {
        val initialMillis = parseDateToMillis(data) ?: System.currentTimeMillis()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { data = formatMillisToDate(it) }
                    showDatePicker = false
                }) { Text(stringResource(R.string.pe_confirm)) }
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
                }) { Text(stringResource(R.string.pe_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
