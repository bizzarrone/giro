package simplylucaapps.girocalendar.ui.trails

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
import simplylucaapps.girocalendar.data.remote.firestore.Trail
import simplylucaapps.girocalendar.data.remote.firestore.TrailComment
import simplylucaapps.girocalendar.data.remote.firestore.TrailRepository
import simplylucaapps.girocalendar.data.remote.firestore.TrailStatus

data class TrailUiState(
    val trails: List<Trail> = emptyList(),
    val signedIn: Boolean = false,
    val isAdmin: Boolean = false,
    val myUid: String? = null,
    val zones: List<simplylucaapps.girocalendar.data.remote.firestore.TrailZone> = emptyList(),
    val pois: List<simplylucaapps.girocalendar.data.remote.firestore.Poi> = emptyList(),
)

class TrailViewModel(
    private val repo: TrailRepository,
    private val auth: GoogleAuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TrailUiState())
    val state: StateFlow<TrailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeZones().collect { list -> _state.update { it.copy(zones = list) } }
        }
        viewModelScope.launch {
            repo.observePois().collect { list -> _state.update { it.copy(pois = list) } }
        }
        viewModelScope.launch {
            repo.observeTrails().collect { list -> _state.update { it.copy(trails = list) } }
        }
        viewModelScope.launch {
            auth.observeUser().collect { user ->
                _state.update { it.copy(signedIn = user != null, myUid = user?.uid) }
                _state.update { it.copy(isAdmin = if (user != null) repo.isAdmin() else false) }
            }
        }
    }

    fun signIn(context: Context, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onDone(auth.signIn(context) != null) }
    }

    fun observeLog() = repo.observeLog()

    fun observeComments(trailId: String) = repo.observeComments(trailId)

    fun createTrail(name: String, zone: String, difficulty: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.createTrail(name, zone, difficulty)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun renameZone(oldZone: String, newZone: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.renameZone(oldZone, newZone)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    /** Annulla un'operazione del log. onResult(ok, conflitto, errore). */
    fun undoLog(e: simplylucaapps.girocalendar.data.remote.firestore.TrailLogEntry, force: Boolean, onResult: (Boolean, Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.undo(e, force)
                .onSuccess { onResult(true, false, null) }
                .onFailure { onResult(false, it is simplylucaapps.girocalendar.data.remote.firestore.UndoConflictException, it.message) }
        }
    }

    fun renameTrail(trailId: String, newName: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.renameTrail(trailId, newName)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun setDifficulty(trailId: String, difficulty: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.setDifficulty(trailId, difficulty)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun setPosition(trailId: String, lat: Double?, lon: Double?, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.setPosition(trailId, lat, lon)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun setPath(trailId: String, points: List<simplylucaapps.girocalendar.util.LatLon>, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.setPath(trailId, points)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    /** Importa più tracciati in sequenza; [onDone] riceve il numero di quelli scritti con successo. */
    fun setPaths(items: List<Pair<String, List<simplylucaapps.girocalendar.util.LatLon>>>, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            var ok = 0
            items.forEach { (id, pts) -> if (repo.setPath(id, pts).isSuccess) ok++ }
            onDone(ok)
        }
    }

    /** Crea un trail e gli assegna subito il tracciato. */
    fun createTrailWithPath(
        name: String, zone: String, difficulty: String, points: List<simplylucaapps.girocalendar.util.LatLon>,
        onResult: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            repo.createTrail(name, zone, difficulty)
                .onSuccess { id ->
                    repo.setPath(id, points)
                        .onSuccess { onResult(true, null) }
                        .onFailure { onResult(false, it.message) }
                }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun savePoi(p: simplylucaapps.girocalendar.data.remote.firestore.Poi, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch { repo.savePoi(p).onSuccess { onResult(true, null) }.onFailure { onResult(false, it.message) } }
    }

    fun deletePoi(id: String, name: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch { repo.deletePoi(id, name).onSuccess { onResult(true, null) }.onFailure { onResult(false, it.message) } }
    }

    fun setZone(zone: String, points: List<simplylucaapps.girocalendar.util.LatLon>, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.setZone(zone, points)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun deleteZone(zone: simplylucaapps.girocalendar.data.remote.firestore.TrailZone, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.deleteZone(zone)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun setZoneBuilders(zone: String, builders: List<simplylucaapps.girocalendar.data.remote.firestore.ZoneBuilder>, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.setZoneBuilders(zone, builders)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun setStatus(trailId: String, status: TrailStatus, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.setStatus(trailId, status)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun deleteZoneWithTrails(zone: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val r = repo.deleteZoneWithTrails(zone)
            onResult(r.isSuccess, r.exceptionOrNull()?.message)
        }
    }

    fun deleteTrail(trailId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.deleteTrail(trailId)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun addComment(trailId: String, text: String, newStatus: TrailStatus?, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.addComment(trailId, text, newStatus)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun editComment(trailId: String, commentId: String, text: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.editComment(trailId, commentId, text)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun deleteComment(trailId: String, commentId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.deleteComment(trailId, commentId)
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    companion object {
        fun factory(repo: TrailRepository, auth: GoogleAuthRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { TrailViewModel(repo, auth) } }
    }
}
