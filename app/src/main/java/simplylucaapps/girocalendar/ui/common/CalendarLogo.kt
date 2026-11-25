package simplylucaapps.girocalendar.ui.common

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Cache globale dei bitmap decodificati: senza, ogni volta che una card esce/rientra nel
// viewport di una LazyColumn, Compose rifà il Base64.decode + BitmapFactory da zero.
private val logoBitmapCache = LruCache<String, ImageBitmap?>(64)

private fun decodeLogo(logoData: String): ImageBitmap? {
    logoBitmapCache.get(logoData)?.let { return it }
    if (logoBitmapCache.snapshot().containsKey(logoData)) return null // già tentato, fallito
    val decoded = runCatching {
        val bytes = Base64.decode(logoData, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
    logoBitmapCache.put(logoData, decoded)
    return decoded
}

/**
 * Il "pallino" del calendario: il logo caricato (PNG base64, ritagliato a cerchio) se c'è,
 * altrimenti un cerchio pieno del colore scelto. Stessa dimensione nei due casi.
 */
@Composable
fun CalendarLogo(
    code: String,
    logoColor: String,
    logoData: String,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    val bmp = remember(logoData) {
        if (logoData.isBlank()) null else decodeLogo(logoData)
    }
    val fill = remember(code, logoColor) { dotColor(code, logoColor) }
    val ring = Color.Black.copy(alpha = 0.12f)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Box(Modifier.size(size).background(fill))
        }
    }
}

/** Colore del pallino: quello scelto, o uno derivato deterministicamente dal codice. */
fun dotColor(code: String, logoColor: String): Color {
    parseHexColor(logoColor)?.let { return it }
    val h = (code.fold(0) { acc, ch -> acc * 31 + ch.code } and 0x7FFFFFFF) % 360
    return Color.hsv(h.toFloat(), 0.55f, 0.75f)
}

fun parseHexColor(hex: String): Color? {
    val h = hex.trim().removePrefix("#")
    if (h.length != 6) return null
    val v = h.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or v)
}
