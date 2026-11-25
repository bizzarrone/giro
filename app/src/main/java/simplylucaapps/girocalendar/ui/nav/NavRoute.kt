package simplylucaapps.girocalendar.ui.nav

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/** Una svolta lungo il percorso. [angle] > 0 = a destra, < 0 = a sinistra (gradi, 0..180). */
class NavTurn(val idx: Int, val dist: Float, val x: Float, val y: Float, val angle: Float, val inBearing: Float) {
    val right get() = angle > 0
    /** 0 = inversione a U, 1 = curva stretta, 2 = svolta, 3 = leggera. */
    val kind: Int get() { val a = abs(angle); return if (a >= 165f) 0 else if (a >= 125f) 1 else if (a >= 85f) 2 else 3 }
}

/**
 * Percorso ricampionato ogni [STEP] metri su un piano locale in metri (x = est, y = SUD, come lo schermo), con quota
 * interpolata (se il GPX ce l'ha) e dislivello positivo cumulato filtrato. Le svolte si ricavano solo dalla geometria
 * della traccia: un bivio dove la traccia prosegue dritta non compare.
 */
class NavRoute(lat: DoubleArray, lon: DoubleArray, ele: DoubleArray?) {
    private val lat0 = lat[0]
    private val lon0 = lon[0]
    private val mLat = 111_132.0
    private val mLon = 111_320.0 * cos(Math.toRadians(lat0))

    val x: FloatArray
    val y: FloatArray
    val cum: FloatArray
    val n: Int
    val turns: List<NavTurn>
    val length: Float get() = cum[n - 1]

    /** Quota (m) di ogni campione, già levigata; null se il GPX non ha quote. */
    val ele: FloatArray?
    /** D+ cumulato (m) fino a ogni campione, con soglia anti-rumore. */
    val gain: FloatArray?
    val totalGain: Float get() = gain?.last() ?: 0f

    fun xOf(lon: Double): Float = ((lon - lon0) * mLon).toFloat()
    fun yOf(lat: Double): Float = (-(lat - lat0) * mLat).toFloat()
    fun lonOf(x: Float): Double = lon0 + x / mLon
    fun latOf(y: Float): Double = lat0 - y / mLat

    fun bearing(i: Int, j: Int): Float =
        Math.toDegrees(atan2((x[j] - x[i]).toDouble(), -(y[j] - y[i]).toDouble())).toFloat().let { if (it < 0) it + 360f else it }

    init {
        val rx = DoubleArray(lat.size) { (lon[it] - lon0) * mLon }
        val ry = DoubleArray(lat.size) { -(lat[it] - lat0) * mLat }
        val hasEle = ele != null && ele.size == lat.size && ele.count { !it.isNaN() } >= 2
        // quote mancanti riempite con l'ultima nota (e la prima per l'inizio)
        val re = if (hasEle) DoubleArray(lat.size).also { a ->
            var last = ele!!.firstOrNull { !it.isNaN() } ?: 0.0
            for (i in a.indices) { if (!ele[i].isNaN()) last = ele[i]; a[i] = last }
        } else null
        val ox = ArrayList<Float>(); val oy = ArrayList<Float>(); val oe = ArrayList<Float>()
        ox.add(rx[0].toFloat()); oy.add(ry[0].toFloat()); if (re != null) oe.add(re[0].toFloat())
        var carry = 0.0
        for (i in 1 until lat.size) {
            var ax = rx[i - 1]; var ay = ry[i - 1]
            val seg0 = hypot(rx[i] - ax, ry[i] - ay)
            if (seg0 == 0.0) continue
            var seg = seg0
            val ux = (rx[i] - ax) / seg0; val uy = (ry[i] - ay) / seg0
            var need = STEP - carry
            while (seg >= need) {
                ax += ux * need; ay += uy * need; seg -= need
                ox.add(ax.toFloat()); oy.add(ay.toFloat())
                if (re != null) { val f = (seg0 - seg) / seg0; oe.add((re[i - 1] + (re[i] - re[i - 1]) * f).toFloat()) }
                need = STEP.toDouble(); carry = 0.0
            }
            carry += seg
        }
        val lx = rx.last().toFloat(); val ly = ry.last().toFloat()
        if (hypot(lx - ox.last(), ly - oy.last()) > 1f) { ox.add(lx); oy.add(ly); if (re != null) oe.add(re.last().toFloat()) }
        n = ox.size
        x = ox.toFloatArray(); y = oy.toFloatArray()
        cum = FloatArray(n)
        for (i in 1 until n) cum[i] = cum[i - 1] + hypot(x[i] - x[i - 1], y[i] - y[i - 1])
        if (re != null && oe.size == n) {
            // media mobile su ±25 m, poi D+ con soglia di 3 m (come la registrazione): il rumore del GPS non gonfia i metri
            val raw = oe.toFloatArray()
            val sm = FloatArray(n) { i -> var s = 0f; var c = 0; for (k in maxOf(0, i - 5)..minOf(n - 1, i + 5)) { s += raw[k]; c++ }; s / c }
            val g = FloatArray(n)
            var ref = sm[0]; var acc = 0f
            for (i in 1 until n) {
                if (sm[i] - ref >= 3f) { acc += sm[i] - ref; ref = sm[i] } else if (sm[i] < ref) ref = sm[i]
                g[i] = acc
            }
            this.ele = sm; this.gain = g
        } else { this.ele = null; this.gain = null }
        turns = findTurns()
    }

    private fun findTurns(): List<NavTurn> {
        val out = ArrayList<NavTurn>()
        var best = -1; var bestD = 0f; var lastCand = -100
        fun flush() {
            if (best >= 0) {
                val t = NavTurn(best, cum[best], x[best], y[best], bestD, bearing(best - W, best))
                if (out.isNotEmpty() && t.dist - out.last().dist < MERGE) { if (abs(t.angle) > abs(out.last().angle)) out[out.size - 1] = t }
                else out.add(t)
            }
            best = -1; bestD = 0f
        }
        for (i in W until n - W) {
            var d = bearing(i, i + W) - bearing(i - W, i)
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            if (abs(d) >= MIN_TURN) {
                if (i - lastCand > GAP) flush()
                if (abs(d) > abs(bestD)) { best = i; bestD = d }
                lastCand = i
            }
        }
        flush()
        return out
    }

    companion object {
        const val STEP = 5f
        const val W = 8
        const val GAP = 6
        const val MIN_TURN = 70f
        const val MERGE = 80f
    }
}
