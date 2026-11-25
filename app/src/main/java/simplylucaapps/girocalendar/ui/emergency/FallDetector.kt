package simplylucaapps.girocalendar.ui.emergency

import simplylucaapps.girocalendar.R

import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Riconoscimento di una caduta in MTB dai dati dell'accelerometro (e, se c'è, dalla velocità GPS).
 * Niente dipendenze Android: si prova con test JVM.
 *
 * Un solo urto forte NON basta (salti, buche, telefono sballottato in tasca/zaino/manubrio). Serve la sequenza:
 *  1. URTO: modulo dell'accelerazione sopra soglia (fronte di salita);
 *  2. PRIMA dell'urto il telefono era "in movimento" (stava pedalando, non era fermo su un tavolo);
 *  3. DOPO l'urto (da 1,6 s a 4,6 s) il telefono è FERMO (poca variazione, modulo ≈ 1 g);
 *  4. l'ORIENTAMENTO è cambiato: l'angolo tra la gravità media prima (−3,5…−0,7 s) e dopo supera la soglia;
 *  5. col GPS: andava a > 2 m/s prima dell'urto e dopo è sotto 2 m/s (se non c'è fix, il controllo è saltato).
 * Un salto (stesso orientamento, si continua a pedalare) o il telefono che rimbalza in tasca (mai fermo) non passano.
 */
class FallDetector(var sensitivity: Int = 1, private val onFall: (String) -> Unit) {
    /** Serve solo per i testi del registro (localizzati); nei test unitari resta null. */
    var ctx: android.content.Context? = null
    private fun t(id: Int, vararg a: Any): String = ctx?.getString(id, *a) ?: ""
    /** Modalità prova: soglie più basse e ignora GPS/movimento precedente (per provare a casa lasciando cadere il telefono). */
    var testMode = false
    /** Diagnostica leggibile (mostrata a schermo): urti visti e perché una caduta è stata scartata o accettata. */
    var log: ((String) -> Unit)? = null
    var samples = 0L; private set
    private class S(val t: Long, val x: Float, val y: Float, val z: Float) { val m = sqrt(x * x + y * y + z * z) }
    private class Sp(val t: Long, val v: Float)

    private val buf = ArrayDeque<S>()
    private val speeds = ArrayDeque<Sp>()
    private var prevMag = 9.81f
    private var firstImpact = -1L
    private var lastImpact = -1L
    private var preG: FloatArray? = null
    private var cooldownUntil = 0L

    private val impactThr get() = if (testMode) 25f else when (sensitivity) { 0 -> 60f; 2 -> 33f; else -> 45f }
    private val angleThr get() = if (testMode) 40.0 else when (sensitivity) { 0 -> 70.0; 2 -> 40.0; else -> 55.0 }
    private val stillThr get() = if (testMode) 1.3 else when (sensitivity) { 0 -> 0.6; 2 -> 1.3; else -> 0.9 }
    private val preActThr get() = when (sensitivity) { 2 -> 0.5; else -> 0.7 }
    private fun g(v: Number) = "%.1f".format(java.util.Locale.US, v.toDouble() / 9.81)

    fun onSpeed(t: Long, mps: Float) { speeds.addLast(Sp(t, mps)); while (speeds.size > 100) speeds.removeFirst() }

    fun onSample(t: Long, x: Float, y: Float, z: Float) {
        val s = S(t, x, y, z)
        samples++
        buf.addLast(s)
        while (buf.isNotEmpty() && t - buf.first().t > 14000) buf.removeFirst()
        val rising = s.m > impactThr && prevMag <= impactThr
        prevMag = s.m
        if (t < cooldownUntil) return
        if (rising) {
            if (firstImpact < 0) {
                val g = meanVec(t - 3500, t - 700)
                val act = stdMag(t - 5000, t - 700)
                if (g != null && act != null && (testMode || act >= preActThr)) { firstImpact = t; lastImpact = t; preG = g; log?.invoke(t(R.string.fd_impact, g(s.m))) }
                else log?.invoke(t(R.string.fd_ignored, g(s.m), if (g == null || act == null) t(R.string.fd_no_data) else t(R.string.fd_was_still)))
            } else if (t - lastImpact < 3000 && t - firstImpact < 10000) lastImpact = t  // più urti ravvicinati (rotolamento)
        }
        if (firstImpact >= 0 && t >= lastImpact + 4600) evaluate(t)
    }

    private fun evaluate(now: Long) {
        val from = lastImpact + 1600; val to = lastImpact + 4600
        val g0 = preG; val g1 = meanVec(from, to); val sd = stdMag(from, to); val mm = meanMag(from, to)
        val first = firstImpact
        firstImpact = -1; lastImpact = -1; preG = null
        if (g0 == null || g1 == null || sd == null || mm == null) { log?.invoke(t(R.string.fd_eval_nodata)); return }
        val angle = angleDeg(g0, g1)
        val info = t(R.string.fd_info, angle.toInt(), angleThr.toInt(), "%.2f".format(java.util.Locale.US, sd), stillThr.toString())
        if (angle < angleThr) { log?.invoke(t(R.string.fd_orient_same, info)); return }
        if (sd > stillThr || abs(mm - 9.81) > 1.5) { log?.invoke(t(R.string.fd_not_still, info)); return }
        val preSp = speeds.filter { it.t in (first - 15000)..first }.maxOfOrNull { it.v }
        if (!testMode && preSp != null && preSp < 2.0f) { log?.invoke(t(R.string.fd_gps_pre, "%.1f".format(java.util.Locale.US, preSp))); return }
        val postSp = speeds.filter { it.t in from..to }.maxOfOrNull { it.v }
        if (!testMode && postSp != null && postSp > 2.0f) { log?.invoke(t(R.string.fd_gps_post, "%.1f".format(java.util.Locale.US, postSp))); return }
        log?.invoke(t(R.string.fd_confirmed, info))
        cooldownUntil = now + 60000
        onFall(t(R.string.fd_reason, angle.toInt()))
    }

    private fun win(a: Long, b: Long) = buf.filter { it.t in a..b }
    private fun meanVec(a: Long, b: Long): FloatArray? {
        val w = win(a, b); if (w.size < 15) return null
        return floatArrayOf(w.map { it.x }.average().toFloat(), w.map { it.y }.average().toFloat(), w.map { it.z }.average().toFloat())
    }
    private fun meanMag(a: Long, b: Long): Double? { val w = win(a, b); if (w.size < 15) return null; return w.map { it.m.toDouble() }.average() }
    private fun stdMag(a: Long, b: Long): Double? {
        val w = win(a, b); if (w.size < 15) return null
        val m = w.map { it.m.toDouble() }; val mu = m.average()
        return sqrt(m.sumOf { (it - mu) * (it - mu) } / m.size)
    }
    private fun angleDeg(a: FloatArray, b: FloatArray): Double {
        val na = sqrt((a[0] * a[0] + a[1] * a[1] + a[2] * a[2]).toDouble()); val nb = sqrt((b[0] * b[0] + b[1] * b[1] + b[2] * b[2]).toDouble())
        if (na < 1e-3 || nb < 1e-3) return 0.0
        val c = ((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (na * nb)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(c))
    }
}
