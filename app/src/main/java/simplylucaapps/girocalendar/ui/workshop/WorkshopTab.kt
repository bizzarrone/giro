package simplylucaapps.girocalendar.ui.workshop

import simplylucaapps.girocalendar.ui.common.CompactTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.data.remote.auth.GoogleAuthRepository
import simplylucaapps.girocalendar.data.remote.firestore.JobColor
import simplylucaapps.girocalendar.data.remote.firestore.MAX_BIKES
import simplylucaapps.girocalendar.data.remote.firestore.WorkshopBike
import simplylucaapps.girocalendar.data.remote.firestore.WorkshopJob
import simplylucaapps.girocalendar.util.WorkshopExport
import simplylucaapps.girocalendar.data.settings.WorkshopIconPrefs
import simplylucaapps.girocalendar.data.remote.firestore.WorkshopRepository
import java.time.Instant
import java.time.ZoneOffset
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class WorkshopViewModel(
    private val repo: WorkshopRepository,
    auth: GoogleAuthRepository,
) : ViewModel() {
    private val uid = auth.observeUser().map { it?.uid }
    val bikes: StateFlow<List<WorkshopBike>> = uid
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else repo.observeBikes(u) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val jobs: StateFlow<List<WorkshopJob>> = uid
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else repo.observeJobs(u) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(job: WorkshopJob) { viewModelScope.launch { repo.save(job) } }
    fun delete(id: String) { viewModelScope.launch { repo.delete(id) } }
    /** [onCreated] riceve l'id (per selezionare subito la bici nuova). */
    fun saveBike(bike: WorkshopBike, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch { repo.saveBike(bike).onSuccess(onCreated) }
    }
    fun deleteBike(id: String) { viewModelScope.launch { repo.deleteBike(id) } }

    companion object {
        fun factory(repo: WorkshopRepository, auth: GoogleAuthRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { WorkshopViewModel(repo, auth) } }
    }
}

private val Mono = FontFamily.Monospace
private val LineColor = Color(0xFF888888)
private val HeaderBg = Color(0xFF999999)

/** Costo con virgola decimale (16,7), senza decimali se intero. */
private fun fmtCost(c: Double?): String = when {
    c == null -> ""
    c % 1.0 == 0.0 -> c.toLong().toString()
    else -> String.format(Locale.US, "%.2f", c).trimEnd('0').trimEnd('.').replace('.', ',')
}

/** Data compatta gg.mm.aa da ISO aaaa-mm-gg. */
private fun fmtDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yy")) }.getOrDefault(iso)

/**
 * Tab "Officina": tabella (DATA, COSTO, LUOGO, LAVORO, km) dei lavori svolti sulla bici,
 * con totale in fondo. Visibile solo con login Google; dati privati dell'utente.
 * Il pulsante +LAVORO vive nello Scaffold: [showNew]/[onNewDismiss] arrivano da lì.
 */
@Composable
fun WorkshopTab(vm: WorkshopViewModel, showNew: Boolean, onNewDismiss: () -> Unit) {
    val allJobs by vm.jobs.collectAsStateWithLifecycle()
    val bikes by vm.bikes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<WorkshopJob?>(null) }
    var editing by remember { mutableStateOf<WorkshopJob?>(null) }
    var bikeDialog by remember { mutableStateOf<WorkshopBike?>(null) }   // id vuoto = nuova
    var confirmDeleteBike by remember { mutableStateOf(false) }
    var bikeMenu by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) {
        val sctx = LocalContext.current
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(stringResource(R.string.ws_settings)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.pref_workshop_auto_icons), Modifier.weight(1f))
                        Switch(checked = WorkshopIconPrefs.autoIcons, onCheckedChange = { WorkshopIconPrefs.setAutoIcons(sctx, it) })
                    }
                    simplylucaapps.girocalendar.ui.calendar.WorkshopIconColorsPicker()
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }

    val bike = bikes.firstOrNull { it.id == selectedId } ?: bikes.firstOrNull()
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(allJobs, bike, query) {
        val q = query.trim().lowercase()
        allJobs.filter { it.bikeId == bike?.id }.filter { j ->
            q.isEmpty() || listOf(j.work, j.place, j.date, fmtDate(j.date), fmtCost(j.cost), j.km?.toString().orEmpty())
                .any { it.lowercase().contains(q) }
        }
    }
    val total = shown.sumOf { it.cost ?: 0.0 }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { bikeMenu = true }) {
                    Text(bike?.let { stringResource(R.string.workshop_title_bike, it.name) } ?: stringResource(R.string.workshop_title),
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(bikeMenu, { bikeMenu = false }) {
                    bikes.forEach { b ->
                        DropdownMenuItem(text = { Text(b.name, fontWeight = if (b.id == bike?.id) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { selectedId = b.id; bikeMenu = false })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.workshop_new_bike, bikes.size, MAX_BIKES)) },
                        enabled = bikes.size < MAX_BIKES,
                        leadingIcon = { Icon(Icons.Default.Add, null) },
                        onClick = { bikeMenu = false; bikeDialog = WorkshopBike() })
                    if (bike != null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.workshop_rename_bike)) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { bikeMenu = false; bikeDialog = bike })
                        DropdownMenuItem(text = { Text(stringResource(R.string.workshop_delete_bike)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { bikeMenu = false; confirmDeleteBike = true })
                    }
                }
            }
            if (bike != null) androidx.compose.foundation.text.BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.width(96.dp).height(30.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(15.dp)),
                decorationBox = { inner ->
                    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.weight(1f).padding(start = 4.dp)) {
                            if (query.isEmpty()) Text(stringResource(R.string.ws_search), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            inner()
                        }
                        if (query.isNotEmpty()) Icon(Icons.Default.Close, null, Modifier.size(14.dp).clickable { query = "" })
                    }
                })
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, stringResource(R.string.ws_settings)) }
            if (bike != null) Box {
                IconButton(onClick = { exportMenu = true }) { Icon(Icons.Default.Share, stringResource(R.string.workshop_export)) }
                DropdownMenu(exportMenu, { exportMenu = false }) {
                    listOf(WorkshopExport.Format.CSV to "CSV", WorkshopExport.Format.PDF to "PDF", WorkshopExport.Format.JPEG to "JPEG",
                        WorkshopExport.Format.TEXT to stringResource(R.string.workshop_export_text), WorkshopExport.Format.ODS to "ODS")
                        .forEach { (f, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                exportMenu = false
                                WorkshopExport.share(context, f, bike.name, shown, WorkshopIconPrefs.autoIcons) { WorkshopIconPrefs.colorOf(it) }
                            })
                        }
                }
            }
        }
        if (bike == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.workshop_private_notice), textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.workshop_empty), textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (bike != null) Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            Row(Modifier.background(HeaderBg).height(IntrinsicSize.Min)) {
                Cell(stringResource(R.string.workshop_col_date), W_DATE, header = true)
                Cell(stringResource(R.string.workshop_col_cost) + " €", W_COST, header = true)
                Cell(stringResource(R.string.workshop_col_place), W_PLACE, header = true)
                Cell(stringResource(R.string.workshop_col_job), W_WORK, header = true)
                Cell(stringResource(R.string.workshop_col_km), W_KM, header = true)
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(shown, key = { it.id }) { j ->
                    val bg = j.color.color
                    Row(Modifier.height(IntrinsicSize.Min).clickable { viewing = j }) {
                        Cell(fmtDate(j.date), W_DATE, align = TextAlign.Center)
                        Cell(fmtCost(j.cost), W_COST, align = TextAlign.Center)
                        Cell(j.place, W_PLACE, align = TextAlign.Center)
                        Cell(j.work, W_WORK, bg = bg, icons = JobIcon.resolve(j, WorkshopIconPrefs.autoIcons))
                        Cell(j.km?.toString() ?: "", W_KM, align = TextAlign.End)
                    }
                }
                item("total") {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Spacer(Modifier.weight(W_DATE))
                        Text(fmtCost(total) + " €", Modifier.weight(W_COST), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.workshop_total), Modifier.weight(W_PLACE), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(96.dp))
                }
            }
        }
    }

    // +LAVORO senza nessuna bici: prima si crea la bici.
    if (showNew && bike == null && bikeDialog == null) bikeDialog = WorkshopBike()
    if (showNew && bike != null) {
        JobDialog(initial = WorkshopJob(date = LocalDate.now().toString(), bikeId = bike.id),
            onDismiss = onNewDismiss, onSave = { vm.save(it); onNewDismiss() }, onDelete = null)
    }
    viewing?.let { j ->
        JobDetailDialog(j, bike?.name.orEmpty(), WorkshopIconPrefs.autoIcons,
            onEdit = { editing = j; viewing = null }, onDismiss = { viewing = null })
    }
    editing?.let { j ->
        JobDialog(initial = j, onDismiss = { editing = null },
            onSave = { vm.save(it); editing = null }, onDelete = { vm.delete(j.id); editing = null })
    }
    bikeDialog?.let { b ->
        var name by remember { mutableStateOf(b.name) }
        val dismiss = { bikeDialog = null; if (b.id.isEmpty() && bikes.isEmpty()) onNewDismiss() }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(if (b.id.isEmpty()) R.string.workshop_new_bike_title else R.string.workshop_rename_bike)) },
            text = { CompactTextField(name, { name = it }, label = { Text(stringResource(R.string.workshop_field_bike)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.saveBike(b.copy(name = name)) { id -> if (b.id.isEmpty()) selectedId = id }
                    bikeDialog = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmDeleteBike && bike != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteBike = false },
            title = { Text(stringResource(R.string.workshop_delete_bike_confirm, bike.name)) },
            confirmButton = { TextButton(onClick = { vm.deleteBike(bike.id); selectedId = null; confirmDeleteBike = false }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDeleteBike = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

// Pesi delle colonne: la tabella riempie sempre la larghezza dello schermo (vista compatta).
private const val W_DATE = 0.95f
private const val W_COST = 0.85f
private const val W_PLACE = 1f
private const val W_WORK = 2.6f
private const val W_KM = 0.75f

@Composable
private fun RowScope.Cell(
    text: String, width: Float, header: Boolean = false,
    align: TextAlign = TextAlign.Start, bg: Color? = null, icons: List<JobIcon> = emptyList(),
) {
    Box(
        Modifier.weight(width).fillMaxHeight().heightIn(min = 22.dp)
            .background(bg ?: Color.Transparent)
            .border(0.5.dp, LineColor)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val fg = when {
            header -> Color.White
            bg != null -> Color.Black
            else -> MaterialTheme.colorScheme.onBackground
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            icons.forEach { Icon(it.icon, it.labelIt, Modifier.padding(end = 2.dp).size(11.dp), tint = WorkshopIconPrefs.colorOf(it.id)?.let(::Color) ?: fg) }
            Text(text, Modifier.weight(1f), color = fg, fontFamily = Mono, fontSize = with(LocalDensity.current) { 9.dp.toSp() }, lineHeight = with(LocalDensity.current) { 11.dp.toSp() },
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal, textAlign = align)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JobDialog(
    initial: WorkshopJob, onDismiss: () -> Unit,
    onSave: (WorkshopJob) -> Unit, onDelete: (() -> Unit)?,
) {
    var date by remember { mutableStateOf(initial.date) }
    var cost by remember { mutableStateOf(fmtCost(initial.cost)) }
    var place by remember { mutableStateOf(initial.place) }
    var work by remember { mutableStateOf(initial.work) }
    var km by remember { mutableStateOf(initial.km?.toString() ?: "") }
    var color by remember { mutableStateOf(initial.color) }
    var icons by remember { mutableStateOf(initial.icons ?: if (WorkshopIconPrefs.autoIcons) JobIcon.detect(initial.work).map { it.id } else emptyList()) }
    var dismissed by remember { mutableStateOf(setOf<String>()) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (onDelete == null) R.string.workshop_add else R.string.workshop_edit)) },
        text = {
            // lo spazio in alto evita che l'etichetta «DATA», che sporge sopra il bordo del primo campo, venga tagliata
            Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactTextField(date, {}, readOnly = true, label = { Text(stringResource(R.string.workshop_col_date)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().clickable { pickDate = true },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactTextField(cost, { cost = it }, label = { Text(stringResource(R.string.workshop_col_cost)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    CompactTextField(km, { km = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.workshop_col_km)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                CompactTextField(place, { place = it }, label = { Text(stringResource(R.string.workshop_col_place)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CompactTextField(work, {
                    work = it
                    if (WorkshopIconPrefs.autoIcons) icons = icons + JobIcon.detect(it).map { i -> i.id }.filter { id -> id !in icons && id !in dismissed }
                }, label = { Text(stringResource(R.string.workshop_col_job)) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.workshop_field_icons), style = MaterialTheme.typography.labelMedium)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    JobIcon.entries.forEach { ji ->
                        val on = ji.id in icons
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .border(1.dp, if (on) MaterialTheme.colorScheme.primary else LineColor, CircleShape)
                                .clickable {
                                    if (on) { icons = icons - ji.id; dismissed = dismissed + ji.id }
                                    else { icons = icons + ji.id; dismissed = dismissed - ji.id }
                                },
                            contentAlignment = Alignment.Center,
                        ) { Icon(ji.icon, ji.labelIt, Modifier.size(22.dp), tint = WorkshopIconPrefs.colorOf(ji.id)?.let(::Color) ?: LocalContentColor.current) }
                    }
                }
                Text(stringResource(R.string.workshop_field_color), style = MaterialTheme.typography.labelMedium)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    JobColor.entries.forEach { c ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape)
                                .background(c.color ?: Color.Transparent)
                                .border(if (c == color) 3.dp else 1.dp,
                                    if (c == color) MaterialTheme.colorScheme.primary else LineColor, CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = work.isNotBlank() && date.isNotBlank(), onClick = {
                onSave(initial.copy(
                    date = date, place = place, work = work, color = color, icons = icons,
                    cost = cost.replace(',', '.').toDoubleOrNull(), km = km.toIntOrNull(),
                ))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.action_delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )

    if (pickDate) {
        val startMillis = runCatching { LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
        val st = rememberDatePickerState(initialSelectedDateMillis = startMillis)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(st) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.workshop_delete_confirm)) },
            confirmButton = { TextButton(onClick = { onDelete?.invoke() }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}


/** Dettaglio di un lavoro (tap sulla riga); la penna apre il dialogo di modifica. */
@Composable
private fun JobDetailDialog(job: WorkshopJob, bikeName: String, autoIcons: Boolean, onEdit: () -> Unit, onDismiss: () -> Unit) {
    val ics = JobIcon.resolve(job, autoIcons)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    runCatching { LocalDate.parse(job.date).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) }.getOrDefault(job.date),
                    Modifier.weight(1f),
                )
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.workshop_edit)) }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (bikeName.isNotBlank()) DetailField(stringResource(R.string.workshop_field_bike), bikeName)
                Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .background(job.color.color ?: Color.Transparent).padding(if (job.color.color != null) 8.dp else 0.dp),
                ) {
                    val fg = if (job.color.color != null) Color.Black else MaterialTheme.colorScheme.onSurface
                    Text(stringResource(R.string.workshop_col_job), style = MaterialTheme.typography.labelMedium,
                        color = if (job.color.color != null) Color.DarkGray else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (ics.isNotEmpty()) Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ics.forEach { Icon(it.icon, it.labelIt, Modifier.size(22.dp), tint = WorkshopIconPrefs.colorOf(it.id)?.let(::Color) ?: fg) }
                    }
                    Text(job.work, style = MaterialTheme.typography.bodyLarge, color = fg)
                }
                DetailField(stringResource(R.string.workshop_col_cost) + " €", fmtCost(job.cost).ifEmpty { "—" })
                DetailField(stringResource(R.string.workshop_col_place), job.place.ifBlank { "—" })
                DetailField(stringResource(R.string.workshop_col_km), job.km?.toString() ?: "—")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun DetailField(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
