package simplylucaapps.girocalendar.ui.trails

import android.content.Context
import android.util.Base64
import android.widget.Toast
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Giri registrati salvati online nello spazio personale (users/{uid}/rides/rec_<ms>, privato): GPX gzip+base64 così
 * si possono scaricare dalla web app. Se ne tengono gli ultimi [MyStorage.MAX_REC] e ognuno scade dopo
 * [MyStorage.DAYS] giorni (`expiresAt`, anche per un'eventuale policy TTL di Firestore; in più la pulizia lato
 * client all'apertura dell'app e della web app). Vedi [MyStorage] per l'elenco e i file caricati a mano.
 */
object LastRide {
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun pendingFile(ctx: Context) = File(ctx.filesDir, "lastride_pending.txt")

    private fun gz(points: List<RecPoint>, name: String): String {
        var pts = points
        while (true) {
            val out = ByteArrayOutputStream()
            GZIPOutputStream(out).use { it.write(TrackRecorder.toGpx(pts, name).toByteArray()) }
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (b64.length <= MyStorage.MAX_B64 || pts.size < 200) return b64
            // troppo grande: toglie un punto su tre mantenendo l'ultimo
            pts = pts.filterIndexed { i, _ -> i % 3 != 1 || i == pts.size - 1 }
        }
    }

    /** Chiamata a fine registrazione. Se non si è loggati o si è offline, ritenta alla prossima apertura. */
    fun save(ctx: Context, name: String, points: List<RecPoint>, distM: Double, movingMs: Long) {
        val app = ctx.applicationContext
        scope.launch {
            runCatching {
                val data = mapOf(
                    "name" to name, "gpxZ" to gz(points, name), "startMs" to points.first().t, "atMillis" to System.currentTimeMillis(),
                    "distM" to distM, "movingMs" to movingMs, "points" to points.size,
                )
                pendingFile(app).writeText(org.json.JSONObject(data).toString())
                push(app, true)
            }
        }
    }

    private suspend fun push(app: Context, toast: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.takeUnless { it.isAnonymous }?.uid ?: return
        val f = pendingFile(app); if (!f.exists()) return
        val j = org.json.JSONObject(f.readText())
        val at = j.getLong("atMillis")
        val data = mapOf(
            "kind" to MyStorage.KIND_REC,
            "name" to j.getString("name"), "gpxZ" to j.getString("gpxZ"), "startMs" to j.getLong("startMs"), "atMillis" to at,
            "distM" to j.getDouble("distM"), "movingMs" to j.getLong("movingMs"), "points" to j.getInt("points"),
            "expiresAt" to Timestamp(java.util.Date(at + MyStorage.DAYS * 86_400_000L)),
        )
        MyStorage.col(uid).document("rec_$at").set(data).await()
        f.delete()
        runCatching { MyStorage.prune(uid) }
        if (toast) android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(app, app.getString(simplylucaapps.girocalendar.R.string.lr_saved, MyStorage.DAYS), Toast.LENGTH_LONG).show()
        }
    }

    /** All'apertura dell'app: invia un giro rimasto in attesa e cancella quelli scaduti o in eccesso. */
    fun maintain(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch {
            val hadPending = pendingFile(app).exists()
            runCatching { push(app, false) }   // dopo un invio riuscito pulisce già lui
            // La pulizia scarica tutti i file dello spazio personale (fino a ~5 MB): al massimo ogni 6 ore, non a ogni avvio.
            val prefs = app.getSharedPreferences("girocalendar_boot", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (!hadPending && now - prefs.getLong("ms_prune_at", 0L) > 6 * 3_600_000L) {
                runCatching {
                    val uid = FirebaseAuth.getInstance().currentUser?.takeUnless { it.isAnonymous }?.uid ?: return@runCatching
                    MyStorage.prune(uid)
                    prefs.edit().putLong("ms_prune_at", now).apply()
                }
            }
        }
    }
}
