package simplylucaapps.girocalendar.util

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.roundToLong

typealias LatLon = Pair<Double, Double>

/**
 * Tracciati dei trail: stringa "encoded polyline" (precisione 1e-5, ~1 m) salvata in un solo
 * campo Firestore, più compatta di un array di numeri e senza entrate di indice per punto.
 */
object Polyline {

    fun encode(points: List<LatLon>): String {
        val sb = StringBuilder()
        var pLat = 0L; var pLon = 0L
        for ((lat, lon) in points) {
            val la = (lat * 1e5).roundToLong(); val lo = (lon * 1e5).roundToLong()
            put(la - pLat, sb); put(lo - pLon, sb)
            pLat = la; pLon = lo
        }
        return sb.toString()
    }

    private fun put(v: Long, sb: StringBuilder) {
        var n = if (v < 0) (v shl 1).inv() else v shl 1
        while (n >= 0x20) { sb.append((((n and 0x1f) or 0x20) + 63).toInt().toChar()); n = n shr 5 }
        sb.append((n + 63).toInt().toChar())
    }

    fun decode(s: String): List<LatLon> {
        val out = ArrayList<LatLon>()
        var i = 0; var lat = 0L; var lon = 0L
        fun next(): Long {
            var shift = 0; var result = 0L
            while (true) {
                val b = s[i++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
                if (b < 0x20) break
            }
            return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        }
        try {
            while (i < s.length) { lat += next(); lon += next(); out.add(lat / 1e5 to lon / 1e5) }
        } catch (_: Exception) { /* stringa troncata: si tengono i punti letti */ }
        return out
    }

    /** Douglas-Peucker con tolleranza in metri; alza la tolleranza finché i punti sono ≤ [maxPoints]. */
    fun simplify(points: List<LatLon>, toleranceM: Double = 3.0, maxPoints: Int = 1500): List<LatLon> {
        if (points.size <= 2) return points
        var tol = toleranceM
        var res = dp(points, tol)
        while (res.size > maxPoints) { tol *= 1.5; res = dp(points, tol) }
        return res
    }

    private fun dp(pts: List<LatLon>, tolM: Double): List<LatLon> {
        val keep = keepMask(pts, tolM)
        return pts.filterIndexed { i, _ -> keep[i] }
    }

    /** Maschera dei punti da tenere con Douglas-Peucker a tolleranza [tolM] metri. */
    fun keepMask(pts: List<LatLon>, tolM: Double): BooleanArray {
        val lat0 = pts[0].first
        val kx = 111_320.0 * cos(Math.toRadians(lat0)); val ky = 110_540.0
        val x = DoubleArray(pts.size) { pts[it].second * kx }
        val y = DoubleArray(pts.size) { pts[it].first * ky }
        val keep = BooleanArray(pts.size)
        keep[0] = true; keep[pts.size - 1] = true
        val stack = ArrayDeque<IntArray>().apply { addLast(intArrayOf(0, pts.size - 1)) }
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var maxD = 0.0; var idx = -1
            val dx = x[b] - x[a]; val dy = y[b] - y[a]; val len = hypot(dx, dy)
            for (k in a + 1 until b) {
                val d = if (len == 0.0) hypot(x[k] - x[a], y[k] - y[a])
                else abs(dy * (x[k] - x[a]) - dx * (y[k] - y[a])) / len
                if (d > maxD) { maxD = d; idx = k }
            }
            if (idx >= 0 && maxD > tolM) { keep[idx] = true; stack.addLast(intArrayOf(a, idx)); stack.addLast(intArrayOf(idx, b)) }
        }
        return keep
    }

    fun lengthKm(points: List<LatLon>): Double {
        var m = 0.0
        for (i in 1 until points.size) {
            val (la1, lo1) = points[i - 1]; val (la2, lo2) = points[i]
            val dy = (la2 - la1) * 110_540.0
            val dx = (lo2 - lo1) * 111_320.0 * cos(Math.toRadians((la1 + la2) / 2))
            m += hypot(dx, dy)
        }
        return m / 1000.0
    }
}
