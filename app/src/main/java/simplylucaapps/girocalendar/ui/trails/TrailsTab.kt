package simplylucaapps.girocalendar.ui.trails

import simplylucaapps.girocalendar.ui.common.CompactTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.data.remote.firestore.Trail
import simplylucaapps.girocalendar.data.remote.firestore.TrailComment
import simplylucaapps.girocalendar.data.remote.firestore.TrailStatus

/**
 * Tab "TRAIL": elenco sentieri raggruppati per zona, ad albero, con lo stato di percorribilità
 * (pallino colorato, stile trailforks.com/region/.../status/). Dati globali per tutta l'app
 * (non legati al calendario corrente).
 */
@Composable
fun TrailsTab(vm: TrailViewModel, onShowOnMap: (() -> Unit)? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by rememberSaveableQuery()
    var expandedZones by remember { mutableStateOf(setOf<String>()) }
    var showAddTrail by remember { mutableStateOf(false) }
    var detailTrail by remember { mutableStateOf<Trail?>(null) }
    var renamingZone by remember { mutableStateOf<String?>(null) }
    var deletingZone by remember { mutableStateOf<String?>(null) }
    var buildersFor by remember { mutableStateOf<String?>(null) }
    var log by remember { mutableStateOf<List<simplylucaapps.girocalendar.data.remote.firestore.TrailLogEntry>>(emptyList()) }
    LaunchedEffect(state.isAdmin) {
        if (state.isAdmin) vm.observeLog().collect { log = it } else log = emptyList()
    }

    val grouped = remember(state.trails, state.zones, query) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) state.trails else state.trails.filter {
            it.name.lowercase().contains(q) || it.zone.lowercase().contains(q)
        }
        val m = filtered.groupBy { it.zone.ifBlank { "—" } }.toMutableMap()
        // Zone create senza trail: compaiono comunque nell'elenco (vuote).
        state.zones.map { it.zone.trim() }.filter { it.isNotEmpty() && (q.isEmpty() || it.lowercase().contains(q)) && it !in m }.forEach { m[it] = emptyList() }
        m.toSortedMap(compareBy { it.lowercase() })
    }

    // Con una ricerca attiva, espandi automaticamente tutte le zone risultanti.
    LaunchedEffect(query) {
        if (query.isNotBlank()) expandedZones = grouped.keys
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CompactSearchField(query, { query = it }, stringResource(R.string.trail_search_placeholder), Modifier.weight(1f))
            if (state.signedIn) { Spacer(Modifier.width(8.dp)); TrailEditToggle(true) }
            }

            if (grouped.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (query.isBlank()) R.string.trail_empty else R.string.trail_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    grouped.forEach { (zone, trails) ->
                        val expanded = zone in expandedZones
                        item(key = "zone-$zone") {
                            ZoneHeader(
                                zone = zone,
                                count = trails.size,
                                expanded = expanded,
                                onToggle = {
                                    expandedZones = if (expanded) expandedZones - zone else expandedZones + zone
                                },
                                onRename = if (state.isAdmin && TrailEdit.on && zone != "—") ({ renamingZone = zone }) else null,
                                onBuilders = if (state.signedIn && TrailEdit.on && zone != "—") ({ buildersFor = zone }) else null,
                            )
                        }
                        if (expanded) {
                            items(trails, key = { "trail-" + it.id }) { t ->
                                TrailRow(trail = t, onClick = { detailTrail = t })
                            }
                        }
                    }
                    item("log") { TrailLogSection(log, state.isAdmin, onUndo = { e, force, cb -> vm.undoLog(e, force, cb) }) }
                    item("bottom") { Spacer(Modifier.height(96.dp)) }
                }
            }
        }

        if (TrailEdit.on) ExtendedFloatingActionButton(
            onClick = {
                if (state.signedIn) showAddTrail = true
                else vm.signIn(context) { ok -> if (ok) showAddTrail = true }
            },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text(stringResource(R.string.trail_add)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (showAddTrail) {
        AddTrailDialog(
            zones = remember(state.trails, state.zones) { (state.trails.map { it.zone.trim() } + state.zones.map { it.zone.trim() }).filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase() } },
            onDismiss = { showAddTrail = false },
            onConfirm = { name, zone, difficulty ->
                if (name.isBlank()) {
                    // Solo zona, senza trail: crea il documento zona (senza perimetro né costruttori) se non esiste già.
                    if (zone.isNotBlank() && state.zones.none { it.zone == zone }) vm.setZoneBuilders(zone, emptyList()) { ok, e -> android.widget.Toast.makeText(context, if (ok) context.getString(R.string.tr_zone_created) else (e ?: context.getString(R.string.tr_error)), android.widget.Toast.LENGTH_LONG).show() }
                } else vm.createTrail(name, zone, difficulty) { _, _ -> }
                showAddTrail = false
            },
        )
    }

    buildersFor?.let { zn ->
        BuildersDialog(
            zone = zn, existing = state.zones.firstOrNull { it.zone == zn }?.builders.orEmpty(),
            onSave = { list -> vm.setZoneBuilders(zn, list) { ok, e -> android.widget.Toast.makeText(context, if (ok) context.getString(R.string.tr_builders_saved) else (e ?: context.getString(R.string.tr_error)), android.widget.Toast.LENGTH_LONG).show() }; buildersFor = null },
            onDismiss = { buildersFor = null },
        )
    }

    renamingZone?.let { old ->
        var newName by remember(old) { mutableStateOf(old) }
        AlertDialog(
            onDismissRequest = { renamingZone = null },
            title = { Text(stringResource(R.string.trail_zone_rename)) },
            text = {
                CompactTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.trail_field_zone)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank() && newName.trim() != old,
                    onClick = {
                        val target = newName.trim()
                        vm.renameZone(old, target) { ok, err ->
                            if (!ok) android.widget.Toast.makeText(context, err ?: context.getString(R.string.tr_error), android.widget.Toast.LENGTH_LONG).show()
                        }
                        expandedZones = if (old in expandedZones) expandedZones - old + target else expandedZones
                        renamingZone = null
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { deletingZone = old; renamingZone = null }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { renamingZone = null }) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )
    }

    deletingZone?.let { zn ->
        val n = state.trails.count { it.zone == zn }
        AlertDialog(
            onDismissRequest = { deletingZone = null },
            title = { Text(stringResource(R.string.tr_del_zone_q, zn)) },
            text = { Text(if (n > 0) stringResource(R.string.tr_del_zone_n, n) else stringResource(R.string.tr_del_zone_0)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteZoneWithTrails(zn) { ok, e -> android.widget.Toast.makeText(context, if (ok) context.getString(R.string.tr_zone_deleted) else (e ?: context.getString(R.string.tr_error)), android.widget.Toast.LENGTH_LONG).show() }
                    deletingZone = null
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingZone = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    detailTrail?.let { t ->
        // Rilegge sempre la versione più recente dallo stato (lo stato/commenti cambiano da
        // sotto mentre il dialog è aperto), altrimenti mostrerebbe dati stantii dopo un commento.
        val fresh = state.trails.firstOrNull { it.id == t.id } ?: t
        TrailDetailDialog(
            trail = fresh,
            vm = vm,
            signedIn = state.signedIn,
            myUid = state.myUid,
            canDelete = state.myUid == fresh.createdByUid || state.isAdmin,
            onDismiss = { detailTrail = null },
            onDeleted = { detailTrail = null },
            onShowOnMap = onShowOnMap?.let { cb -> { MapFocus.trailId = fresh.id; detailTrail = null; cb() } },
        )
    }
}

@Composable
private fun rememberSaveableQuery(): MutableState<String> =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }

/** Registro delle modifiche ai trail in fondo alla lista. Sempre contratto all'apertura; il
 * contenuto lo vedono solo gli admin, l'avviso che ogni modifica è tracciata è visibile a tutti. */
@Composable
internal fun TrailLogSection(
    log: List<simplylucaapps.girocalendar.data.remote.firestore.TrailLogEntry>,
    isAdmin: Boolean,
    onUndo: (simplylucaapps.girocalendar.data.remote.firestore.TrailLogEntry, Boolean, (Boolean, Boolean, String?) -> Unit) -> Unit,
    startExpanded: Boolean = false,
) {
    val ctx0 = LocalContext.current
    var undoTarget by remember { mutableStateOf<simplylucaapps.girocalendar.data.remote.firestore.TrailLogEntry?>(null) }
    var conflict by remember { mutableStateOf(false) }
    val undone = remember(log) { log.mapNotNull { it.undoOf.ifEmpty { null } }.toSet() }
    fun runUndo(e: simplylucaapps.girocalendar.data.remote.firestore.TrailLogEntry, force: Boolean) {
        onUndo(e, force) { ok, isConflict, msg ->
            when {
                ok -> { undoTarget = null; conflict = false; android.widget.Toast.makeText(ctx0, ctx0.getString(R.string.tr_undo_done), android.widget.Toast.LENGTH_SHORT).show() }
                isConflict -> conflict = true
                else -> { undoTarget = null; conflict = false; android.widget.Toast.makeText(ctx0, msg ?: ctx0.getString(R.string.tr_error), android.widget.Toast.LENGTH_LONG).show() }
            }
        }
    }
    val fmt = remember { java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault()) }
    var expanded by remember { mutableStateOf(startExpanded) }
    var showAll by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    fun actLabel(a: String): String = when (a) {
        "rename" -> ctx.getString(R.string.tr_act_rename); "rename_zone" -> ctx.getString(R.string.tr_act_rename_zone)
        "difficulty" -> ctx.getString(R.string.tr_act_difficulty); "status" -> ctx.getString(R.string.tr_act_status)
        "position" -> ctx.getString(R.string.tr_act_position); "path" -> ctx.getString(R.string.tr_act_path)
        "delete" -> ctx.getString(R.string.tr_act_delete); "create" -> ctx.getString(R.string.tr_act_create)
        else -> a
    }
    fun describe(e: simplylucaapps.girocalendar.data.remote.firestore.TrailLogEntry): String {
        val who = listOf(e.userName, if (e.userEmail.isNotBlank()) "(${e.userEmail})" else "")
            .filter { it.isNotBlank() }.joinToString(" ").ifBlank { ctx.getString(R.string.tr_log_user) }
        val what = when (e.action) {
            "create" -> if (e.detail.isNotBlank()) ctx.getString(R.string.tr_log_create_in, e.trailName, e.detail) else ctx.getString(R.string.tr_log_create, e.trailName)
            "status" -> ctx.getString(R.string.tr_log_status, e.trailName, e.detail)
            "difficulty" -> ctx.getString(R.string.tr_log_difficulty, e.trailName, e.detail)
            "rename_zone" -> ctx.getString(R.string.tr_log_rename_zone, e.detail)
            "rename" -> ctx.getString(R.string.tr_log_rename, e.trailName, e.detail)
            "delete" -> ctx.getString(R.string.tr_log_delete, e.trailName)
            "comment" -> if (e.detail.isNotBlank()) ctx.getString(R.string.tr_log_comment_status, e.trailName, e.detail) else ctx.getString(R.string.tr_log_comment, e.trailName)
            "comment_edit" -> ctx.getString(R.string.tr_log_comment_edit, e.trailName)
            "zone" -> ctx.getString(R.string.tr_log_zone, e.trailName, e.detail)
            "zone_builders" -> ctx.getString(R.string.tr_log_zone_builders, e.trailName, e.detail)
            "zone_delete" -> ctx.getString(R.string.tr_log_zone_delete, e.trailName)
            "poi_create" -> ctx.getString(R.string.tr_log_poi_create, e.trailName, e.detail)
            "poi_edit" -> ctx.getString(R.string.tr_log_poi_edit, e.trailName)
            "poi_delete" -> ctx.getString(R.string.tr_log_poi_delete, e.trailName)
            "position" -> ctx.getString(R.string.tr_log_position, e.trailName, e.detail)
            "path" -> ctx.getString(R.string.tr_log_path, e.trailName, e.detail)
            "comment_delete" -> ctx.getString(R.string.tr_log_comment_delete, e.trailName)
            "undo" -> ctx.getString(R.string.tr_log_undo, actLabel(e.detail), e.trailName)
            else -> e.action
        }
        return "$who $what"
    }
    undoTarget?.let { e ->
        AlertDialog(
            onDismissRequest = { undoTarget = null; conflict = false },
            title = { Text(stringResource(if (e.action == "undo") R.string.tr_redo_q else R.string.tr_undo_q)) },
            text = { Text(if (conflict) stringResource(R.string.tr_undo_conflict) else describe(e)) },
            confirmButton = { TextButton(onClick = { runUndo(e, conflict) }) { Text(stringResource(if (conflict) R.string.tr_undo_anyway else if (e.action == "undo") R.string.tr_redo_yes else R.string.tr_undo_yes)) } },
            dismissButton = { TextButton(onClick = { undoTarget = null; conflict = false }) { Text(stringResource(R.string.tr_undo_no)) } },
        )
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().then(if (isAdmin) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("LOG", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (isAdmin) Icon(Icons.Default.ExpandMore, null, modifier = Modifier.rotate(if (expanded) 180f else 0f))
        }
        Text(
            stringResource(R.string.tr_log_info),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isAdmin && expanded) {
            Spacer(Modifier.height(8.dp))
            CompactSearchField(query, { query = it }, stringResource(R.string.tr_log_search), Modifier.fillMaxWidth())
            val q = query.trim().lowercase()
            // "-parola" esclude le voci che la contengono; le altre parole devono comparire tutte.
            val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val neg = tokens.filter { it.startsWith("-") && it.length > 1 }.map { it.drop(1) }
            val pos = tokens.filter { !it.startsWith("-") }
            val matches = if (tokens.isEmpty()) log else log.filter { e -> val d = describe(e).lowercase(); pos.all { d.contains(it) } && neg.none { d.contains(it) } }
            // Con una ricerca attiva si cerca sempre in tutto il log; senza, ultime 20 (o tutte).
            val shown = if (q.isNotEmpty() || showAll) matches else matches.take(20)
            Spacer(Modifier.height(6.dp))
            if (shown.isEmpty()) {
                Text(stringResource(if (log.isEmpty()) R.string.tr_log_none else R.string.tr_log_no_result),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            shown.forEach { e ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(fmt.format(java.util.Date(e.createdAtMillis)), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(describe(e), style = MaterialTheme.typography.bodySmall)
                    }
                    if (e.undo != null) {
                        if (e.id in undone) Text(stringResource(R.string.tr_undone), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else TextButton(onClick = { conflict = false; undoTarget = e }) {
                            Text(stringResource(if (e.action == "undo") R.string.tr_redo_btn else R.string.tr_undo_btn))
                        }
                    }
                }
            }
            if (q.isEmpty() && matches.size > 20) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) stringResource(R.string.tr_log_last20) else stringResource(R.string.tr_log_all, matches.size))
                }
            }
        }
    }
}

@Composable
private fun ZoneHeader(zone: String, count: Int, expanded: Boolean, onToggle: () -> Unit, onRename: (() -> Unit)? = null, onBuilders: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            zone,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onBuilders != null) {
            Icon(
                Icons.Default.Sell, stringResource(R.string.tr_builders_desc),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).clickable(onClick = onBuilders),
            )
            Spacer(Modifier.width(12.dp))
        }
        if (onRename != null) {
            Icon(
                Icons.Default.Edit, stringResource(R.string.trail_zone_rename),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).clickable(onClick = onRename),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrailRow(trail: Trail, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 34.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(12.dp).clip(CircleShape).background(trail.status.color),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    trail.name, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (trail.difficulty.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    simplylucaapps.girocalendar.ui.common.DifficultyBadge(trail.difficulty)
                }
                // Simbolo "ha il tracciato (GPX) sulla mappa".
                if (trail.path.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Route, stringResource(R.string.tr_gpx_on_map), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (trail.lastUpdateAtMillis > 0L) {
                Text(
                    stringResource(
                        R.string.trail_last_update,
                        trail.lastUpdateByName,
                        formatRelativeDate(trail.lastUpdateAtMillis),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            trail.status.label,
            style = MaterialTheme.typography.labelMedium,
            color = trail.status.color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTrailDialog(zones: List<String>, onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("") }
    var zoneMenu by remember { mutableStateOf(false) }
    // Senza zone esistenti si scrive direttamente quella nuova.
    var newZoneMode by remember { mutableStateOf(zones.isEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trail_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (zones.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = zoneMenu, onExpandedChange = { zoneMenu = it }) {
                        CompactTextField(
                            value = if (newZoneMode) stringResource(R.string.trail_new_zone) else zone,
                            onValueChange = {}, readOnly = true,
                            label = { Text(stringResource(R.string.trail_field_zone)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = zoneMenu) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = zoneMenu, onDismissRequest = { zoneMenu = false }) {
                            zones.forEach { z ->
                                DropdownMenuItem(text = { Text(z) }, onClick = { zone = z; newZoneMode = false; zoneMenu = false })
                            }
                            DropdownMenuItem(
                                text = { Text("+ " + stringResource(R.string.trail_new_zone), fontWeight = FontWeight.Bold) },
                                onClick = { zone = ""; newZoneMode = true; zoneMenu = false },
                            )
                        }
                    }
                }
                if (newZoneMode) {
                    CompactTextField(
                        value = zone, onValueChange = { zone = it },
                        label = { Text(if (zones.isEmpty()) stringResource(R.string.trail_field_zone) else stringResource(R.string.trail_new_zone)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                CompactTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.trail_field_name) + if (newZoneMode) stringResource(R.string.tr_optional) else "") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.trail_field_difficulty), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    simplylucaapps.girocalendar.data.remote.firestore.Difficulty.LEVELS.forEach { level ->
                        val selected = difficulty == level.id
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) level.color else level.color.copy(alpha = 0.35f))
                                .clickable { difficulty = if (selected) "" else level.id }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(level.label, style = MaterialTheme.typography.labelSmall, color = level.onColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() || zone.isNotBlank()) onConfirm(name.trim(), zone.trim(), difficulty) },
                enabled = name.isNotBlank() || (newZoneMode && zone.isNotBlank()),
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrailDetailDialog(
    onShowOnMap: (() -> Unit)? = null,
    trail: Trail,
    vm: TrailViewModel,
    signedIn: Boolean,
    myUid: String?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    var editingComment by remember { mutableStateOf<TrailComment?>(null) }
    var deletingComment by remember { mutableStateOf<TrailComment?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var comments by remember(trail.id) { mutableStateOf<List<TrailComment>>(emptyList()) }
    LaunchedEffect(trail.id) { vm.observeComments(trail.id).collect { comments = it } }

    var commentText by remember { mutableStateOf("") }
    var newStatus by remember(trail.id) { mutableStateOf<TrailStatus?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editingDifficulty by remember { mutableStateOf(false) }
    var confirmRemoveFromMap by remember { mutableStateOf(false) }

    if (confirmRemoveFromMap) {
        AlertDialog(
            onDismissRequest = { confirmRemoveFromMap = false },
            title = { Text(stringResource(R.string.tr_remove_map_q)) },
            text = { Text(stringResource(R.string.tr_remove_map_msg, trail.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setPath(trail.id, emptyList()) { _, _ -> }
                    confirmRemoveFromMap = false
                }) { Text(stringResource(R.string.tr_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveFromMap = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (editingDifficulty) {
        AlertDialog(
            onDismissRequest = { editingDifficulty = false },
            title = { Text(stringResource(R.string.tr_edit_trail)) },
            text = {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canDelete) {
                    var newName by remember(trail.id) { mutableStateOf(trail.name) }
                    Text(stringResource(R.string.tr_trail_name), style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CompactTextField(newName, { newName = it }, singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        FilledTonalButton(
                            enabled = newName.isNotBlank() && newName.trim() != trail.name,
                            onClick = { vm.renameTrail(trail.id, newName) { ok, e -> android.widget.Toast.makeText(context, if (ok) context.getString(R.string.tr_name_changed) else (e ?: context.getString(R.string.tr_error)), android.widget.Toast.LENGTH_LONG).show() } },
                        ) { Text(stringResource(R.string.action_save)) }
                    }
                }
                Text(stringResource(R.string.trail_field_difficulty), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    simplylucaapps.girocalendar.data.remote.firestore.Difficulty.LEVELS.forEach { level ->
                        val selected = trail.difficulty == level.id
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) level.color else level.color.copy(alpha = 0.35f))
                                .clickable {
                                    vm.setDifficulty(trail.id, if (selected) "" else level.id) { _, _ -> }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(level.label, style = MaterialTheme.typography.labelSmall, color = level.onColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(stringResource(R.string.trail_update_status), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrailStatus.entries.forEach { s ->
                        StatusChip(status = s, selected = trail.status == s, onClick = {
                            if (trail.status != s) vm.setStatus(trail.id, s) { _, _ -> }
                        })
                    }
                }
                if (trail.lat != null) {
                    OutlinedButton(
                        onClick = { confirmRemoveFromMap = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.tr_remove_map_btn)) }
                }
              }
            },
            confirmButton = { TextButton(onClick = { editingDifficulty = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }

    editingComment?.let { ec ->
        var txt by remember(ec.id) { mutableStateOf(ec.text) }
        AlertDialog(
            onDismissRequest = { editingComment = null },
            title = { Text(stringResource(R.string.tr_edit_comment)) },
            text = { CompactTextField(txt, { txt = it }, modifier = Modifier.fillMaxWidth(), minLines = 2) },
            confirmButton = {
                TextButton(
                    enabled = txt.isNotBlank() || ec.newStatus.isNotBlank(),
                    onClick = { vm.editComment(trail.id, ec.id, txt) { _, _ -> }; editingComment = null },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { editingComment = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    deletingComment?.let { dc ->
        AlertDialog(
            onDismissRequest = { deletingComment = null },
            title = { Text(stringResource(R.string.tr_delete_comment_q)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteComment(trail.id, dc.id) { _, _ -> }; deletingComment = null }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deletingComment = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {},
                        actions = {
                            // Modifica (stato, difficoltà, tracciato): matita per ogni utente con login, solo in modalità
                            // modifica; eliminazione solo per chi ha creato il trail o per un admin.
                            if (signedIn && TrailEdit.on) {
                                IconButton(onClick = { editingDifficulty = true }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Edit, stringResource(R.string.trail_field_difficulty), modifier = Modifier.size(26.dp))
                                }
                            }
                            if (canDelete && TrailEdit.on) {
                                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        Icons.Default.Delete,
                                        stringResource(R.string.action_delete),
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Close, stringResource(R.string.action_close), modifier = Modifier.size(28.dp))
                            }
                        },
                    )
                },
            ) { inner ->
            Column(Modifier.padding(inner).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                // Nome per intero (va a capo se serve, mai troncato), zona e difficoltà sotto,
                // sulla stessa riga tra loro — le icone cestino/chiudi sono nella TopAppBar,
                // a margine dal bordo destro, non più schiacciate contro il titolo.
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 6.dp).size(14.dp).clip(CircleShape).background(trail.status.color))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(trail.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(trail.zone, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (trail.difficulty.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                simplylucaapps.girocalendar.ui.common.DifficultyBadge(trail.difficulty)
                            }
                        }
                    }
                }
                // Se il trail ha un tracciato sulla mappa, lo propone: apre la tab MAPPA centrata su di lui.
                if (onShowOnMap != null && trail.path.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = onShowOnMap, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Map, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.tr_see_on_map))
                    }
                }
                Spacer(Modifier.height(20.dp))

                Text(stringResource(R.string.trail_comments_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                if (comments.isEmpty()) {
                    Text(
                        stringResource(R.string.trail_no_comments),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // Niente scroll/altezza massima propri: la scheda intera scrolla (vedi
                    // Column esterna), un doppio scroll qui dentro sarebbe solo fastidioso.
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        comments.forEach { c ->
                            val mine = myUid != null && c.authorUid == myUid
                            TrailCommentItem(c, mine, onEdit = { editingComment = c }, onDelete = { deletingComment = c })
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                if (signedIn) {
                    Text(stringResource(R.string.trail_update_status), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TrailStatus.entries.forEach { s ->
                            StatusChip(status = s, selected = newStatus == s, onClick = {
                                newStatus = if (newStatus == s) null else s
                            })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    CompactTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text(stringResource(R.string.trail_comment_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            vm.addComment(trail.id, commentText, newStatus) { ok, _ ->
                                if (ok) { commentText = ""; newStatus = null }
                            }
                        },
                        enabled = commentText.isNotBlank() || newStatus != null,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(stringResource(R.string.trail_publish_comment)) }
                } else {
                    OutlinedButton(
                        onClick = { vm.signIn(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.trail_signin_to_comment)) }
                }
                Spacer(Modifier.height(24.dp))
            }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.trail_delete_confirm_title)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTrail(trail.id) { _, _ -> }
                    showDeleteConfirm = false
                    onDeleted()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun StatusChip(status: TrailStatus, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) status.color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(status.color))
        Spacer(Modifier.width(6.dp))
        Text(status.label, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun TrailCommentItem(c: TrailComment, mine: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.authorName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                formatRelativeDate(c.createdAtMillis) + if (c.editedAtMillis > 0L) stringResource(R.string.tr_edited) else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (mine) {
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, stringResource(R.string.tr_edit_comment), modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, stringResource(R.string.tr_delete_comment), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (c.newStatus.isNotBlank()) {
            val s = TrailStatus.of(c.newStatus)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(s.color))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.trail_status_changed_to, s.label), style = MaterialTheme.typography.labelSmall, color = s.color)
            }
        }
        if (c.text.isNotBlank()) {
            Text(c.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** "Oggi", "Ieri" o "gg/MM/yyyy" per la data di un commento/aggiornamento trail. */
private fun formatRelativeDate(millis: Long): String {
    if (millis <= 0L) return ""
    val cal = java.util.Calendar.getInstance()
    val today = cal.clone() as java.util.Calendar
    cal.timeInMillis = millis
    val diffDays = ((today.timeInMillis - millis) / (24 * 60 * 60 * 1000)).toInt()
    return when {
        diffDays == 0 && today.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR) -> "Oggi"
        diffDays in 1..1 -> "Ieri"
        else -> java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(millis))
    }
}


/** Campo di ricerca basso (OutlinedTextField ha un minimo di 56dp). */
@Composable
private fun CompactSearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.text.BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.height(38.dp),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxSize().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                }
                if (value.isNotEmpty()) Icon(Icons.Default.Close, null, Modifier.size(18.dp).clickable { onChange("") })
            }
        },
    )
}
