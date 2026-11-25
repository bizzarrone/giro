package simplylucaapps.girocalendar.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.data.remote.firestore.RideCalendar
import simplylucaapps.girocalendar.data.remote.firestore.RideEvent

/** «Collega un evento di un altro calendario»: si inserisce il codice, si sceglie l'evento (le uscite non ancora passate). */
@Composable
fun LinkEventDialog(
    currentCode: String,
    alreadyLinked: Set<String>,
    findCalendars: (String, (List<RideCalendar>) -> Unit) -> Unit,
    loadEvents: (String, (List<RideEvent>?) -> Unit) -> Unit,
    onPick: (srcCode: String, event: RideEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    var cals by remember { mutableStateOf<List<RideCalendar>?>(null) }
    var sel by remember { mutableStateOf<RideCalendar?>(null) }
    var list by remember { mutableStateOf<List<RideEvent>?>(null) }
    var loading by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    fun open(c: RideCalendar) {
        sel = c; list = null; loading = true
        loadEvents(c.code) { r -> loading = false; list = r?.filter { !simplylucaapps.girocalendar.util.EventTime.isPast(it, now) } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lk_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sel == null) Text(stringResource(R.string.lk_hint), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(q, { q = it.take(40) }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.lk_code)) })
                    Button(onClick = {
                        if (q.isBlank() || loading) return@Button
                        loading = true; sel = null; list = null; cals = null
                        findCalendars(q) { r -> loading = false; cals = r; if (r.size == 1) open(r[0]) }
                    }, enabled = q.isNotBlank() && !loading) { Text(stringResource(R.string.lk_search)) }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                val s = sel
                if (s != null) {
                    Text(s.name.ifBlank { s.code }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    when {
                        loading -> {}
                        list == null -> Text(stringResource(R.string.lk_not_found), color = MaterialTheme.colorScheme.error)
                        list!!.isEmpty() -> Text(stringResource(R.string.lk_none))
                        else -> LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(list!!, key = { it.id }) { e ->
                                val linked = "L:${s.code}:${e.id}" in alreadyLinked
                                Column(Modifier.fillMaxWidth().clickable(enabled = !linked) { onPick(s.code, e) }.padding(vertical = 8.dp)) {
                                    Text(e.titolo.ifBlank { stringResource(R.string.default_event_title) }, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    Text(listOf(e.data, e.ora).filter { it.isNotBlank() }.joinToString(" · ") + if (linked) " · ✔ " + stringResource(R.string.lk_already) else "",
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                } else if (!loading && cals != null) {
                    if (cals!!.isEmpty()) Text(stringResource(R.string.lk_not_found), color = MaterialTheme.colorScheme.error)
                    else LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(cals!!, key = { it.code }) { c ->
                            Column(Modifier.fillMaxWidth().clickable { open(c) }.padding(vertical = 8.dp)) {
                                Text(c.name.ifBlank { c.code }, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(c.code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
