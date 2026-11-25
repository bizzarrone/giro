package simplylucaapps.girocalendar.util

import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Controllo "aggiornamento disponibile". Prima chiede al Play Store (Play In-App Updates API,
 * asincrono, funziona solo se l'app è installata da Play); se non risponde (APK installato a
 * mano, Play assente, errore) ripiega su version.json: legge `version.json` dal sito web
 * (`{"android": <versionCode ultima versione pubblicata>}`) e lo confronta col versionCode
 * installato. Il file va aggiornato a mano (~/web/girocalendar-web/public/version.json) quando
 * una nuova versione è disponibile sul Play Store. Un solo controllo per avvio del processo.
 */
object UpdateCheck {
    private const val URL_VERSION = "https://girocalendar.pages.dev/version.json"
    @Volatile private var cached: Boolean? = null

    suspend fun isUpdateAvailable(context: android.content.Context, installedCode: Int): Boolean {
        cached?.let { return it }
        val play = runCatching {
            val info = AppUpdateManagerFactory.create(context.applicationContext).appUpdateInfo.await()
            info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
        }.getOrNull()
        if (play != null) { cached = play; return play }
        val r = withContext(Dispatchers.IO) {
            runCatching {
                val c = (URL("$URL_VERSION?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000; readTimeout = 6000
                }
                try {
                    val latest = JSONObject(c.inputStream.bufferedReader().readText()).getInt("android")
                    latest > installedCode
                } finally { c.disconnect() }
            }.getOrNull()
        } ?: return false   // errore di rete: non memorizzo, riprovo alla prossima apertura
        cached = r
        return r
    }
}
