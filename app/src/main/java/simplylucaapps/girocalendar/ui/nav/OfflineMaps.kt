package simplylucaapps.girocalendar.ui.nav

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Mappe offline per un percorso: scarica le tessere lungo la traccia (fascia di [BUFFER_M] metri, zoom [MIN_Z]..maxZ)
 * in `filesDir/offline_tiles/<layer>/z/x/y.png`, cartella che Android non svuota da solo (a differenza della cache).
 * NavTiles guarda prima qui, poi nella cache.
 */
object OfflineMaps {
    const val MIN_Z = 12
    const val BUFFER_M = 600.0
    const val MAX_TILES = 4000
    private const val AVG_KB = 20

    fun dir(ctx: Context, l: NavLayer) = File(ctx.filesDir, "offline_tiles/${l.id}")

    private fun tx(lon: Double, z: Int) = floor((lon + 180.0) / 360.0 * (1 shl z)).toInt()
    private fun ty(lat: Double, z: Int): Int {
        val r = Math.toRadians(lat)
        return floor((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * (1 shl z)).toInt()
    }

    /** Tessere (z, x, y) da avere per il percorso. maxZ scende da solo se sono troppe. */
    fun plan(lat: DoubleArray, lon: DoubleArray, wantMaxZ: Int): Pair<Int, List<Triple<Int, Int, Int>>> {
        var maxZ = wantMaxZ
        while (true) {
            val set = LinkedHashSet<Triple<Int, Int, Int>>()
            for (z in MIN_Z..maxZ) {
                val n = 1 shl z
                var lastKey = Long.MIN_VALUE
                for (i in lat.indices) {
                    val dLat = BUFFER_M / 111_132.0
                    val dLon = BUFFER_M / (111_320.0 * cos(Math.toRadians(lat[i])))
                    val x0 = tx(lon[i] - dLon, z).coerceIn(0, n - 1); val x1 = tx(lon[i] + dLon, z).coerceIn(0, n - 1)
                    val y0 = ty(lat[i] + dLat, z).coerceIn(0, n - 1); val y1 = ty(lat[i] - dLat, z).coerceIn(0, n - 1)
                    val key = (x0.toLong() shl 48) or (x1.toLong() shl 32) or (y0.toLong() shl 16) or y1.toLong()
                    if (key == lastKey) continue
                    lastKey = key
                    for (x in x0..x1) for (y in y0..y1) set.add(Triple(z, x, y))
                }
            }
            if (set.size <= MAX_TILES || maxZ <= MIN_Z) return maxZ to set.toList()
            maxZ--
        }
    }

    fun estimateMb(tiles: Int): Int = maxOf(1, tiles * AVG_KB / 1024)

    /** Scarica le tessere mancanti per gli strati dati. Ritorna il numero di tessere nuove scaricate; [progress](fatte, totali). */
    fun download(ctx: Context, layers: List<NavLayer>, tiles: List<Triple<Int, Int, Int>>, cancelled: () -> Boolean, progress: (Int, Int) -> Unit): Int {
        val total = tiles.size * layers.size
        val done = AtomicInteger(0)
        val fresh = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(3)
        for (l in layers) for ((z, x, y) in tiles) pool.execute {
            if (!cancelled()) {
                try {
                    val f = File(dir(ctx, l), "$z/$x/$y.png")
                    if (!f.exists() || f.length() == 0L) {
                        f.parentFile?.mkdirs()
                        val c = URL(l.url(z, x, y)).openConnection() as HttpURLConnection
                        c.setRequestProperty("User-Agent", "GiroCalendar (simplylucaapps)")
                        c.connectTimeout = 10000; c.readTimeout = 15000
                        try {
                            if (c.responseCode == 200) {
                                val tmp = File(f.path + ".part")
                                c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                                tmp.renameTo(f); fresh.incrementAndGet()
                            }
                        } finally { c.disconnect() }
                        Thread.sleep(40) // cortesia verso i server delle mappe
                    }
                } catch (_: Exception) { }
            }
            progress(done.incrementAndGet(), total)
        }
        pool.shutdown()
        pool.awaitTermination(2, TimeUnit.HOURS)
        return fresh.get()
    }

    fun sizeMb(ctx: Context): Double = File(ctx.filesDir, "offline_tiles").walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1_048_576.0
    fun clear(ctx: Context) { File(ctx.filesDir, "offline_tiles").deleteRecursively() }
}
