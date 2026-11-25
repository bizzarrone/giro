package simplylucaapps.girocalendar.ui.createcalendar

import simplylucaapps.girocalendar.util.Str

import simplylucaapps.girocalendar.R

import android.content.Context
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
import simplylucaapps.girocalendar.data.remote.firestore.CalendarRepository
import simplylucaapps.girocalendar.data.settings.AppSettings

data class CreateCalendarState(
    val signedIn: Boolean = false,
    val signInAvailable: Boolean = true,
    val userLabel: String? = null,
    val name: String = "",
    val description: String = "",
    /** "" = colore automatico derivato dal nome (vedi [simplylucaapps.girocalendar.ui.common.dotColor]). */
    val logoColor: String = "",
    /** PNG base64 caricato dalla galleria, o vuoto per usare il pallino colorato. */
    val logoData: String = "",
    val uploadingLogo: Boolean = false,
    val listed: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val createdCode: String? = null,
) {
    val canCreate: Boolean
        get() = signedIn && name.isNotBlank() && !busy
}

class CreateCalendarViewModel(
    private val repo: CalendarRepository,
    private val auth: GoogleAuthRepository,
    private val settings: AppSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateCalendarState())
    val state: StateFlow<CreateCalendarState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            auth.observeUser().collect { user ->
                _state.update {
                    it.copy(signedIn = user != null, userLabel = user?.email ?: user?.displayName)
                }
            }
        }
    }

    fun refreshAvailability(context: Context) {
        _state.update { it.copy(signInAvailable = auth.isAvailable(context)) }
    }

    fun onName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun onListed(v: Boolean) = _state.update { it.copy(listed = v) }
    fun onLogoColor(v: String) = _state.update { it.copy(logoColor = v) }
    fun onLogoUploading(v: Boolean) = _state.update { it.copy(uploadingLogo = v) }
    fun onLogoData(v: String) = _state.update { it.copy(logoData = v) }

    fun signIn(context: Context) {
        viewModelScope.launch {
            val user = auth.signIn(context)
            if (user == null) {
                _state.update {
                    it.copy(error = auth.lastError.value?.message?.let { m -> Str.t(R.string.err_signin_failed_m, m) })
                }
            }
        }
    }

    fun signOut() = auth.signOut()

    fun create() {
        val s = _state.value
        if (!s.canCreate) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            repo.createCalendar(s.name, s.description, s.listed, s.logoColor, s.logoData)
                .onSuccess { code ->
                    settings.rememberCalendar(code, s.name, isOwner = true, logoColor = s.logoColor, logoData = s.logoData)
                    _state.update { it.copy(busy = false, createdCode = code) }
                }
                .onFailure { e ->
                    _state.update { it.copy(busy = false, error = e.message ?: Str.t(R.string.err_create)) }
                }
        }
    }

    companion object {
        fun factory(
            repo: CalendarRepository,
            auth: GoogleAuthRepository,
            settings: AppSettings,
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { CreateCalendarViewModel(repo, auth, settings) } }
    }
}
