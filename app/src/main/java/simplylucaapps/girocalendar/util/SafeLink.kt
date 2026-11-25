package simplylucaapps.girocalendar.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Link che arrivano da Firestore (scritti da altri utenti): si aprono solo se sono http(s) con un host.
 * Senza questo controllo un valore come "tel:", "sms:", "content://" o "market://" veniva passato
 * direttamente ad ACTION_VIEW sotto un'etichetta innocua ("Komoot", "Video", …).
 */
fun safeWebUri(raw: String): Uri? {
    val u = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return null
    val scheme = u.scheme?.lowercase()
    return if ((scheme == "http" || scheme == "https") && !u.host.isNullOrBlank()) u else null
}

/** Apre [raw] nel browser/app solo se [safeWebUri] lo accetta. Ritorna false se scartato. */
fun openWebLink(context: Context, raw: String): Boolean {
    val u = safeWebUri(raw) ?: return false
    return runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}
