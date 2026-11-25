package simplylucaapps.girocalendar.ui.gpx

import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.OutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formati di tracciato supportati dal convertitore (lettura e scrittura). */
enum class TrackFormat(val ext: String, val mime: String, val label: String) {
    GPX("gpx", "application/gpx+xml", "GPX"),
    KML("kml", "application/vnd.google-earth.kml+xml", "KML"),
    GEOJSON("geojson", "application/geo+json", "GeoJSON"),
    TCX("tcx", "application/vnd.garmin.tcx+xml", "TCX"),
    CSV("csv", "text/csv", "CSV"),
}

object TrackFormats {
    private fun iso(t: Instant) = DateTimeFormatter.ISO_INSTANT.format(t)
    private fun num(d: Double) = String.format(Locale.US, "%.7f", d).trimEnd('0').trimEnd('.')
    private fun m(d: Double) = String.format(Locale.US, "%.1f", d)

    fun write(t: GTrack, f: TrackFormat, out: OutputStream) {
        when (f) {
            TrackFormat.GPX -> GpxModel.write(t, out)
            TrackFormat.KML -> writeKml(t, out)
            TrackFormat.GEOJSON -> out.write(toGeoJson(t).toByteArray(Charsets.UTF_8))
            TrackFormat.TCX -> writeTcx(t, out)
            TrackFormat.CSV -> out.write(toCsv(t).toByteArray(Charsets.UTF_8))
        }
        out.flush()
    }

    private fun serializer(out: OutputStream) = Xml.newSerializer().also { it.setOutput(out, "UTF-8"); it.startDocument("UTF-8", true) }
    private fun org.xmlpull.v1.XmlSerializer.el(name: String, text: String) { startTag(null, name); text(text); endTag(null, name) }

    private fun writeKml(t: GTrack, out: OutputStream) {
        val s = serializer(out)
        s.startTag(null, "kml"); s.attribute(null, "xmlns", "http://www.opengis.net/kml/2.2")
        s.startTag(null, "Document"); s.el("name", t.name)
        s.startTag(null, "Placemark"); s.el("name", t.name)
        s.startTag(null, "LineString"); s.el("tessellate", "1")
        s.el("coordinates", t.pts.joinToString(" ") { p -> num(p.lon) + "," + num(p.lat) + (p.ele?.let { "," + m(it) } ?: "") })
        s.endTag(null, "LineString"); s.endTag(null, "Placemark"); s.endTag(null, "Document"); s.endTag(null, "kml")
        s.endDocument()
    }

    private fun toGeoJson(t: GTrack): String {
        val coords = JSONArray()
        for (p in t.pts) coords.put(JSONArray().put(p.lon).put(p.lat).also { a -> p.ele?.let { a.put(it) } })
        val props = JSONObject().put("name", t.name)
        if (t.pts.isNotEmpty() && t.pts.all { it.time != null }) {
            props.put("coordinateProperties", JSONObject().put("times", JSONArray(t.pts.map { iso(it.time!!) })))
        }
        val feat = JSONObject().put("type", "Feature").put("properties", props)
            .put("geometry", JSONObject().put("type", "LineString").put("coordinates", coords))
        return JSONObject().put("type", "FeatureCollection").put("features", JSONArray().put(feat)).toString(1)
    }

    private fun writeTcx(t: GTrack, out: OutputStream) {
        // TCX richiede un orario per ogni punto: se il tracciato non ne ha, lo stimo a 15 km/h.
        val hasTime = t.pts.isNotEmpty() && t.pts.all { it.time != null }
        val start = t.pts.firstOrNull()?.time ?: Instant.now()
        var cum = 0.0
        val s = serializer(out)
        s.startTag(null, "TrainingCenterDatabase"); s.attribute(null, "xmlns", "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2")
        s.startTag(null, "Activities"); s.startTag(null, "Activity"); s.attribute(null, "Sport", "Biking")
        s.el("Id", iso(start))
        s.startTag(null, "Lap"); s.attribute(null, "StartTime", iso(start))
        s.startTag(null, "Track")
        t.pts.forEachIndexed { i, p ->
            if (i > 0) cum += GpxModel.dist(t.pts[i - 1], p)
            val time = if (hasTime) p.time!! else start.plusMillis((cum / 4.1667 * 1000).toLong())
            s.startTag(null, "Trackpoint"); s.el("Time", iso(time))
            s.startTag(null, "Position"); s.el("LatitudeDegrees", num(p.lat)); s.el("LongitudeDegrees", num(p.lon)); s.endTag(null, "Position")
            p.ele?.let { s.el("AltitudeMeters", m(it)) }
            s.el("DistanceMeters", m(cum)); s.endTag(null, "Trackpoint")
        }
        s.endTag(null, "Track"); s.endTag(null, "Lap")
        s.el("Notes", t.name)
        s.endTag(null, "Activity"); s.endTag(null, "Activities"); s.endTag(null, "TrainingCenterDatabase")
        s.endDocument()
    }

    private fun toCsv(t: GTrack): String {
        val sb = StringBuilder("lat,lon,ele,time\n")
        for (p in t.pts) sb.append(num(p.lat)).append(',').append(num(p.lon)).append(',')
            .append(p.ele?.let { m(it) } ?: "").append(',').append(p.time?.let { iso(it) } ?: "").append('\n')
        return sb.toString()
    }

    // ---------- lettura ----------

    /** Riconosce il formato dal contenuto (non dall'estensione) e restituisce i tracciati; vuoto se non riconosciuto. */
    fun parse(bytes: ByteArray, fallbackName: String): List<GTrack> {
        val head = String(bytes, 0, minOf(bytes.size, 2000), Charsets.UTF_8).trimStart('﻿', ' ', '\n', '\r', '\t')
        return runCatching {
            when {
                head.startsWith("{") -> parseGeoJson(String(bytes, Charsets.UTF_8), fallbackName)
                head.startsWith("<") -> when {
                    head.contains("<gpx") -> GpxModel.parse(bytes.inputStream(), fallbackName)
                    head.contains("<kml") -> parseKml(bytes, fallbackName)
                    head.contains("TrainingCenterDatabase") -> parseTcx(bytes, fallbackName)
                    else -> GpxModel.parse(bytes.inputStream(), fallbackName)
                }
                else -> parseCsv(String(bytes, Charsets.UTF_8), fallbackName)
            }
        }.getOrElse { android.util.Log.d("TrackFormats", "parse failed: $it"); emptyList() }
    }

    private fun parseKml(bytes: ByteArray, fb: String): List<GTrack> {
        val ps = Xml.newPullParser(); ps.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); ps.setInput(bytes.inputStream(), null)
        val out = mutableListOf<GTrack>()
        var pmName: String? = null; var pts = mutableListOf<GPt>(); var whens = mutableListOf<Instant?>(); var n = 0
        fun flush() {
            if (pts.size >= 2) { n++; out.add(GTrack(GpxModel.newId(), pmName?.takeIf { it.isNotBlank() } ?: if (n == 1) fb else "$fb ($n)", pts)) }
            pts = mutableListOf(); whens = mutableListOf()
        }
        var ev = ps.eventType; var inPm = false
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) when (ps.name) {
                "Placemark" -> { inPm = true; pmName = null; pts = mutableListOf(); whens = mutableListOf() }
                "name" -> if (inPm && pmName == null) pmName = ps.nextText().trim()
                "when" -> whens.add(runCatching { Instant.parse(ps.nextText().trim()) }.getOrNull())
                "gx:coord" -> {
                    val v = ps.nextText().trim().split(Regex("\\s+")).mapNotNull { it.toDoubleOrNull() }
                    if (v.size >= 2) pts.add(GPt(v[1], v[0], v.getOrNull(2), whens.getOrNull(pts.size)))
                }
                "coordinates" -> ps.nextText().trim().split(Regex("\\s+")).forEach { c ->
                    val v = c.split(',').mapNotNull { it.toDoubleOrNull() }
                    if (v.size >= 2) pts.add(GPt(v[1], v[0], v.getOrNull(2)?.takeIf { it != 0.0 || v.size > 2 }))
                }
            } else if (ev == XmlPullParser.END_TAG && ps.name == "Placemark") { flush(); inPm = false }
            ev = ps.next()
        }
        return out
    }

    private fun parseTcx(bytes: ByteArray, fb: String): List<GTrack> {
        val ps = Xml.newPullParser(); ps.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); ps.setInput(bytes.inputStream(), null)
        val pts = mutableListOf<GPt>()
        var lat: Double? = null; var lon: Double? = null; var ele: Double? = null; var time: Instant? = null
        var ev = ps.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) when (ps.name) {
                "Trackpoint" -> { lat = null; lon = null; ele = null; time = null }
                "Time" -> time = runCatching { Instant.parse(ps.nextText().trim()) }.getOrNull()
                "LatitudeDegrees" -> lat = ps.nextText().trim().toDoubleOrNull()
                "LongitudeDegrees" -> lon = ps.nextText().trim().toDoubleOrNull()
                "AltitudeMeters" -> ele = ps.nextText().trim().toDoubleOrNull()
            } else if (ev == XmlPullParser.END_TAG && ps.name == "Trackpoint") {
                if (lat != null && lon != null) pts.add(GPt(lat!!, lon!!, ele, time))
            }
            ev = ps.next()
        }
        return if (pts.size >= 2) listOf(GTrack(GpxModel.newId(), fb, pts)) else emptyList()
    }

    private fun parseGeoJson(text: String, fb: String): List<GTrack> {
        val lines = mutableListOf<Triple<String?, JSONArray, JSONArray?>>()
        fun geom(g: JSONObject?, name: String?, times: JSONArray?) {
            g ?: return
            when (g.optString("type")) {
                "LineString" -> lines.add(Triple(name, g.getJSONArray("coordinates"), times))
                "MultiLineString" -> { val a = g.getJSONArray("coordinates"); for (i in 0 until a.length()) lines.add(Triple(name, a.getJSONArray(i), null)) }
                "GeometryCollection" -> { val a = g.getJSONArray("geometries"); for (i in 0 until a.length()) geom(a.getJSONObject(i), name, null) }
            }
        }
        fun node(o: JSONObject) {
            when (o.optString("type")) {
                "FeatureCollection" -> { val a = o.getJSONArray("features"); for (i in 0 until a.length()) node(a.getJSONObject(i)) }
                "Feature" -> {
                    val pr = o.optJSONObject("properties")
                    geom(o.optJSONObject("geometry"), pr?.optString("name")?.takeIf { it.isNotBlank() }, pr?.optJSONObject("coordinateProperties")?.optJSONArray("times"))
                }
                else -> geom(o, null, null)
            }
        }
        node(JSONObject(text))
        return lines.mapIndexedNotNull { i, (nm, c, times) ->
            val pts = (0 until c.length()).map { k ->
                val a = c.getJSONArray(k)
                GPt(a.getDouble(1), a.getDouble(0), if (a.length() > 2) a.optDouble(2).takeIf { !it.isNaN() } else null,
                    times?.optString(k)?.let { runCatching { Instant.parse(it) }.getOrNull() })
            }
            if (pts.size < 2) null else GTrack(GpxModel.newId(), nm ?: if (i == 0) fb else "$fb (${i + 1})", pts)
        }
    }

    private fun parseCsv(text: String, fb: String): List<GTrack> {
        val rows = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (rows.size < 3) return emptyList()
        val sep = if (rows[0].count { it == ';' } > rows[0].count { it == ',' }) ';' else ','
        val head = rows[0].split(sep).map { it.trim().trim('"').lowercase() }
        fun idx(vararg names: String) = head.indexOfFirst { it in names }
        var iLat = idx("lat", "latitude", "latitudine"); var iLon = idx("lon", "lng", "long", "longitude", "longitudine")
        val iEle = idx("ele", "elevation", "alt", "altitude", "quota"); val iTime = idx("time", "timestamp", "date", "data")
        var first = 1
        if (iLat < 0 || iLon < 0) { iLat = 0; iLon = 1; first = if (rows[0].split(sep)[0].toDoubleOrNull() != null) 0 else 1 }
        val pts = rows.drop(first).mapNotNull { r ->
            val c = r.split(sep).map { it.trim().trim('"') }
            val la = c.getOrNull(iLat)?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
            val lo = c.getOrNull(iLon)?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
            GPt(la, lo, c.getOrNull(iEle)?.takeIf { iEle >= 0 }?.replace(',', '.')?.toDoubleOrNull(),
                c.getOrNull(iTime)?.takeIf { iTime >= 0 }?.let { runCatching { Instant.parse(it) }.getOrNull() })
        }
        return if (pts.size >= 2) listOf(GTrack(GpxModel.newId(), fb, pts)) else emptyList()
    }
}
