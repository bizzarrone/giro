package simplylucaapps.girocalendar.ui.calendar

import simplylucaapps.girocalendar.util.Str

import simplylucaapps.girocalendar.R

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import simplylucaapps.girocalendar.data.remote.firestore.Announcement
import simplylucaapps.girocalendar.data.remote.firestore.CalendarRepository
import simplylucaapps.girocalendar.data.remote.firestore.CalendarStats
import simplylucaapps.girocalendar.data.remote.firestore.RideCalendar
import simplylucaapps.girocalendar.data.remote.firestore.RideEvent
import simplylucaapps.girocalendar.data.remote.firestore.RideProposal
import simplylucaapps.girocalendar.data.settings.AppSettings

data class CalendarState(
    val code: String,
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val error: String? = null,
    val calendar: RideCalendar? = null,
    val events: List<RideEvent> = emptyList(),
    val proposals: List<RideProposal> = emptyList(),
    val proposalsLoaded: Boolean = false,
    val myBlock: String = "",                 // "" | "edit" | "ban" (lista nera: livello dell'utente corrente)
    val blocks: List<simplylucaapps.girocalendar.data.remote.firestore.BlockedUser> = emptyList(),   // true dopo il primo snapshot delle proposte (per non avvisare al caricamento)
    val archivedEvents: List<RideEvent> = emptyList(),
    val epics: List<RideEvent> = emptyList(),
    val announcements: List<Announcement> = emptyList(),
    val myUid: String? = null,
    val isOwner: Boolean = false,
    val canEdit: Boolean = false,   // uid ∈ calendar.editors
)

class CalendarViewModel(
    private val repo: CalendarRepository,
    private val settings: AppSettings,
    val code: String,
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarState(code = code, myUid = repo.currentUid))
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    /** Statistiche leggere di un calendario (tab "Calendari" dei recenti). */
    suspend fun loadCalendarStats(code: String): CalendarStats = repo.calendarStats(code)

    // ── Modifica di UN ALTRO calendario dalla tab "Calendari" (l'utente è owner) ──
    // A differenza del resto del ViewModel (legato a [code], il calendario aperto ora), questi
    // metodi operano su un codice arbitrario passato esplicitamente: la tab "Calendari" mostra
    // i recenti dell'utente, che possono essere calendari diversi da quello corrente.

    /** Calendario completo (per il dialog di modifica), per un codice arbitrario. */
    suspend fun loadCalendarInfo(code: String): RideCalendar? = repo.calendarInfo(code)

    /** Elimina un calendario di cui l'utente è owner e lo toglie dai recenti locali. */
    fun deleteCalendarByCode(code: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.deleteCalendar(code)
                .onSuccess { settings.forgetCalendar(code); onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    /** Completa le email dei collaboratori aggiunti per UID; [onDone] se ha scritto qualcosa. */
    fun backfillEditorLabels(cal: RideCalendar, onDone: () -> Unit) {
        viewModelScope.launch { if (repo.backfillEditorLabels(cal.code, cal)) onDone() }
    }

    fun editCalendarInfo(
        code: String, name: String, description: String, listed: Boolean,
        onResult: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            repo.updateCalendarInfo(code, name, description, listed)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun editCalendarLogo(code: String, logoColor: String, logoData: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.updateCalendarLogo(code, logoColor, logoData)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun editAddEditor(code: String, input: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val email = input.trim().takeIf { '@' in it }
            repo.resolveEditorInput(input)
                .mapCatching { uid -> repo.addEditor(code, uid, email).getOrThrow() }
                .onSuccess { onResult(true, email?.let { Str.t(R.string.err_added, it) }) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun editRemoveEditor(code: String, uid: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.removeEditor(code, uid)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    private fun applyPerms(calendar: RideCalendar?) {
        if (calendar == null) {
            _state.update { it.copy(loading = false, notFound = true) }
            // Calendario inesistente (es. eliminato): non riaprirlo più all'avvio.
            viewModelScope.launch { settings.forgetCalendar(code); repo.forgetAccountCalendar(code) }
            return
        }
        val uid = repo.currentUid
        val sup = repo.isSuperAdmin
        val realOwner = uid != null && uid == calendar.ownerUid
        val owner = realOwner || sup
        val editor = ((uid != null && uid in calendar.editors) && _state.value.myBlock.isEmpty()) || sup
        // fuori dal thread UI: decodifica/codifica JSON dei recenti (con i loghi base64) bloccava l'avvio
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            settings.rememberCalendar(calendar.code, calendar.name, isOwner = realOwner, logoColor = calendar.logoColor, logoData = calendar.logoData, ownerEmail = calendar.ownerEmail)
            repo.rememberAccountCalendar(
                simplylucaapps.girocalendar.data.settings.RecentCalendar(
                    calendar.code, calendar.name, realOwner, calendar.logoColor, calendar.logoData, calendar.ownerEmail,
                )
            )
        }
        if (realOwner) viewModelScope.launch { repo.backfillOwnerEmail(calendar.code, calendar) }
        // Dati privati (email owner completa, etichette collaboratori) solo per chi è loggato.
        if (uid != null) viewModelScope.launch {
            val full = repo.withPrivate(calendar)
            _state.update { s -> if (s.calendar?.code == calendar.code) s.copy(calendar = full) else s }
        }
        _state.update {
            it.copy(
                loading = false, notFound = false, calendar = calendar,
                myUid = uid, isOwner = owner, canEdit = editor,
            )
        }
    }

    /** Ricalcola i permessi dopo un login/logout (il doc calendario non è cambiato). */
    fun reload() = applyPerms(_state.value.calendar)

    private var blocksJob: kotlinx.coroutines.Job? = null

    fun addBlock(input: String, level: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val raw = input.trim()
            if ('@' in raw) {
                // per email: il documento ha come id l'email (minuscola), non serve che l'account sia mai stato indicizzato
                val email = raw.lowercase()
                repo.addBlock(code, email, email, level).onSuccess { onResult(true, null) }.onFailure { onResult(false, it.message) }
            } else {
                repo.resolveEditorInput(raw)
                    .mapCatching { uid -> repo.addBlock(code, uid, uid.take(6) + "…" + uid.takeLast(4), level).getOrThrow() }
                    .onSuccess { onResult(true, null) }
                    .onFailure { onResult(false, it.message) }
            }
        }
    }

    fun removeBlock(uid: String) { viewModelScope.launch { repo.removeBlock(code, uid) } }

    private var proposalsJob: kotlinx.coroutines.Job? = null
    private var archivedJob: kotlinx.coroutines.Job? = null

    init {
        // Una volta per apertura del calendario (il ViewModel è creato una volta per "code",
        // vedi viewModel(key = "cal-$code", ...) in MainActivity), non a ogni ricomposizione.
        repo.incrementOpenCount(code)
        viewModelScope.launch {
            repo.observeMyBlock(code).collect { level ->
                _state.update { it.copy(myBlock = level) }
                applyPerms(_state.value.calendar)
            }
        }
        viewModelScope.launch {
            _state.map { it.canEdit }.distinctUntilChanged().collect { canEdit ->
                blocksJob?.cancel()
                blocksJob = if (canEdit) viewModelScope.launch { repo.observeBlocks(code).collect { l -> _state.update { it.copy(blocks = l) } } }
                else { _state.update { it.copy(blocks = emptyList()) }; null }
            }
        }
        viewModelScope.launch {
            repo.observeCalendar(code).collect { calendar -> applyPerms(calendar) }
        }
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(repo.observeEvents(code).distinctUntilChanged(), repo.observeLinkedEvents(code)) { own, linked ->
                if (linked.isEmpty()) own else (own + linked).sortedBy { CalendarRepository.parseDate(it.data) }
            }.collect { list -> _state.update { it.copy(events = list) } }
        }
        viewModelScope.launch {
            repo.observeEpics(code).collect { list -> _state.update { it.copy(epics = list) } }
        }
        viewModelScope.launch {
            repo.observeAnnouncements(code).collect { list -> _state.update { it.copy(announcements = list) } }
        }
        // Le proposte sono leggibili solo da owner/editor (rules): avviamo l'ascolto solo
        // quando canEdit diventa true, altrimenti la lettura fallirebbe silenziosamente
        // per un ospite o un editor non ancora riconosciuto.
        viewModelScope.launch {
            _state.map { it.canEdit }.distinctUntilChanged().collect { canEdit ->
                proposalsJob?.cancel()
                proposalsJob = if (canEdit) {
                    viewModelScope.launch {
                        repo.observeProposals(code).collect { list -> _state.update { it.copy(proposals = list, proposalsLoaded = true) } }
                    }
                } else {
                    _state.update { it.copy(proposals = emptyList(), proposalsLoaded = false) }
                    null
                }
            }
        }
        // Stessa logica per l'archivio: leggibile solo da owner/editor.
        viewModelScope.launch {
            _state.map { it.canEdit }.distinctUntilChanged().collect { canEdit ->
                archivedJob?.cancel()
                archivedJob = if (canEdit) {
                    viewModelScope.launch {
                        repo.observeArchivedEvents(code).collect { list -> _state.update { it.copy(archivedEvents = list) } }
                    }
                } else {
                    _state.update { it.copy(archivedEvents = emptyList()) }
                    null
                }
            }
        }
    }

    /** Carica la clip audio esistente di un evento (documento separato) per precompilare
     * l'editor quando si modifica un evento con [RideEvent.hasAudio] true — lo stato degli
     * eventi osservati non la contiene più (vedi [simplylucaapps.girocalendar.data.remote.firestore.EventAudio]). */
    suspend fun loadEventAudio(eventId: String): simplylucaapps.girocalendar.data.remote.firestore.EventAudio? =
        tgt(eventId).let { (c, i) -> repo.eventAudio(c, i) }

    /** Calendari dove l'utente corrente è editor/owner (escluso quello aperto ora), per il
     * picker "Clona in un mio calendario" nel dettaglio evento. */
    fun myCalendars(onResult: (List<RideCalendar>) -> Unit) {
        viewModelScope.launch {
            onResult(repo.myCalendars().sortedByDescending { it.code == code })
        }
    }

    /** Clona [event] (letto da questo calendario, anche senza esserne editor) in [targetCode],
     * dove l'utente deve essere editor/owner — le regole Firestore lo verificano comunque
     * lato server, qui si passa solo il codice scelto nel picker. */
    fun cloneEvent(event: RideEvent, targetCode: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.cloneEvent(code, event, targetCode)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    // ── Scritture ──────────────────────────────────────────────
    /** [onResult]: (successo, messaggio d'errore già pronto per l'utente o null, documento
     * troppo grande sì/no). Il terzo parametro permette al chiamante di mostrare un messaggio
     * localizzato dedicato per [CalendarRepository.DocumentTooLargeException], dato che qui
     * non c'è accesso alle risorse stringa.
     *
     * [audioChanged]: true se l'utente ha toccato l'audio in questa sessione dell'editor
     * (caricato uno nuovo o rimosso quello esistente) — senza questo flag non sapremmo
     * distinguere "audioData vuoto perché non toccato" da "audioData vuoto perché rimosso
     * esplicitamente", dato che l'evento osservato non porta più l'audio con sé.
     */
    fun saveEvent(e: RideEvent, audioChanged: Boolean, onResult: (Boolean, String?, Boolean) -> Unit) {
        viewModelScope.launch {
            repo.upsertEvent(code, e.copy(hasAudio = e.audioData.isNotBlank() || (e.hasAudio && !audioChanged)))
                .onSuccess { eventId ->
                    if (audioChanged) {
                        repo.saveEventAudio(code, eventId, e.audioData, e.audioMimeType)
                            .onSuccess { onResult(true, null, false) }
                            .onFailure {
                                val tooLarge = it is CalendarRepository.DocumentTooLargeException
                                onResult(false, if (tooLarge) null else it.message, tooLarge)
                            }
                    } else {
                        onResult(true, null, false)
                    }
                }
                .onFailure {
                    val tooLarge = it is CalendarRepository.DocumentTooLargeException
                    onResult(false, if (tooLarge) null else it.message, tooLarge)
                }
        }
    }

    fun deleteEvent(eventId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.deleteEvent(code, eventId)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    /** Sposta l'evento in archivio invece di eliminarlo definitivamente. */
    fun archiveEvent(e: RideEvent, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.archiveEvent(code, e)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    /** Elimina definitivamente un evento archiviato. */
    fun deleteArchivedEvent(eventId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.deleteArchivedEvent(code, eventId)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    // ── Epici ──────────────────────────────────────────────────

    suspend fun loadEpicAudio(epicId: String): simplylucaapps.girocalendar.data.remote.firestore.EventAudio? =
        repo.epicAudio(code, epicId)

    fun saveEpic(e: RideEvent, audioChanged: Boolean, onResult: (Boolean, String?, Boolean) -> Unit) {
        viewModelScope.launch {
            repo.upsertEpic(code, e.copy(hasAudio = e.audioData.isNotBlank() || (e.hasAudio && !audioChanged)))
                .onSuccess { epicId ->
                    if (audioChanged) {
                        repo.saveEpicAudio(code, epicId, e.audioData, e.audioMimeType)
                            .onSuccess { onResult(true, null, false) }
                            .onFailure {
                                val tooLarge = it is CalendarRepository.DocumentTooLargeException
                                onResult(false, if (tooLarge) null else it.message, tooLarge)
                            }
                    } else {
                        onResult(true, null, false)
                    }
                }
                .onFailure {
                    val tooLarge = it is CalendarRepository.DocumentTooLargeException
                    onResult(false, if (tooLarge) null else it.message, tooLarge)
                }
        }
    }

    fun deleteEpic(epicId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.deleteEpic(code, epicId)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    /** Clona un evento (normale o archiviato) come nuova scheda "Epici" nello stesso calendario. */
    fun addToEpics(source: RideEvent, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.cloneEventToEpics(code, source)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    /** Evento collegato da un altro calendario: id «L:codice:idOriginale» → (codice d'origine, id originale). */
    private fun tgt(eventId: String): Pair<String, String> =
        if (eventId.startsWith("L:")) eventId.split(":", limit = 3).let { it[1] to it[2] } else code to eventId

    /** Collega un evento di un altro calendario a questo. */
    fun linkEvent(srcCode: String, srcEventId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch { repo.addLink(code, srcCode, srcEventId).onSuccess { onResult(true, null) }.onFailure { onResult(false, it.message) } }
    }
    fun unlinkEvent(linkId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch { repo.removeLink(code, linkId).onSuccess { onResult(true, null) }.onFailure { onResult(false, it.message) } }
    }
    fun linkedIds(): Set<String> = _state.value.events.filter { it.fromCode.isNotEmpty() }.map { it.id }.toSet()
    private var calPool: List<RideCalendar>? = null
    /** Cerca calendari per codice esatto o per parte del nome (pubblici + i miei). */
    fun findCalendars(q: String, onResult: (List<RideCalendar>) -> Unit) {
        viewModelScope.launch {
            val pool = calPool ?: (repo.allCalendars() + runCatching { repo.myCalendars() }.getOrDefault(emptyList())).distinctBy { it.code }.also { calPool = it }
            val t = q.trim()
            val byName = pool.filter { it.name.contains(t, ignoreCase = true) || it.code.equals(t, ignoreCase = true) }
            val exact = if (byName.none { it.code.equals(t, ignoreCase = true) }) repo.calendarInfo(t.uppercase()) else null
            onResult((listOfNotNull(exact) + byName).filter { it.code != code })
        }
    }
    fun eventsOf(c: String, onResult: (List<RideEvent>?) -> Unit) { viewModelScope.launch { onResult(repo.eventsOf(c.trim().uppercase())) } }

    /** "Partecipa": chiunque, anche senza login. */
    fun addParticipant(eventId: String, name: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            tgt(eventId).let { (c, i) -> repo.addParticipant(c, i, name) }
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    /** status: "yes" | "maybe" | "no". */
    fun setParticipation(eventId: String, name: String, status: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            tgt(eventId).let { (c, i) -> repo.setParticipation(c, i, name, status) }
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun removeParticipant(eventId: String, name: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            tgt(eventId).let { (c, i) -> repo.removeParticipant(c, i, name) }
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    // ── Commenti sulle uscite ──
    val canComment: Boolean get() = repo.canComment
    fun observeEventComments(eventId: String) = tgt(eventId).let { (c, i) -> repo.observeEventComments(c, i) }
    fun addEventComment(eventId: String, text: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch { tgt(eventId).let { (c, i) -> repo.addEventComment(c, i, text) }.onSuccess { onResult(true, null) }.onFailure { onResult(false, it.message) } }
    }
    fun editEventComment(eventId: String, id: String, text: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch { tgt(eventId).let { (c, i) -> repo.editEventComment(c, i, id, text) }.onSuccess { onResult(true, null) }.onFailure { onResult(false, it.message) } }
    }
    fun deleteEventComment(eventId: String, id: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch { tgt(eventId).let { (c, i) -> repo.deleteEventComment(c, i, id) }.onSuccess { onResult(true, null) }.onFailure { onResult(false, it.message) } }
    }

    /** Chiunque può proporre un'uscita, anche senza login. */
    fun submitProposal(p: RideProposal, onResult: (Boolean, String?, Boolean) -> Unit) {
        viewModelScope.launch {
            repo.submitProposal(code, p)
                .onSuccess { onResult(true, null, false) }
                .onFailure {
                    val tooLarge = it is CalendarRepository.DocumentTooLargeException
                    onResult(false, if (tooLarge) null else it.message, tooLarge)
                }
        }
    }

    fun approveProposal(p: RideProposal, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.approveProposal(code, p)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    /** "Rivedi": approva la proposta con i campi eventualmente modificati nell'editor. */
    fun approveProposalReviewed(proposalId: String, reviewed: RideEvent, audioChanged: Boolean, onResult: (Boolean, String?, Boolean) -> Unit) {
        viewModelScope.launch {
            repo.approveProposalReviewed(code, proposalId, reviewed, audioChanged)
                .onSuccess { onResult(true, null, false) }
                .onFailure {
                    val tooLarge = it is CalendarRepository.DocumentTooLargeException
                    onResult(false, if (tooLarge) null else it.message, tooLarge)
                }
        }
    }

    fun rejectProposal(proposalId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.rejectProposal(code, proposalId)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun saveCalendarInfo(
        name: String, description: String, listed: Boolean,
        onResult: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            repo.updateCalendarInfo(code, name, description, listed)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun saveCalendarLogo(logoColor: String, logoData: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.updateCalendarLogo(code, logoColor, logoData)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    /** Aggiunge una nuova comunicazione. Solo owner. */
    fun addAnnouncement(title: String, text: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.addAnnouncement(code, title, text)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    /** Elimina una comunicazione. Solo owner. */
    fun updateAnnouncement(id: String, title: String, text: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.updateAnnouncement(code, id, title, text)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    /** Sposta una comunicazione di [delta] posti (-1 su, +1 giù) e salva l'ordine. Solo owner. */
    fun moveAnnouncement(id: String, delta: Int, onResult: (Boolean, String?) -> Unit) {
        val ids = _state.value.announcements.map { it.id }.toMutableList()
        val i = ids.indexOf(id); val j = i + delta
        if (i < 0 || j !in ids.indices) return
        ids.add(j, ids.removeAt(i))
        viewModelScope.launch {
            repo.reorderAnnouncements(code, ids)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun deleteAnnouncement(announcementId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.deleteAnnouncement(code, announcementId)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    /**
     * [input] può essere l'email di login del collaboratore o il suo codice utente (uid).
     * In caso di successo, [onResult] riceve come secondo parametro l'etichetta aggiunta
     * (l'email, se l'invito è avvenuto per email) per confermare in UI chi è stato aggiunto.
     */
    fun addEditor(input: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val email = input.trim().takeIf { '@' in it }
            repo.resolveEditorInput(input)
                .mapCatching { uid -> repo.addEditor(code, uid, email).getOrThrow() }
                .onSuccess { onResult(true, email?.let { Str.t(R.string.err_added, it) }) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun removeEditor(uid: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.removeEditor(code, uid)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    companion object {
        fun factory(repo: CalendarRepository, settings: AppSettings, code: String): ViewModelProvider.Factory =
            viewModelFactory { initializer { CalendarViewModel(repo, settings, code) } }
    }
}
