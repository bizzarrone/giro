package simplylucaapps.girocalendar.util

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Cache su disco per la mappa dei trail (WebView + Leaflet): tile dei vari sfondi (OSM, OpenTopoMap,
 * CyclOSM, Waymarked Trails) e file di Leaflet (unpkg). Le risorse già viste si servono dal disco senza rete, quindi la mappa si apre subito e
 * funziona anche offline nelle zone già visitate. Le richieste partono dall'app con uno
 * User-Agent che la identifica, come richiesto dalla tile usage policy di OSM.
 *
 * - Validità: [MAX_AGE_MS]; scaduta, si riprova la rete e, se manca, si usa comunque la copia vecchia.
 * - Dimensione: al massimo [maxBytes] (scelta nelle Preferenze), eliminando i file meno recenti.
 */
object MapTileCache {
    /** Dimensione massima scelta dall'utente (Preferenze → Cache mappa). */
    @Volatile private var maxBytes = 150L * 1024 * 1024
    val SIZE_OPTIONS_MB = listOf(50, 150, 300, 600, 1000)
    private fun sp(c: Context) = c.getSharedPreferences("girocalendar_cache", Context.MODE_PRIVATE)
    fun maxMb(c: Context): Int = sp(c).getInt("map_mb", 150).also { maxBytes = it * 1024L * 1024 }
    fun setMaxMb(c: Context, mb: Int) { sp(c).edit().putInt("map_mb", mb).apply(); maxBytes = mb * 1024L * 1024; trim(File(c.cacheDir, "maptiles")) }
    fun usedBytes(c: Context): Long = File(c.cacheDir, "maptiles").listFiles()?.sumOf { it.length() } ?: 0L
    fun clear(c: Context) { File(c.cacheDir, "maptiles").listFiles()?.forEach { it.delete() } }
    private const val MAX_AGE_MS = 30L * 24 * 3600 * 1000
    private const val UA = "GiroCalendar/1.0 (Android; trail map)"
    private var writes = 0
    @Volatile private var limitLoaded = false

    private val HOSTS = listOf("tile.openstreetmap.org", "tile.opentopomap.org", "tile-cyclosm.openstreetmap.fr", "tile.waymarkedtrails.org", "basemaps.cartocdn.com")
    private fun cacheable(host: String) = host == "unpkg.com" || HOSTS.any { host == it || host.endsWith(".$it") }

    private fun mimeOf(url: String, fromServer: String?): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".png") -> "image/png"
            path.endsWith(".js") -> "application/javascript"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            else -> fromServer?.substringBefore(';')?.trim() ?: "application/octet-stream"
        }
    }

    private fun key(url: String) = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        .joinToString("") { "%02x".format(it) }

    /** Risposta dalla cache/rete per le risorse della mappa; null = lascia fare al WebView. */
    fun intercept(context: Context, uri: Uri, method: String): WebResourceResponse? {
        if (method != "GET" || uri.scheme != "https" || !cacheable(uri.host ?: return null)) return null
        val url = uri.toString()
        val dir = File(context.cacheDir, "maptiles").apply { mkdirs() }
        if (!limitLoaded) { maxMb(context); limitLoaded = true }
        val file = File(dir, key(url))
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS
        if (fresh) return response(url, file.readBytes(), null)
        return try {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 10000
                setRequestProperty("User-Agent", UA)
            }
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            val bytes = c.inputStream.use { it.readBytes() }
            val type = c.contentType
            // scrittura atomica: un altro thread non deve mai leggere un tile a metà
            val tmp = File(dir, file.name + "." + Thread.currentThread().id + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) tmp.delete()
            if (++writes % 50 == 0) trim(dir)
            response(url, bytes, type)
        } catch (e: Exception) {
            if (file.exists()) response(url, file.readBytes(), null) else null
        }
    }

    private fun response(url: String, bytes: ByteArray, serverType: String?) =
        WebResourceResponse(mimeOf(url, serverType), null, 200, "OK",
            mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(bytes))

    private fun trim(dir: File) {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) { if (total <= maxBytes) break; total -= f.length(); f.delete() }
    }
}
