package simplylucaapps.girocalendar.ui.search

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import simplylucaapps.girocalendar.data.remote.auth.GoogleAuthRepository
import simplylucaapps.girocalendar.data.remote.firestore.CalendarLookup
import simplylucaapps.girocalendar.data.remote.firestore.CalendarRepository
import simplylucaapps.girocalendar.data.remote.firestore.RideCalendar
import simplylucaapps.girocalendar.data.settings.AppSettings
import simplylucaapps.girocalendar.data.settings.RecentCalendar

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val recents: List<RecentCalendar> = emptyList(),
    val pinned: List<RecentCalendar> = emptyList(),
    /** Ultimi calendari aperti salvati sull'account (Firestore) — seguono l'utente su più dispositivi. */
    val accountRecents: List<RecentCalendar> = emptyList(),
    val myCalendars: List<RideCalendar> = emptyList(),
    val loadingMyCalendars: Boolean = false,
    val resolvedCode: String? = null,
    val myUid: String? = null,
    val isSuper: Boolean = false,
    val signedIn: Boolean = false,
    val userLabel: String? = null,
    val photoUrl: String? = null,
    val allCalendars: List<RideCalendar> = emptyList(),
    val loadingAll: Boolean = false,
    val ownedForDeletion: List<RideCalendar> = emptyList(),
    val loadingOwnedForDeletion: Boolean = false,
    val deletingAccount: Boolean = false,
    val deleteAccountError: String? = null,
    val accountDeleted: Boolean = false,
    val signingIn: Boolean = false,
    val signInError: String? = null,
    val infoCode: String? = null,
    val infoCalendar: RideCalendar? = null,
    val infoEventCount: Int? = null,
    val loadingInfo: Boolean = false,
) {
    /**
     * Recenti da mostrare: da account (Firestore, segue l'utente su più dispositivi) se
     * loggato e già sincronizzati, altrimenti quelli locali del dispositivo.
     */
    val displayedRecents: List<RecentCalendar>
        get() = if (signedIn && accountRecents.isNotEmpty()) accountRecents else recents
}

class SearchViewModel(
    private val repo: CalendarRepository,
    private val auth: GoogleAuthRepository,
    private val settings: AppSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState(myUid = repo.currentUid,
                        isSuper = repo.isSuperAdmin))
    val state: StateFlow<SearchState> = _state.asStateFlow()

    init {
        // I calendari pubblici sono leggibili senza login (Firestore rules: read pubblica su
        // calendars/), quindi vanno caricati subito e mostrati anche a un utente anonimo, non
        // solo dopo un accesso Google riuscito.
        loadAllCalendars()
        viewModelScope.launch {
            settings.recents.collect { list -> _state.update { it.copy(recents = list) } }
        }
        viewModelScope.launch {
            settings.pinned.collect { list -> _state.update { it.copy(pinned = list) } }
        }
        viewModelScope.launch {
            auth.observeUser().collect { user ->
                _state.update {
                    it.copy(
                        signedIn = user != null,
                        myUid = repo.currentUid,
                        isSuper = repo.isSuperAdmin,
                        userLabel = user?.email ?: user?.displayName,
                        photoUrl = user?.photoUrl?.toString(),
                    )
                }
                if (user != null) { loadMyCalendars(); loadAccountRecents(); loadAllCalendars() }
                else _state.update { it.copy(myCalendars = emptyList(), accountRecents = emptyList()) }
            }
        }
    }

    /** Stella su un calendario; onLimit se si supera il massimo. */
    fun togglePinned(cal: RecentCalendar, onLimit: () -> Unit) {
        viewModelScope.launch {
            val on = _state.value.pinned.none { it.code == cal.code }
            if (!settings.setPinned(cal, on)) onLimit()
        }
    }

    private fun loadMyCalendars() {
        _state.update { it.copy(loadingMyCalendars = true) }
        viewModelScope.launch {
            val list = repo.myCalendars()
            _state.update { it.copy(myCalendars = list, loadingMyCalendars = false) }
        }
    }

    fun refreshMyCalendars() {
        if (_state.value.signedIn) { loadMyCalendars(); loadAccountRecents() }
    }

    private fun loadAccountRecents() {
        viewModelScope.launch {
            val recents = repo.accountRecents()
            _state.update { it.copy(accountRecents = recents) }
        }
    }

    fun loadAllCalendars() {
        if (_state.value.loadingAll) return
        _state.update { it.copy(loadingAll = true) }
        viewModelScope.launch {
            val all = repo.allCalendars()
            _state.update { it.copy(allCalendars = all, loadingAll = false) }
        }
    }

    fun signIn(context: android.content.Context) {
        _state.update { it.copy(signingIn = true, signInError = null) }
        viewModelScope.launch {
            val user = auth.signIn(context)
            _state.update {
                it.copy(
                    signingIn = false,
                    myUid = repo.currentUid,
                        isSuper = repo.isSuperAdmin,
                    signInError = if (user == null) (auth.lastError.value?.message ?: Str.t(R.string.err_signin_failed)) else null,
                )
            }
            if (user != null) { loadMyCalendars(); loadAllCalendars() }
        }
    }

    fun consumeSignInError() = _state.update { it.copy(signInError = null) }

    fun signOut() {
        auth.signOut()
        _state.update { it.copy(signedIn = false, myUid = null, isSuper = false, userLabel = null) }
    }

    /** Carica i calendari posseduti, per mostrare all'utente cosa succederà eliminando l'account. */
    fun loadOwnedForDeletion() {
        _state.update { it.copy(loadingOwnedForDeletion = true) }
        viewModelScope.launch {
            val owned = repo.ownedCalendarsForDeletion()
            _state.update { it.copy(ownedForDeletion = owned, loadingOwnedForDeletion = false) }
        }
    }

    /**
     * Elimina l'account. [nominations] mappa code->uid per i calendari dove l'utente
     * è l'unico editor e ha scelto di nominare un successore invece di eliminarli.
     */
    fun deleteAccount(nominations: Map<String, String>) {
        _state.update { it.copy(deletingAccount = true, deleteAccountError = null) }
        viewModelScope.launch {
            repo.deleteAccount(nominations)
                .onSuccess {
                    _state.update {
                        it.copy(
                            deletingAccount = false, accountDeleted = true,
                            signedIn = false, myUid = null, isSuper = false, userLabel = null,
                            myCalendars = emptyList(), ownedForDeletion = emptyList(),
                        )
                    }
                }
                .onFailure { e ->
                    val msg = if (e is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException)
                        Str.t(R.string.err_recent_login)
                    else e.message ?: Str.t(R.string.err_delete)
                    _state.update { it.copy(deletingAccount = false, deleteAccountError = msg) }
                }
        }
    }

    fun consumeAccountDeletionError() = _state.update { it.copy(deleteAccountError = null) }

    fun onQueryChange(q: String) {
        _state.update {
            it.copy(query = q.uppercase().filter { c -> c.isLetterOrDigit() }.take(12), error = null)
        }
    }

    fun search() {
        val code = _state.value.query.trim().uppercase()
        if (code.isBlank()) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.lookupCode(code)) {
                is CalendarLookup.Found -> {
                    settings.rememberCalendar(r.calendar.code, r.calendar.name, isOwner = false)
                    _state.update { it.copy(loading = false, resolvedCode = r.calendar.code) }
                }
                CalendarLookup.NotFound ->
                    _state.update { it.copy(loading = false, error = Str.t(R.string.err_no_calendar)) }
                is CalendarLookup.Error ->
                    _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun consumeResolved() = _state.update { it.copy(resolvedCode = null, query = "") }

    /** Apre il dialog "Info calendario" per [code], caricando il calendario e il conteggio eventi. */
    fun showInfo(code: String) {
        _state.update { it.copy(infoCode = code, infoCalendar = null, infoEventCount = null, loadingInfo = true) }
        viewModelScope.launch {
            val calendar = repo.calendarInfo(code)
            val count = repo.eventCount(code)
            _state.update {
                if (it.infoCode == code) it.copy(infoCalendar = calendar, infoEventCount = count, loadingInfo = false)
                else it
            }
        }
    }

    fun dismissInfo() = _state.update { it.copy(infoCode = null, infoCalendar = null, infoEventCount = null, loadingInfo = false) }

    /** Ricarica solo il calendario del dialog "Info calendario" aperto (dopo add/rimozione collaboratore). */
    private fun refreshInfoCalendar(code: String) {
        viewModelScope.launch {
            val calendar = repo.calendarInfo(code)
            _state.update { if (it.infoCode == code) it.copy(infoCalendar = calendar) else it }
        }
    }

    /** Completa le email dei collaboratori aggiunti per UID e ricarica il dialog. */
    fun backfillEditorLabels(code: String) {
        viewModelScope.launch {
            val cal = _state.value.infoCalendar ?: return@launch
            if (repo.backfillEditorLabels(code, cal)) refreshInfoCalendar(code)
        }
    }

    /** Moderazione (super-admin): rende privato / pubblico un calendario. */
    fun setListed(code: String, listed: Boolean, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.setListed(code, listed)
                .onSuccess { refreshInfoCalendar(code); onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    /** Moderazione (super-admin): elimina definitivamente un calendario. */
    fun deleteCalendarAdmin(code: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.deleteCalendar(code)
                .onSuccess { settings.forgetCalendar(code); dismissInfo(); onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    /** [input] può essere l'email di login del collaboratore o il suo codice utente (uid). */
    fun addEditor(code: String, input: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val email = input.trim().takeIf { '@' in it }
            repo.resolveEditorInput(input)
                .mapCatching { uid -> repo.addEditor(code, uid, email).getOrThrow() }
                .onSuccess { refreshInfoCalendar(code); onResult(true, email?.let { Str.t(R.string.err_added, it) }) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun removeEditor(code: String, uid: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.removeEditor(code, uid)
                .onSuccess { refreshInfoCalendar(code); onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    companion object {
        fun factory(
            repo: CalendarRepository,
            auth: GoogleAuthRepository,
            settings: AppSettings,
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { SearchViewModel(repo, auth, settings) } }
    }
}
