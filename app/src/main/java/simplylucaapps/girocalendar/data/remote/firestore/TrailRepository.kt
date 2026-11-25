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

private const val TAG = "TrailRepository"

/**
 * Accesso Firestore per la tab "TRAIL": sentieri e il loro stato di percorribilità, condivisi
 * da tutta l'app (non legati a un calendario). Piano Spark, stesso pattern di
 * [CalendarRepository]: nessuna Cloud Function, tutto client-side + Security Rules.
 *
 * Struttura:
 *   trails/{trailId}                     -> [Trail]
 *   trails/{trailId}/comments/{commentId} -> [TrailComment]
 */
class TrailRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {

    val currentUid: String? get() = auth.currentUser?.uid

    /**
     * Parsing e ordinamento fuori dal thread UI (executor singolo → eventi in ordine) e
     * incrementale: dopo il primo snapshot si rileggono solo i documenti cambiati, non tutti
     * i ~400 trail con le loro polyline.
     */
    fun observeTrails(): Flow<List<Trail>> = callbackFlow {
        val exec = java.util.concurrent.Executors.newSingleThreadExecutor()
        val byId = HashMap<String, Trail>()
        var primed = false
        val reg = db.collection("trails")
            .addSnapshotListener(exec) { snap, err ->
                if (err != null) { Log.w(TAG, "observeTrails error", err); trySend(emptyList()); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                if (!primed) {
                    snap.documents.forEach { byId[it.id] = it.toTrail() }
                    primed = true
                } else {
                    for (c in snap.documentChanges) {
                        if (c.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) byId.remove(c.document.id)
                        else byId[c.document.id] = c.document.toTrail()
                    }
                }
                trySend(byId.values.sortedWith(compareBy({ it.zone.lowercase() }, { it.name.lowercase() })))
            }
        awaitClose { reg.remove(); exec.shutdown() }
    }

    @Suppress("UNCHECKED_CAST")
    fun observeLog(limit: Long = 500): Flow<List<TrailLogEntry>> = callbackFlow {
        val reg = db.collection("trailLog")
            .orderBy("createdAtMillis", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "observeLog error", err); trySend(emptyList()); return@addSnapshotListener }
                trySend(snap?.documents.orEmpty().map {
                    TrailLogEntry(
                        id = it.id,
                        action = it.getString("action") ?: "",
                        trailName = it.getString("trailName") ?: "",
                        detail = it.getString("detail") ?: "",
                        userName = it.getString("userName") ?: "",
                        userEmail = it.getString("userEmail") ?: "",
                        createdAtMillis = it.getLong("createdAtMillis") ?: 0L,
                        trailId = it.getString("trailId") ?: "",
                        undo = it.get("undo") as? Map<String, Any?>,
                        undoOf = it.getString("undoOf") ?: "",
                    )
                })
            }
        awaitClose { reg.remove() }
    }

    /** Registra una modifica nel log (best-effort: un errore qui non deve far fallire l'azione). */
    private suspend fun log(
        action: String, trailName: String, detail: String = "",
        trailId: String = "", undo: Map<String, Any?>? = null, undoOf: String = "",
    ) {
        try {
            val user = auth.currentUser ?: return
            val extra = buildMap<String, Any?> {
                if (trailId.isNotEmpty()) put("trailId", trailId)
                if (undo != null) put("undo", undo)
                if (undoOf.isNotEmpty()) put("undoOf", undoOf)
            }
            db.collection("trailLog").document().set(
                extra + mapOf(
                    "action" to action,
                    "trailName" to trailName,
                    "detail" to detail,
                    "userUid" to user.uid,
                    "userName" to (user.displayName?.takeIf { it.isNotBlank() } ?: ""),
                    "userEmail" to (user.email ?: ""),
                    "createdAtMillis" to System.currentTimeMillis(),
                )
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "log failed", e)
        }
    }

    private suspend fun nameOf(trailId: String): String = try {
        db.collection("trails").document(trailId).get().await().getString("name") ?: ""
    } catch (e: Exception) { "" }

    fun observeZones(): Flow<List<TrailZone>> = callbackFlow {
        val reg = db.collection("trailZones").addSnapshotListener { snap, err ->
            if (err != null) { Log.w(TAG, "observeZones error", err); trySend(emptyList()); return@addSnapshotListener }
            trySend(snap?.documents.orEmpty().map { d ->
                @Suppress("UNCHECKED_CAST")
                val bl = (d.get("builders") as? List<Map<String, Any?>>).orEmpty().map { b ->
                    ZoneBuilder(b["name"] as? String ?: "", b["logo"] as? String ?: "", b["url"] as? String ?: "")
                }
                TrailZone(d.id, d.getString("zone") ?: "", d.getString("polygon") ?: "", bl)
            })
        }
        awaitClose { reg.remove() }
    }

    fun observePois(): Flow<List<Poi>> = callbackFlow {
        val reg = db.collection("pois").addSnapshotListener { snap, err ->
            if (err != null) { Log.w(TAG, "observePois error", err); trySend(emptyList()); return@addSnapshotListener }
            trySend(snap?.documents.orEmpty().map { d ->
                Poi(d.id, d.getString("name") ?: "", d.getString("desc") ?: "", d.getString("type") ?: "altro",
                    d.getDouble("lat") ?: 0.0, d.getDouble("lon") ?: 0.0, d.getString("createdByUid") ?: "",
                    d.getString("createdByName") ?: "", d.getString("updatedByName") ?: "")
            })
        }
        awaitClose { reg.remove() }
    }

    /** Crea (id vuoto) o aggiorna un POI. Richiede login (rules). */
    suspend fun savePoi(p: Poi): Result<Unit> = write {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException(Str.t(R.string.err_sign_in_poi))
        val u = auth.currentUser
        val who = u?.displayName?.takeIf { it.isNotBlank() } ?: u?.email?.substringBefore('@').orEmpty()
        val data = mapOf("name" to p.name.trim(), "desc" to p.desc.trim(), "type" to p.type, "lat" to p.lat, "lon" to p.lon)
        if (p.id.isBlank()) {
            db.collection("pois").add(data + mapOf("createdByUid" to uid, "createdByName" to who, "createdAtMillis" to System.currentTimeMillis())).await()
            log("poi_create", p.name.trim(), p.type)
        } else {
            db.collection("pois").document(p.id).set(data + mapOf("updatedByName" to who, "updatedAtMillis" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge()).await()
            log("poi_edit", p.name.trim(), p.type)
        }
    }

    suspend fun deletePoi(id: String, name: String): Result<Unit> = write { db.collection("pois").document(id).delete().await(); log("poi_delete", name) }

    private fun zoneId(zone: String): String = java.text.Normalizer.normalize(zone.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "zona" }

    /** Disegna/sostituisce il perimetro di una zona esistente. Richiede login (rules). */
    suspend fun setZone(zone: String, points: List<simplylucaapps.girocalendar.util.LatLon>): Result<Unit> = write {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException(Str.t(R.string.err_sign_in_zone))
        val ref = db.collection("trailZones").document(zoneId(zone))
        val creator = runCatching { ref.get().await().getString("createdByUid") }.getOrNull() ?: uid
        ref.set(mapOf(
            "zone" to zone, "polygon" to simplylucaapps.girocalendar.util.Polyline.encode(points),
            "createdByUid" to creator, "updatedAtMillis" to System.currentTimeMillis(),
        ), com.google.firebase.firestore.SetOptions.merge()).await()  // merge: restano i costruttori
        log("zone", zone, "${points.size} vertici")
    }

    suspend fun deleteZone(zone: TrailZone): Result<Unit> = write {
        val ref = db.collection("trailZones").document(zone.id)
        // Se la zona ha dei costruttori si svuota solo il perimetro; altrimenti si elimina il documento.
        if (zone.builders.isNotEmpty()) ref.set(mapOf("polygon" to "", "updatedAtMillis" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge()).await()
        else ref.delete().await()
        log("zone_delete", zone.zone)
    }

    /** Imposta i costruttori (loghi + link social) di una zona esistente; crea il documento senza perimetro se manca. */
    suspend fun setZoneBuilders(zone: String, builders: List<ZoneBuilder>): Result<Unit> = write {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException(Str.t(R.string.err_sign_in_builders))
        val ref = db.collection("trailZones").document(zoneId(zone))
        val exists = runCatching { ref.get().await().exists() }.getOrDefault(false)
        val data = mutableMapOf<String, Any>(
            "zone" to zone, "updatedAtMillis" to System.currentTimeMillis(),
            "builders" to builders.map { mapOf("name" to it.name, "logo" to it.logo, "url" to it.url) },
        )
        if (!exists) { data["polygon"] = ""; data["createdByUid"] = uid }
        ref.set(data, com.google.firebase.firestore.SetOptions.merge()).await()
        log("zone_builders", zone, "${builders.size} costruttori")
    }

    fun observeComments(trailId: String): Flow<List<TrailComment>> = callbackFlow {
        val reg = db.collection("trails").document(trailId).collection("comments")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "observeComments error", err); trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents.orEmpty().map { it.toTrailComment() }
                    .sortedByDescending { it.createdAtMillis }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /** true se l'utente corrente è owner di almeno un calendario (privilegi "admin" sui trail).
     * Legge l'indice `admins/{uid}` (scritto da [CalendarRepository.createCalendar]), lo
     * stesso usato dalle Security Rules per verificare il privilegio lato server. */
    suspend fun isAdmin(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            db.collection("admins").document(uid).get().await().exists()
        } catch (e: Exception) {
            Log.w(TAG, "isAdmin check failed", e); false
        }
    }

    /** Crea un nuovo trail. Richiede login Google. [difficulty]: "s0".."s5" o vuoto. */
    suspend fun createTrail(name: String, zone: String, difficulty: String = ""): Result<String> = try {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException(Str.t(R.string.err_sign_in_trail)))
        val ref = db.collection("trails").document()
        ref.set(
            mapOf(
                "name" to name.trim(),
                "zone" to zone.trim(),
                "status" to TrailStatus.OPEN.id,
                "difficulty" to difficulty.trim().lowercase(),
                "createdByUid" to uid,
                "createdAtMillis" to System.currentTimeMillis(),
                "lastUpdateAtMillis" to 0L,
                "lastUpdateByName" to "",
            )
        ).await()
        log("create", name.trim(), zone.trim(), ref.id, mapOf("kind" to "delete"))
        Result.success(ref.id)
    } catch (e: Exception) {
        Log.w(TAG, "createTrail failed", e); Result.failure(e)
    }

    /** Rinomina un'intera zona (aggiorna il campo zone di tutti i suoi trail). Solo admin (rules). */
    suspend fun renameZone(oldZone: String, newZone: String, undoOf: String = ""): Result<Unit> = write {
        val docs = db.collection("trails").whereEqualTo("zone", oldZone).get().await().documents
        docs.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.update(it.reference, "zone", newZone.trim()) }
            batch.commit().await()
        }
        // Sposta anche il documento zona (perimetro/costruttori): l'id deriva dal nome. Vale pure per le zone senza trail.
        val uid = auth.currentUser?.uid
        val oldRef = db.collection("trailZones").document(zoneId(oldZone))
        val newRef = db.collection("trailZones").document(zoneId(newZone))
        if (uid != null && oldRef.path != newRef.path) {
            val oldSnap = oldRef.get().await()
            if (oldSnap.exists()) {
                val data = (oldSnap.data ?: emptyMap()).toMutableMap()
                data["zone"] = newZone.trim(); data["createdByUid"] = uid; data["updatedAtMillis"] = System.currentTimeMillis()
                if (!newRef.get().await().exists()) { newRef.set(data).await(); oldRef.delete().await() }
            }
        }
        log("rename_zone", "", "$oldZone → ${newZone.trim()}", "", mapOf("kind" to "zone", "from" to oldZone, "to" to newZone.trim()), undoOf)
    }

    /** Elimina una zona: tutti i suoi trail (con commenti) e il documento zona (perimetro/costruttori). */
    suspend fun deleteZoneWithTrails(zone: String): Result<Unit> = write {
        val docs = db.collection("trails").whereEqualTo("zone", zone).get().await().documents
        for (t in docs) {
            for (c in t.reference.collection("comments").get().await().documents) c.reference.delete().await()
            t.reference.delete().await()
        }
        runCatching { db.collection("trailZones").document(zoneId(zone)).delete().await() }
        log("zone_delete_all", zone, "${docs.size} trail eliminati")
    }

    /** Rinomina un trail (solo creatore o admin, vedi rules). */
    suspend fun renameTrail(trailId: String, newName: String): Result<Unit> = write {
        val old = nameOf(trailId)
        db.collection("trails").document(trailId).update("name", newName.trim()).await()
        log("rename", newName.trim(), "era «$old»", trailId, fieldsUndo(mapOf("name" to old), mapOf("name" to newName.trim())))
    }

    /** Imposta la difficoltà ("s0".."s5" o vuoto). Le rules consentono l'update a chi è loggato. */
    suspend fun setDifficulty(trailId: String, difficulty: String): Result<Unit> = write {
        val ref = db.collection("trails").document(trailId)
        val old = runCatching { ref.get().await().getString("difficulty") }.getOrNull() ?: ""
        ref.update("difficulty", difficulty.trim().lowercase()).await()
        log("difficulty", nameOf(trailId), difficulty.trim().uppercase().ifBlank { "—" }, trailId,
            fieldsUndo(mapOf("difficulty" to old), mapOf("difficulty" to difficulty.trim().lowercase())))
    }

    /** Imposta (o con null rimuove) la posizione del trail sulla mappa. Richiede login (rules). */
    suspend fun setPosition(trailId: String, lat: Double?, lon: Double?): Result<Unit> = write {
        val ref = db.collection("trails").document(trailId)
        val keys = if (lat != null && lon != null) listOf("lat", "lon", "path") else listOf("lat", "lon")
        val prev = snapFields(ref, keys)
        if (lat != null && lon != null) ref.update(mapOf("lat" to lat, "lon" to lon, "path" to com.google.firebase.firestore.FieldValue.delete())).await()
        else ref.update(mapOf("lat" to com.google.firebase.firestore.FieldValue.delete(), "lon" to com.google.firebase.firestore.FieldValue.delete())).await()
        val next: Map<String, Any?> = if (lat != null && lon != null) mapOf("lat" to lat, "lon" to lon, "path" to null) else mapOf("lat" to null, "lon" to null)
        log("position", nameOf(trailId), if (lat != null && lon != null) "%.5f, %.5f".format(lat, lon) else "rimossa", trailId, fieldsUndo(prev, next))
    }

    /**
     * Imposta il tracciato (≥2 punti, già semplificati) e, come posizione dell'etichetta, il punto
     * centrale. Con lista vuota rimuove tracciato e posizione.
     */
    suspend fun setPath(trailId: String, points: List<simplylucaapps.girocalendar.util.LatLon>): Result<Unit> = write {
        val ref = db.collection("trails").document(trailId)
        val del = com.google.firebase.firestore.FieldValue.delete()
        val prev = snapFields(ref, listOf("path", "lat", "lon"))
        if (points.size >= 2) {
            val mid = points[points.size / 2]
            val enc = simplylucaapps.girocalendar.util.Polyline.encode(points)
            ref.update(mapOf("path" to enc, "lat" to mid.first, "lon" to mid.second)).await()
            log("path", nameOf(trailId), "%d punti, %.2f km".format(points.size, simplylucaapps.girocalendar.util.Polyline.lengthKm(points)), trailId,
                fieldsUndo(prev, mapOf("path" to enc, "lat" to mid.first, "lon" to mid.second)))
        } else {
            ref.update(mapOf("path" to del, "lat" to del, "lon" to del)).await()
            log("path", nameOf(trailId), "rimosso", trailId, fieldsUndo(prev, mapOf("path" to null, "lat" to null, "lon" to null)))
        }
    }

    /** Imposta direttamente lo stato (senza commento). Aggiorna anche lastUpdate*. Richiede login. */
    suspend fun setStatus(trailId: String, status: TrailStatus): Result<Unit> = write {
        val user = auth.currentUser ?: throw IllegalStateException(Str.t(R.string.err_sign_in_status))
        val authorName = user.displayName?.takeIf { it.isNotBlank() } ?: user.email?.substringBefore('@')?.takeIf { it.isNotBlank() } ?: "Utente"
        val ref = db.collection("trails").document(trailId)
        val prev = snapFields(ref, listOf("status", "lastUpdateAtMillis", "lastUpdateByName"))
        val next = mapOf("status" to status.id, "lastUpdateAtMillis" to System.currentTimeMillis(), "lastUpdateByName" to authorName)
        ref.update(next).await()
        log("status", nameOf(trailId), status.label, trailId, fieldsUndo(prev, next))
    }

    /** Elimina il trail (e i suoi commenti). Solo chi l'ha creato o un admin (vedi rules). */
    suspend fun deleteTrail(trailId: String): Result<Unit> = write {
        val ref = db.collection("trails").document(trailId)
        val deletedName = nameOf(trailId)
        val snapshot = snapshotTrail(trailId)
        val comments = ref.collection("comments").get().await()
        for (c in comments.documents) c.reference.delete().await()
        ref.delete().await()
        log("delete", deletedName, "", trailId, snapshot)
    }

    /**
     * Pubblica un commento di aggiornamento. Se [newStatus] non è null, aggiorna anche lo
     * stato denormalizzato sul documento trail (due scritture separate, non atomiche: accettabile
     * per questo dato non critico). Richiede login Google (rules).
     */
    suspend fun addComment(trailId: String, text: String, newStatus: TrailStatus?): Result<Unit> = write {
        val user = auth.currentUser ?: throw IllegalStateException(Str.t(R.string.err_sign_in_comment))
        val authorName = user.displayName?.takeIf { it.isNotBlank() } ?: user.email?.substringBefore('@')?.takeIf { it.isNotBlank() } ?: "Utente"
        db.collection("trails").document(trailId).collection("comments").document().set(
            mapOf(
                "authorUid" to user.uid,
                "authorName" to authorName,
                "text" to text.trim(),
                "newStatus" to (newStatus?.id ?: ""),
                "createdAtMillis" to System.currentTimeMillis(),
            )
        ).await()
        if (newStatus != null) {
            db.collection("trails").document(trailId).update(
                mapOf(
                    "status" to newStatus.id,
                    "lastUpdateAtMillis" to System.currentTimeMillis(),
                    "lastUpdateByName" to authorName,
                )
            ).await()
        }
        log("comment", nameOf(trailId), newStatus?.label.orEmpty())
    }

    /** Modifica il testo di un proprio commento (rules: solo l'autore, solo text/editedAtMillis). */
    suspend fun editComment(trailId: String, commentId: String, text: String): Result<Unit> = write {
        db.collection("trails").document(trailId).collection("comments").document(commentId)
            .update(mapOf("text" to text.trim(), "editedAtMillis" to System.currentTimeMillis())).await()
        log("comment_edit", nameOf(trailId))
    }

    /** Elimina un proprio commento (l'eventuale stato impostato resta sul trail). */
    suspend fun deleteComment(trailId: String, commentId: String): Result<Unit> = write {
        db.collection("trails").document(trailId).collection("comments").document(commentId).delete().await()
        log("comment_delete", nameOf(trailId))
    }

    private fun fieldsUndo(before: Map<String, Any?>, after: Map<String, Any?>): Map<String, Any?> =
        mapOf("kind" to "fields", "before" to before, "after" to after)

    private suspend fun snapFields(ref: com.google.firebase.firestore.DocumentReference, keys: List<String>): Map<String, Any?> {
        val snap = runCatching { ref.get().await() }.getOrNull()
        return keys.associateWith { snap?.get(it) }
    }

    /** Istantanea completa di un trail (dati + commenti) per poterlo ricreare. */
    private suspend fun snapshotTrail(trailId: String): Map<String, Any?> {
        val ref = db.collection("trails").document(trailId)
        val data = ref.get().await().data.orEmpty()
        val comments = ref.collection("comments").get().await().documents.map { mapOf("id" to it.id, "data" to it.data.orEmpty()) }
        return mapOf("kind" to "restore", "data" to data, "comments" to comments)
    }

    private fun same(a: Any?, b: Any?): Boolean =
        if (a is Number && b is Number) a.toDouble() == b.toDouble() else (a ?: "") == (b ?: "")

    /**
     * Annulla un'operazione del log (solo admin, vedi rules). Se il trail è stato modificato dopo l'operazione
     * lancia [UndoConflictException] a meno che [force]. Scrive una nuova voce "undo" che a sua volta è annullabile (ripristino).
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun undo(e: TrailLogEntry, force: Boolean): Result<Unit> = write {
        val u = e.undo ?: throw IllegalStateException(Str.t(R.string.tr_undo_none))
        val tid = e.trailId
        when (u["kind"] as? String) {
            "fields" -> {
                val ref = db.collection("trails").document(tid)
                val snap = ref.get().await()
                if (!snap.exists()) throw IllegalStateException(Str.t(R.string.tr_undo_gone))
                val before = (u["before"] as? Map<String, Any?>).orEmpty()
                val after = (u["after"] as? Map<String, Any?>).orEmpty()
                if (!force && after.any { (k, v) -> !same(snap.get(k), v) }) throw UndoConflictException()
                val cur = before.keys.associateWith { snap.get(it) }
                ref.update(before.mapValues { (_, v) -> v ?: com.google.firebase.firestore.FieldValue.delete() }).await()
                log("undo", snap.getString("name") ?: e.trailName, e.action, tid, fieldsUndo(cur, before), e.id)
            }
            "delete" -> {
                val ref = db.collection("trails").document(tid)
                if (!ref.get().await().exists()) throw IllegalStateException(Str.t(R.string.tr_undo_gone))
                val name = nameOf(tid)
                val snapshot = snapshotTrail(tid)
                for (c in ref.collection("comments").get().await().documents) c.reference.delete().await()
                ref.delete().await()
                log("undo", name, e.action, tid, snapshot, e.id)
            }
            "restore" -> {
                val ref = db.collection("trails").document(tid)
                if (ref.get().await().exists()) throw IllegalStateException(Str.t(R.string.tr_undo_exists))
                val data = (u["data"] as? Map<String, Any?>).orEmpty()
                ref.set(data).await()
                for (c in (u["comments"] as? List<Map<String, Any?>>).orEmpty()) {
                    val cid = c["id"] as? String ?: continue
                    ref.collection("comments").document(cid).set((c["data"] as? Map<String, Any?>).orEmpty()).await()
                }
                log("undo", data["name"] as? String ?: e.trailName, e.action, tid, mapOf("kind" to "delete"), e.id)
            }
            "zone" -> renameZone(u["to"] as String, u["from"] as String, e.id).getOrThrow()
            else -> throw IllegalStateException(Str.t(R.string.tr_undo_none))
        }
    }

    private suspend inline fun write(block: () -> Unit): Result<Unit> = try {
        block(); Result.success(Unit)
    } catch (e: Exception) {
        Log.w(TAG, "write failed", e); Result.failure(e)
    }
}

internal fun com.google.firebase.firestore.DocumentSnapshot.toTrail(): Trail = Trail(
    id = id,
    name = getString("name") ?: "",
    zone = getString("zone") ?: "",
    status = TrailStatus.of(getString("status") ?: "open"),
    difficulty = getString("difficulty") ?: "",
    lat = getDouble("lat"),
    lon = getDouble("lon"),
    path = getString("path") ?: "",
    createdByUid = getString("createdByUid") ?: "",
    createdAtMillis = getLong("createdAtMillis") ?: 0L,
    lastUpdateAtMillis = getLong("lastUpdateAtMillis") ?: 0L,
    lastUpdateByName = getString("lastUpdateByName") ?: "",
)

private fun com.google.firebase.firestore.DocumentSnapshot.toTrailComment(): TrailComment = TrailComment(
    id = id,
    authorUid = getString("authorUid") ?: "",
    authorName = getString("authorName") ?: "",
    text = getString("text") ?: "",
    newStatus = getString("newStatus") ?: "",
    createdAtMillis = getLong("createdAtMillis") ?: 0L,
    editedAtMillis = getLong("editedAtMillis") ?: 0L,
)

/** L'operazione da annullare non coincide più con lo stato attuale del trail (modificato dopo). */
class UndoConflictException : Exception("conflict")
