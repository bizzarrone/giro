package simplylucaapps.girocalendar.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** Logo/colore di un calendario d'origine (per gli eventi collegati da altri calendari). */
data class LinkedCalInfo(val logoColor: String, val logoData: String, val dominant: Int? = null)

/** Colore dominante del logo (pixel vivaci: si ignorano bianchi, grigi e neri); null se non c'è un logo immagine. */
private fun dominantColor(logoData: String): Int? = runCatching {
    if (logoData.isBlank()) return null
    val bytes = android.util.Base64.decode(logoData, android.util.Base64.DEFAULT)
    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }) ?: return null
    val small = android.graphics.Bitmap.createScaledBitmap(bmp, 24, 24, true)
    var r = 0.0; var g = 0.0; var b = 0.0; var w = 0.0
    val hsv = FloatArray(3)
    for (y in 0 until 24) for (x in 0 until 24) {
        val c = small.getPixel(x, y)
        if (android.graphics.Color.alpha(c) < 128) continue
        android.graphics.Color.colorToHSV(c, hsv)
        val wt = (hsv[1] * hsv[2]).toDouble()   // vivace e luminoso pesa di più
        if (hsv[1] < 0.35f || hsv[2] < 0.3f) continue
        r += android.graphics.Color.red(c) * wt; g += android.graphics.Color.green(c) * wt; b += android.graphics.Color.blue(c) * wt; w += wt
    }
    if (w <= 0.0) null else android.graphics.Color.rgb((r / w).toInt(), (g / w).toInt(), (b / w).toInt())
}.getOrNull()

/** Cache in memoria: una sola lettura per calendario, finché l'app resta aperta. */
private object LinkedCalCache {
    val map = HashMap<String, LinkedCalInfo>()
}

/** Restituisce le info del calendario `code` (null finché non sono lette, o se non esiste). */
@Composable
fun rememberLinkedCalInfo(code: String): LinkedCalInfo? {
    val st = produceState(LinkedCalCache.map[code], code) {
        if (code.isBlank() || value != null) return@produceState
        value = runCatching {
            val d = FirebaseFirestore.getInstance().collection("calendars").document(code).get().await()
            if (!d.exists()) null else d.getString("logoData").orEmpty().let { ld -> LinkedCalInfo(d.getString("logoColor").orEmpty(), ld, kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { dominantColor(ld) }) }
        }.getOrNull()?.also { LinkedCalCache.map[code] = it }
    }
    return st.value
}
