package simplylucaapps.girocalendar.ui.nav

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.data.remote.firestore.Trail
import simplylucaapps.girocalendar.data.remote.firestore.TrailRepository
import simplylucaapps.girocalendar.data.remote.firestore.TrailStatus
import simplylucaapps.girocalendar.ui.record.ReportType
import simplylucaapps.girocalendar.util.LatLon
import simplylucaapps.girocalendar.util.Polyline
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Collega una segnalazione al trail più vicino (entro [MAX_M] m dal suo tracciato) pubblicando un commento sul trail.
 * Albero, scavato, fango, frana e pericolo portano lo stato da «Aperto» ad «Attenzione» (mai a «Chiuso», e mai un declassamento);
 * vegetazione, info e altro restano un commento informativo.
 */
object TrailLink {
    const val MAX_M = 150.0
    const val ALONG_M = 80.0
    private const val POINT_M = 100.0
    private const val CACHE_MS = 10 * 60_000L

    private var cache: List<Trail> = emptyList()
    private var cacheAt = 0L
    private val paths = HashMap<String, List<LatLon>>()

    private suspend fun trails(repo: TrailRepository): List<Trail> {
        if (cache.isNotEmpty() && System.currentTimeMillis() - cacheAt < CACHE_MS) return cache
        cache = runCatching { repo.observeTrails().first() }.getOrDefault(cache)
        cacheAt = System.currentTimeMillis()
        return cache
    }

    private fun pathOf(t: Trail): List<LatLon> = synchronized(paths) { paths.getOrPut(t.id + t.path.length) { Polyline.decode(t.path) } }

    /** Distanza in metri da (lat, lon) al tracciato (o al punto) del trail; MAX_VALUE se il trail non ha posizione. */
    private fun distance(t: Trail, lat: Double, lon: Double): Double {
        val kx = 111_320.0 * cos(Math.toRadians(lat)); val ky = 111_132.0
        val pts = if (t.path.isNotEmpty()) pathOf(t) else emptyList()
        if (pts.size >= 2) {
            var best = Double.MAX_VALUE
            for (i in 1 until pts.size) {
                val ax = (pts[i - 1].second - lon) * kx; val ay = (pts[i - 1].first - lat) * ky
                val bx = (pts[i].second - lon) * kx; val by = (pts[i].first - lat) * ky
                val dx = bx - ax; val dy = by - ay
                val l2 = dx * dx + dy * dy
                val u = if (l2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / l2).coerceIn(0.0, 1.0)
                best = minOf(best, hypot(ax + u * dx, ay + u * dy))
            }
            return best
        }
        val tl = t.lat ?: return Double.MAX_VALUE; val tn = t.lon ?: return Double.MAX_VALUE
        val d = hypot((tn - lon) * kx, (tl - lat) * ky)
        return if (d <= POINT_M) d else Double.MAX_VALUE
    }


    /** Trail che toccano il percorso (entro [ALONG_M] m), nell'ordine in cui li incontri. Vuoto se i trail non sono raggiungibili. */
    suspend fun alongRoute(repo: TrailRepository, lat: DoubleArray, lon: DoubleArray): List<Pair<Trail, Float>> = withContext(Dispatchers.Default) {
        if (lat.size < 2) return@withContext emptyList()
        val all = kotlinx.coroutines.withTimeoutOrNull(8000) { trails(repo) } ?: cache
        val step = maxOf(1, lat.size / 600)
        val ri = (lat.indices step step).toList().let { if (it.last() != lat.size - 1) it + (lat.size - 1) else it }
        val rLat = DoubleArray(ri.size) { lat[ri[it]] }; val rLon = DoubleArray(ri.size) { lon[ri[it]] }
        val kx = 111_320.0 * cos(Math.toRadians(rLat[rLat.size / 2])); val ky = 111_132.0
        fun nearestIdx(la: Double, lo: Double): Pair<Int, Double> {
            var bi = 0; var bd = Double.MAX_VALUE
            for (i in rLat.indices) { val d = hypot((rLon[i] - lo) * kx, (rLat[i] - la) * ky); if (d < bd) { bd = d; bi = i } }
            return bi to bd
        }
        val cumD = FloatArray(lat.size)
        for (i in 1 until lat.size) cumD[i] = cumD[i - 1] + hypot((lon[i] - lon[i - 1]) * kx, (lat[i] - lat[i - 1]) * ky).toFloat()
        val hits = ArrayList<Pair<Int, Trail>>()
        for (t in all) {
            val pts: List<LatLon> = if (t.path.isNotEmpty()) pathOf(t) else listOfNotNull(t.lat?.let { a -> t.lon?.let { b -> a to b } })
            if (pts.isEmpty()) continue
            val sample = pts.filterIndexed { i, _ -> i % maxOf(1, pts.size / 60) == 0 }
            var first = Int.MAX_VALUE
            for ((la, lo) in sample) { val (i, d) = nearestIdx(la, lo); if (d <= ALONG_M && i < first) first = i }
            if (first != Int.MAX_VALUE) hits.add(first to t)
        }
        hits.sortedBy { it.first }.map { it.second to cumD[ri[it.first]] }
    }

    /** Tutti i trail (cache di 10 minuti) per disegnarli sulla mappa di navigazione. */
    suspend fun all(repo: TrailRepository): List<Trail> = kotlinx.coroutines.withTimeoutOrNull(8000) { trails(repo) } ?: cache

    /** Punti (lat, lon) del tracciato di un trail; vuoto se ha solo un punto. */
    fun points(t: Trail): List<LatLon> = if (t.path.isNotEmpty()) pathOf(t) else emptyList()

    /** I trail più vicini a un punto (fino a 5 km), con la distanza in metri, per assegnare a mano una segnalazione. */
    suspend fun nearby(repo: TrailRepository, lat: Double, lon: Double, limit: Int = 12): List<Pair<Trail, Double>> = withContext(Dispatchers.Default) {
        trails(repo).map { it to distance(it, lat, lon) }.filter { it.second <= 5000.0 }.sortedBy { it.second }.take(limit)
    }

    /** Pubblica la segnalazione come commento sul trail scelto (stato Aperto → Attenzione per i tipi che lo meritano). */
    suspend fun attachTo(context: Context, repo: TrailRepository, trail: Trail, type: ReportType): Boolean {
        if (repo.currentUid == null) return false
        val caution = type in setOf(ReportType.TREE, ReportType.DUG, ReportType.FLOODED, ReportType.ROCKS, ReportType.DANGER)
        val status = if (caution && trail.status == TrailStatus.OPEN) TrailStatus.CAUTION else null
        val text = type.emoji + " " + context.getString(type.labelRes) + " · " + context.getString(R.string.nv_trail_auto)
        return repo.addComment(trail.id, text, status).isSuccess
    }

    /** Pubblica il commento sul trail vicino; restituisce il trail collegato o null (nessun trail vicino, non loggato, offline…). */
    suspend fun attach(context: Context, repo: TrailRepository, type: ReportType, lat: Double, lon: Double): Trail? = withContext(Dispatchers.Default) {
        if (repo.currentUid == null) return@withContext null
        val near = trails(repo).map { it to distance(it, lat, lon) }.filter { it.second <= MAX_M }.minByOrNull { it.second }?.first
            ?: return@withContext null
        val caution = type in setOf(ReportType.TREE, ReportType.DUG, ReportType.FLOODED, ReportType.ROCKS, ReportType.DANGER)
        val status = if (caution && near.status == TrailStatus.OPEN) TrailStatus.CAUTION else null
        val text = type.emoji + " " + context.getString(type.labelRes) + " · " + context.getString(R.string.nv_trail_auto)
        if (repo.addComment(near.id, text, status).isSuccess) near else null
    }

    /** Il trail di una segnalazione: quello indicato (id) o, per le vecchie segnalazioni, il più vicino entro [MAX_M] m. */
    suspend fun trailOf(repo: TrailRepository, trailId: String, lat: Double, lon: Double): Trail? {
        if (trailId.isNotEmpty()) all(repo).firstOrNull { it.id == trailId }?.let { return it }
        return nearby(repo, lat, lon, 1).firstOrNull()?.takeIf { it.second <= MAX_M }?.first
    }

    /** Riporta il trail ad «Aperto» con un commento (se non lo è già). Serve il login. */
    suspend fun reopen(repo: TrailRepository, trail: Trail, text: String): Boolean {
        if (trail.status == TrailStatus.OPEN || repo.currentUid == null) return false
        return repo.addComment(trail.id, text, TrailStatus.OPEN).isSuccess
    }
}
