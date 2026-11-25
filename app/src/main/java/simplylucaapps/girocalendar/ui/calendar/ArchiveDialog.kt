package simplylucaapps.girocalendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.data.remote.firestore.RideEvent
import simplylucaapps.girocalendar.ui.common.DifficultyBadge

/**
 * Archivio degli eventi tolti dalla vista normale (owner/editor): ognuno può essere
 * "ripescato" (riapre l'editor precompilato per ricrearlo come nuovo evento con una data
 * aggiornata) o eliminato definitivamente. Pattern gemello di [ProposalsDialog].
 */
@Composable
fun ArchiveDialog(
    archivedEvents: List<RideEvent>,
    onDismiss: () -> Unit,
    onRepesca: (RideEvent) -> Unit,
    onDelete: (RideEvent) -> Unit,
    onAddToEpics: (RideEvent) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
                Text(
                    stringResource(R.string.archive_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (archivedEvents.isEmpty()) stringResource(R.string.archive_empty)
                    else if (archivedEvents.size == 1) stringResource(R.string.archive_count_one)
                    else stringResource(R.string.archive_count_many, archivedEvents.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    items(archivedEvents, key = { it.id }) { e ->
                        Box(Modifier.padding(vertical = 6.dp)) {
                            ArchivedEventCard(
                                e,
                                onRepesca = { onRepesca(e) },
                                onDelete = { onDelete(e) },
                                onAddToEpics = { onAddToEpics(e) },
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                }
            }
        }
    }
}

@Composable
private fun ArchivedEventCard(e: RideEvent, onRepesca: () -> Unit, onDelete: () -> Unit, onAddToEpics: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                e.titolo.ifBlank { stringResource(R.string.default_event_title) }, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            if (e.difficolta.isNotBlank()) DifficultyBadge(e.difficolta)
            IconButton(onClick = onAddToEpics, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Star, stringResource(R.string.action_add_to_epics), modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        val subtitle = buildString {
            if (e.data.isNotBlank()) append(e.data)
            if (e.ora.isNotBlank()) { if (isNotEmpty()) append(" · "); append(e.ora) }
            if (e.localita.isNotBlank()) { if (isNotEmpty()) append(" · "); append(e.localita) }
        }
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.labelMedium)
            }
            Button(onClick = onRepesca, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.Unarchive, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.action_repesca), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
