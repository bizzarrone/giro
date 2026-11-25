package simplylucaapps.girocalendar.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Comprime un logo scelto dalla galleria in un PNG base64 puro (nessun prefisso data:), lo
 * stesso formato che [simplylucaapps.girocalendar.ui.common.CalendarLogo] decodifica con
 * Base64.decode + BitmapFactory. Ritaglio quadrato al centro.
 */
object LogoImageProcessor {
    private const val MAX_DIMENSION = 200
    private const val TARGET_BYTES = 40 * 1024

    suspend fun compressToBase64(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeSampledBitmap(context, uri) ?: return@withContext null
            val squared = cropToSquare(bitmap)
            val scaled = scaleDown(squared, MAX_DIMENSION)
            val bytes = compressToTargetSize(scaled)
            Base64.encodeToString(bytes, Base64.DEFAULT)
        }.getOrNull()
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri): Bitmap? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var inSampleSize = 1
        if (bounds.outHeight > MAX_DIMENSION || bounds.outWidth > MAX_DIMENSION) {
            val halfHeight = bounds.outHeight / 2
            val halfWidth = bounds.outWidth / 2
            while (halfHeight / inSampleSize >= MAX_DIMENSION && halfWidth / inSampleSize >= MAX_DIMENSION) {
                inSampleSize *= 2
            }
        }
        val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - side) / 2
        val y = (bitmap.height - side) / 2
        return Bitmap.createBitmap(bitmap, x, y, side, side)
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        if (maxOf(bitmap.width, bitmap.height) <= maxDimension) return bitmap
        return Bitmap.createScaledBitmap(bitmap, maxDimension, maxDimension, true)
    }

    private fun compressToTargetSize(bitmap: Bitmap): ByteArray {
        var quality = 90
        while (quality >= 30) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, quality, out)
            val bytes = out.toByteArray()
            if (bytes.size <= TARGET_BYTES) return bytes
            quality -= 15
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
        return out.toByteArray()
    }
}
