package simplylucaapps.girocalendar.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icona indicativa per un link in base al dominio, dato che Material Icons non include i
 * loghi di terze parti (a parte Facebook, un'eccezione nel set esteso). Categorie:
 * percorso/traccia (Komoot, Strava, Garmin, Wikiloc, AllTrails…), social con foto
 * (Instagram — niente logo dedicato, usiamo una fotocamera come proxy visivo), Facebook
 * (logo vero disponibile), video (YouTube e simili), altrimenti un link generico.
 */
fun iconForLink(url: String): ImageVector {
    val host = url.trim().lowercase()
    return when {
        host.isBlank() -> Icons.Default.Link
        listOf("komoot", "strava", "garmin", "wikiloc", "alltrails", "outdooractive").any { it in host } -> Icons.Default.Route
        "facebook.com" in host || "fb.com" in host -> Icons.Default.Facebook
        "instagram.com" in host -> Icons.Default.PhotoCamera
        "youtube.com" in host || "youtu.be" in host || "tiktok.com" in host -> Icons.Default.SmartDisplay
        else -> Icons.Default.Link
    }
}

/**
 * Nome del servizio riconosciuto dal dominio del link, usato per rietichettare dinamicamente
 * i campi link generici (es. "Altro link" -> "Instagram", "Link percorso" -> "Komoot",
 * "Video" -> "YouTube") invece di lasciare sempre l'etichetta generica. Null = dominio non
 * riconosciuto, il chiamante ripiega sull'etichetta generica.
 */
fun platformNameForLink(url: String): String? {
    val host = url.trim().lowercase()
    return when {
        host.isBlank() -> null
        "komoot" in host -> "Komoot"
        "strava" in host -> "Strava"
        "garmin" in host -> "Garmin"
        "wikiloc" in host -> "Wikiloc"
        "alltrails" in host -> "AllTrails"
        "outdooractive" in host -> "Outdooractive"
        "instagram.com" in host -> "Instagram"
        "facebook.com" in host || "fb.com" in host -> "Facebook"
        "youtube.com" in host || "youtu.be" in host -> "YouTube"
        "tiktok.com" in host -> "TikTok"
        "twitter.com" in host || "x.com" in host -> "X"
        "wa.me" in host || "whatsapp.com" in host -> "WhatsApp"
        "telegram.org" in host || "t.me" in host -> "Telegram"
        else -> null
    }
}
