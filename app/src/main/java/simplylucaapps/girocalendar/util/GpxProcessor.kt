package simplylucaapps.girocalendar.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Risultato della lettura di un file GPX scelto dall'utente. */
sealed class GpxResult {
    data class Ok(val gpxData: String, val fileName: String) : GpxResult()
    /** File troppo grande anche dopo la compressione: va rifiutato invece di rischiare di
     * sforare il limite Firestore di 1 MiB per documento insieme alle immagini dell'evento. */
    data class TooLarge(val originalSizeKb: Int) : GpxResult()
    object ReadError : GpxResult()
}

/**
 * Carica una traccia GPX scelta dall'utente e la comprime (gzip + base64, nessun prefisso
 * data:) prima di salvarla nel documento evento. Un GPX è XML molto ripetitivo (tag e
 * attributi ripetuti per ogni punto), comprime tipicamente al 10-20% della dimensione
 * originale: un file di 500 KB scende a 50-100 KB, ben dentro il limite di 1 MiB per
 * documento anche con le due immagini di [CardBackgroundProcessor] già presenti.
 */
object GpxProcessor {
    /** Oltre questa soglia sul file ORIGINALE (prima della compressione) rifiutiamo l'upload:
     * anche con una compressione ottima il rischio di avvicinarsi al limite del documento
     * resta troppo alto se ci sono già foto. 2 MB coprono ampiamente un giro di più ore con
     * tracciamento GPS fitto. */
    private const val MAX_ORIGINAL_BYTES = 2 * 1024 * 1024

    suspend fun load(context: Context, uri: Uri, fileName: String): GpxResult = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext GpxResult.ReadError
            if (bytes.size > MAX_ORIGINAL_BYTES) {
                return@withContext GpxResult.TooLarge(bytes.size / 1024)
            }
            val compressed = ByteArrayOutputStream().apply {
                GZIPOutputStream(this).use { it.write(bytes) }
            }.toByteArray()
            val gpxData = Base64.encodeToString(compressed, Base64.DEFAULT)
            GpxResult.Ok(gpxData = gpxData, fileName = fileName)
        }.getOrElse { GpxResult.ReadError }
    }

    /** Decomprime per l'export/condivisione: testo XML del GPX originale. */
    fun decompress(gpxData: String): String? = runCatching {
        val compressed = Base64.decode(gpxData, Base64.DEFAULT)
        GZIPInputStream(compressed.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
    }.getOrNull()
}
