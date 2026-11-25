package simplylucaapps.girocalendar.data.remote.firestore

import simplylucaapps.girocalendar.util.Str

import simplylucaapps.girocalendar.R

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "WorkshopRepository"

/** Colore di evidenziazione di una riga della tabella Officina (come nel foglio di calcolo). */
enum class JobColor(val id: String, val color: Color?) {
    NONE("", null),
    YELLOW("yellow", Color(0xFFFFFF00)),
    ORANGE("orange", Color(0xFFFFC000)),
    PEACH("peach", Color(0xFFFFB570)),
    GRAY("gray", Color(0xFFDDDDDD)),
    // Tinte pastello molto tenui
    ROSE("rose", Color(0xFFFADADD)),
    SALMON("salmon", Color(0xFFFFD9CC)),
    CREAM("cream", Color(0xFFFFF3C4)),
    LIME("lime", Color(0xFFEAF5C8)),
    MINT("mint", Color(0xFFCFF1DC)),
    AQUA("aqua", Color(0xFFCDEFF0)),
    SKY("sky", Color(0xFFD3E6FA)),
    LAVENDER("lavender", Color(0xFFE2DAF5)),
    LILAC("lilac", Color(0xFFF1D9F0));

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/** Un lavoro svolto su una bici. Documento privato: `users/{uid}/jobs/{id}`. */
data class WorkshopJob(
    val id: String = "",
    val bikeId: String = "",
    /** ISO yyyy-MM-dd (si ordina come stringa). */
    val date: String = "",
    val cost: Double? = null,
    val place: String = "",
    val work: String = "",
    val km: Int? = null,
    val color: JobColor = JobColor.NONE,
    /** Id icone ([simplylucaapps.girocalendar.ui.workshop.JobIcon]); null = mai scelte (vale il rilevamento automatico). */
    val icons: List<String>? = null,
)

/** Una bicicletta dell'utente (max [MAX_BIKES]): `users/{uid}/bikes/{id}`. */
data class WorkshopBike(val id: String = "", val name: String = "", val createdAt: Long = 0L)

const val MAX_BIKES = 10

/** Tab "Officina": lavori sulla bici, privati per utente loggato. */
class WorkshopRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    private fun col(uid: String) = db.collection("users").document(uid).collection("jobs")

    fun observeJobs(uid: String): Flow<List<WorkshopJob>> = callbackFlow {
        val reg = col(uid).addSnapshotListener { snap, err ->
            if (err != null) { Log.w(TAG, "observeJobs error", err); trySend(emptyList()); return@addSnapshotListener }
            trySend(snap?.documents.orEmpty().map { it.toJob() }.sortedWith(compareBy({ it.date }, { it.km ?: 0 })))
        }
        awaitClose { reg.remove() }
    }

    fun observeBikes(uid: String): Flow<List<WorkshopBike>> = callbackFlow {
        val reg = db.collection("users").document(uid).collection("bikes").addSnapshotListener { snap, err ->
            if (err != null) { Log.w(TAG, "observeBikes error", err); trySend(emptyList()); return@addSnapshotListener }
            trySend(snap?.documents.orEmpty().map {
                WorkshopBike(it.id, it.getString("name") ?: "", it.getLong("createdAt") ?: 0L)
            }.sortedBy { it.createdAt })
        }
        awaitClose { reg.remove() }
    }

    /** Crea (id vuoto) o rinomina una bici. Il limite di [MAX_BIKES] è verificato dal chiamante. */
    suspend fun saveBike(bike: WorkshopBike): Result<String> = try {
        val uid = auth.currentUser?.uid ?: error(Str.t(R.string.err_sign_in))
        val ref = db.collection("users").document(uid).collection("bikes")
            .let { if (bike.id.isEmpty()) it.document() else it.document(bike.id) }
        ref.set(mapOf("name" to bike.name.trim(),
            "createdAt" to if (bike.createdAt == 0L) System.currentTimeMillis() else bike.createdAt)).await()
        Result.success(ref.id)
    } catch (e: Exception) { Log.w(TAG, "saveBike failed", e); Result.failure(e) }

    /** Elimina la bici e tutto il suo log di lavori. */
    suspend fun deleteBike(id: String): Result<Unit> = try {
        val uid = auth.currentUser?.uid ?: error(Str.t(R.string.err_sign_in))
        col(uid).whereEqualTo("bikeId", id).get().await().documents.forEach { it.reference.delete().await() }
        db.collection("users").document(uid).collection("bikes").document(id).delete().await()
        Result.success(Unit)
    } catch (e: Exception) { Log.w(TAG, "deleteBike failed", e); Result.failure(e) }

    /** Crea (id vuoto) o aggiorna un lavoro. */
    suspend fun save(job: WorkshopJob): Result<Unit> = try {
        val uid = auth.currentUser?.uid ?: error(Str.t(R.string.err_sign_in))
        val ref = if (job.id.isEmpty()) col(uid).document() else col(uid).document(job.id)
        val data = mutableMapOf<String, Any?>(
            "bikeId" to job.bikeId, "date" to job.date, "cost" to job.cost,
            "place" to job.place.trim(), "work" to job.work.trim(), "km" to job.km,
            "color" to job.color.id,
        )
        if (job.icons != null) data["icons"] = job.icons
        ref.set(data).await()
        Result.success(Unit)
    } catch (e: Exception) { Log.w(TAG, "save failed", e); Result.failure(e) }

    suspend fun delete(id: String): Result<Unit> = try {
        val uid = auth.currentUser?.uid ?: error(Str.t(R.string.err_sign_in))
        col(uid).document(id).delete().await()
        Result.success(Unit)
    } catch (e: Exception) { Log.w(TAG, "delete failed", e); Result.failure(e) }
}

private fun DocumentSnapshot.toJob() = WorkshopJob(
    id = id,
    bikeId = getString("bikeId") ?: "",
    date = getString("date") ?: "",
    cost = getDouble("cost"),
    place = getString("place") ?: "",
    work = getString("work") ?: "",
    km = getLong("km")?.toInt(),
    color = JobColor.of(getString("color") ?: ""),
    icons = (get("icons") as? List<*>)?.filterIsInstance<String>(),
)
