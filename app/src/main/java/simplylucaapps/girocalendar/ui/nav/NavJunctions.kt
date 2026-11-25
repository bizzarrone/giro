package simplylucaapps.girocalendar.ui.nav

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Bivi lungo il percorso, ricavati da OpenStreetMap: un nodo dove si incontrano più sentieri/strade e dove, oltre al
 * tracciato, c'è almeno un'altra via d'uscita. [kind]: 0 = vai dritto, 1 = tieni la sinistra, 2 = tieni la destra.
 * Il risultato si salva su file per percorso, così dopo la prima volta funziona anche senza rete.
 */
object NavJunctions {
    class Junction(val distM: Float, val kind: Int)

    private const val MAX_ROUTE_M = 60_000f
    private const val CHUNK_M = 3000f
    private const val NEAR_M = 12f
    private const val MIN_BRANCH_M = 25f
    private val ENDPOINTS = listOf("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter")
    private const val HW = "path|track|footway|cycleway|bridleway|service|unclassified|residential|tertiary|secondary|primary|living_street|pedestrian"

    private class W(val nodes: LongArray, val lat: DoubleArray, val lon: DoubleArray)

    fun find(ctx: Context, r: NavRoute, key: String): List<Junction> {
        val f = File(File(ctx.filesDir, "junctions").apply { mkdirs() }, "${key.hashCode()}.json")
        if (f.exists()) return runCatching {
            val a = JSONArray(f.readText()); List(a.length()) { Junction(a.getJSONArray(it).getDouble(0).toFloat(), a.getJSONArray(it).getInt(1)) }
        }.getOrDefault(emptyList())
        if (r.length > MAX_ROUTE_M) return emptyList()
        val ways = LinkedHashMap<Long, W>()
        var ok = true
        var i0 = 0
        while (i0 < r.n - 1) {
            var i1 = i0
            while (i1 < r.n - 1 && r.cum[i1] - r.cum[i0] < CHUNK_M) i1++
            var mnLa = 90.0; var mxLa = -90.0; var mnLo = 180.0; var mxLo = -180.0
            for (i in i0..i1) { val la = r.latOf(r.y[i]); val lo = r.lonOf(r.x[i]); mnLa = minOf(mnLa, la); mxLa = maxOf(mxLa, la); mnLo = minOf(mnLo, lo); mxLo = maxOf(mxLo, lo) }
            val q = "[out:json][timeout:40];way[\"highway\"~\"^($HW)$\"](${mnLa - 0.0006},${mnLo - 0.0009},${mxLa + 0.0006},${mxLo + 0.0009});out geom;"
            val js = post(q)
            if (js == null) { ok = false; break }
            val els = js.optJSONArray("elements") ?: JSONArray()
            for (k in 0 until els.length()) {
                val e = els.getJSONObject(k)
                if (e.optString("type") != "way") continue
                val tags = e.optJSONObject("tags")
                if (tags != null && (tags.optString("access") in setOf("private", "no") || tags.optString("service") in setOf("driveway", "parking_aisle", "drive-through") || tags.optString("footway") in setOf("crossing", "sidewalk"))) continue
                val nd = e.optJSONArray("nodes") ?: continue; val g = e.optJSONArray("geometry") ?: continue
                if (nd.length() != g.length() || nd.length() < 2) continue
                ways[e.getLong("id")] = W(LongArray(nd.length()) { nd.getLong(it) }, DoubleArray(g.length()) { g.getJSONObject(it).getDouble("lat") }, DoubleArray(g.length()) { g.getJSONObject(it).getDouble("lon") })
            }
            i0 = i1
            Thread.sleep(600)
        }
        val res = compute(r, ways.values.toList())
        if (ok) runCatching {
            f.writeText(JSONArray().also { a -> res.forEach { a.put(JSONArray().put(it.distM.toDouble()).put(it.kind)) } }.toString())
        }
        return res
    }

    private fun post(q: String): JSONObject? {
        for (ep in ENDPOINTS) {
            try {
                val c = URL(ep).openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 50000
                c.setRequestProperty("User-Agent", "GiroCalendar (simplylucaapps)")
                c.outputStream.use { it.write(("data=" + java.net.URLEncoder.encode(q, "UTF-8")).toByteArray()) }
                if (c.responseCode == 200) return JSONObject(c.inputStream.bufferedReader().readText())
            } catch (_: Exception) { }
        }
        return null
    }

    private fun bearing(dx: Double, dy: Double): Double = Math.toDegrees(atan2(dx, -dy)).let { if (it < 0) it + 360.0 else it }
    private fun rel(a: Double, base: Double): Double { var d = (a - base) % 360.0; if (d > 180) d -= 360.0; if (d < -180) d += 360.0; return d }

    private fun compute(r: NavRoute, ways: List<W>): List<Junction> {
        // nodo -> (via, posizione)
        val occ = HashMap<Long, MutableList<IntArray>>()
        ways.forEachIndexed { wi, w -> w.nodes.forEachIndexed { p, id -> occ.getOrPut(id) { ArrayList(2) }.add(intArrayOf(wi, p)) } }
        val out = ArrayList<Junction>()
        for ((_, list) in occ) {
            if (list.size < 2) continue
            val w0 = ways[list[0][0]]; val p0 = list[0][1]
            val jx = r.xOf(w0.lon[p0]); val jy = r.yOf(w0.lat[p0])
            // punto del percorso più vicino
            var bi = -1; var bd = Float.MAX_VALUE
            for (i in 0 until r.n) { val d = hypot(r.x[i] - jx, r.y[i] - jy); if (d < bd) { bd = d; bi = i } }
            if (bd > NEAR_M || bi < 4 || bi > r.n - 5) continue
            val a = maxOf(0, bi - 4); val b = minOf(r.n - 1, bi + 4)
            val rin = bearing((r.x[bi] - r.x[a]).toDouble(), (r.y[bi] - r.y[a]).toDouble())
            val rout = bearing((r.x[b] - r.x[bi]).toDouble(), (r.y[b] - r.y[bi]).toDouble())
            // direzioni in uscita dal nodo (a ~25 m lungo ogni via)
            val dirs = ArrayList<Double>()
            for (oc in list) {
                val w = ways[oc[0]]
                for (step in intArrayOf(-1, 1)) {
                    var q = oc[1]; var acc = 0.0; var px = jx; var py = jy
                    var ex = 0f; var ey = 0f; var reached = false
                    while (true) {
                        q += step
                        if (q < 0 || q >= w.nodes.size) break
                        val nx = r.xOf(w.lon[q]); val ny = r.yOf(w.lat[q])
                        acc += hypot(nx - px, ny - py).toDouble(); px = nx; py = ny
                        if (acc >= MIN_BRANCH_M) { ex = nx; ey = ny; reached = true; break }
                    }
                    if (!reached) continue
                    val dir = bearing((ex - jx).toDouble(), (ey - jy).toDouble())
                    if (dirs.none { abs(rel(dir, it)) < 20.0 }) dirs.add(dir)
                }
            }
            // togli la via da cui arrivi e quella che segui
            val alts = dirs.filter { abs(rel(it, (rin + 180.0) % 360.0)) > 30.0 && abs(rel(it, rout)) > 30.0 }
            if (alts.isEmpty()) continue
            val rho = rel(rout, rin)
            if (abs(rho) >= 70.0) continue          // è una svolta: la dice già l'avviso delle svolte
            val altRel = alts.map { rel(it, rin) }
            val kind = when {
                abs(rho) <= 25.0 -> 0
                altRel.all { rho > it } -> 2
                altRel.all { rho < it } -> 1
                else -> 0
            }
            out.add(Junction(r.cum[bi], kind))
        }
        out.sortBy { it.distM }
        // niente bivi a meno di 30 m uno dall'altro, né vicino a una svolta già annunciata
        val res = ArrayList<Junction>()
        for (j in out) {
            if (res.isNotEmpty() && j.distM - res.last().distM < 30f) continue
            if (r.turns.any { abs(it.dist - j.distM) < 40f }) continue
            res.add(j)
        }
        return res
    }
}
