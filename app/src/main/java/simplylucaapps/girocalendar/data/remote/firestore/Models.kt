package simplylucaapps.girocalendar.data.remote.firestore

/**
 * Un calendario di uscite in bicicletta (gruppo MTB/strada/gravel/cicloturismo). Il [code] è
 * l'identità (univoco, 6 caratteri); [name] è solo un'etichetta e può essere duplicato tra
 * calendari diversi.
 *
 * Documento Firestore: `calendars/{code}`.
 *
 * Modello permessi (piano Spark, senza Cloud Functions):
 *  - [ownerUid]  : chi ha creato il calendario. Può modificare tutto + gestire gli editor.
 *  - [editors]   : lista di UID Firebase autorizzati a modificare gli eventi.
 *                  L'owner è sempre incluso. Nuovi editor si aggiungono inserendo il loro UID
 *                  (o la loro email, risolta via `emailIndex/`).
 */
data class RideCalendar(
    val code: String = "",
    val name: String = "",
    val description: String = "",
    val ownerUid: String = "",
    /** Email dell'account Google dell'owner. Visibile pubblicamente nell'elenco calendari. */
    val ownerEmail: String = "",
    /** Se true il calendario compare nell'elenco pubblico; se false è raggiungibile solo col codice. */
    val listed: Boolean = true,
    val editors: List<String> = emptyList(),
    /**
     * Etichetta (email) per gli [editors] invitati scrivendo un'email, chiave = uid. Solo
     * per mostrare chi è stato aggiunto nella UI (EditorsDialog); un editor aggiunto per
     * codice utente diretto non ha voce qui e si mostra come uid abbreviato.
     */
    val editorLabels: Map<String, String> = emptyMap(),
    val createdAtMillis: Long = 0L,
    /** Colore del pallino-logo del calendario (esadecimale "#RRGGBB"). Vuoto = derivato dal codice. */
    val logoColor: String = "",
    /** PNG del logo del calendario in base64 (max ~64 KB). Vuoto = si usa il pallino colorato. */
    val logoData: String = "",
    /** Contatore di aperture del calendario (statistica approssimativa, incrementato a ogni
     * apertura via FieldValue.increment — non un vero conteggio di utenti unici, solo una
     * stima di quanto viene usato). Visibile nel dialog Info del calendario. */
    val openCount: Long = 0L,
)

/** Statistiche leggere di un calendario per la tab "Calendari" dei recenti: numero di
 * collaboratori (non utenti unici che l'hanno visitato, solo chi può modificarlo), uscite
 * ed aperture totali (approssimative). */
data class CalendarStats(val editorCount: Int, val eventCount: Int, val openCount: Long)

/**
 * Una comunicazione (owner-only). Documento Firestore: `calendars/{code}/announcements/{id}`.
 * Più comunicazioni indipendenti, ciascuna con la propria data di pubblicazione, invece del
 * vecchio [RideCalendar.announcement] a testo singolo — sostituito da questa sottocollezione.
 * Supporta **grassetto** e URL auto-linkati (vedi RichText).
 */
data class Announcement(
    val id: String = "",
    val title: String = "",
    val text: String = "",
    val createdAtMillis: Long = 0L,
    /** Posizione scelta dal proprietario (frecce su/giù); null = mai ordinata, va in cima per data. */
    val sortOrder: Long? = null,
)

/**
 * Una uscita in bici. Documento Firestore: `calendars/{code}/events/{eventId}` (eventi
 * attivi) oppure `calendars/{code}/archived/{eventId}` (eventi archiviati — stesso schema,
 * owner/editor le spostano da una collezione all'altra con "Archivia"/"Ripesca" invece di
 * eliminarle: comodo per riproporre in futuro un'uscita passata come nuovo evento).
 */
data class RideEvent(
    val id: String = "",
    /** Evento collegato da un altro calendario (solo in memoria, mai salvato): codice e nome del calendario d'origine; [id] è «L:codice:idOriginale». */
    val fromCode: String = "",
    val fromName: String = "",
    /** Documento `links/{linkId}` del calendario che mostra l'evento collegato (per rimuovere il collegamento). */
    val linkId: String = "",
    val titolo: String = "",
    val data: String = "",          // dd/MM/yyyy
    val ora: String = "",           // HH:mm
    val durataGiorni: Int = 1,      // durata in giorni (>= 1); 1 = uscita di un giorno
    val localita: String = "",
    val ritrovo: String = "",       // link Google Maps o testo libero
    val proponente: String = "",    // nome di chi propone l'uscita (testuale, non un editor)
    val waProponente: String = "",  // numero WhatsApp del proponente, opzionale (solo cifre/+)
    val difficolta: String = "",    // "s0".."s5" — scala STS (v. Difficulty.kt)
    val linkPercorso: String = "",  // url Komoot/Strava/GPX, opzionale
    val link2: String = "",         // secondo link libero, es. Instagram/Facebook/sito evento
    val video: String = "",         // link YouTube/social del video dell'uscita, opzionale
    val note: String = "",
    /**
     * Immagine di sfondo della card evento (JPEG base64, ritagliata 16:9, nessun prefisso
     * data:), come su Spaccasassi Eventi (.has-card-bg): riempie la card con overlay chiaro
     * sfumato e testo nero grassetto sopra. Vuoto = card normale a tinta unita. Vedi
     * [simplylucaapps.girocalendar.util.CardBackgroundProcessor].
     */
    val cardBgData: String = "",
    /**
     * Stessa immagine di [cardBgData] ma scalata SENZA ritaglio (aspect ratio originale
     * preservato): usata nel banner del dettaglio evento e nel viewer a tutto schermo, dove
     * si vede la foto intera invece della versione tagliata 16:9. Vuoto (eventi importati
     * prima di questo campo) = si ripiega su [cardBgData] nella UI.
     */
    val photoData: String = "",
    /**
     * Nomi di chi ha confermato la partecipazione ("Partecipa", come su Spaccasassi Eventi).
     * Scrittura pubblica anche senza login (rules: solo arrayUnion di UN nome alla volta,
     * mai riscrittura totale della lista, per evitare che un ospite cancelli quella altrui).
     */
    val partecipanti: List<String> = emptyList(),
    /** Chi ha risposto «Forse» (stessa logica nome libero di [partecipanti]). */
    val forse: List<String> = emptyList(),
    /**
     * Traccia GPX del percorso, compressa (gzip + base64, senza prefisso data:): un GPX è XML
     * molto ripetitivo, comprime tipicamente al 10-20% della dimensione originale, utile per
     * restare sotto il limite Firestore di 1 MiB per documento insieme alle immagini. Vuoto =
     * nessuna traccia caricata. Vedi [simplylucaapps.girocalendar.util.GpxProcessor].
     */
    val gpxData: String = "",
    /** Nome del file GPX originale (es. "anello_monte_rosa.gpx"), solo per mostrarlo/riproporlo nel download. */
    val gpxFileName: String = "",
    /**
     * Clip audio (base64, nessun prefisso data:) riprodotta quando si apre l'immagine
     * dell'evento a tutto schermo. Nessuna ricompressione lato app (richiederebbe MediaCodec a
     * basso livello): l'utente sceglie un file già compresso (es. registrato con l'app
     * Registratore, tipicamente M4A/AAC) e viene solo validata la durata. Vedi
     * [simplylucaapps.girocalendar.util.AudioProcessor].
     *
     * NON persistito qui: solo stato in-memory per il transito nell'editor (anteprima, upload
     * in corso). Il salvataggio reale avviene in un documento SEPARATO
     * (`calendars/{code}/events/{eventId}/audio/clip`, vedi [EventAudio]) perché condivide il
     * limite di 1 MiB per documento Firestore con `cardBgData`/`photoData`/`gpxData`: tenerlo
     * nello stesso documento dell'evento costringerebbe a comprimere l'audio molto più del
     * necessario per lasciare spazio a foto e traccia. Vuoto = nessun audio caricato/modificato
     * in questa sessione dell'editor (non necessariamente "nessun audio salvato": vedi [hasAudio]).
     */
    val audioData: String = "",
    /** MIME type del file audio caricato (es. "audio/mp4", "audio/mpeg"), per il MediaPlayer in riproduzione. */
    val audioMimeType: String = "",
    /** true se l'evento ha una clip audio salvata (documento [EventAudio] separato). Persistito
     * qui come flag leggero per decidere se mostrare il pulsante Play senza dover leggere la
     * sub-collection audio per ogni evento della lista. */
    val hasAudio: Boolean = false,
    /** Uscita annullata: la scheda mostra una barra diagonale rossa; sparisce dalla vista Mese. */
    val annullata: Boolean = false,
    /** Se non vuota, la scheda mostra lo stato dei trail di questa zona (campo `zone` dei trail). */
    val trailZone: String = "",
    /** Solo per gli eventi in `archived/`: quando sono stati archiviati (per ordinamento). 0 in `events/`. */
    val archivedAtMillis: Long = 0L,
)

/**
 * Clip audio di un evento, in un documento SEPARATO dall'evento stesso (vedi
 * [RideEvent.audioData]) per avere un limite di 1 MiB Firestore indipendente da foto/GPX.
 * Documento Firestore: `calendars/{code}/events/{eventId}/audio/clip` (id fisso "clip": un solo
 * audio per evento, sostituito interamente ad ogni modifica).
 */
data class EventAudio(
    val audioData: String = "",
    val audioMimeType: String = "",
)

/**
 * Una proposta di uscita inviata da un ospite (senza login). Documento Firestore:
 * `calendars/{code}/proposals/{proposalId}`.
 *
 * Chiunque può crearne una (scrittura pubblica autorizzata dalle rules, campo per campo);
 * solo owner/editor del calendario possono leggerle, approvarle (diventa un [RideEvent] vero)
 * o rifiutarle (cancellazione). Stesso schema campi di [RideEvent], meno [id] che qui è
 * l'id della proposta stessa, non dell'evento finale.
 */
data class RideProposal(
    val id: String = "",
    val titolo: String = "",
    val data: String = "",
    val ora: String = "",
    val localita: String = "",
    val ritrovo: String = "",
    val proponente: String = "",
    val waProponente: String = "",
    val difficolta: String = "",
    val linkPercorso: String = "",
    val link2: String = "",
    val video: String = "",
    val note: String = "",
    // Allegati opzionali, stessi campi/formati di [RideEvent]. A differenza dell'evento, qui
    // l'audio resta nello STESSO documento della proposta (le rules di `proposals` non
    // permettono sub-documenti per ospiti anonimi): il limite 1 MiB vale per la somma di tutti.
    // All'approvazione l'audio viene spostato nel documento separato dell'evento.
    val cardBgData: String = "",
    val photoData: String = "",
    val gpxData: String = "",
    val gpxFileName: String = "",
    val audioData: String = "",
    val audioMimeType: String = "",
    val proposedAtMillis: Long = 0L,
)

/** Risultato della ricerca di un codice calendario. */
sealed interface CalendarLookup {
    data class Found(val calendar: RideCalendar) : CalendarLookup
    data object NotFound : CalendarLookup
    data class Error(val message: String) : CalendarLookup
}

/** Account in lista nera di un calendario (calendars/{code}/blocks/{uid}). [level]: "edit" = niente modifiche né proposte; "ban" = niente accesso. */
data class BlockedUser(val uid: String = "", val level: String = "edit", val label: String = "", val atMillis: Long = 0L)

/** Commento sulla scheda di un'uscita. Firestore: `calendars/{code}/events/{eventId}/comments/{id}`. Solo utenti loggati. */
data class EventComment(
    val id: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val text: String = "",
    val createdAtMillis: Long = 0L,
    val editedAtMillis: Long = 0L,
)
