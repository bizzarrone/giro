package simplylucaapps.girocalendar.ui.trails

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import simplylucaapps.girocalendar.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Spazio personale online (Firestore `users/{uid}/rides/{id}`, privato, niente Firebase Storage): gli ultimi
 * [MAX_REC] giri registrati dall'app + fino a [MAX_UP] file GPX caricati a mano (da qui o dal sito web, anche
 * con drag & drop). Ogni file è al massimo [MAX_FILE_BYTES] (2 MB di XML: compresso in gzip sta ampiamente nel
 * limite di 1 MiB del documento) e resta online [DAYS] giorni.
 */
object MyStorage {
    const val DAYS = 30
    const val MAX_REC = 10
    const val MAX_UP = 10
    const val MAX_FILE_BYTES = 2 * 1024 * 1024
    const val MAX_B64 = 800_000            // limite documento Firestore 1 MiB
    const val KIND_REC = "rec"
    const val KIND_UP = "upload"

    class Item(
        val id: String, val kind: String, val name: String, val atMillis: Long, val expMs: Long,
        val distM: Double, val movingMs: Long, val gpxZ: String,
    )

    enum class UploadResult { OK, FULL, TOO_LARGE, NOT_GPX, ERROR }

    fun col(uid: String) = FirebaseFirestore.getInstance().collection("users").document(uid).collection("rides")

    private fun uid() = FirebaseAuth.getInstance().currentUser?.takeUnless { it.isAnonymous }?.uid

    private fun com.google.firebase.firestore.DocumentSnapshot.toItem(): Item {
        val at = getLong("atMillis") ?: 0L
        val exp = getTimestamp("expiresAt")?.toDate()?.time ?: (at + DAYS * 86_400_000L)
        // il vecchio documento "last" (prima versione) è un giro registrato come gli altri
        val kind = getString("kind") ?: KIND_REC
        return Item(id, kind, getString("name").orEmpty(), at, exp, getDouble("distM") ?: 0.0, getLong("movingMs") ?: 0L, getString("gpxZ").orEmpty())
    }

    /** Cancella i file scaduti e quelli oltre il massimo (i più vecchi); restituisce quelli rimasti, più recenti prima. */
    suspend fun prune(uid: String): List<Item> {
        val now = System.currentTimeMillis()
        val all = col(uid).get().await().documents.map { it.toItem() }
        val keep = ArrayList<Item>()
        for (kind in listOf(KIND_REC, KIND_UP)) {
            val max = if (kind == KIND_REC) MAX_REC else MAX_UP
            val mine = all.filter { it.kind == kind }.sortedByDescending { it.atMillis }
            mine.forEachIndexed { i, it ->
                if (it.expMs < now || i >= max) col(uid).document(it.id).delete().await() else keep.add(it)
            }
        }
        return keep.sortedByDescending { it.atMillis }
    }

    suspend fun list(): List<Item> = uid()?.let { prune(it) } ?: emptyList()

    suspend fun delete(id: String) { uid()?.let { col(it).document(id).delete().await() } }

    /** Carica un GPX scelto dall'utente: max [MAX_FILE_BYTES] e max [MAX_UP] file caricati. */
    suspend fun upload(ctx: Context, uri: Uri): UploadResult = withContext(Dispatchers.IO) {
        val uid = uid() ?: return@withContext UploadResult.ERROR
        runCatching {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching UploadResult.ERROR
            if (bytes.size > MAX_FILE_BYTES) return@runCatching UploadResult.TOO_LARGE
            val head = String(bytes, 0, minOf(bytes.size, 4000), Charsets.UTF_8)
            if (!head.contains("<gpx", true)) return@runCatching UploadResult.NOT_GPX
            if (prune(uid).count { it.kind == KIND_UP } >= MAX_UP) return@runCatching UploadResult.FULL
            val z = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(bytes) } }.toByteArray()
            val b64 = Base64.encodeToString(z, Base64.NO_WRAP)
            if (b64.length > MAX_B64) return@runCatching UploadResult.TOO_LARGE
            val display = ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "GPX"
            val now = System.currentTimeMillis()
            col(uid).document("up_$now").set(mapOf(
                "kind" to KIND_UP, "name" to display.removeSuffix(".gpx").removeSuffix(".GPX"), "gpxZ" to b64,
                "atMillis" to now, "expiresAt" to Timestamp(Date(now + DAYS * 86_400_000L)),
            )).await()
            UploadResult.OK
        }.getOrElse { UploadResult.ERROR }
    }

    fun xml(item: Item): String? = runCatching {
        GZIPInputStream(Base64.decode(item.gpxZ, Base64.DEFAULT).inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
    }.getOrNull()

    fun safeName(n: String) = n.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "giro" }
}

/** Dialogo «Il mio spazio»: stesso contenuto della scheda «I miei giri», in un popup. */
@Composable
fun MyStorageDialog(onDismiss: () -> Unit, onOpen: ((name: String, gpx: ByteArray) -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ms_title)) },
        text = { MyStorageContent(onDismiss, onOpen) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** «Il mio spazio»: elenco dei giri registrati e dei file caricati, con salva / condividi / elimina e «Carica un GPX». Usato dalla scheda «I miei giri» e dal popup. */
@Composable
fun MyStorageContent(onDismiss: () -> Unit, onOpen: ((name: String, gpx: ByteArray) -> Unit)? = null, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<MyStorage.Item>?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var toSave by remember { mutableStateOf<MyStorage.Item?>(null) }
    val errRead = stringResource(R.string.ms_err_read)
    val errAct = stringResource(R.string.ms_err)
    val okUp = stringResource(R.string.ms_uploaded, MyStorage.DAYS)
    val tooLarge = stringResource(R.string.ms_toolarge)
    val notGpx = stringResource(R.string.ms_notgpx)
    val full = stringResource(R.string.ms_full, MyStorage.MAX_UP)
    val saved = stringResource(R.string.gpx_saved)

    fun reload() = scope.launch {
        items = runCatching { MyStorage.list() }.getOrElse { msg = errRead; emptyList() }
    }
    LaunchedEffect(Unit) { reload() }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            msg = when (MyStorage.upload(ctx, uri)) {
                MyStorage.UploadResult.OK -> okUp
                MyStorage.UploadResult.FULL -> full
                MyStorage.UploadResult.TOO_LARGE -> tooLarge
                MyStorage.UploadResult.NOT_GPX -> notGpx
                MyStorage.UploadResult.ERROR -> errAct
            }
            reload().join(); busy = false
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        val it = toSave
        if (uri != null && it != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { o -> o.write((MyStorage.xml(it) ?: error("xml")).toByteArray()) }
        }.onSuccess { msg = saved }.onFailure { msg = errAct }
    }

    fun share(it: MyStorage.Item) = runCatching {
        val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
        val f = File(dir, MyStorage.safeName(it.name) + ".gpx")
        f.writeText(MyStorage.xml(it) ?: error("xml"))
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { msg = errAct }

    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val dtf = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    @Composable
    fun section(title: String, list: List<MyStorage.Item>) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
        if (list.isEmpty()) Text(stringResource(R.string.ms_empty), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        list.forEach { it ->
            // scheda compatta: nome e data su due righe a sinistra, azioni a destra sulla stessa riga
            OutlinedCard(Modifier.fillMaxWidth().padding(top = 6.dp), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
              val openIt: () -> Unit = { val x = MyStorage.xml(it); if (x == null) msg = errAct else { onDismiss(); onOpen?.invoke(it.name.ifBlank { "GPX" }, x.toByteArray()) } }
              Row(Modifier.fillMaxWidth().clickable(enabled = onOpen != null, onClick = openIt).padding(start = 12.dp, top = 4.dp, end = 2.dp, bottom = 4.dp),
                  verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(it.name.ifBlank { "GPX" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    val km = if (it.distM > 0) " · %.1f km".format(it.distM / 1000) else ""
                    Text(dtf.format(Date(it.atMillis)) + km + " · " + stringResource(R.string.ms_expires, df.format(Date(it.expMs))),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                IconButton(onClick = { toSave = it; saveLauncher.launch(MyStorage.safeName(it.name) + ".gpx") }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Save, stringResource(R.string.ms_save), Modifier.size(20.dp)) }
                IconButton(onClick = { share(it) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Share, stringResource(R.string.ms_share), Modifier.size(20.dp)) }
                IconButton(onClick = { scope.launch { runCatching { MyStorage.delete(it.id) }.onFailure { msg = errAct }; reload() } }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, stringResource(R.string.ms_delete), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                }
              }
            }
        }
    }

    Column(modifier.verticalScroll(rememberScrollState())) {
                val l = items
                if (l == null) Text(stringResource(R.string.ms_loading), Modifier.padding(top = 12.dp))
                else {
                    val rec = l.filter { it.kind == MyStorage.KIND_REC }
                    val up = l.filter { it.kind == MyStorage.KIND_UP }
                    section(stringResource(R.string.ms_rec, rec.size, MyStorage.MAX_REC), rec)
                    section(stringResource(R.string.ms_up, up.size, MyStorage.MAX_UP), up)
                    Button(enabled = !busy, onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.padding(top = 14.dp)) { Text(stringResource(R.string.ms_upload)) }
                }
                msg?.let { Text(it, Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
                Text(stringResource(R.string.ms_intro, MyStorage.DAYS), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 18.dp))
                if (onOpen != null) Text(stringResource(R.string.ms_tap_open), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
    }
}
