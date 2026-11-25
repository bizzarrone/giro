package simplylucaapps.girocalendar.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Foto del Mercatino: alta qualità (un documento per foto), miniatura piccola nel documento dell'annuncio. */
object MarketImage {
    private const val FULL_MAX_EDGE = 1600
    private const val FULL_MAX_BYTES = 600_000   // → ≈800 KB in base64, sotto il limite di 1 MiB per documento
    private const val THUMB_EDGE = 256

    /** Decodifica una foto della galleria (ridotta a max ~2000 px, con la rotazione EXIF applicata). */
    suspend fun decode(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
            val deg = when (ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f
            }
            if (deg != 0f) rotate(bmp, deg) else bmp
        }.getOrNull()
    }

    fun fromBase64(b64: String, sample: Int = 1): Bitmap? = runCatching {
        val b = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    fun rotate(bmp: Bitmap, deg: Float): Bitmap =
        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)

    /** Ritaglia secondo frazioni (0..1) di larghezza/altezza. */
    fun crop(bmp: Bitmap, l: Float, t: Float, r: Float, b: Float): Bitmap {
        val x = (l * bmp.width).toInt().coerceIn(0, bmp.width - 2); val y = (t * bmp.height).toInt().coerceIn(0, bmp.height - 2)
        val w = ((r - l) * bmp.width).toInt().coerceIn(2, bmp.width - x); val h = ((b - t) * bmp.height).toInt().coerceIn(2, bmp.height - y)
        return Bitmap.createBitmap(bmp, x, y, w, h)
    }

    private fun scaled(bmp: Bitmap, edge: Int): Bitmap {
        val s = minOf(1f, edge.toFloat() / maxOf(bmp.width, bmp.height))
        return if (s >= 1f) bmp else Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
    }

    /** JPEG base64 (senza prefisso) a piena qualità: lato max 1600 px, qualità 90 che scende solo se serve. */
    fun encodeFull(bmp: Bitmap): String {
        val sc = scaled(bmp, FULL_MAX_EDGE)
        var q = 90
        var out: ByteArray
        do {
            val bos = ByteArrayOutputStream(); sc.compress(Bitmap.CompressFormat.JPEG, q, bos); out = bos.toByteArray(); q -= 5
        } while (out.size > FULL_MAX_BYTES && q >= 50)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun thumb(bmp: Bitmap): String {
        val bos = ByteArrayOutputStream(); scaled(bmp, THUMB_EDGE).compress(Bitmap.CompressFormat.JPEG, 70, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }
}
