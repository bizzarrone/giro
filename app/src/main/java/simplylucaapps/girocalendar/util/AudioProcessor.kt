package simplylucaapps.girocalendar.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Risultato della lettura di un file audio scelto dall'utente per un evento. */
sealed class AudioResult {
    data class Ok(val audioData: String, val mimeType: String) : AudioResult()
    /** Dimensione del file oltre il limite di sicurezza. */
    data class TooLarge(val sizeKb: Int) : AudioResult()
    object ReadError : AudioResult()
}

/**
 * Carica una clip audio per la riproduzione nel viewer foto a tutto schermo di un'uscita.
 * Il file sorgente scelto dall'utente può avere qualunque durata: viene sempre passato prima
 * da [AudioEditor] (taglio a max 60s + fade opzionali), che produce il file M4A finale già
 * dentro i limiti — qui si valida solo la dimensione risultante prima di salvarla in base64.
 */
object AudioProcessor {
    /** Limite sui byte BINARI del file, non sulla stringa base64 poi salvata in Firestore (che
     * pesa ~37% in più: 4/3 dei byte originali). L'audio vive in un documento Firestore
     * SEPARATO dall'evento (vedi EventAudio/CalendarRepository.saveEventAudio), con il proprio
     * limite di 1 MiB indipendente da foto e GPX — niente più bisogno di comprimere l'audio per
     * lasciare spazio ad altri campi. Il bitrate target dell'encoder (128 kbps, vedi AudioEditor)
     * è solo indicativo: un clip di 45s misurato in pratica pesa ~827 KB (contenitore M4A/AAC
     * reale, non il calcolo ideale bitrate*durata). Il vero tetto è imposto da
     * CalendarRepository.saveEventAudio (base64 sotto ~1.040.000 byte, limite Firestore
     * 1_048_576): questo valore lascia margine sufficiente perché quel controllo, non questo,
     * sia l'ultima parola. */
    private const val MAX_BYTES = 900 * 1024
    /** Oltre questa dimensione il file SORGENTE (prima del taglio) non viene nemmeno aperto in
     * memoria per leggerne la durata, per evitare OOM su registrazioni molto lunghe scelte per
     * errore. 15 MB copre ampiamente qualche minuto di parlato anche non compresso. */
    private const val MAX_SOURCE_BYTES = 15 * 1024 * 1024

    /** Legge un file sorgente (di qualunque durata, entro [MAX_SOURCE_BYTES]) e ne ricava
     * MIME e durata, per popolare l'editor di taglio. Null se il file non è leggibile o troppo
     * grande per essere anche solo aperto. */
    suspend fun openSource(context: Context, uri: Uri): SourceAudio? = withContext(Dispatchers.IO) {
        val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: return@withContext null
        if (size <= 0 || size > MAX_SOURCE_BYTES) return@withContext null
        val mimeType = context.contentResolver.getType(uri) ?: "audio/mp4"
        val durationMs = durationOf(context, uri) ?: return@withContext null
        SourceAudio(uri, mimeType, durationMs)
    }

    /** Legge il file già tagliato/con fade (uscita di [AudioEditor]) e lo prepara per il salvataggio. */
    suspend fun loadEdited(file: java.io.File, mimeType: String): AudioResult = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = file.readBytes()
            if (bytes.size > MAX_BYTES) return@withContext AudioResult.TooLarge(bytes.size / 1024)
            AudioResult.Ok(audioData = Base64.encodeToString(bytes, Base64.DEFAULT), mimeType = mimeType)
        }.getOrElse { AudioResult.ReadError }
    }

    /** Durata in millisecondi del file audio all'uri indicato, null se non determinabile.
     * MediaMetadataRetriever implementa AutoCloseable solo da API 29: release() esplicito in
     * finally invece di use{} per restare sicuri anche sul minSdk 26 del progetto. */
    fun durationOf(context: Context, uri: Uri): Long? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            retriever.release()
        }
    }.getOrNull()
}

/** File audio sorgente scelto dall'utente, prima del taglio. */
data class SourceAudio(val uri: Uri, val mimeType: String, val durationMs: Long)
