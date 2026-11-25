package simplylucaapps.girocalendar.ui.nav

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.data.remote.firestore.RideEvent
import simplylucaapps.girocalendar.ui.gpx.GPt
import simplylucaapps.girocalendar.ui.gpx.GpxModel
import simplylucaapps.girocalendar.util.EventTime
import simplylucaapps.girocalendar.util.GpxProcessor

private class FolderGpx(val name: String, val uri: Uri, val size: Long, val modified: Long)

private const val PREFS = "girocalendar_boot"
private const val KEY_FOLDER = "nav_folder"

private fun readFolder(ctx: Context, tree: Uri): List<FolderGpx> {
    val out = ArrayList<FolderGpx>()
    runCatching {
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        ctx.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ), null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (!name.endsWith(".gpx", ignoreCase = true)) continue
                out.add(FolderGpx(name, DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)), c.getLong(2), c.getLong(3)))
            }
        }
    }
    return out.sortedByDescending { it.modified }
}

private fun readRecordings(ctx: Context): List<FolderGpx> {
    if (android.os.Build.VERSION.SDK_INT < 29) return emptyList()
    val out = ArrayList<FolderGpx>()
    runCatching {
        ctx.contentResolver.query(
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                android.provider.MediaStore.MediaColumns.SIZE, android.provider.MediaStore.MediaColumns.DATE_MODIFIED),
            "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("Download/Giro/%"), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (!name.endsWith(".gpx", true)) continue
                out.add(FolderGpx(name, android.content.ContentUris.withAppendedId(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0)), c.getLong(2), c.getLong(3)))
            }
        }
    }
    return out.sortedByDescending { it.modified }
}

private fun fmtSize(b: Long): String = if (b >= 1_048_576) "%.1f MB".format(b / 1_048_576.0) else "${(b + 512) / 1024} KB"

/**
 * Scelta del percorso da navigare: GPX delle schede del calendario in uso (scarica dal documento e apre),
 * GPX di una cartella locale scelta una volta sola, oppure un singolo file. Tutto locale: niente archivio online.
 */
@Composable
fun NavPickerDialog(events: List<RideEvent>, onPick: (name: String, pts: List<GPt>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var folder by remember { mutableStateOf(prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)) }
    var files by remember { mutableStateOf<List<FolderGpx>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var recs by remember { mutableStateOf<List<FolderGpx>>(emptyList()) }
    LaunchedEffect(Unit) { recs = withContext(Dispatchers.IO) { readRecordings(context) } }

    val withGpx = remember(events) {
        val now = System.currentTimeMillis()
        val ok = events.filter { it.gpxData.isNotEmpty() && !it.annullata }
        val (past, up) = ok.partition { EventTime.isPast(it, now) }
        up.sortedBy { EventTime.startMillis(it) ?: Long.MAX_VALUE } + past.sortedByDescending { EventTime.startMillis(it) ?: 0L }
    }

    LaunchedEffect(folder) {
        files = folder?.let { f -> withContext(Dispatchers.IO) { readFolder(context, f) } } ?: emptyList()
    }

    fun load(name: String, open: suspend () -> List<GPt>?) {
        if (busy) return
        busy = true
        scope.launch {
            val pts = withContext(Dispatchers.IO) { runCatching { open() }.getOrNull() }
            busy = false
            if (pts == null || pts.size < 2) Toast.makeText(context, context.getString(R.string.nv_bad_gpx), Toast.LENGTH_LONG).show()
            else onPick(name, pts)
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            prefs.edit().putString(KEY_FOLDER, uri.toString()).apply()
            folder = uri
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) load("") {
            context.contentResolver.openInputStream(uri)!!.use { GpxModel.parse(it, "") }.flatMap { it.pts }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nv_pick_title), fontWeight = FontWeight.Bold) },
        text = {
          Column {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 400.dp)) {
                item { Header(stringResource(R.string.nv_pick_events)) }
                if (withGpx.isEmpty()) item { Hint(stringResource(R.string.nv_pick_none_events)) }
                items(withGpx, key = { "e" + it.id }) { e ->
                    Row2(e.titulo(), listOf(e.data, e.gpxFileName).filter { it.isNotBlank() }.joinToString(" · ")) {
                        load(e.titolo) {
                            GpxProcessor.decompress(e.gpxData)?.let { xml ->
                                GpxModel.parse(xml.byteInputStream(), e.titolo).flatMap { it.pts }
                            }
                        }
                    }
                }
                if (recs.isNotEmpty()) {
                    item { Header(stringResource(R.string.nv_pick_recs)) }
                    items(recs, key = { "r" + it.uri }) { f ->
                        Row2(f.name.removeSuffix(".gpx").removeSuffix(".GPX"), fmtSize(f.size)) {
                            load(f.name) { context.contentResolver.openInputStream(f.uri)!!.use { GpxModel.parse(it, "") }.flatMap { it.pts } }
                        }
                    }
                }
                item { Header(stringResource(R.string.nv_pick_folder)) }
                if (folder == null) item { Hint(stringResource(R.string.nv_pick_none_folder)) }
                else if (files.isEmpty()) item { Hint(stringResource(R.string.nv_pick_empty_folder)) }
                items(files, key = { "f" + it.uri }) { f ->
                    Row2(f.name.removeSuffix(".gpx").removeSuffix(".GPX"), fmtSize(f.size)) {
                        load(f.name) {
                            context.contentResolver.openInputStream(f.uri)!!.use { GpxModel.parse(it, "") }.flatMap { it.pts }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // stessa altezza e testo su una riga: prima «Scegli cartella» andava a capo e i due pulsanti risultavano diversi
                OutlinedButton(onClick = { pickFolder.launch(null) }, Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(stringResource(if (folder == null) R.string.nv_pick_folder_set else R.string.nv_pick_folder_change), fontSize = 13.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = { pickFile.launch(arrayOf("*/*")) }, Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(stringResource(R.string.nv_pick_file), fontSize = 13.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
          }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

private fun RideEvent.titulo(): String = titolo.ifBlank { "—" }

@Composable
private fun Header(t: String) {
    Text(t, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

@Composable
private fun Hint(t: String) {
    Text(t, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun Row2(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    HorizontalDivider()
}
