package simplylucaapps.girocalendar.ui.gpx

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import simplylucaapps.girocalendar.util.Polyline
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Punto di un tracciato GPX (scheda GPX). */
data class GPt(val lat: Double, val lon: Double, val ele: Double? = null, val time: Instant? = null)

/** Tracciato (tutti i segmenti uniti) con nome. */
data class GTrack(val id: Long, val name: String, val pts: List<GPt>)

data class GStats(val distM: Double, val gain: Double, val loss: Double, val minEle: Double?, val maxEle: Double?, val durSec: Long?, val avgKmh: Double?)

object GpxModel {
    private var nextId = 1L
    fun newId() = nextId++

    /** Distanza in metri fra due coordinate (haversine). Unica implementazione dell'app. */
    fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 6371000.0 * 2 * atan2(sqrt(h), sqrt(1 - h))
    }

    fun dist(a: GPt, b: GPt): Double = haversineM(a.lat, a.lon, b.lat, b.lon)

    fun stats(p: List<GPt>): GStats {
        var d = 0.0; var gain = 0.0; var loss = 0.0
        for (i in 1 until p.size) {
            d += dist(p[i - 1], p[i])
            val e1 = p[i - 1].ele; val e2 = p[i].ele
            if (e1 != null && e2 != null) { if (e2 > e1) gain += e2 - e1 else loss += e1 - e2 }
        }
        val eles = p.mapNotNull { it.ele }
        val t1 = p.firstOrNull { it.time != null }?.time; val t2 = p.lastOrNull { it.time != null }?.time
        val dur = if (t1 != null && t2 != null) Duration.between(t1, t2).seconds.coerceAtLeast(0) else null
        val avg = if (dur != null && dur > 0) (d / 1000.0) / (dur / 3600.0) else null
        return GStats(d, gain, loss, eles.minOrNull(), eles.maxOrNull(), dur, avg)
    }

    /** Douglas-Peucker: toglie i punti entro [tolM] metri dalla retta fra i vicini. */
    fun simplify(p: List<GPt>, tolM: Double): List<GPt> {
        if (p.size < 3) return p
        val keep = Polyline.keepMask(p.map { it.lat to it.lon }, tolM)
        return p.filterIndexed { i, _ -> keep[i] }
    }

    /** Inverte il senso di marcia; i tempi vengono rimappati in modo che restino crescenti. */
    fun reverse(p: List<GPt>): List<GPt> {
        val t1 = p.firstOrNull()?.time; val t2 = p.lastOrNull()?.time
        val r = p.reversed()
        if (t1 == null || t2 == null) return r.map { it.copy(time = null) }
        return r.mapIndexed { i, pt -> pt.copy(time = p[i].time) }
    }

    fun parse(input: InputStream, fallbackName: String): List<GTrack> {
        val ps = Xml.newPullParser()
        ps.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        ps.setInput(input, null)
        val out = mutableListOf<GTrack>()
        var inTrk = false; var inRte = false; var inWpt = false
        var name: String? = null; var pts = mutableListOf<GPt>()
        var lat = 0.0; var lon = 0.0; var ele: Double? = null; var time: Instant? = null; var inPt = false
        var ev = ps.eventType; var n = 0
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (ps.name) {
                    "trk" -> { inTrk = true; name = null; pts = mutableListOf() }
                    "rte" -> { inRte = true; name = null; pts = mutableListOf() }
                    "wpt" -> inWpt = true
                    "trkpt", "rtept" -> {
                        inPt = true; ele = null; time = null
                        lat = ps.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                        lon = ps.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                    }
                    "ele" -> if (inPt) ele = ps.nextText().trim().toDoubleOrNull()
                    "time" -> if (inPt) time = runCatching { Instant.parse(ps.nextText().trim()) }.getOrNull()
                    "name" -> if ((inTrk || inRte) && !inPt && !inWpt) name = ps.nextText().trim()
                }
            } else if (ev == XmlPullParser.END_TAG) {
                when (ps.name) {
                    "trkpt", "rtept" -> { if (inPt) pts.add(GPt(lat, lon, ele, time)); inPt = false }
                    "wpt" -> inWpt = false
                    "trk", "rte" -> {
                        if (pts.size >= 2) { n++; out.add(GTrack(newId(), name?.takeIf { it.isNotBlank() } ?: (if (n == 1) fallbackName else "$fallbackName ($n)"), pts)) }
                        inTrk = false; inRte = false
                    }
                }
            }
            ev = ps.next()
        }
        return out
    }

    fun write(t: GTrack, out: OutputStream) {
        val s = Xml.newSerializer()
        s.setOutput(out, "UTF-8"); s.startDocument("UTF-8", true)
        s.startTag(null, "gpx"); s.attribute(null, "version", "1.1"); s.attribute(null, "creator", "GiroCalendar")
        s.attribute(null, "xmlns", "http://www.topografix.com/GPX/1/1")
        s.startTag(null, "trk"); s.startTag(null, "name"); s.text(t.name); s.endTag(null, "name")
        s.startTag(null, "trkseg")
        for (p in t.pts) {
            s.startTag(null, "trkpt"); s.attribute(null, "lat", p.lat.toString()); s.attribute(null, "lon", p.lon.toString())
            p.ele?.let { s.startTag(null, "ele"); s.text(it.toString()); s.endTag(null, "ele") }
            p.time?.let { s.startTag(null, "time"); s.text(DateTimeFormatter.ISO_INSTANT.format(it)); s.endTag(null, "time") }
            s.endTag(null, "trkpt")
        }
        s.endTag(null, "trkseg"); s.endTag(null, "trk"); s.endTag(null, "gpx")
        s.endDocument(); s.flush()
    }
}
