package simplylucaapps.girocalendar.util

import simplylucaapps.girocalendar.R

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import simplylucaapps.girocalendar.data.remote.firestore.RideEvent

/**
 * Export semplice del calendario: solo testo condivisibile e GPX (nessuna locandina
 * immagine — troppo specifica del dominio pallavolo/VolleyCalendar per questo scaffold).
 */
object EventExport {

    private fun cacheFile(context: Context, name: String): File =
        File(context.cacheDir, "export").apply { mkdirs() }.let { File(it, name) }

    private fun uriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    // ── Testo ───────────────────────────────────────────────────

    fun shareText(context: Context, calendarName: String, events: List<RideEvent>) {
        val now = System.currentTimeMillis()
        val upcoming = events.filter { !EventTime.isPast(it, now) }
            .sortedBy { EventTime.startMillis(it) ?: Long.MAX_VALUE }
        val past = events.filter { EventTime.isPast(it, now) }
            .sortedByDescending { EventTime.startMillis(it) ?: 0L }

        val sb = StringBuilder()
        sb.append(calendarName.ifBlank { context.getString(R.string.x_default_title) })
        sb.append("\n")
        if (upcoming.isNotEmpty()) {
            sb.append("\n" + context.getString(R.string.x_upcoming) + "\n")
            upcoming.forEach { e ->
                sb.append("• ${e.data} ${e.ora}  ${e.titolo}")
                if (e.localita.isNotBlank()) sb.append("  (${e.localita})")
                if (e.difficolta.isNotBlank()) sb.append("  [${e.difficolta.uppercase()}]")
                sb.append("\n")
            }
        }
        if (past.isNotEmpty()) {
            sb.append("\n" + context.getString(R.string.x_past) + "\n")
            past.forEach { e -> sb.append("• ${e.data}  ${e.titolo}\n") }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sb.toString().trim())
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.x_share_list)))
    }

    // ── GPX ─────────────────────────────────────────────────────

    /** Nome di default del file da proporre per il salvataggio/condivisione della traccia. */
    fun gpxFileNameOf(event: RideEvent): String = event.gpxFileName.ifBlank { "traccia.gpx" }

    /** Decomprime la traccia GPX dell'evento in XML testuale, o null se non c'è una traccia
     * valida. */
    private fun gpxXmlOf(event: RideEvent): String? =
        event.gpxData.takeIf { it.isNotBlank() }?.let { GpxProcessor.decompress(it) }

    /** Apre il chooser di sistema per condividere la traccia GPX (WhatsApp, altra app, ecc.),
     * appoggiandosi a un file temporaneo in cache esposto via [FileProvider]. Non fa nulla se
     * l'evento non ha una traccia valida. */
    fun shareGpx(context: Context, event: RideEvent) {
        val xml = gpxXmlOf(event) ?: return
        val file = cacheFile(context, gpxFileNameOf(event))
        file.writeText(xml)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.x_share_gpx)))
    }

    /** Scrive la traccia GPX dell'evento nell'[uri] scelto dall'utente tramite il picker
     * "Salva con nome" di sistema (`ActivityResultContracts.CreateDocument`, da lanciare nel
     * composable chiamante). Non fa nulla se l'evento non ha una traccia valida o l'uri non è
     * scrivibile. */
    fun saveGpxTo(context: Context, event: RideEvent, uri: Uri) {
        val xml = gpxXmlOf(event) ?: return
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(xml.toByteArray()) }
        }
    }
}
