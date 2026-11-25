package simplylucaapps.girocalendar.ui.nav

import android.location.Location
import kotlin.math.max

/**
 * Filtra il «tremolio» del GPS da fermo e calcola le statistiche del giro (tempo totale e in movimento,
 * distanza, velocità media e massima). Il giro parte quando ci si allontana di più di 25 m dal punto iniziale.
 */
internal class RideTracker {
    private var shown: Location? = null     // ultima posizione mostrata
    private var anchor: Location? = null    // punto di partenza, finché non ci si muove
    private var anchorTime = 0L
    private var prevTime = 0L
    private val recent = ArrayDeque<Float>()
    var started = false; private set
    var tStart = 0L; private set
    var tLast = 0L; private set
    var distM = 0.0; private set
    var movingMs = 0L; private set
    var maxMs = 0f; private set

    val totalMs get() = if (started) max(0L, tLast - tStart) else 0L
    val avgKmh get() = if (movingMs > 5_000) (distM / (movingMs / 1000.0) * 3.6).toFloat() else 0f
    val maxKmh get() = maxMs * 3.6f

    /** Registra una posizione. Restituisce quella da mostrare, o null se si è fermi (il punto resta dov'è). */
    fun accept(l: Location): Location? {
        val prev = shown
        val dt = if (prevTime > 0) (l.time - prevTime).coerceAtLeast(0L) else 0L
        val d = prev?.distanceTo(l) ?: 0f
        val v = when {
            l.hasSpeed() -> l.speed
            dt > 0 -> d / (dt / 1000f)
            else -> 0f
        }.coerceIn(0f, 30f)
        val acc = if (l.hasAccuracy()) l.accuracy else 10f
        val still = prev != null && v < 0.7f && d < max(8f, acc)
        prevTime = l.time; tLast = l.time

        val a = anchor
        if (a == null) { anchor = l; anchorTime = l.time }
        else if (!started) {
            if (a.distanceTo(l) > 25f) { started = true; tStart = anchorTime; distM += a.distanceTo(l) } else anchorTime = l.time
        } else if (dt in 1..15_000L) {
            if (v >= 1.0f) movingMs += dt
            if (!still) distM += d
            if (acc <= 30f) {
                recent.addLast(v); if (recent.size > 3) recent.removeFirst()
                if (recent.size == 3) maxMs = max(maxMs, recent.average().toFloat())
            }
        }
        if (still) return null
        shown = l
        return l
    }
}
