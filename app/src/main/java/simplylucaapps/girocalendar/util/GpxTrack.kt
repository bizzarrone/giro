package simplylucaapps.girocalendar.util

import simplylucaapps.girocalendar.ui.gpx.GpxModel

/** Un punto della traccia GPX: coordinate + altitudine opzionale (metri). */
data class GpxPoint(val lat: Double, val lon: Double, val eleM: Double?)

/** Traccia GPX parsata, con le statistiche derivate mostrate nel popup Mappa. */
data class GpxTrack(
    val points: List<GpxPoint>,
    val distanceKm: Double,
    val minEleM: Double?,
    val maxEleM: Double?,
    val ascentM: Double,
    val descentM: Double,
)

/**
 * Vista di sola lettura per il popup Mappa: parsing e statistiche sono quelli di [GpxModel]
 * (tutti i segmenti uniti in un'unica traccia), qui si adatta solo la forma del risultato.
 */
object GpxTrackParser {

    fun parse(xml: String): GpxTrack? = runCatching {
        val pts = GpxModel.parse(xml.byteInputStream(), "").flatMap { it.pts }
        if (pts.isEmpty()) return@runCatching null
        val st = GpxModel.stats(pts)
        GpxTrack(
            points = pts.map { GpxPoint(it.lat, it.lon, it.ele) },
            distanceKm = st.distM / 1000.0,
            minEleM = st.minEle,
            maxEleM = st.maxEle,
            ascentM = st.gain,
            descentM = st.loss,
        )
    }.getOrNull()
}
