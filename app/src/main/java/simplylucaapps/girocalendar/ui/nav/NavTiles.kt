package simplylucaapps.girocalendar.ui.nav

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Sfondo mappa OpenStreetMap, facoltativo. Le tessere si scaricano in background (2 alla volta), si salvano su
 * disco e si tengono in memoria in RGB_565 (metà memoria): se non c'è rete resta la sola traccia.
 */
class NavTiles(private val dir: File, private val offDir: File, private val layer: NavLayer, private val onTile: () -> Unit) {
    private val mem = object : LruCache<Long, Bitmap>(if (layer.overlay) 24 else 40) {}
    private val pool = Executors.newFixedThreadPool(2)
    private val pending = HashSet<Long>()
    private val failed = HashMap<Long, Long>()

    private fun key(z: Int, x: Int, y: Int): Long = (z.toLong() shl 56) or (x.toLong() shl 28) or y.toLong()

    fun get(z: Int, x: Int, y: Int): Bitmap? {
        val k = key(z, x, y)
        mem.get(k)?.let { return it }
        synchronized(pending) {
            if (k in pending) return null
            val f = failed[k]
            if (f != null && System.currentTimeMillis() - f < 30_000) return null
            pending.add(k)
        }
        pool.execute { load(z, x, y, k) }
        return null
    }

    private fun load(z: Int, x: Int, y: Int, k: Long) {
        try {
            val off = File(offDir, "$z/$x/$y.png")
            val f = if (off.exists() && off.length() > 0) off else File(dir, "$z/$x/$y.png")
            if (!f.exists()) {
                f.parentFile?.mkdirs()
                val c = URL(layer.url(z, x, y)).openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", "GiroCalendar (simplylucaapps)")
                c.connectTimeout = 10000; c.readTimeout = 15000
                try {
                    if (c.responseCode != 200) error("HTTP ${c.responseCode}")
                    val tmp = File(f.path + ".part")
                    c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    tmp.renameTo(f)
                } finally { c.disconnect() }
            }
            val o = BitmapFactory.Options().apply { inPreferredConfig = if (layer.overlay) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565 }
            BitmapFactory.decodeFile(f.path, o)?.let { mem.put(k, it) }
            synchronized(pending) { failed.remove(k) }
        } catch (e: Exception) {
            synchronized(pending) { failed[k] = System.currentTimeMillis() }
        } finally {
            synchronized(pending) { pending.remove(k) }
            onTile()
        }
    }

    fun shutdown() { pool.shutdownNow() }
}

/** Sfondi disponibili (stessi della mappa grande di Giro). [overlay] = strato trasparente da sovrapporre a uno sfondo. */
enum class NavLayer(val id: String, val maxZoom: Int, val attribution: String, val overlay: Boolean = false, val url: (Int, Int, Int) -> String) {
    OSM("osm", 19, "© OpenStreetMap", url = { z, x, y -> "https://tile.openstreetmap.org/$z/$x/$y.png" }),
    TOPO("topo", 17, "© OpenTopoMap, OpenStreetMap", url = { z, x, y -> "https://tile.opentopomap.org/$z/$x/$y.png" }),
    CYCLE("cyclosm", 19, "© CyclOSM, OpenStreetMap", url = { z, x, y -> "https://a.tile-cyclosm.openstreetmap.fr/cyclosm/$z/$x/$y.png" }),
    SAT("sat", 19, "© Esri", url = { z, x, y -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x" }),
    MTB("mtb", 18, "© Waymarked Trails", overlay = true, url = { z, x, y -> "https://tile.waymarkedtrails.org/mtb/$z/$x/$y.png" });
}
