package simplylucaapps.girocalendar.data.remote.firestore

import simplylucaapps.girocalendar.R

import androidx.compose.ui.graphics.Color

/**
 * Stato di percorribilità di un [Trail], stile Trailforks (pallino colorato). Impostato
 * dall'ultimo commento pubblicato su quel trail (vedi [TrailComment.newStatus]).
 */
enum class TrailStatus(val id: String, private val labelRes: Int, val color: Color) {
    OPEN("open", R.string.ts_open, Color(0xFF27AE60)),
    CAUTION("caution", R.string.ts_caution, Color(0xFFF2994A)),
    CLOSED("closed", R.string.ts_closed, Color(0xFFE74C3C));

    val label: String get() = simplylucaapps.girocalendar.util.Str.t(labelRes)

    companion object {
        fun of(id: String): TrailStatus = entries.firstOrNull { it.id == id } ?: OPEN
    }
}

/**
 * Un sentiero/trail, condiviso globalmente da tutta l'app (non legato a un singolo
 * calendario), raggruppato per [zone] nella tab "TRAIL". Documento Firestore: `trails/{id}`.
 *
 * Chiunque sia loggato con Google può crearne uno; solo chi l'ha creato ([createdByUid]) o un
 * admin (owner di almeno un calendario, vedi firestore.rules) può eliminarlo. Lo stato non si
 * modifica direttamente: cambia solo pubblicando un [TrailComment] con [TrailComment.newStatus]
 * valorizzato, che aggiorna anche i campi denormalizzati qui sotto per ordinare/mostrare la
 * lista senza dover leggere i commenti.
 */
data class Trail(
    val id: String = "",
    val name: String = "",
    val zone: String = "",
    val status: TrailStatus = TrailStatus.OPEN,
    /** Difficoltà tecnica del trail, scala STS "s0".."s5" (vedi [Difficulty]) — stessa scala
     * usata per le uscite, così riusa badge/colori già esistenti. Vuoto = non specificata. */
    val difficulty: String = "",
    /** Posizione sulla mappa (tab MAPPA); null = non posizionato. */
    val lat: Double? = null,
    val lon: Double? = null,
    /** Tracciato del sentiero: encoded polyline (vedi util/Polyline); vuoto = solo punto o niente. */
    val path: String = "",
    val createdByUid: String = "",
    val createdAtMillis: Long = 0L,
    /** Denormalizzati dall'ultimo commento con cambio di stato, per la lista ad albero. */
    val lastUpdateAtMillis: Long = 0L,
    val lastUpdateByName: String = "",
)

/**
 * Un aggiornamento sullo stato di un [Trail], stile "segnalazioni" di Trailforks. Documento
 * Firestore: `trails/{trailId}/comments/{commentId}`.
 *
 * Solo utenti loggati con Google possono pubblicarne (niente ospiti anonimi, per evitare spam
 * su un dato che le persone usano per decidere se andare in un sentiero). [newStatus] è vuoto
 * per un commento puramente informativo che non cambia lo stato corrente del trail.
 */
data class TrailComment(
    val id: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val text: String = "",
    val newStatus: String = "", // "" | "open" | "caution" | "closed"
    val createdAtMillis: Long = 0L,
    val editedAtMillis: Long = 0L,
)

/**
 * Voce del registro delle modifiche ai trail. Documento Firestore: `trailLog/{id}`.
 * Scritta dal client dopo ogni modifica (append-only: le rules vietano update/delete) con nome ed
 * email dell'utente loggato, per scoraggiare abusi; leggibile solo dagli admin.
 */
data class TrailLogEntry(
    val id: String = "",
    /** create | status | difficulty | rename_zone | delete | comment | comment_edit | comment_delete */
    val action: String = "",
    val trailName: String = "",
    val detail: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val createdAtMillis: Long = 0L,
    /** Dati per annullare l'operazione (solo voci recenti): kind = fields | restore | delete | zone. */
    val trailId: String = "",
    val undo: Map<String, Any?>? = null,
    /** Id della voce di log annullata da questa voce. */
    val undoOf: String = "",
)

/**
 * Perimetro (poligono) di una zona dei trail, solo informativo. Documento Firestore: `trailZones/{id}` con
 * id = nome della zona normalizzato. [polygon] = encoded polyline dei vertici; [zone] = nome di una zona
 * esistente (il campo `zone` dei trail).
 */
data class TrailZone(
    val id: String = "", val zone: String = "", val polygon: String = "",
    /** Costruttori dei trail della zona (loghi + link social), mostrati sulla mappa con la zona attiva. */
    val builders: List<ZoneBuilder> = emptyList(),
)

/** Punto di interesse sulla mappa (ritrovo, vetta, area picnic, altro). Documento Firestore `pois/{id}`. [type] = ritrovo|vetta|picnic|altro. */
data class Poi(
    val id: String = "", val name: String = "", val desc: String = "", val type: String = "altro",
    val lat: Double = 0.0, val lon: Double = 0.0, val createdByUid: String = "",
    /** Nomi (da account) di chi l'ha aggiunto e di chi l'ha modificato per ultimo, mostrati nel fumetto. */
    val createdByName: String = "", val updatedByName: String = "",
)

/** Un costruttore di trail: [logo] è un data URL ("data:image/webp;base64,…") ridotto a 96 px, [url] il link social. */
data class ZoneBuilder(val name: String = "", val logo: String = "", val url: String = "")
