package simplylucaapps.girocalendar.data.remote.firestore

import simplylucaapps.girocalendar.util.Str

import simplylucaapps.girocalendar.R

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random
import simplylucaapps.girocalendar.data.settings.RecentCalendar

private const val TAG = "CalendarRepository"

/**
 * Unico punto d'accesso a Firestore per GiroCalendar. Piano **Spark**: nessuna Cloud Function,
 * tutta la logica è client-side + Firestore Security Rules.
 *
 * Struttura:
 *   calendars/{code}                    -> [RideCalendar] (ownerUid, editors[])
 *   calendars/{code}/events/{eventId}   -> [RideEvent]
 *
 * Permessi (vedi firestore.rules):
 *   - lettura pubblica sotto calendars/
 *   - create calendars/{code}: autenticato, ownerUid == uid, editors == [uid]
 *   - update calendar: solo owner (per name/description/editors/listed/logo)
 *   - write events/: uid ∈ calendars/{code}.editors
 */
class CalendarRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {

    val currentUid: String? get() = auth.currentUser?.uid

    // ── Ricerca / lettura ───────────────────────────────────────

    suspend fun lookupCode(codeRaw: String): CalendarLookup {
        val code = codeRaw.trim().uppercase()
        if (code.isEmpty()) return CalendarLookup.NotFound
        return try {
            val snap = db.collection("calendars").document(code).get().await()
            if (!snap.exists()) CalendarLookup.NotFound
            else CalendarLookup.Found(snap.toRideCalendar(code))
        } catch (e: Exception) {
            Log.w(TAG, "lookupCode failed", e)
            CalendarLookup.Error(e.message ?: Str.t(R.string.err_network))
        }
    }

    /** Calendario completo + conteggio eventi, per il dialog "Info calendario" nell'elenco. */
    suspend fun calendarInfo(code: String): RideCalendar? {
        return try {
            val doc = db.collection("calendars").document(code).get().await()
            if (!doc.exists()) null else withPrivate(doc.toRideCalendar(code))
        } catch (e: Exception) {
            Log.w(TAG, "calendarInfo failed", e); null
        }
    }

    /** Numero di eventi totali del calendario (per le statistiche nel dialog info). */
    suspend fun eventCount(code: String): Int = try {
        db.collection("calendars").document(code).collection("events").get().await().size()
    } catch (e: Exception) {
        Log.w(TAG, "eventCount failed", e); 0
    }

    /** Statistiche leggere per un singolo calendario (tab "Calendari" dei recenti): numero di
     * collaboratori, uscite ed aperture. Usa l'API di aggregazione per gli eventi (non scarica
     * i documenti), e legge il documento calendario una volta per editors/openCount. */
    suspend fun calendarStats(code: String): CalendarStats = try {
        val calSnap = db.collection("calendars").document(code).get().await()
        val editorCount = (calSnap.get("editors") as? List<*>)?.size ?: 0
        val openCount = calSnap.getLong("openCount") ?: 0L
        val eventCount = db.collection("calendars").document(code).collection("events")
            .count().get(com.google.firebase.firestore.AggregateSource.SERVER).await().count.toInt()
        CalendarStats(editorCount, eventCount, openCount)
    } catch (e: Exception) {
        Log.w(TAG, "calendarStats failed for $code", e); CalendarStats(0, 0, 0L)
    }

    /** Incrementa il contatore "aperture" del calendario di 1. Statistica approssimativa (non
     * un vero conteggio di utenti unici): FieldValue.increment è una singola scrittura atomica
     * lato server, senza bisogno di leggere prima il valore attuale. Fallisce silenziosamente
     * (es. offline, o calendario appena eliminato) perché non è un'operazione critica. */
    fun incrementOpenCount(code: String) {
        db.collection("calendars").document(code)
            .update("openCount", FieldValue.increment(1))
            .addOnFailureListener { Log.w(TAG, "incrementOpenCount failed", it) }
    }

    fun observeCalendar(code: String): Flow<RideCalendar?> = callbackFlow {
        val reg = db.collection("calendars").document(code)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) { trySend(null); return@addSnapshotListener }
                trySend(snap.toRideCalendar(code))
            }
        awaitClose { reg.remove() }
    }

    /** I calendari pubblici (listed != false), ordinati per popolarità (aperture, decrescente):
     * i gruppi più attivi compaiono per primi sia nel dialog "Sfoglia" sia nella sezione
     * "Calendari pubblici" della SearchScreen. */
    suspend fun allCalendars(): List<RideCalendar> {
        return try {
            db.collection("calendars").get().await().documents
                .map { it.toRideCalendar(it.id) }
                .filter { it.listed }
                .sortedByDescending { it.openCount }
        } catch (e: Exception) {
            Log.w(TAG, "allCalendars failed", e); emptyList()
        }
    }

    /** Migra l'email dell'owner: completa in `private/owner` (solo utenti loggati), mascherata nel doc pubblico. */
    suspend fun backfillOwnerEmail(code: String, current: RideCalendar) {
        val user = auth.currentUser ?: return
        if (current.ownerUid != user.uid) return
        val email = user.email ?: return
        val masked = maskEmail(email)
        if (current.ownerEmail == masked) return
        runCatching {
            val ref = db.collection("calendars").document(code)
            ref.collection("private").document("owner").set(mapOf("email" to email)).await()
            ref.set(mapOf("ownerEmail" to masked), SetOptions.merge()).await()
        }
    }

    private suspend fun setEditorLabels(code: String, labels: Map<String, String>) {
        db.collection("calendars").document(code).collection("private").document("labels")
            .set(mapOf("labels" to labels), SetOptions.merge()).await()
    }

    /** Aggiunge i dati privati (email owner completa, etichette collaboratori) solo se l'utente è loggato.
     * Da non loggati restano la versione mascherata e nessun nome di collaboratore. */
    suspend fun withPrivate(cal: RideCalendar): RideCalendar {
        if (auth.currentUser == null) return cal.copy(editorLabels = emptyMap())
        val priv = db.collection("calendars").document(cal.code).collection("private")
        val full = runCatching { priv.document("owner").get().await().getString("email") }.getOrNull()
        @Suppress("UNCHECKED_CAST")
        val labels = runCatching { priv.document("labels").get().await().get("labels") as? Map<String, String> }.getOrNull().orEmpty()
        return cal.copy(
            ownerEmail = full?.takeIf { it.isNotBlank() } ?: cal.ownerEmail,
            editorLabels = cal.editorLabels + labels,
        )
    }

    /** Email completa dell'owner: null se non loggato o non disponibile. */
    suspend fun ownerEmailFull(code: String): String? {
        if (auth.currentUser == null) return null
        return runCatching {
            db.collection("calendars").document(code).collection("private").document("owner").get().await().getString("email")
        }.getOrNull()
    }

    /** I calendari di cui l'utente corrente è owner o collaboratore. Vuoto se non loggato. */
    suspend fun myCalendars(): List<RideCalendar> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        return try {
            val owner = db.collection("calendars").whereEqualTo("ownerUid", uid).get().await()
            val editor = db.collection("calendars").whereArrayContains("editors", uid).get().await()
            (owner.documents + editor.documents)
                .associateBy { it.id }          // dedup (l'owner è anche in editors)
                .values
                .map { it.toRideCalendar(it.id) }
                .sortedByDescending { it.createdAtMillis }
        } catch (e: Exception) {
            Log.w(TAG, "myCalendars failed", e)
            emptyList()
        }
    }

    fun observeEvents(code: String): Flow<List<RideEvent>> = callbackFlow<List<com.google.firebase.firestore.DocumentSnapshot>?> {
        val reg = db.collection("calendars").document(code).collection("events")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "observeEvents error", err); trySend(emptyList()); return@addSnapshotListener }
                trySend(snap?.documents.orEmpty())
            }
        awaitClose { reg.remove() }
    }.map { docs -> docs.orEmpty().map { it.toRideEvent() }.sortedBy { parseDate(it.data) } }
        // conversione + ordinamento fuori dal thread UI (i documenti pesano diversi MB per le foto)
        .distinctUntilChanged() // confronto (foto comprese) fuori dal thread UI
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        // Cache su file: l'ultima lista vista compare subito, poi arriva Firestore e la sostituisce.
        .onEach { list -> if (list.isNotEmpty()) writeEventsCache(code, list) }
        .onStart { readEventsCache(code)?.let { emit(it) } }

    // ── Eventi collegati da altri calendari ─────────────────────
    // calendars/{code}/links/{id} = {srcCode, srcEventId, byUid, atMillis}: l'evento resta nel calendario d'origine
    // (partecipanti e commenti vanno lì); qui compare in sola lettura con la dicitura del calendario d'origine.

    class EventLink(val id: String, val srcCode: String, val srcEventId: String)

    fun observeLinks(code: String): Flow<List<EventLink>> = callbackFlow {
        val reg = db.collection("calendars").document(code).collection("links")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "observeLinks error", err); trySend(emptyList()); return@addSnapshotListener }
                trySend(snap?.documents.orEmpty().mapNotNull { d ->
                    val c = d.getString("srcCode") ?: return@mapNotNull null
                    val e = d.getString("srcEventId") ?: return@mapNotNull null
                    EventLink(d.id, c, e)
                })
            }
        awaitClose { reg.remove() }
    }

    private val calNames = HashMap<String, String>()

    private fun observeLinkedOne(l: EventLink): Flow<RideEvent?> = callbackFlow {
        val name = calNames[l.srcCode] ?: (runCatching { calendarInfo(l.srcCode)?.name }.getOrNull() ?: "").also { calNames[l.srcCode] = it }
        val reg = db.collection("calendars").document(l.srcCode).collection("events").document(l.srcEventId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) { trySend(null); return@addSnapshotListener }
                trySend(snap.toRideEvent().copy(id = "L:${l.srcCode}:${l.srcEventId}", fromCode = l.srcCode, fromName = name, linkId = l.id))
            }
        awaitClose { reg.remove() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeLinkedEvents(code: String): Flow<List<RideEvent>> = observeLinks(code).flatMapLatest { links ->
        if (links.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
        else kotlinx.coroutines.flow.combine(links.map { observeLinkedOne(it) }) { arr -> arr.filterNotNull() }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)

    suspend fun addLink(code: String, srcCode: String, srcEventId: String): Result<Unit> = write {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException(Str.t(R.string.err_sign_in_comment))
        db.collection("calendars").document(code).collection("links").document().set(
            mapOf("srcCode" to srcCode, "srcEventId" to srcEventId, "byUid" to uid, "atMillis" to System.currentTimeMillis())
        ).await()
    }

    suspend fun removeLink(code: String, linkId: String): Result<Unit> = write {
        db.collection("calendars").document(code).collection("links").document(linkId).delete().await()
    }

    /** Eventi di un altro calendario (lettura una tantum) per sceglierne uno da collegare. */
    suspend fun eventsOf(code: String): List<RideEvent>? = try {
        db.collection("calendars").document(code).collection("events").get().await().documents.map { it.toRideEvent() }
            .sortedBy { parseDate(it.data) }
    } catch (e: Exception) { Log.w(TAG, "eventsOf failed", e); null }

    private fun eventsCacheFile(code: String): java.io.File? =
        Str.ctx?.let { java.io.File(it.filesDir, "events_cache_${code.filter { c -> c.isLetterOrDigit() }}.json") }

    private suspend fun readEventsCache(code: String): List<RideEvent>? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val f = eventsCacheFile(code)?.takeIf { it.exists() } ?: return@runCatching null
                val arr = org.json.JSONArray(f.readText())
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    RideEvent(
                        id = o.optString("id"), titolo = o.optString("titolo"), data = o.optString("data"),
                        ora = o.optString("ora"), durataGiorni = o.optInt("durataGiorni", 1),
                        localita = o.optString("localita"), ritrovo = o.optString("ritrovo"),
                        proponente = o.optString("proponente"), waProponente = o.optString("waProponente"),
                        difficolta = o.optString("difficolta"), linkPercorso = o.optString("linkPercorso"),
                        link2 = o.optString("link2"), video = o.optString("video"), note = o.optString("note"),
                        cardBgData = o.optString("cardBgData"), photoData = o.optString("photoData"),
                        partecipanti = o.optJSONArray("partecipanti")?.let { a -> List(a.length()) { j -> a.getString(j) } }.orEmpty(),
                        forse = o.optJSONArray("forse")?.let { a -> List(a.length()) { j -> a.getString(j) } }.orEmpty(),
                        gpxData = o.optString("gpxData"), gpxFileName = o.optString("gpxFileName"),
                        audioData = o.optString("audioData"), audioMimeType = o.optString("audioMimeType"),
                        hasAudio = o.optBoolean("hasAudio"), annullata = o.optBoolean("annullata"), trailZone = o.optString("trailZone"),
                        archivedAtMillis = o.optLong("archivedAtMillis"),
                    )
                }
            }.getOrNull()
        }

    private suspend fun writeEventsCache(code: String, list: List<RideEvent>) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val f = eventsCacheFile(code) ?: return@runCatching
                val arr = org.json.JSONArray()
                list.forEach { e ->
                    arr.put(org.json.JSONObject().apply {
                        put("id", e.id); put("titolo", e.titolo); put("data", e.data); put("ora", e.ora)
                        put("durataGiorni", e.durataGiorni); put("localita", e.localita); put("ritrovo", e.ritrovo)
                        put("proponente", e.proponente); put("waProponente", e.waProponente)
                        put("difficolta", e.difficolta); put("linkPercorso", e.linkPercorso); put("link2", e.link2)
                        put("video", e.video); put("note", e.note); put("cardBgData", e.cardBgData)
                        put("photoData", e.photoData); put("partecipanti", org.json.JSONArray(e.partecipanti)); put("forse", org.json.JSONArray(e.forse))
                        put("gpxData", e.gpxData); put("gpxFileName", e.gpxFileName)
                        put("audioData", e.audioData); put("audioMimeType", e.audioMimeType)
                        put("hasAudio", e.hasAudio); put("annullata", e.annullata); put("trailZone", e.trailZone)
                        put("archivedAtMillis", e.archivedAtMillis)
                    })
                }
                val tmp = java.io.File(f.path + ".tmp")
                tmp.writeText(arr.toString()); tmp.renameTo(f)
            }
        }

    // ── Creazione calendario (transazione client, retry su collisione) ──

    suspend fun createCalendar(
        name: String, description: String, listed: Boolean = true,
        logoColor: String = "", logoData: String = "",
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException(Str.t(R.string.err_must_sign_in_cal)))
        if (auth.currentUser?.isAnonymous == true) {
            return Result.failure(IllegalStateException(Str.t(R.string.err_sign_in_cal)))
        }
        repeat(12) {
            val code = randomCode()
            val ref = db.collection("calendars").document(code)
            try {
                db.runTransaction { tx ->
                    if (tx.get(ref).exists()) throw CollisionException()
                    tx.set(
                        ref,
                        mapOf(
                            "code" to code,
                            "name" to name.trim(),
                            "description" to description.trim(),
                            "ownerUid" to uid,
                            "ownerEmail" to maskEmail(auth.currentUser?.email ?: ""),
                            "listed" to listed,
                            "editors" to listOf(uid),
                            "createdAtMillis" to System.currentTimeMillis(),
                            "logoColor" to logoColor.trim(),
                            "logoData" to logoData,
                        )
                    )
                }.await()
                // Indice per le Security Rules di trails/ ("admin" = owner di almeno un
                // calendario): scrittura best-effort, non blocca la creazione se fallisce.
                runCatching { db.collection("admins").document(uid).set(mapOf("since" to System.currentTimeMillis(), "code" to code)).await() }
                // Email completa dell'owner: documento privato, leggibile solo da chi è loggato.
                runCatching { auth.currentUser?.email?.let { db.collection("calendars").document(code).collection("private").document("owner").set(mapOf("email" to it)).await() } }
                return Result.success(code)
            } catch (e: CollisionException) {
                // riprova con un altro codice
            } catch (e: Exception) {
                Log.w(TAG, "createCalendar failed", e)
                return Result.failure(e)
            }
        }
        return Result.failure(IllegalStateException(Str.t(R.string.err_no_code)))
    }

    // ── Gestione editor (solo owner) ────────────────────────────

    /** [editorEmail] se l'invito è avvenuto per email, per salvare la label uid->email in [RideCalendar.editorLabels]. */
    suspend fun addEditor(code: String, editorUid: String, editorEmail: String? = null): Result<Unit> = write {
        val uid = editorUid.trim()
        // Aggiunto per UID: prova a ricavare l'email dall'indice, così si vede un nome leggibile.
        val email = editorEmail?.takeIf { it.isNotBlank() } ?: emailForUid(uid)
        db.collection("calendars").document(code).update("editors", FieldValue.arrayUnion(uid)).await()
        // Le etichette (email) dei collaboratori stanno nel documento privato, visibile solo a chi è loggato.
        if (!email.isNullOrBlank()) setEditorLabels(code, mapOf(uid to email.trim().lowercase()))
    }

    /** Email di login di un uid, dall'indice `emailIndex` (solo chi ha fatto login almeno una volta). */
    private suspend fun emailForUid(uid: String): String? = try {
        db.collection("emailIndex").whereEqualTo("uid", uid).limit(1).get().await()
            .documents.firstOrNull()?.id
    } catch (e: Exception) { Log.w(TAG, "emailForUid failed", e); null }

    /** Per i collaboratori senza etichetta (aggiunti per UID) ricava l'email e la salva. Ritorna true se ha scritto. */
    suspend fun backfillEditorLabels(code: String, cal: RideCalendar): Boolean {
        val missing = cal.editors.filter { it != cal.ownerUid && cal.editorLabels[it].isNullOrBlank() }
        if (missing.isEmpty()) return false
        val found = mutableMapOf<String, String>()
        for (uid in missing) emailForUid(uid)?.let { found[uid] = it.lowercase() }
        if (found.isEmpty()) return false
        return try { setEditorLabels(code, found); true }
        catch (e: Exception) { Log.w(TAG, "backfillEditorLabels failed", e); false }
    }

    /** Super-admin dell'app (stesso account riconosciuto dalle rules). */
    val isSuperAdmin: Boolean get() =
        auth.currentUser?.email.equals("bizzarrone@gmail.com", ignoreCase = true)

    suspend fun setListed(code: String, listed: Boolean): Result<Unit> = write {
        db.collection("calendars").document(code).update("listed", listed).await()
    }

    suspend fun removeEditor(code: String, editorUid: String): Result<Unit> = write {
        val uid = editorUid.trim()
        runCatching { db.collection("calendars").document(code).collection("contacts").document(uid).delete().await() }
        db.collection("calendars").document(code)
            .update(mapOf(
                "editors" to FieldValue.arrayRemove(uid),
                "editorLabels.$uid" to FieldValue.delete(), // pulizia di eventuali etichette legacy pubbliche
            )).await()
        runCatching {
            db.collection("calendars").document(code).collection("private").document("labels")
                .set(mapOf("labels" to mapOf(uid to FieldValue.delete())), SetOptions.merge()).await()
        }
    }

    /**
     * Registra (o riconferma) la voce email→uid dell'utente loggato in `emailIndex/`, così
     * un owner può poi invitare un collaboratore scrivendo la sua email invece del codice
     * utente. Self-write idempotente: va chiamata dopo ogni login riuscito. Nessun effetto
     * se non loggato o senza email (account anonimo).
     */
    suspend fun registerEmailIndex(): Unit {
        val user = auth.currentUser ?: return
        val email = user.email?.trim()?.lowercase() ?: return
        try {
            db.collection("emailIndex").document(email)
                .set(mapOf("uid" to user.uid)).await()
        } catch (e: Exception) {
            Log.w(TAG, "registerEmailIndex failed", e)
        }
    }

    /**
     * Risolve l'input dell'owner nel campo "aggiungi collaboratore": se sembra un'email
     * (contiene '@') la cerca in `emailIndex/`, altrimenti la tratta come codice utente
     * (uid) diretto. Fallisce con un messaggio adatto alla UI se l'email non è registrata
     * (l'altro utente non ha mai fatto login) o se l'uid non è plausibile.
     */
    suspend fun resolveEditorInput(inputRaw: String): Result<String> {
        val input = inputRaw.trim()
        if (input.isEmpty()) return Result.failure(IllegalArgumentException(Str.t(R.string.err_enter_email)))
        if ('@' in input) {
            val email = input.lowercase()
            return try {
                val snap = db.collection("emailIndex").document(email).get().await()
                val uid = snap.getString("uid")
                if (uid.isNullOrBlank()) {
                    Result.failure(NoSuchElementException(
                        Str.t(R.string.err_no_user_email)))
                } else Result.success(uid)
            } catch (e: Exception) {
                Log.w(TAG, "resolveEditorInput email lookup failed", e)
                Result.failure(e)
            }
        }
        return if (input.length >= 20) Result.success(input)
        else Result.failure(IllegalArgumentException(Str.t(R.string.err_bad_email)))
    }

    // ── Scritture (richiedono uid ∈ editors) ────────────────────

    private fun eventFields(e: RideEvent): Map<String, Any> = mapOf(
        "titolo" to e.titolo.trim(),
        "data" to e.data.trim(),
        "ora" to e.ora.trim(),
        "durataGiorni" to e.durataGiorni.coerceAtLeast(1),
        "localita" to e.localita.trim(),
        "ritrovo" to e.ritrovo.trim(),
        "proponente" to e.proponente.trim(),
        "waProponente" to e.waProponente.trim(),
        "difficolta" to e.difficolta.trim().lowercase(),
        "linkPercorso" to e.linkPercorso.trim(),
        "link2" to e.link2.trim(),
        "video" to e.video.trim(),
        "note" to e.note.trim(),
        "cardBgData" to e.cardBgData,
        "photoData" to e.photoData,
        "partecipanti" to e.partecipanti,
        "forse" to e.forse,
        "gpxData" to e.gpxData,
        "gpxFileName" to e.gpxFileName.trim(),
        "hasAudio" to e.hasAudio,
        "annullata" to e.annullata,
        "trailZone" to e.trailZone.trim(),
    )

    /** Salva l'evento (senza l'audio: vedi [saveEventAudio], documento separato) e ritorna
     * l'id assegnato — il chiamante lo usa per salvare l'audio subito dopo se presente. */
    suspend fun upsertEvent(code: String, e: RideEvent): Result<String> = writeReturning {
        // Stima la dimensione del documento PRIMA di inviarlo: Firestore rifiuta oltre 1 MiB
        // (1_048_576 byte) con un errore tecnico (INVALID_ARGUMENT, dimensione in byte) poco
        // comprensibile per l'utente. cardBgData/photoData/gpxData sono i campi in base64 che
        // pesano davvero (l'audio è in un documento separato, vedi EventAudio, e non entra in
        // questo conteggio). Soglia vicina al vero limite, con margine per i campi testuali
        // (titolo, note, link...) e per l'overhead di Firestore (nomi dei campi, struttura).
        val estimatedBytes = e.cardBgData.length + e.photoData.length + e.gpxData.length
        if (estimatedBytes > 1_044_000) {
            throw DocumentTooLargeException(estimatedBytes)
        }
        val col = db.collection("calendars").document(code).collection("events")
        val doc = if (e.id.isBlank()) col.document() else col.document(e.id)
        doc.set(eventFields(e)).await()
        doc.id
    }

    /** Salva (o sovrascrive) la clip audio dell'evento in un documento separato, con un limite
     * di 1 MiB indipendente da foto/GPX (vedi [EventAudio]). [audioData]/[audioMimeType] vuoti
     * eliminano la clip esistente invece di scriverne una vuota. */
    suspend fun saveEventAudio(code: String, eventId: String, audioData: String, audioMimeType: String): Result<Unit> = write {
        // Stima separata dal documento evento: stessa soglia di Firestore, ma qui l'audio è
        // l'unico contenuto pesante nel documento, quindi ha margine molto maggiore.
        if (audioData.length > 1_044_000) {
            throw DocumentTooLargeException(audioData.length)
        }
        val audioRef = db.collection("calendars").document(code).collection("events")
            .document(eventId).collection("audio").document("clip")
        if (audioData.isBlank()) {
            audioRef.delete().await()
        } else {
            audioRef.set(mapOf("audioData" to audioData, "audioMimeType" to audioMimeType.trim())).await()
        }
    }

    /** Legge la clip audio dell'evento (documento separato), null se non presente. */
    suspend fun eventAudio(code: String, eventId: String): EventAudio? = try {
        val snap = db.collection("calendars").document(code).collection("events")
            .document(eventId).collection("audio").document("clip").get().await()
        if (!snap.exists()) null
        else EventAudio(
            audioData = snap.getString("audioData") ?: "",
            audioMimeType = snap.getString("audioMimeType") ?: "",
        )
    } catch (e: Exception) {
        Log.w(TAG, "eventAudio failed for $eventId", e); null
    }

    suspend fun deleteEvent(code: String, eventId: String): Result<Unit> = write {
        db.collection("calendars").document(code).collection("events").document(eventId).collection("audio").document("clip").delete().await()
        db.collection("calendars").document(code).collection("events").document(eventId).delete().await()
    }

    /**
     * Clona un evento (letto pubblicamente, anche da un calendario dove non si è editor) in un
     * calendario dove l'utente corrente è editor/owner, incluso l'audio se presente. Crea un
     * nuovo evento con nuovo id (mai sovrascrive), stesso schema di [upsertEvent]/[saveEventAudio]:
     * nessuna regola Firestore dedicata, basta poter scrivere su [targetCode] come editor.
     */
    suspend fun cloneEvent(sourceCode: String, sourceEvent: RideEvent, targetCode: String): Result<String> = writeReturning {
        val clone = sourceEvent.copy(id = "", annullata = false, hasAudio = false, partecipanti = emptyList(), forse = emptyList(), archivedAtMillis = 0L)
        val newId = upsertEvent(targetCode, clone).getOrThrow()
        val audio = eventAudio(sourceCode, sourceEvent.id)
        if (audio != null && audio.audioData.isNotBlank()) {
            saveEventAudio(targetCode, newId, audio.audioData, audio.audioMimeType).getOrThrow()
        }
        newId
    }

    // ── Archivio (owner/editor) ──────────────────────────────────
    // calendars/{code}/archived/{eventId}: eventi tolti dalla vista normale ma conservati,
    // consultabili e "ripescabili" come nuovo evento invece di essere persi con la
    // cancellazione definitiva.

    fun observeArchivedEvents(code: String): Flow<List<RideEvent>> = callbackFlow {
        val reg = db.collection("calendars").document(code).collection("archived")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "observeArchivedEvents error", err); trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents.orEmpty().map { it.toRideEvent() }
                    .sortedByDescending { it.archivedAtMillis }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /** Sposta un evento da events/ ad archived/ (stesso id) incluso il suo eventuale audio
     * (documento separato, non toccato da eventFields/estimatedBytes): più scritture, non
     * atomiche. */
    suspend fun archiveEvent(code: String, e: RideEvent): Result<Unit> = write {
        val fields = eventFields(e) + ("archivedAtMillis" to System.currentTimeMillis())
        db.collection("calendars").document(code).collection("archived").document(e.id).set(fields).await()
        if (e.hasAudio) {
            val audio = db.collection("calendars").document(code).collection("events")
                .document(e.id).collection("audio").document("clip").get().await()
            if (audio.exists()) {
                db.collection("calendars").document(code).collection("archived")
                    .document(e.id).collection("audio").document("clip").set(audio.data.orEmpty()).await()
            }
            db.collection("calendars").document(code).collection("events")
                .document(e.id).collection("audio").document("clip").delete().await()
        }
        db.collection("calendars").document(code).collection("events").document(e.id).delete().await()
    }

    /** Elimina definitivamente un evento archiviato, incluso il suo eventuale audio. */
    suspend fun deleteArchivedEvent(code: String, eventId: String): Result<Unit> = write {
        db.collection("calendars").document(code).collection("archived").document(eventId).collection("audio").document("clip").delete().await()
        db.collection("calendars").document(code).collection("archived").document(eventId).delete().await()
    }

    // ── Epici (owner/editor) ──────────────────────────────────────
    // calendars/{code}/epics/{id}: giri "da ripetere ogni anno", schede a sé stanti (non
    // legate a una data), popolate clonando un evento esistente (mai spostate: l'originale
    // in events/archived resta intatto) o create/modificate direttamente come gli eventi.

    fun observeEpics(code: String): Flow<List<RideEvent>> = callbackFlow {
        val reg = db.collection("calendars").document(code).collection("epics")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "observeEpics error", err); trySend(emptyList()); return@addSnapshotListener }
                trySend(snap?.documents.orEmpty().map { it.toRideEvent() })
            }
        awaitClose { reg.remove() }
    }

    suspend fun upsertEpic(code: String, e: RideEvent): Result<String> = writeReturning {
        val estimatedBytes = e.cardBgData.length + e.photoData.length + e.gpxData.length
        if (estimatedBytes > 1_044_000) {
            throw DocumentTooLargeException(estimatedBytes)
        }
        val col = db.collection("calendars").document(code).collection("epics")
        val doc = if (e.id.isBlank()) col.document() else col.document(e.id)
        doc.set(eventFields(e)).await()
        doc.id
    }

    suspend fun saveEpicAudio(code: String, epicId: String, audioData: String, audioMimeType: String): Result<Unit> = write {
        if (audioData.length > 1_044_000) {
            throw DocumentTooLargeException(audioData.length)
        }
        val audioRef = db.collection("calendars").document(code).collection("epics")
            .document(epicId).collection("audio").document("clip")
        if (audioData.isBlank()) {
            audioRef.delete().await()
        } else {
            audioRef.set(mapOf("audioData" to audioData, "audioMimeType" to audioMimeType.trim())).await()
        }
    }

    suspend fun epicAudio(code: String, epicId: String): EventAudio? = try {
        val snap = db.collection("calendars").document(code).collection("epics")
            .document(epicId).collection("audio").document("clip").get().await()
        if (!snap.exists()) null
        else EventAudio(
            audioData = snap.getString("audioData") ?: "",
            audioMimeType = snap.getString("audioMimeType") ?: "",
        )
    } catch (e: Exception) {
        Log.w(TAG, "epicAudio failed for $epicId", e); null
    }

    suspend fun deleteEpic(code: String, epicId: String): Result<Unit> = write {
        db.collection("calendars").document(code).collection("epics").document(epicId).collection("audio").document("clip").delete().await()
        db.collection("calendars").document(code).collection("epics").document(epicId).delete().await()
    }

    /** Clona un evento (normale o archiviato) come nuova scheda Epici nello stesso calendario,
     * incluso l'audio se presente. L'originale resta intatto (vedi [cloneEvent], stesso pattern). */
    suspend fun cloneEventToEpics(code: String, source: RideEvent): Result<String> = writeReturning {
        // data/ora azzerate: una scheda Epici non ha mai una data propria (è un modello da
        // "Pianificare" di nuovo ogni anno), altrimenti resterebbe quella dell'uscita di
        // origine anche se l'editor non la mostra più (es. l'icona meteo, che dipende da
        // event.data.isNotBlank(), tornerebbe visibile).
        val clone = source.copy(id = "", annullata = false, data = "", ora = "", hasAudio = false, partecipanti = emptyList(), forse = emptyList(), archivedAtMillis = 0L)
        val newId = upsertEpic(code, clone).getOrThrow()
        val audio = eventAudio(code, source.id) ?: try {
            val snap = db.collection("calendars").document(code).collection("archived")
                .document(source.id).collection("audio").document("clip").get().await()
            if (!snap.exists()) null else EventAudio(
                audioData = snap.getString("audioData") ?: "",
                audioMimeType = snap.getString("audioMimeType") ?: "",
            )
        } catch (e: Exception) { null }
        if (audio != null && audio.audioData.isNotBlank()) {
            saveEpicAudio(code, newId, audio.audioData, audio.audioMimeType).getOrThrow()
        }
        newId
    }

    /**
     * "Partecipa": chiunque, anche senza login, aggiunge il proprio nome alla lista
     * partecipanti dell'evento. arrayUnion, non set/merge dell'intera lista: le Firestore
     * rules autorizzano solo questa forma, così un ospite non può cancellare i nomi altrui.
     */
    suspend fun addParticipant(code: String, eventId: String, name: String): Result<Unit> = write {
        db.collection("calendars").document(code).collection("events").document(eventId)
            .update("partecipanti", FieldValue.arrayUnion(name.trim())).await()
    }

    /**
     * Risposta di partecipazione in una sola scrittura: "yes" (partecipo), "maybe" (forse) o "no" (nessuna).
     * Il nome sta in al più una delle due liste; ogni array cambia di un solo elemento (vedi le rules).
     */
    suspend fun setParticipation(code: String, eventId: String, name: String, status: String): Result<Unit> = write {
        val n = name.trim()
        val yes = if (status == "yes") FieldValue.arrayUnion(n) else FieldValue.arrayRemove(n)
        val maybe = if (status == "maybe") FieldValue.arrayUnion(n) else FieldValue.arrayRemove(n)
        db.collection("calendars").document(code).collection("events").document(eventId)
            .update(mapOf("partecipanti" to yes, "forse" to maybe)).await()
    }

    /** Rimuove il proprio nome dalla lista partecipanti (per correggere un'iscrizione). */
    suspend fun removeParticipant(code: String, eventId: String, name: String): Result<Unit> = write {
        db.collection("calendars").document(code).collection("events").document(eventId)
            .update("partecipanti", FieldValue.arrayRemove(name.trim())).await()
    }

    // ── Commenti sulle uscite (solo utenti loggati) ─────────────

    /** true se l'utente è loggato con un account vero (non anonimo): può leggere e scrivere i commenti. */
    val canComment: Boolean get() = auth.currentUser?.let { !it.isAnonymous } == true

    fun observeEventComments(code: String, eventId: String): Flow<List<EventComment>> = callbackFlow {
        val reg = db.collection("calendars").document(code).collection("events").document(eventId)
            .collection("comments").orderBy("createdAtMillis")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "observeEventComments error", err); trySend(emptyList()); return@addSnapshotListener }
                trySend(snap?.documents.orEmpty().map { d ->
                    EventComment(
                        id = d.id, authorUid = d.getString("authorUid") ?: "", authorName = d.getString("authorName") ?: "",
                        text = d.getString("text") ?: "", createdAtMillis = d.getLong("createdAtMillis") ?: 0L,
                        editedAtMillis = d.getLong("editedAtMillis") ?: 0L,
                    )
                })
            }
        awaitClose { reg.remove() }
    }

    suspend fun addEventComment(code: String, eventId: String, text: String): Result<Unit> = write {
        val user = auth.currentUser?.takeIf { !it.isAnonymous } ?: throw IllegalStateException(Str.t(R.string.err_sign_in_comment))
        val name = user.displayName?.takeIf { it.isNotBlank() } ?: user.email?.substringBefore('@')?.takeIf { it.isNotBlank() } ?: "Utente"
        db.collection("calendars").document(code).collection("events").document(eventId).collection("comments").document().set(
            mapOf("authorUid" to user.uid, "authorName" to name, "text" to text.trim().take(1000), "createdAtMillis" to System.currentTimeMillis())
        ).await()
    }

    suspend fun editEventComment(code: String, eventId: String, commentId: String, text: String): Result<Unit> = write {
        db.collection("calendars").document(code).collection("events").document(eventId).collection("comments").document(commentId)
            .update(mapOf("text" to text.trim().take(1000), "editedAtMillis" to System.currentTimeMillis())).await()
    }

    suspend fun deleteEventComment(code: String, eventId: String, commentId: String): Result<Unit> = write {
        db.collection("calendars").document(code).collection("events").document(eventId).collection("comments").document(commentId).delete().await()
    }

    // ── Lista nera (account) ─────────────────────────────────────

    /** Livello di blocco dell'utente corrente su [code] ("" | "edit" | "ban"): vale il documento con id = uid
     * oppure con id = email (minuscola), il più severo. Segue login/logout. */
    fun observeMyBlock(code: String): Flow<String> = callbackFlow {
        var regs = listOf<com.google.firebase.firestore.ListenerRegistration>()
        val levels = mutableMapOf<String, String>()
        fun push() { trySend(if (levels.values.any { it == "ban" }) "ban" else if (levels.values.any { it.isNotEmpty() }) "edit" else "") }
        val l = com.google.firebase.auth.FirebaseAuth.AuthStateListener { a ->
            regs.forEach { it.remove() }; regs = emptyList(); levels.clear()
            val u = a.currentUser
            if (u == null) { trySend(""); return@AuthStateListener }
            val ids = listOfNotNull(u.uid, u.email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() })
            regs = ids.map { id ->
                db.collection("calendars").document(code).collection("blocks").document(id)
                    .addSnapshotListener { s, err -> levels[id] = if (err != null) "" else s?.getString("level").orEmpty(); push() }
            }
        }
        auth.addAuthStateListener(l)
        awaitClose { regs.forEach { it.remove() }; auth.removeAuthStateListener(l) }
    }

    fun observeBlocks(code: String): Flow<List<BlockedUser>> = callbackFlow {
        val reg = db.collection("calendars").document(code).collection("blocks")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "observeBlocks error", err); trySend(emptyList()); return@addSnapshotListener }
                trySend(snap?.documents.orEmpty().map { BlockedUser(it.id, it.getString("level") ?: "edit", it.getString("label") ?: "", it.getLong("atMillis") ?: 0L) }
                    .sortedBy { it.label.lowercase() })
            }
        awaitClose { reg.remove() }
    }

    suspend fun addBlock(code: String, uid: String, label: String, level: String): Result<Unit> = write {
        val me = auth.currentUser?.uid ?: throw IllegalStateException(Str.t(R.string.err_not_signed_in))
        db.collection("calendars").document(code).collection("blocks").document(uid).set(
            mapOf("level" to level, "label" to label, "byUid" to me, "atMillis" to System.currentTimeMillis())
        ).await()
    }

    suspend fun removeBlock(code: String, uid: String): Result<Unit> = write {
        db.collection("calendars").document(code).collection("blocks").document(uid).delete().await()
    }

    // ── Proposte pubbliche (chiunque, anche senza login) ────────
    // calendars/{code}/proposals/{proposalId}: scrittura pubblica (creazione soltanto,
    // via rules), lettura/approvazione/rifiuto riservate a owner/editor.

    /** Chiunque può proporre un'uscita, anche senza login. */
    suspend fun submitProposal(code: String, p: RideProposal): Result<Unit> = write {
        // Allegati nello stesso documento: stima come in upsertEvent (limite Firestore 1 MiB).
        val estimatedBytes = p.cardBgData.length + p.photoData.length + p.gpxData.length + p.audioData.length
        if (estimatedBytes > 1_044_000) {
            throw DocumentTooLargeException(estimatedBytes)
        }
        // Una sola proposta in attesa per utente e calendario: id = uid (le regole impongono id == uid,
        // quindi un secondo invio è rifiutato finché un editor non approva o rifiuta la prima).
        val myUid = auth.currentUser?.uid ?: throw IllegalStateException("Accedi con Google per proporre un'uscita.")
        val propRef = db.collection("calendars").document(code).collection("proposals").document(myUid)
        try { propRef.set(
            mapOf(
                "titolo" to p.titolo.trim(),
                "data" to p.data.trim(),
                "ora" to p.ora.trim(),
                "localita" to p.localita.trim(),
                "ritrovo" to p.ritrovo.trim(),
                "proponente" to p.proponente.trim(),
                "waProponente" to p.waProponente.trim(),
                "difficolta" to p.difficolta.trim().lowercase(),
                "linkPercorso" to p.linkPercorso.trim(),
                "link2" to p.link2.trim(),
                "video" to p.video.trim(),
                "note" to p.note.trim(),
                "cardBgData" to p.cardBgData,
                "photoData" to p.photoData,
                "gpxData" to p.gpxData,
                "gpxFileName" to p.gpxFileName.trim(),
                "audioData" to p.audioData,
                "audioMimeType" to p.audioMimeType.trim(),
                "proposedAtMillis" to System.currentTimeMillis(),
            )
        ).await()
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
                throw IllegalStateException("Hai già una proposta in attesa di revisione (o non puoi proporre in questo calendario). / You already have a pending proposal.")
            throw e
        }
        simplylucaapps.girocalendar.push.Push.notifyProposal(code, propRef.id)
    }

    fun observeProposals(code: String): Flow<List<RideProposal>> = callbackFlow {
        val reg = db.collection("calendars").document(code).collection("proposals")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "observeProposals error", err); trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents.orEmpty().map { d ->
                    RideProposal(
                        id = d.id,
                        titolo = d.getString("titolo") ?: "",
                        data = d.getString("data") ?: "",
                        ora = d.getString("ora") ?: "",
                        localita = d.getString("localita") ?: "",
                        ritrovo = d.getString("ritrovo") ?: "",
                        proponente = d.getString("proponente") ?: "",
                        waProponente = d.getString("waProponente") ?: "",
                        difficolta = d.getString("difficolta") ?: "",
                        linkPercorso = d.getString("linkPercorso") ?: "",
                        link2 = d.getString("link2") ?: "",
                        video = d.getString("video") ?: "",
                        note = d.getString("note") ?: "",
                        cardBgData = d.getString("cardBgData") ?: "",
                        photoData = d.getString("photoData") ?: "",
                        gpxData = d.getString("gpxData") ?: "",
                        gpxFileName = d.getString("gpxFileName") ?: "",
                        audioData = d.getString("audioData") ?: "",
                        audioMimeType = d.getString("audioMimeType") ?: "",
                        proposedAtMillis = d.getLong("proposedAtMillis") ?: 0L,
                    )
                }.sortedByDescending { it.proposedAtMillis }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /** Approva la proposta: la copia come [RideEvent] vero e la rimuove dalla coda. Solo editor. */
    suspend fun approveProposal(code: String, p: RideProposal): Result<Unit> = write {
        val eventId = upsertEvent(code, RideEvent(
            titolo = p.titolo, data = p.data, ora = p.ora, localita = p.localita,
            ritrovo = p.ritrovo, proponente = p.proponente, waProponente = p.waProponente,
            difficolta = p.difficolta, linkPercorso = p.linkPercorso, link2 = p.link2,
            video = p.video, note = p.note,
            cardBgData = p.cardBgData, photoData = p.photoData,
            gpxData = p.gpxData, gpxFileName = p.gpxFileName,
            hasAudio = p.audioData.isNotBlank(),
        )).getOrThrow()
        if (p.audioData.isNotBlank()) {
            saveEventAudio(code, eventId, p.audioData, p.audioMimeType).getOrThrow()
        }
        db.collection("calendars").document(code).collection("proposals").document(p.id).delete().await()
    }

    /**
     * Come [approveProposal], ma con un [RideEvent] già modificato dall'owner/editor
     * ("Rivedi" nel dialog Proposte: apre l'editor completo prima di pubblicare, invece di
     * approvare la proposta così com'è arrivata).
     */
    suspend fun approveProposalReviewed(code: String, proposalId: String, reviewed: RideEvent, audioChanged: Boolean): Result<Unit> = write {
        val eventId = upsertEvent(code, reviewed.copy(hasAudio = reviewed.audioData.isNotBlank())).getOrThrow()
        if (audioChanged) {
            saveEventAudio(code, eventId, reviewed.audioData, reviewed.audioMimeType).getOrThrow()
        }
        db.collection("calendars").document(code).collection("proposals").document(proposalId).delete().await()
    }

    /** Rifiuta (elimina) la proposta senza crearla come evento. Solo editor. */
    suspend fun rejectProposal(code: String, proposalId: String): Result<Unit> = write {
        db.collection("calendars").document(code).collection("proposals").document(proposalId).delete().await()
    }

    suspend fun updateCalendarInfo(
        code: String, name: String, description: String, listed: Boolean,
    ): Result<Unit> = write {
        db.collection("calendars").document(code).set(
            mapOf(
                "name" to name.trim(), "description" to description.trim(), "listed" to listed,
            ),
            SetOptions.merge()
        ).await()
    }

    /** Aggiorna il logo del calendario: colore del pallino e/o immagine PNG (base64). Solo owner. */
    suspend fun updateCalendarLogo(code: String, logoColor: String, logoData: String): Result<Unit> = write {
        db.collection("calendars").document(code).set(
            mapOf("logoColor" to logoColor.trim(), "logoData" to logoData),
            SetOptions.merge()
        ).await()
    }

    // ── Comunicazioni (owner-only, più annunci indipendenti con data) ──
    // calendars/{code}/announcements/{id}, ordinate più recente prima.

    fun observeAnnouncements(code: String): Flow<List<Announcement>> = callbackFlow {
        val reg = db.collection("calendars").document(code).collection("announcements")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "observeAnnouncements error", err); trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents.orEmpty().map { d ->
                    Announcement(
                        id = d.id,
                        title = d.getString("title") ?: "",
                        text = d.getString("text") ?: "",
                        createdAtMillis = d.getLong("createdAtMillis") ?: 0L,
                        sortOrder = d.getLong("sortOrder"),
                    )
                }.sortedWith(compareBy<Announcement> { it.sortOrder != null }
                    .thenBy { it.sortOrder ?: 0L }
                    .thenByDescending { it.createdAtMillis })
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /** Aggiunge una nuova comunicazione. Solo owner. */
    suspend fun addAnnouncement(code: String, title: String, text: String): Result<Unit> = write {
        db.collection("calendars").document(code).collection("announcements").document().set(
            mapOf("title" to title.trim(), "text" to text.trim(), "createdAtMillis" to System.currentTimeMillis())
        ).await()
    }

    /** Modifica titolo/testo di una comunicazione (la data di pubblicazione resta). Solo owner. */
    suspend fun updateAnnouncement(code: String, id: String, title: String, text: String): Result<Unit> = write {
        db.collection("calendars").document(code).collection("announcements").document(id)
            .update(mapOf("title" to title.trim(), "text" to text.trim())).await()
    }

    /** Salva l'ordine scelto: assegna a ogni comunicazione la sua posizione nella lista. Solo owner. */
    suspend fun reorderAnnouncements(code: String, orderedIds: List<String>): Result<Unit> = write {
        val col = db.collection("calendars").document(code).collection("announcements")
        val batch = db.batch()
        orderedIds.forEachIndexed { i, id -> batch.update(col.document(id), "sortOrder", i.toLong()) }
        batch.commit().await()
    }

    /** Elimina una comunicazione. Solo owner. */
    suspend fun deleteAnnouncement(code: String, announcementId: String): Result<Unit> = write {
        db.collection("calendars").document(code).collection("announcements").document(announcementId).delete().await()
    }

    // ── Calendari recenti sincronizzati con l'account ───────────
    // users/{uid}.recents: gli ultimi calendari aperti dall'utente, per ritrovarli su
    // qualunque dispositivo dopo il login. Nessuna Cloud Function: sovrascrittura client-side
    // dell'intero campo ad ogni apertura, protetta dalle rules (solo il proprietario scrive).

    /** Ultimi calendari aperti dall'utente loggato, più recente prima. Vuoto se non loggato. */
    suspend fun accountRecents(): List<RecentCalendar> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snap = db.collection("users").document(uid).get().await()
            @Suppress("UNCHECKED_CAST")
            val raw = (snap.get("recents") as? List<Map<String, Any?>>).orEmpty()
            raw.mapNotNull { m ->
                val code = m["code"] as? String ?: return@mapNotNull null
                RecentCalendar(
                    code = code,
                    name = m["name"] as? String ?: "",
                    isOwner = m["isOwner"] as? Boolean ?: false,
                    logoColor = m["logoColor"] as? String ?: "",
                    logoData = m["logoData"] as? String ?: "",
                    ownerEmail = m["ownerEmail"] as? String ?: "",
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "accountRecents failed", e); emptyList()
        }
    }

    /** Registra l'apertura di [code] nei recenti dell'account. Nessun effetto se non loggato. */
    suspend fun rememberAccountCalendar(recent: RecentCalendar): Unit {
        val uid = auth.currentUser?.uid ?: return
        try {
            val ref = db.collection("users").document(uid)
            val current = accountRecents()
            val updated = (listOf(recent) + current.filterNot { it.code == recent.code }).take(20)
            ref.set(
                mapOf(
                    "recents" to updated.map {
                        mapOf(
                            "code" to it.code, "name" to it.name, "isOwner" to it.isOwner,
                            "logoColor" to it.logoColor, "logoData" to it.logoData,
                            "ownerEmail" to it.ownerEmail,
                        )
                    }
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "rememberAccountCalendar failed", e)
        }
    }

    /** Toglie [code] dai recenti dell'account (es. calendario eliminato da un altro dispositivo). */
    suspend fun forgetAccountCalendar(code: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val current = accountRecents()
            if (current.none { it.code == code }) return
            db.collection("users").document(uid).set(
                mapOf("recents" to current.filterNot { it.code == code }.map {
                    mapOf("code" to it.code, "name" to it.name, "isOwner" to it.isOwner,
                        "logoColor" to it.logoColor, "logoData" to it.logoData, "ownerEmail" to it.ownerEmail)
                }),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "forgetAccountCalendar failed", e)
        }
    }

    // ── Eliminazione account ──────────────────────────────────────
    // Piano Spark, nessuna Cloud Function: tutto eseguito dal dispositivo dell'utente,
    // autenticato come lui, prima di FirebaseAuth.currentUser.delete().

    /**
     * Calendari amministrati dall'utente corrente, per decidere cosa fare alla cancellazione:
     * quelli con altri collaboratori passeranno automaticamente al più anziano di loro;
     * quelli dove l'utente è l'unico editor verranno eliminati insieme all'account, a meno
     * che l'utente non nomini qualcuno prima (vedi [deleteAccount]).
     */
    suspend fun ownedCalendarsForDeletion(): List<RideCalendar> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        return try {
            db.collection("calendars").whereEqualTo("ownerUid", uid).get().await()
                .documents.map { it.toRideCalendar(it.id) }
        } catch (e: Exception) {
            Log.w(TAG, "ownedCalendarsForDeletion failed", e); emptyList()
        }
    }

    /**
     * Elimina l'account corrente. Per ogni calendario posseduto:
     *  - se ha altri collaboratori, la proprietà passa al più anziano di loro (primo
     *    aggiunto, cioè il primo di `editors` diverso dall'utente);
     *  - altrimenti, se l'utente ha nominato qualcuno in [nominations] (code -> uid),
     *    quella persona diventa owner;
     *  - altrimenti il calendario (e i suoi eventi) viene eliminato.
     * Poi l'utente viene rimosso dagli `editors` di ogni calendario altrui a cui collabora,
     * e infine l'account Firebase viene eliminato.
     */
    suspend fun deleteAccount(nominations: Map<String, String> = emptyMap()): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException(Str.t(R.string.err_not_signed_in)))
        val uid = user.uid
        return try {
            // 1) calendari posseduti: trasferisci o elimina
            val owned = db.collection("calendars").whereEqualTo("ownerUid", uid).get().await()
            for (doc in owned.documents) {
                val code = doc.id
                @Suppress("UNCHECKED_CAST")
                val editors = (doc.get("editors") as? List<String>).orEmpty()
                val heir = editors.firstOrNull { it != uid } ?: nominations[code]?.trim()?.takeIf { it.isNotBlank() }
                if (heir != null) {
                    if (heir !in editors) {
                        db.collection("calendars").document(code)
                            .update("editors", FieldValue.arrayUnion(heir)).await()
                    }
                    db.collection("calendars").document(code)
                        .update("ownerUid", heir).await()
                } else {
                    deleteCalendarDeep(code)
                }
            }
            // 2) rimuovi l'utente dai calendari altrui a cui collabora
            val editing = db.collection("calendars").whereArrayContains("editors", uid).get().await()
            for (doc in editing.documents) {
                if (doc.getString("ownerUid") == uid) continue // già gestiti sopra
                runCatching { db.collection("calendars").document(doc.id).collection("contacts").document(uid).delete().await() }
                db.collection("calendars").document(doc.id)
                    .update("editors", FieldValue.arrayRemove(uid)).await()
            }
            // 3) elimina l'account Firebase
            user.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "deleteAccount failed", e)
            Result.failure(e)
        }
    }

    /** Elimina definitivamente un calendario e tutto il suo contenuto. Solo owner (rules).
     * Lo toglie anche dai recenti sincronizzati sull'account. */
    suspend fun deleteCalendar(code: String): Result<Unit> = write {
        deleteCalendarDeep(code)
        val uid = auth.currentUser?.uid
        if (uid != null) {
            val left = accountRecents().filterNot { it.code == code }
            db.collection("users").document(uid).set(
                mapOf("recents" to left.map {
                    mapOf("code" to it.code, "name" to it.name, "isOwner" to it.isOwner,
                        "logoColor" to it.logoColor, "logoData" to it.logoData, "ownerEmail" to it.ownerEmail)
                }),
                SetOptions.merge()
            ).await()
        }
    }

    /** Cancella il calendario e le sue sottocollezioni. Il documento calendario va per ultimo:
     * le rules delle sottocollezioni leggono `editors` da lì. */
    private suspend fun deleteCalendarDeep(code: String) {
        val ref = db.collection("calendars").document(code)
        // Raccolte con un sotto-documento audio/clip.
        for (name in listOf("events", "archived", "epics")) {
            for (doc in ref.collection(name).get().await().documents) {
                doc.reference.collection("audio").get().await().documents.forEach { it.reference.delete().await() }
                doc.reference.delete().await()
            }
        }
        for (name in listOf("proposals", "announcements", "contacts")) {
            for (doc in ref.collection(name).get().await().documents) doc.reference.delete().await()
        }
        ref.delete().await()
    }

    private suspend inline fun write(block: () -> Unit): Result<Unit> = try {
        block(); Result.success(Unit)
    } catch (e: Exception) {
        Log.w(TAG, "write failed", e); Result.failure(e)
    }

    private suspend inline fun <T> writeReturning(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: Exception) {
        Log.w(TAG, "write failed", e); Result.failure(e)
    }

    private class CollisionException : Exception()

    /** Il documento (evento con foto+GPX, o audio) supera il limite di 1 MiB per documento
     * Firestore. [estimatedBytes] è la stima usata per la decisione, in byte. */
    class DocumentTooLargeException(val estimatedBytes: Int) : Exception()

    companion object {
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // niente 0/O/1/I
        private const val LEN = 6

        fun randomCode(): String = buildString {
            repeat(LEN) { append(ALPHABET[Random.nextInt(ALPHABET.length)]) }
        }

        /** dd/MM/yyyy -> intero yyyymmdd (per ordinamento); 0 se non parsabile. */
        fun parseDate(s: String): Long {
            val p = s.split("/")
            if (p.size != 3) return 0L
            val d = p[0].toIntOrNull() ?: return 0L
            val m = p[1].toIntOrNull() ?: return 0L
            val y = p[2].toIntOrNull() ?: return 0L
            return y.toLong() * 10000 + m.toLong() * 100 + d.toLong()
        }
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toRideEvent(): RideEvent = RideEvent(
    id = id,
    titolo = getString("titolo") ?: "",
    data = getString("data") ?: "",
    ora = getString("ora") ?: "",
    localita = getString("localita") ?: "",
    ritrovo = getString("ritrovo") ?: "",
    proponente = getString("proponente") ?: "",
    waProponente = getString("waProponente") ?: "",
    difficolta = getString("difficolta") ?: "",
    linkPercorso = getString("linkPercorso") ?: "",
    link2 = getString("link2") ?: "",
    video = getString("video") ?: "",
    note = getString("note") ?: "",
    cardBgData = getString("cardBgData") ?: "",
    photoData = getString("photoData") ?: "",
    partecipanti = (get("partecipanti") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
    forse = (get("forse") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
    gpxData = getString("gpxData") ?: "",
    gpxFileName = getString("gpxFileName") ?: "",
    // audioData/audioMimeType NON letti da qui: da questo refactoring l'audio vive in un
    // documento separato (vedi EventAudio/CalendarRepository.eventAudio), caricato a parte
    // quando serve mostrare/riprodurre la clip. hasAudio (sotto) segnala solo se esiste.
    // Retrocompatibilità: eventi salvati PRIMA di questo refactoring possono avere ancora
    // audioData inline qui — letto comunque per non perdere quelle clip già esistenti.
    audioData = getString("audioData") ?: "",
    audioMimeType = getString("audioMimeType") ?: "",
    hasAudio = getBoolean("hasAudio") ?: getString("audioData").let { !it.isNullOrBlank() },
    annullata = getBoolean("annullata") ?: false,
    trailZone = getString("trailZone") ?: "",
    durataGiorni = (getLong("durataGiorni") ?: 1L).toInt().coerceAtLeast(1),
    archivedAtMillis = getLong("archivedAtMillis") ?: 0L,
)

private fun com.google.firebase.firestore.DocumentSnapshot.toRideCalendar(code: String): RideCalendar {
    @Suppress("UNCHECKED_CAST")
    val editors = (get("editors") as? List<String>).orEmpty()
    @Suppress("UNCHECKED_CAST")
    val editorLabels = (get("editorLabels") as? Map<String, String>).orEmpty()
    return RideCalendar(
        code = code,
        name = getString("name") ?: "",
        description = getString("description") ?: "",
        ownerUid = getString("ownerUid") ?: "",
        ownerEmail = getString("ownerEmail") ?: "",
        listed = getBoolean("listed") ?: true,
        editors = editors,
        editorLabels = editorLabels,
        createdAtMillis = getLong("createdAtMillis") ?: 0L,
        logoColor = getString("logoColor") ?: "",
        logoData = getString("logoData") ?: "",
        openCount = getLong("openCount") ?: 0L,
    )
}

/** "luca@gmail.com" -> "lu***@gmail.com": versione pubblica dell'email dell'owner. */
fun maskEmail(email: String): String {
    val e = email.trim()
    val at = e.indexOf('@')
    if (at <= 0) return ""
    val keep = if (at >= 3) 2 else 1
    return e.substring(0, keep) + "***" + e.substring(at)
}
