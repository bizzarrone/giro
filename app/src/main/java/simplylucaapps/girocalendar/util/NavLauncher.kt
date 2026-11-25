package simplylucaapps.girocalendar.util

import simplylucaapps.girocalendar.R

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Apertura del parcheggio/ritrovo con l'app di navigazione scelta (Google Maps, Waze, altra app).
 * Il link salvato nell'evento è di solito un link Google Maps (anche corto, maps.app.goo.gl): per Waze e
 * per il selettore di sistema servono le coordinate, ricavate dal link (o seguendo i reindirizzamenti).
 */
object NavLauncher {
    const val ASK = "ask"; const val MAPS = "maps"; const val WAZE = "waze"; const val OTHER = "other"

    data class Dest(val url: String, val lat: Double?, val lon: Double?)

    private val coordPatterns = listOf(
        Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)"""),
        Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)"""),
        Regex("""[?&](?:q|ll|query|destination|daddr|center)=(-?\d+\.\d+)(?:,|%2C)(-?\d+\.\d+)"""),
    )

    fun parseCoords(url: String): Pair<Double, Double>? {
        val candidates = mutableListOf(url)
        // pagina di consenso Google: il link vero è nel parametro "continue"
        Regex("""[?&]continue=([^&]+)""").find(url)?.let { runCatching { candidates += URLDecoder.decode(it.groupValues[1], "UTF-8") } }
        for (c in candidates) for (p in coordPatterns) p.find(c)?.let { m ->
            val la = m.groupValues[1].toDoubleOrNull(); val lo = m.groupValues[2].toDoubleOrNull()
            if (la != null && lo != null) return la to lo
        }
        return null
    }

    private fun isShort(url: String): Boolean {
        val h = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
        return h == "maps.app.goo.gl" || h == "goo.gl" || h == "g.co"
    }

    /** Da chiamare fuori dal thread principale: segue i link corti e ricava le coordinate. */
    fun resolve(url: String): Dest {
        parseCoords(url)?.let { return Dest(url, it.first, it.second) }
        if (!isShort(url) && !url.contains("google.", true)) return Dest(url, null, null)
        var cur = url
        runCatching {
            for (i in 0 until 6) {
                val c = (URL(cur).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false; connectTimeout = 6000; readTimeout = 6000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) GiroCalendar")
                }
                val loc = c.getHeaderField("Location"); c.disconnect()
                if (loc == null) break
                cur = URL(URL(cur), loc).toString()
                parseCoords(cur)?.let { return Dest(url, it.first, it.second) }
            }
        }
        val co = parseCoords(cur)
        return Dest(url, co?.first, co?.second)
    }

    fun isInstalled(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    private fun start(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Apre [dest] con l'app scelta; ritorna false se mancano le coordinate e si è ripiegato sul link originale. */
    fun open(context: Context, app: String, dest: Dest, label: String = "Parcheggio"): Boolean {
        val lat = dest.lat; val lon = dest.lon
        if (lat == null || lon == null) { start(context, Intent(Intent.ACTION_VIEW, Uri.parse(dest.url))); return app == MAPS }
        when (app) {
            WAZE -> {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?ll=$lat,$lon&navigate=yes"))
                if (isInstalled(context, "com.waze")) i.setPackage("com.waze")
                start(context, i)
            }
            OTHER -> start(context, Intent.createChooser(
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")), context.getString(R.string.x_open_with)))
            else -> start(context, Intent(Intent.ACTION_VIEW, Uri.parse(dest.url)))
        }
        return true
    }
}
