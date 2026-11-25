package simplylucaapps.girocalendar.ui.record

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import simplylucaapps.girocalendar.R
import java.io.File
import kotlin.coroutines.resume

/** Tipi di segnalazione: icona grande + etichetta, scelti per essere riconoscibili al volo in sella. */
enum class ReportType(val emoji: String, val labelRes: Int) {
    TREE("🌳", R.string.rp_tree),
    VEGETATION("🌿", R.string.rp_vegetation),
    DUG("🕳️", R.string.rp_dug),
    FLOODED("💧", R.string.rp_flooded),
    ROCKS("🪨", R.string.rp_rocks),
    DANGER("⚠️", R.string.rp_danger),
    INFO("ℹ️", R.string.rp_info),
    OTHER("❓", R.string.rp_other);
}

/** Una segnalazione: privata sul dispositivo finché l'utente non la conferma (completamento e invio: passo successivo). */
data class Report(val n: Int, val type: ReportType, val lat: Double, val lon: Double, val accuracy: Float, val atMillis: Long, val note: String = "",
                  val trailId: String = "", val trailName: String = "", val shared: Boolean = false)

/**
 * Segnalazioni dell'utente, salvate in locale (funzionano anche senza rete, nei sentieri). Numero progressivo per utente
 * (uid Firebase, o «local» da non loggati): non riparte mai da 1, nemmeno dopo aver eliminato delle segnalazioni.
 */
object ReportStore {
    val items = mutableStateListOf<Report>()
    private var loadedFor: String? = null

    private fun file(c: Context, uid: String) = File(c.filesDir, "reports_$uid.json")
    private fun prefs(c: Context) = c.getSharedPreferences("girocalendar_boot", Context.MODE_PRIVATE)

    fun uid(): String = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "local"

    fun load(c: Context) {
        val uid = uid()
        if (loadedFor == uid) return
        loadedFor = uid
        items.clear()
        runCatching {
            val a = JSONArray(file(c, uid).readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                items.add(Report(o.getInt("n"), runCatching { ReportType.valueOf(o.getString("type")) }.getOrDefault(ReportType.OTHER),
                    o.getDouble("lat"), o.getDouble("lon"), o.optDouble("acc", 0.0).toFloat(), o.getLong("at"), o.optString("note"),
                    o.optString("trailId"), o.optString("trailName"), o.optBoolean("shared")))
            }
        }
    }

    private fun save(c: Context) {
        val a = JSONArray()
        items.forEach { r ->
            a.put(JSONObject().put("n", r.n).put("type", r.type.name).put("lat", r.lat).put("lon", r.lon).put("acc", r.accuracy.toDouble()).put("at", r.atMillis).put("note", r.note)
                .put("trailId", r.trailId).put("trailName", r.trailName).put("shared", r.shared))
        }
        val f = file(c, uid()); val tmp = File(f.path + ".tmp")
        runCatching { tmp.writeText(a.toString()); tmp.renameTo(f) }
    }

    private fun nextNumber(c: Context): Int {
        val key = "report_seq_" + uid()
        val n = prefs(c).getInt(key, 0) + 1
        prefs(c).edit().putInt(key, n).apply()
        return n
    }

    fun add(c: Context, type: ReportType, loc: Location): Report {
        load(c)
        val r = Report(nextNumber(c), type, loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else 0f, System.currentTimeMillis())
        items.add(0, r); save(c)
        return r
    }

    /** Aggiorna una segnalazione (es. dopo l'invio al calendario o l'assegnazione a un trail) e la salva. */
    fun update(c: Context, n: Int, f: (Report) -> Report) {
        val i = items.indexOfFirst { it.n == n }
        if (i >= 0) { items[i] = f(items[i]); save(c) }
    }

    fun remove(c: Context, n: Int) { items.removeAll { it.n == n }; save(c) }

    /** Posizione al momento del tocco: l'ultima nota se recente (<20 s), altrimenti una lettura fresca (max 6 s), altrimenti la più recente disponibile. */
    suspend fun locate(c: Context): Location? = withContext(Dispatchers.Main) {
        fun ok(p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED
        if (!ok(Manifest.permission.ACCESS_FINE_LOCATION) && !ok(Manifest.permission.ACCESS_COARSE_LOCATION)) return@withContext null
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val known = runCatching { lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) } }.getOrDefault(emptyList()).maxByOrNull { it.time }
        if (known != null && System.currentTimeMillis() - known.time < 20_000) return@withContext known
        val fresh = withTimeoutOrNull(6000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val sig = CancellationSignal()
                cont.invokeOnCancellation { sig.cancel() }
                val provs = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
                if (provs.isEmpty()) { cont.resume(null); return@suspendCancellableCoroutine }
                var left = provs.size
                provs.forEach { p ->
                    runCatching {
                        LocationManagerCompat.getCurrentLocation(lm, p, sig, ContextCompat.getMainExecutor(c)) { l ->
                            if (l != null && cont.isActive) cont.resume(l) else if (--left == 0 && cont.isActive) cont.resume(null)
                        }
                    }.onFailure { if (--left == 0 && cont.isActive) cont.resume(null) }
                }
            }
        }
        fresh ?: known
    }
}
