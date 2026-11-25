package simplylucaapps.girocalendar.data.remote.firestore

import simplylucaapps.girocalendar.util.Str

import simplylucaapps.girocalendar.R

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Chiavi dei link social, nell'ordine in cui compaiono nel form. */
val CONTACT_LINK_KEYS = listOf(
    "whatsapp", "telegram", "strava", "komoot", "garmin",
    "instagram", "facebook", "youtube", "tiktok",
)

/** Scheda contatti di un collaboratore: `calendars/{code}/contacts/{uid}`. */
data class ContactCard(
    val uid: String = "",
    val nome: String = "",
    val cognome: String = "",
    val soprannome: String = "",
    val email: String = "",
    val phone: String = "",
    val links: Map<String, String> = emptyMap(),
    val altroLabel: String = "",
    val altroUrl: String = "",
    /** Foto profilo (JPEG quadrato piccolo, base64 puro); vuoto = nessuna. */
    val photoData: String = "",
    /** Se true la scheda è leggibile anche via link web pubblico (solo lettura singola). */
    val shared: Boolean = false,
    val updatedAt: Long = 0L,
) {
    /** Obbligatori: nome + almeno un contatto (email, telefono, social o "altro"). */
    val isValid: Boolean get() = nome.isNotBlank() &&
        (email.isNotBlank() || phone.isNotBlank() || altroUrl.isNotBlank() || links.values.any { it.isNotBlank() })
}

/** Scheda «GRUPPO» del calendario: i link del gruppo (WhatsApp, Telegram, …). Una sola per calendario (`calendars/{code}/meta/group`), la modificano gli editor. */
data class GroupInfo(
    val title: String = "",
    val desc: String = "",
    val links: Map<String, String> = emptyMap(),
    val altroLabel: String = "",
    val altroUrl: String = "",
    val updatedAt: Long = 0L,
) {
    val isEmpty: Boolean get() = title.isBlank() && desc.isBlank() && links.values.all { it.isBlank() } && altroUrl.isBlank()
}

class ContactsRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    private fun col(code: String) = db.collection("calendars").document(code).collection("contacts")

    fun observe(code: String): Flow<List<ContactCard>> = callbackFlow {
        val reg = col(code).addSnapshotListener { snap, err ->
            if (err != null) { Log.w(TAG, "observe error", err); trySend(emptyList()); return@addSnapshotListener }
            trySend(snap?.documents.orEmpty().map { it.toCard() })
        }
        awaitClose { reg.remove() }
    }

    private fun groupDoc(code: String) = db.collection("calendars").document(code).collection("meta").document("group")

    fun observeGroup(code: String): Flow<GroupInfo?> = callbackFlow {
        val reg = groupDoc(code).addSnapshotListener { snap, err ->
            if (err != null) { Log.w(TAG, "group error", err); trySend(null); return@addSnapshotListener }
            trySend(snap?.takeIf { it.exists() }?.let { d ->
                @Suppress("UNCHECKED_CAST")
                val links = (d.get("links") as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
                GroupInfo(d.getString("title").orEmpty(), d.getString("desc").orEmpty(), links,
                    d.getString("altroLabel").orEmpty(), d.getString("altroUrl").orEmpty(), d.getLong("updatedAt") ?: 0L)
            })
        }
        awaitClose { reg.remove() }
    }

    suspend fun saveGroup(code: String, g: GroupInfo): Result<Unit> = try {
        groupDoc(code).set(
            mapOf(
                "title" to g.title.trim(), "desc" to g.desc.trim(),
                "links" to g.links.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() },
                "altroLabel" to g.altroLabel.trim(), "altroUrl" to g.altroUrl.trim(),
                "updatedAt" to System.currentTimeMillis(),
            )
        ).await()
        Result.success(Unit)
    } catch (e: Exception) { Log.w(TAG, "saveGroup failed", e); Result.failure(e) }

    suspend fun deleteGroup(code: String): Result<Unit> = try {
        groupDoc(code).delete().await()
        Result.success(Unit)
    } catch (e: Exception) { Log.w(TAG, "deleteGroup failed", e); Result.failure(e) }

    /** Salva (crea o sostituisce) la scheda dell'utente loggato in [code]. */
    suspend fun save(code: String, card: ContactCard): Result<Unit> = try {
        val uid = auth.currentUser?.uid ?: error(Str.t(R.string.err_sign_in))
        col(code).document(uid).set(
            mapOf(
                "uid" to uid,
                "nome" to card.nome.trim(),
                "cognome" to card.cognome.trim(),
                "soprannome" to card.soprannome.trim(),
                "email" to card.email.trim(),
                "phone" to card.phone.trim(),
                "links" to card.links.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() },
                "altroLabel" to card.altroLabel.trim(),
                "altroUrl" to card.altroUrl.trim(),
                "photoData" to card.photoData,
                "shared" to card.shared,
                "updatedAt" to System.currentTimeMillis(),
            )
        ).await()
        Result.success(Unit)
    } catch (e: Exception) { Log.w(TAG, "save failed", e); Result.failure(e) }

    suspend fun delete(code: String): Result<Unit> = try {
        val uid = auth.currentUser?.uid ?: error(Str.t(R.string.err_sign_in))
        col(code).document(uid).delete().await()
        Result.success(Unit)
    } catch (e: Exception) { Log.w(TAG, "delete failed", e); Result.failure(e) }

    /** La scheda dell'utente loggato in un altro calendario (per "copia da…"); null se non c'è. */
    suspend fun myCardIn(code: String): Result<ContactCard?> = try {
        val uid = auth.currentUser?.uid ?: error(Str.t(R.string.err_sign_in))
        val snap = col(code).document(uid).get().await()
        Result.success(if (snap.exists()) snap.toCard() else null)
    } catch (e: Exception) { Log.w(TAG, "myCardIn failed", e); Result.failure(e) }

    private fun com.google.firebase.firestore.DocumentSnapshot.toCard(): ContactCard {
        @Suppress("UNCHECKED_CAST")
        val links = (get("links") as? Map<String, Any?>).orEmpty()
            .mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
        return ContactCard(
            uid = id,
            nome = getString("nome").orEmpty(),
            cognome = getString("cognome").orEmpty(),
            soprannome = getString("soprannome").orEmpty(),
            email = getString("email").orEmpty(),
            phone = getString("phone").orEmpty(),
            links = links,
            altroLabel = getString("altroLabel").orEmpty(),
            altroUrl = getString("altroUrl").orEmpty(),
            photoData = getString("photoData").orEmpty(),
            shared = getBoolean("shared") ?: false,
            updatedAt = getLong("updatedAt") ?: 0L,
        )
    }

    private companion object { const val TAG = "ContactsRepo" }
}
