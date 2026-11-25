package simplylucaapps.girocalendar.ui.calendar

import simplylucaapps.girocalendar.ui.common.CompactTextField
import androidx.compose.ui.res.stringResource

import simplylucaapps.girocalendar.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun EditorsDialog(
    editors: List<String>,
    editorLabels: Map<String, String>,
    ownerUid: String,
    onDismiss: () -> Unit,
    onAdd: (String, (Boolean, String?) -> Unit) -> Unit,
    onRemove: (String) -> Unit,
    onOpen: () -> Unit = {},
) {
    LaunchedEffect(Unit) { onOpen() }
    var newInput by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var justAddedInput by remember { mutableStateOf<String?>(null) }
    var addError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Il collaboratore appena aggiunto per email non è ancora nella lista finché non arriva
    // l'aggiornamento Firestore: lo individuiamo per label per evidenziarlo appena compare.
    val justAddedUid = justAddedInput?.let { input ->
        editors.firstOrNull { editorLabels[it]?.equals(input, ignoreCase = true) == true || it == input }
    }

    val msgAddFail = stringResource(R.string.e_add_failed)
    val small = MaterialTheme.typography.bodySmall
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.s_collaborators), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.e_intro_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (editors.size == 1) stringResource(R.string.e_count_one) else stringResource(R.string.e_count_many, editors.size),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                editors.forEach { uid ->
                    val label = editorLabels[uid]
                    val isJustAdded = uid == justAddedUid
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 30.dp)
                            .then(if (isJustAdded) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)) else Modifier),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            when {
                                uid == ownerUid -> stringResource(R.string.e_you_admin)
                                label != null -> label
                                else -> shortUid(uid)
                            },
                            style = small, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (isJustAdded) {
                            Icon(Icons.Default.Check, stringResource(R.string.e_just_added),
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { copyToClipboard(context, uid) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.ContentCopy, stringResource(R.string.s_copy), modifier = Modifier.size(15.dp))
                        }
                        if (uid != ownerUid) {
                            IconButton(onClick = { onRemove(uid) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, stringResource(R.string.tr_remove),
                                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    CompactTextField(
                        value = newInput,
                        onValueChange = { newInput = it.trim(); addError = null },
                        placeholder = { Text(stringResource(R.string.e_input_label), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true,
                        enabled = !adding,
                        isError = addError != null,
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    )
                    FilledTonalIconButton(
                        onClick = {
                            val input = newInput
                            adding = true
                            addError = null
                            onAdd(input) { ok, msg ->
                                adding = false
                                if (ok) { justAddedInput = input; newInput = "" }
                                else addError = msg ?: msgAddFail
                            }
                        },
                        enabled = !adding && newInput.isNotBlank() && newInput !in editors,
                        modifier = Modifier.padding(start = 6.dp).size(40.dp),
                    ) {
                        if (adding) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Add, stringResource(R.string.e_add))
                    }
                }
                addError?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        modifier = Modifier.widthIn(max = 360.dp),
    )
}

private fun shortUid(uid: String) = if (uid.length > 10) uid.take(6) + "…" + uid.takeLast(4) else uid

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("uid", text))
}
