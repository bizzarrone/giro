package simplylucaapps.girocalendar.ui.calendar

import simplylucaapps.girocalendar.ui.common.CompactTextField
import simplylucaapps.girocalendar.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import simplylucaapps.girocalendar.ui.common.CalendarLogo
import simplylucaapps.girocalendar.ui.common.parseHexColor
import simplylucaapps.girocalendar.util.LogoImageProcessor

private val LOGO_PALETTE = listOf(
    "#27AE60", "#2980B9", "#E74C3C", "#F2994A", "#9B59B6",
    "#16A085", "#D35400", "#2C3E50", "#C0392B", "#8E44AD",
)

/** Dialog owner-only per modificare nome/descrizione/visibilità/logo del calendario. */
@Composable
fun CalendarEditDialog(
    code: String,
    name: String,
    description: String,
    listed: Boolean,
    logoColor: String,
    logoData: String,
    /** Contatore aperture (statistica approssimativa), mostrato in sola lettura all'owner. */
    openCount: Long = 0L,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit,
    onSaveLogo: (String, String) -> Unit,
    /** null = non mostrare il pulsante (usato dal dialog "Info calendario" del calendario
     * aperto, che ha già una voce dedicata "Collaboratori" nel menu). */
    onManageEditors: (() -> Unit)? = null,
    /** null = nessun pulsante di eliminazione. Chiamato dopo la conferma. */
    onDelete: (() -> Unit)? = null,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var n by remember { mutableStateOf(name) }
    var d by remember { mutableStateOf(description) }
    var pub by remember { mutableStateOf(listed) }
    var previewData by remember { mutableStateOf(logoData) }
    var previewColor by remember { mutableStateOf(logoColor) }
    var uploading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            val data = LogoImageProcessor.compressToBase64(context, uri)
            uploading = false
            if (data != null) {
                previewData = data
                onSaveLogo(previewColor.ifBlank { "#27AE60" }, data)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.s_info_cal)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CalendarLogo(code, previewColor, previewData, size = 48.dp)
                    OutlinedButton(onClick = { pickImage.launch("image/*") }, enabled = !uploading) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (uploading) R.string.cc_uploading else R.string.i_change_logo))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LOGO_PALETTE.forEach { hex ->
                        val selected = previewColor.equals(hex, ignoreCase = true)
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(hex) ?: Color.Gray)
                                .then(
                                    if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable {
                                    val next = if (selected) "" else hex
                                    previewColor = next
                                    onSaveLogo(next, previewData)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                CompactTextField(value = n, onValueChange = { n = it },
                    label = { Text(stringResource(R.string.i_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CompactTextField(value = d, onValueChange = { d = it },
                    label = { Text(stringResource(R.string.i_desc)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(if (pub) R.string.cc_public else R.string.cc_private),
                            style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(if (pub) R.string.i_public_hint else R.string.cc_private_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = pub, onCheckedChange = { pub = it })
                }
                if (openCount > 0) {
                    Text(
                        stringResource(R.string.i_opened, openCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onManageEditors != null) {
                    OutlinedButton(onClick = onManageEditors, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.s_manage_collab))
                    }
                }
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.calendar_delete)) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = n.isNotBlank(), onClick = { onSave(n.trim(), d.trim(), pub) }) { Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.action_cancel)) } },
    )
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.calendar_delete_confirm_title, name.ifBlank { code })) },
            text = { Text(stringResource(R.string.calendar_delete_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
