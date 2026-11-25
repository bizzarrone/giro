package simplylucaapps.girocalendar.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.data.settings.TabId
import simplylucaapps.girocalendar.data.settings.TabPrefs

/** Icona stilizzata (monocromatica) di ogni scheda, usata dalla Home e dalla scelta della scheda suggerita. */
private fun tabVector(id: TabId): ImageVector = when (id) {
    TabId.HOME -> Icons.Outlined.Home; TabId.CALENDARS -> Icons.Outlined.FolderCopy; TabId.UPCOMING -> Icons.Outlined.DirectionsBike
    TabId.PAST -> Icons.Outlined.History; TabId.MONTH -> Icons.Outlined.CalendarMonth; TabId.INFO -> Icons.Outlined.Campaign
    TabId.TRAILS -> Icons.Outlined.Route; TabId.MAP -> Icons.Outlined.Map; TabId.EPICS -> Icons.Outlined.Terrain
    TabId.CONTACTS -> Icons.Outlined.Group; TabId.WORKSHOP -> Icons.Outlined.Build; TabId.RECORD -> Icons.Outlined.RadioButtonChecked
    TabId.EMERGENCY -> Icons.Outlined.Sos; TabId.MARKET -> Icons.Outlined.ShoppingCart
    TabId.SETTINGS -> Icons.Outlined.Settings; TabId.MYRIDES -> Icons.Outlined.Folder
}

/** Icona della scheda [id] nel colore [tint] (di default quello del testo). */
@Composable
fun TabIcon(id: TabId, size: Dp, tint: Color = LocalContentColor.current) {
    Icon(tabVector(id), contentDescription = null, modifier = Modifier.size(size), tint = tint)
}

/** Icona + nome della scheda, per le voci dei menu. */
@Composable
private fun TabMenuLabel(id: TabId) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TabIcon(id, 22.dp)
        Spacer(Modifier.width(10.dp))
        Text(id.label)
    }
}

/**
 * Home: un tasto grande per ogni scheda visibile; un tap apre la scheda. La matita attiva la modifica: l'occhio
 * mostra/nasconde la scheda, la stella sceglie quella su cui si apre il calendario (di default la Home).
 * [tabs] = schede mostrate ora, nell'ordine scelto dall'utente; [hiddenTabs] = nascoste ma disponibili (solo in modifica).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTab(tabs: List<TabId>, hiddenTabs: List<TabId>, lockedTabs: List<TabId> = emptyList(), onOpen: (TabId) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    // BACK in modifica = «Fine» (non esce dalla schermata).
    androidx.activity.compose.BackHandler(enabled = editing) { editing = false }
    // Trascinamento stile launcher: tieni premuto un pulsante in modifica e rilascialo sopra un altro.
    var dragging by remember { mutableStateOf<TabId?>(null) }
    var dragOff by remember { mutableStateOf(Offset.Zero) }
    val bounds = remember { mutableMapOf<TabId, Rect>() }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.home_reset)) },
            text = { Text(stringResource(R.string.home_reset_confirm)) },
            confirmButton = { TextButton(onClick = { TabPrefs.reset(ctx); confirmReset = false }) { Text(stringResource(R.string.home_reset)) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    val shown = tabs.filter { it != TabId.HOME }
    // «Bloccate» (Contatti e Officina senza login): si vedono al loro posto, in grigio, e al tocco avvisano che serve il login.
    val all = if (editing) TabPrefs.order.filter { it != TabId.HOME && (it in shown || it in hiddenTabs || it in lockedTabs) }  // nascoste al loro posto, in grigio
              else (shown + lockedTabs).sortedBy { TabPrefs.order.indexOf(it) }
    val start = TabPrefs.startTab
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // Altezza dei pulsanti adattata allo schermo: tutte le schede stanno in una pagina (min 56dp, max 96dp; sotto il minimo si scorre).
    val rows = (all.size + 1) / 2
    val reserved = 12.dp + 8.dp + (if (editing) 30.dp + 10.dp + 100.dp else 0.dp)
    val tileH = ((maxHeight - reserved - 10.dp * (rows - 1).coerceAtLeast(0)) / rows.coerceAtLeast(1)).coerceIn(56.dp, 96.dp)
    val iconSize = (tileH * 0.38f).coerceIn(24.dp, 36.dp)
    val labelSize = if (tileH < 72.dp) 13.sp else 16.sp
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // In modifica: suggerimento e «Fine». Fuori dalla modifica nessuna riga: la matita è un'icona sfumata in sovrimpressione.
        if (editing) {
            Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_edit_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { editing = false }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), modifier = Modifier.height(30.dp)) {
                    Text(stringResource(R.string.home_done), fontWeight = FontWeight.Bold)
                }
            }
        }
        all.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    val hidden = t in hiddenTabs
                    val locked = t in lockedTabs
                    Box(
                        Modifier.weight(1f).height(tileH).alpha(if (hidden || locked) 0.45f else 1f)
                            .onGloballyPositioned { bounds[t] = it.boundsInRoot() }
                            .zIndex(if (dragging == t) 1f else 0f)
                            .graphicsLayer {
                                if (dragging == t) { translationX = dragOff.x; translationY = dragOff.y; scaleX = 1.06f; scaleY = 1.06f; alpha = 0.9f }
                            }
                            .then(
                                if (editing && t != TabId.EMERGENCY) Modifier.pointerInput(t) {
                                    val reset = { dragging = null; dragOff = Offset.Zero }
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { dragging = t; dragOff = Offset.Zero },
                                        onDrag = { ch, d -> ch.consume(); dragOff += d },
                                        onDragEnd = {
                                            val c = bounds[t]?.center?.plus(dragOff)
                                            val target = c?.let { p -> bounds.entries.firstOrNull { it.key != t && it.value.contains(p) }?.key }
                                            if (target != null) TabPrefs.moveTo(ctx, t, target)
                                            reset()
                                        },
                                        onDragCancel = { reset() },
                                    )
                                } else Modifier
                            ),
                    ) {
                        Column(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
                                .combinedClickable(enabled = !editing, onClick = {
                                    if (locked) android.widget.Toast.makeText(ctx, ctx.getString(R.string.home_login_required), android.widget.Toast.LENGTH_SHORT).show() else onOpen(t)
                                }, onLongClick = { editing = true }).padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            TabIcon(t, iconSize, MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(if (t == TabId.EMERGENCY) stringResource(R.string.home_emergency_label) else t.label, fontSize = labelSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
                        }
                        if (editing && t != TabId.EMERGENCY) {
                            IconButton(onClick = { TabPrefs.setVisible(ctx, t, hidden) }, modifier = Modifier.align(Alignment.TopStart).size(36.dp)) {
                                Icon(if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, stringResource(R.string.home_toggle_visible), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        if (editing && t != TabId.EMERGENCY) {
                            val idx = all.indexOf(t)
                            val prev = all.getOrNull(idx - 1); val next = all.getOrNull(idx + 1)?.takeIf { it != TabId.EMERGENCY }
                            IconButton(onClick = { prev?.let { TabPrefs.swap(ctx, t, it) } }, enabled = prev != null, modifier = Modifier.align(Alignment.BottomStart).size(32.dp)) {
                                Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.home_move_back), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            IconButton(onClick = { next?.let { TabPrefs.swap(ctx, t, it) } }, enabled = next != null, modifier = Modifier.align(Alignment.BottomEnd).size(32.dp)) {
                                Icon(Icons.Outlined.ChevronRight, stringResource(R.string.home_move_forward), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        if (editing && !hidden) {
                            IconButton(onClick = { TabPrefs.setStart(ctx, if (start == t) TabId.HOME else t) }, modifier = Modifier.align(Alignment.TopEnd).size(36.dp)) {
                                Icon(if (start == t) Icons.Filled.Star else Icons.Outlined.StarBorder, stringResource(R.string.home_set_start), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (editing) {
            val homeStart = start == TabId.HOME
            OutlinedButton(onClick = { TabPrefs.setStart(ctx, TabId.HOME) }, enabled = !homeStart, modifier = Modifier.fillMaxWidth()) {
                Icon(if (homeStart) Icons.Filled.Star else Icons.Outlined.StarBorder, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_start_home))
            }
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_reset))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (!editing) {
        IconButton(onClick = { editing = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 2.dp).size(40.dp).alpha(0.5f)) {
            Icon(Icons.Outlined.Edit, stringResource(R.string.home_edit), Modifier.size(22.dp))
        }
    }
    }
}

/** Riga delle Preferenze: scelta della scheda su cui si apre il calendario (menu a tendina). */
@Composable
fun SuggestedTabRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.pref_suggested_tab), modifier = Modifier.weight(1f))
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TabIcon(TabPrefs.startTab, 20.dp, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(TabPrefs.startTab.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                TabId.entries.filter { it !in TabPrefs.hidden }.forEach { t ->
                    DropdownMenuItem(text = { TabMenuLabel(t) }, onClick = { TabPrefs.setStart(ctx, t); open = false })
                }
            }
        }
    }
}
