package simplylucaapps.girocalendar.data.remote.firestore

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
import simplylucaapps.girocalendar.util.MarketImage

/** Categorie degli annunci del Mercatino (id salvato su Firestore). */
val MARKET_CATEGORIES = listOf("bike", "parts", "clothing", "other")
const val MARKET_MAX_PHOTOS = 8

/**
 * Annuncio del Mercatino. [scope] = "calendar" (solo nel calendario [fromCode]: `calendars/{code}/listings/{id}`)
 * oppure "all" (visibile in tutti i calendari: `listings/{id}`). Le foto a piena qualità stanno nei documenti
 * `.../listings/{id}/photos/{n}`; nell'annuncio restano la miniatura [thumb] e il numero [photoCount].
 * [legacyPhotos] = vecchio formato (foto dentro l'annuncio), solo in lettura.
 */
data class Listing(
    val id: String = "",
    val scope: String = "calendar",
    val fromCode: String = "",
    val title: String = "",
    val description: String = "",
    val price: String = "",
    val category: String = "bike",
    val thumb: String = "",
    val photoCount: Int = 0,
    val legacyPhotos: List<String> = emptyList(),
    /** Riepilogo testuale dei contatti (compatibilità con gli annunci vecchi, che avevano un solo campo libero). */
    val contact: String = "",
    val phone: String = "",
    val whatsapp: Boolean = false,
    val email: String = "",
    val other: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val createdAtMillis: Long = 0L,
    val sold: Boolean = false,
) {
    val coverB64: String get() = thumb.ifBlank { legacyPhotos.firstOrNull().orEmpty() }
    val totalPhotos: Int get() = if (photoCount > 0) photoCount else legacyPhotos.size
}

class MarketRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    val uid: String? get() = auth.currentUser?.uid
    val displayName: String get() = auth.currentUser?.displayName.orEmpty()

    private fun col(scope: String, code: String) =
        if (scope == "all") db.collection("listings") else db.collection("calendars").document(code).collection("listings")

    fun observeCalendar(code: String): Flow<List<Listing>> = observe(db.collection("calendars").document(code).collection("listings"), "calendar", code)
    fun observeGlobal(): Flow<List<Listing>> = observe(db.collection("listings"), "all", "")

    private fun observe(q: com.google.firebase.firestore.Query, scope: String, code: String): Flow<List<Listing>> = callbackFlow {
        val reg = q.addSnapshotListener { snap, err ->
            if (err != null) { Log.w(TAG, "observe $scope error", err); trySend(emptyList()); return@addSnapshotListener }
            trySend(snap?.documents.orEmpty().map { d ->
                Listing(
                    id = d.id, scope = scope, fromCode = d.getString("fromCode") ?: code,
                    title = d.getString("title") ?: "", description = d.getString("description") ?: "",
                    price = d.getString("price") ?: "", category = d.getString("category") ?: "other",
                    thumb = d.getString("thumb") ?: "", photoCount = (d.getLong("photoCount") ?: 0L).toInt(),
                    legacyPhotos = (d.get("photos") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    contact = d.getString("contact") ?: "", phone = d.getString("phone") ?: "", whatsapp = d.getBoolean("whatsapp") ?: false,
                    email = d.getString("email") ?: "", other = d.getString("other") ?: "", ownerUid = d.getString("ownerUid") ?: "",
                    ownerName = d.getString("ownerName") ?: "", createdAtMillis = d.getLong("createdAtMillis") ?: 0L,
                    sold = d.getBoolean("sold") ?: false,
                )
            })
        }
        awaitClose { reg.remove() }
    }

    /** Foto a piena qualità dell'annuncio (base64), nell'ordine. */
    suspend fun loadPhotos(l: Listing): List<String> {
        if (l.photoCount <= 0) return l.legacyPhotos
        return runCatching {
            col(l.scope, l.fromCode).document(l.id).collection("photos").get().await().documents
                .sortedBy { it.getLong("idx") ?: 0L }.mapNotNull { it.getString("data") }
        }.getOrDefault(emptyList())
    }

    private fun fields(l: Listing, owner: String, ownerName: String, thumb: String, count: Int): Map<String, Any> = mapOf(
        "scope" to l.scope, "fromCode" to l.fromCode, "title" to l.title.trim(), "description" to l.description.trim(),
        "price" to l.price.trim(), "category" to l.category, "thumb" to thumb, "photoCount" to count,
        "contact" to listOf(l.phone.trim(), l.email.trim(), l.other.trim()).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { l.contact.trim() },
        "phone" to l.phone.trim(), "whatsapp" to l.whatsapp, "email" to l.email.trim(), "other" to l.other.trim(),
        "ownerUid" to owner, "ownerName" to ownerName,
        "createdAtMillis" to (if (l.createdAtMillis > 0) l.createdAtMillis else System.currentTimeMillis()), "sold" to l.sold,
    )

    private suspend fun deletePhotoDocs(scope: String, code: String, id: String) {
        val c = col(scope, code).document(id).collection("photos")
        for (i in 0 until MARKET_MAX_PHOTOS) runCatching { c.document(i.toString()).delete().await() }
    }

    /**
     * Crea o aggiorna [l]. [photos] = foto a piena qualità (base64) se sono state toccate, altrimenti null (restano le
     * attuali). Se l'ambito cambia rispetto a [previousScope], annuncio e foto passano nella nuova posizione.
     */
    suspend fun save(l: Listing, photos: List<String>?, previousScope: String? = null): Result<Unit> = runCatching {
        val me = uid ?: throw IllegalStateException("not signed in")
        val owner = l.ownerUid.ifBlank { me }
        val id = l.id.ifBlank { col(l.scope, l.fromCode).document().id }
        val name = l.ownerName.ifBlank { displayName }
        val moved = previousScope != null && previousScope != l.scope
        val finalPhotos = photos ?: if (moved) loadPhotos(l.copy(scope = previousScope!!)) else null
        val thumb = if (finalPhotos != null) finalPhotos.firstOrNull()?.let { b -> MarketImage.fromBase64(b, 4)?.let(MarketImage::thumb) }.orEmpty() else l.thumb
        val count = finalPhotos?.size ?: l.photoCount
        col(l.scope, l.fromCode).document(id).set(fields(l.copy(id = id), owner, name, thumb, count)).await()
        if (finalPhotos != null) {
            val pc = col(l.scope, l.fromCode).document(id).collection("photos")
            finalPhotos.forEachIndexed { i, b -> pc.document(i.toString()).set(mapOf("idx" to i, "data" to b)).await() }
            for (i in finalPhotos.size until MARKET_MAX_PHOTOS) runCatching { pc.document(i.toString()).delete().await() }
        }
        if (moved) { deletePhotoDocs(previousScope!!, l.fromCode, id); col(previousScope, l.fromCode).document(id).delete().await() }
    }

    suspend fun setSold(l: Listing, sold: Boolean): Result<Unit> = runCatching {
        col(l.scope, l.fromCode).document(l.id).update("sold", sold).await()
    }

    suspend fun delete(l: Listing): Result<Unit> = runCatching {
        deletePhotoDocs(l.scope, l.fromCode, l.id)
        col(l.scope, l.fromCode).document(l.id).delete().await()
    }

    private companion object { const val TAG = "MarketRepo" }
}
