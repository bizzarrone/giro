package simplylucaapps.girocalendar.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Le due versioni compresse di un'immagine caricata per un evento. */
data class EventPhoto(
    /** JPEG base64, ritagliata 16:9: sfondo della card nella lista (`.has-card-bg`). */
    val cardBgData: String,
    /** JPEG base64, scalata SENZA ritaglio (aspect ratio originale preservato): usata nel
     * banner del dettaglio evento e nel viewer a tutto schermo, dove si vede la foto intera. */
    val photoData: String,
)

/**
 * Comprime un'immagine scelta dalla galleria in due versioni JPEG base64 (nessun prefisso
 * data:) per un evento: una ritagliata 16:9 per lo sfondo della card (deve riempire un
 * rettangolo fisso), una scalata mantenendo l'aspect ratio originale per mostrare la foto
 * intera altrove. A differenza di [LogoImageProcessor] (quadrato, pensato per un'icona) qui
 * le dimensioni sono maggiori e la compressione più aggressiva, per restare ben sotto il
 * limite di 1 MiB per documento di Firestore anche con due immagini nello stesso evento.
 */
object CardBackgroundProcessor {
    private const val MAX_WIDTH = 800
    private const val ASPECT_RATIO = 16f / 9f // larghezza / altezza, solo per la versione crop
    private const val TARGET_BYTES = 180 * 1024

    suspend fun compressToBase64(context: Context, uri: Uri): EventPhoto? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeSampledBitmap(context, uri) ?: return@withContext null

            val cropped = cropToAspectRatio(bitmap)
            val croppedScaled = scaleDownToWidth(cropped, MAX_WIDTH)
            val cardBgData = Base64.encodeToString(compressToTargetSize(croppedScaled), Base64.DEFAULT)

            val fullScaled = scaleDownToWidth(bitmap, MAX_WIDTH)
            val photoData = Base64.encodeToString(compressToTargetSize(fullScaled), Base64.DEFAULT)

            EventPhoto(cardBgData = cardBgData, photoData = photoData)
        }.getOrNull()
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri): Bitmap? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var inSampleSize = 1
        val target = MAX_WIDTH * 2
        if (bounds.outWidth > target || bounds.outHeight > target) {
            val halfWidth = bounds.outWidth / 2
            val halfHeight = bounds.outHeight / 2
            while (halfWidth / inSampleSize >= target && halfHeight / inSampleSize >= target) {
                inSampleSize *= 2
            }
        }
        val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }

    private fun cropToAspectRatio(bitmap: Bitmap): Bitmap {
        val currentRatio = bitmap.width.toFloat() / bitmap.height
        return if (currentRatio > ASPECT_RATIO) {
            // troppo largo: taglia i lati
            val targetWidth = (bitmap.height * ASPECT_RATIO).toInt().coerceAtMost(bitmap.width)
            val x = (bitmap.width - targetWidth) / 2
            Bitmap.createBitmap(bitmap, x, 0, targetWidth, bitmap.height)
        } else {
            // troppo alto: taglia sopra/sotto (leggero bias verso l'alto, dove di solito
            // sta il soggetto principale di una foto outdoor)
            val targetHeight = (bitmap.width / ASPECT_RATIO).toInt().coerceAtMost(bitmap.height)
            val y = ((bitmap.height - targetHeight) * 0.35f).toInt()
            Bitmap.createBitmap(bitmap, 0, y, bitmap.width, targetHeight)
        }
    }

    private fun scaleDownToWidth(bitmap: Bitmap, maxWidth: Int): Bitmap {
        if (bitmap.width <= maxWidth) return bitmap
        val targetHeight = (bitmap.height.toFloat() / bitmap.width * maxWidth).toInt()
        return Bitmap.createScaledBitmap(bitmap, maxWidth, targetHeight, true)
    }

    private fun compressToTargetSize(bitmap: Bitmap): ByteArray {
        var quality = 85
        while (quality >= 30) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            if (bytes.size <= TARGET_BYTES) return bytes
            quality -= 10
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 30, out)
        return out.toByteArray()
    }
}
