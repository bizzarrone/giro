package simplylucaapps.girocalendar.data.remote.weather

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "WeatherRepository"

/** Previsione per una singola ora: temperatura, probabilità di pioggia, vento, codice WMO. */
data class WeatherForecast(
    val hour: Int,
    val temperatureC: Double,
    val precipitationProbability: Int,
    val windSpeedKmh: Double,
    val weatherCode: Int,
) {
    /** Emoji indicativa dal codice WMO (stessa mappa usata dal sito Spaccasassi). */
    val emoji: String get() = when (weatherCode) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55 -> "🌦️"
        61, 63, 65 -> "🌧️"
        71, 73, 75, 77 -> "🌨️"
        80, 81, 82 -> "🌧️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }
}

/**
 * Meteo via Open-Meteo (gratuito, senza chiave API), chiamato direttamente dal client Android
 * — a differenza del sito PHP di Spaccasassi non serve un proxy server-side: le richieste da
 * app nativa non hanno restrizioni CORS. Stesso servizio, stessi campi (temperature_2m,
 * precipitation_probability, windspeed_10m, weathercode).
 */
object WeatherRepository {

    /** Geocodifica un nome di località in lat/lon (prima corrispondenza). Null se non trovato. */
    suspend fun geocode(place: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        if (place.isBlank()) return@withContext null
        runCatching {
            val encoded = URLEncoder.encode(place.trim(), "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=it&format=json"
            val json = httpGetJson(url) ?: return@withContext null
            val results = json.optJSONArray("results") ?: return@withContext null
            if (results.length() == 0) return@withContext null
            val first = results.getJSONObject(0)
            first.getDouble("latitude") to first.getDouble("longitude")
        }.onFailure { Log.w(TAG, "geocode failed", it) }.getOrNull()
    }

    /**
     * Previsione oraria centrata su [dateDdMmYyyy]/[timeHhMm] (default mezzogiorno se l'ora
     * non è specificata o non parsabile): l'ora esatta più le due ore prima e le due dopo
     * (fino a 5 valori, meno ai bordi della giornata, es. per un'uscita all'alba o a
     * mezzanotte). Lista vuota se fuori dalla finestra di previsione di Open-Meteo (oltre
     * ~16 giorni) o in caso di errore di rete — un'unica chiamata HTTP, i dati orari
     * dell'intera giornata arrivano già tutti insieme.
     */
    suspend fun forecastFor(
        lat: Double, lon: Double, dateDdMmYyyy: String, timeHhMm: String,
    ): List<WeatherForecast> = withContext(Dispatchers.IO) {
        runCatching {
            val isoDate = toIsoDate(dateDdMmYyyy) ?: return@withContext emptyList()
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&hourly=temperature_2m,precipitation_probability,windspeed_10m,weathercode" +
                "&timezone=Europe%2FRome&start_date=$isoDate&end_date=$isoDate"
            val json = httpGetJson(url) ?: return@withContext emptyList()
            val hourly = json.optJSONObject("hourly") ?: return@withContext emptyList()
            val times = hourly.optJSONArray("time") ?: return@withContext emptyList()
            val targetHour = (timeHhMm.split(":").firstOrNull()?.toIntOrNull() ?: 12).coerceIn(0, 23)
            val wantedHours = (targetHour - 2)..(targetHour + 2)

            val byHour = HashMap<Int, Int>() // ora -> indice nell'array hourly
            for (i in 0 until times.length()) {
                val t = times.getString(i) // "2026-10-03T09:00"
                val hour = t.substringAfter("T").substringBefore(":").toIntOrNull() ?: continue
                if (hour in wantedHours) byHour[hour] = i
            }
            wantedHours.mapNotNull { hour ->
                val idx = byHour[hour] ?: return@mapNotNull null
                WeatherForecast(
                    hour = hour,
                    temperatureC = hourly.getJSONArray("temperature_2m").getDouble(idx),
                    precipitationProbability = hourly.getJSONArray("precipitation_probability").getInt(idx),
                    windSpeedKmh = hourly.getJSONArray("windspeed_10m").getDouble(idx),
                    weatherCode = hourly.getJSONArray("weathercode").getInt(idx),
                )
            }
        }.onFailure { Log.w(TAG, "forecastFor failed", it) }.getOrElse { emptyList() }
    }

    /** "03/10/2026" -> "2026-10-03". Null se non parsabile come data italiana gg/mm/aaaa. */
    private fun toIsoDate(ddMmYyyy: String): String? {
        val p = ddMmYyyy.trim().split("/")
        if (p.size != 3) return null
        val d = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        val y = p[2].toIntOrNull() ?: return null
        return "%04d-%02d-%02d".format(y, m, d)
    }

    private fun httpGetJson(urlStr: String): JSONObject? {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("User-Agent", "GiroCalendar/1.0")
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }
}
