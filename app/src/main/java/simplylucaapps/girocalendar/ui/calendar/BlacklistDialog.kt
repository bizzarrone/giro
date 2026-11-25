package simplylucaapps.girocalendar.ui.calendar

import simplylucaapps.girocalendar.ui.common.CompactTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.data.remote.firestore.BlockedUser

/**
 * Lista nera del calendario (owner e collaboratori). Due livelli per account: "edit" = non può più
 * modificare né inviare proposte; "ban" = non può più accedere al calendario.
 * Limite: vale per l'account (login), non per chi usa il calendario senza accedere.
 */
@Composable
fun BlacklistDialog(
    blocks: List<BlockedUser>,
    onDismiss: () -> Unit,
    onAdd: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("edit") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val failMsg = stringResource(R.string.e_add_failed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bl_title), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.bl_intro), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (blocks.isEmpty()) Text(stringResource(R.string.bl_empty), style = MaterialTheme.typography.bodySmall)
                blocks.forEach { b ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.label.ifBlank { b.uid.take(6) + "…" }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(if (b.level == "ban") R.string.bl_level_ban else R.string.bl_level_edit),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { onRemove(b.uid) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, stringResource(R.string.tr_remove), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    FilterChip(selected = level == "edit", onClick = { level = "edit" }, label = { Text(stringResource(R.string.bl_level_edit), style = MaterialTheme.typography.labelSmall) })
                    FilterChip(selected = level == "ban", onClick = { level = "ban" }, label = { Text(stringResource(R.string.bl_level_ban), style = MaterialTheme.typography.labelSmall) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompactTextField(
                        value = input, onValueChange = { input = it.trim(); error = null },
                        placeholder = { Text(stringResource(R.string.e_input_label), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        textStyle = MaterialTheme.typography.bodySmall, singleLine = true, enabled = !busy, isError = error != null,
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    )
                    FilledTonalIconButton(
                        onClick = {
                            busy = true; error = null
                            onAdd(input, level) { ok, msg -> busy = false; if (ok) input = "" else error = msg ?: failMsg }
                        },
                        enabled = !busy && input.isNotBlank(),
                        modifier = Modifier.padding(start = 6.dp).size(40.dp),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Add, stringResource(R.string.e_add))
                    }
                }
                error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
