package simplylucaapps.girocalendar.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Suggerimenti aggiornabili senza rilasciare l'app: `tips.json` sul sito (stesso file del web), copiato in
 * filesDir. Il controllo in background parte al più ogni 6 ore; senza rete o prima del primo scaricamento
 * valgono quelli inclusi nell'app (array `tips`). Formato: {"v":1,"tips":[{"it","en","p":"android"|"web"?}]}.
 */
object TipsStore {
    private const val URL_TIPS = "https://girocalendar.pages.dev/tips.json"
    private const val EVERY_MS = 6 * 3600 * 1000L
    private fun file(ctx: Context) = java.io.File(ctx.filesDir, "tips.json")
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("girocalendar_boot", Context.MODE_PRIVATE)

    /** Elenco da mostrare, nella lingua dell'app; [bundled] se non c'è (ancora) una copia scaricata. */
    fun load(ctx: Context, bundled: List<String>): List<String> {
        val en = ctx.resources.configuration.locales[0].language == "en"
        val l = runCatching {
            val a = JSONObject(file(ctx).readText()).getJSONArray("tips")
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                if (o.optString("p") == "web") null
                else (if (en) o.optString("en") else "").ifBlank { o.optString("it") }.ifBlank { null }
            }
        }.getOrNull().orEmpty()
        return l.ifEmpty { bundled }
    }

    /** true se ha scaricato una versione nuova. Da chiamare in background; salta se l'ultimo controllo è recente. */
    suspend fun refreshIfStale(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val last = prefs(ctx).getLong("tips_checked_at", 0L)
        if (System.currentTimeMillis() - last < EVERY_MS) return@withContext false
        runCatching {
            val c = (URL("$URL_TIPS?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000; readTimeout = 6000
            }
            try {
                val text = c.inputStream.bufferedReader().readText()
                if (JSONObject(text).getJSONArray("tips").length() == 0) return@runCatching false
                val changed = runCatching { file(ctx).readText() }.getOrNull() != text
                val tmp = java.io.File(ctx.filesDir, "tips.json.tmp")
                tmp.writeText(text); tmp.renameTo(file(ctx))
                prefs(ctx).edit().putLong("tips_checked_at", System.currentTimeMillis()).apply()
                changed
            } finally { c.disconnect() }
        }.getOrDefault(false)
    }
}
