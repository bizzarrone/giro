package simplylucaapps.girocalendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.data.remote.firestore.Trail
import simplylucaapps.girocalendar.data.remote.firestore.TrailStatus
import simplylucaapps.girocalendar.data.remote.firestore.toTrail
import simplylucaapps.girocalendar.ui.common.DifficultyBadge

/** Editor: interruttore «Mostra lo stato dei trail» + scelta della zona tra quelle esistenti. */
@Composable
internal fun TrailZoneField(on: Boolean, onOn: (Boolean) -> Unit, zone: String, onZone: (String) -> Unit) {
    var zones by remember { mutableStateOf<List<String>?>(null) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(on) {
        if (on && zones == null) zones = runCatching {
            FirebaseFirestore.getInstance().collection("trails").get().await()
                .documents.mapNotNull { it.getString("zone")?.takeIf { z -> z.isNotBlank() } }.distinct().sortedBy { it.lowercase() }
        }.getOrDefault(emptyList())
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { onOn(!on) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(on, onOn)
            Text(stringResource(R.string.ev_trail_zone_toggle))
        }
        if (on) {
            Box(Modifier.padding(start = 12.dp)) {
                OutlinedButton(onClick = { menu = true }, enabled = zones != null) {
                    Text(zone.ifBlank { stringResource(R.string.ev_trail_zone_pick) })
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    zones.orEmpty().forEach { z -> DropdownMenuItem(text = { Text(z) }, onClick = { onZone(z); menu = false }) }
                }
            }
        }
    }
}

/** Scheda evento: stato attuale dei trail della zona (caricato al momento, sempre aggiornato). */
@Composable
internal fun TrailZoneStatus(zone: String) {
    var trails by remember(zone) { mutableStateOf<List<Trail>?>(null) }
    var open by remember(zone) { mutableStateOf(false) }
    LaunchedEffect(zone) {
        trails = runCatching {
            FirebaseFirestore.getInstance().collection("trails").whereEqualTo("zone", zone).get().await()
                .documents.map { it.toTrail() }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }
    val list = trails
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ev_trail_zone_title, zone), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (list != null) TrailStatus.entries.forEach { st ->
                val n = list.count { it.status == st }
                if (n > 0) {
                    Dot(st); Text("$n", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 3.dp, end = 8.dp))
                }
            }
            Text(if (open) "▲" else "▼", style = MaterialTheme.typography.labelMedium)
        }
        when {
            list == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            list.isEmpty() -> Text(stringResource(R.string.ev_trail_zone_none), style = MaterialTheme.typography.bodySmall)
            open -> list.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(t.status)
                    Text(t.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    if (t.difficulty.isNotBlank()) { DifficultyBadge(t.difficulty); Spacer(Modifier.width(8.dp)) }
                    Text(t.status.label, style = MaterialTheme.typography.labelMedium, color = t.status.color)
                }
            }
        }
    }
}

@Composable
private fun Dot(st: TrailStatus) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(st.color))
}
