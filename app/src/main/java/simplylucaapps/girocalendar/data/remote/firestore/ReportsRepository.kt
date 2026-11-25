package simplylucaapps.girocalendar.data.remote.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import simplylucaapps.girocalendar.ui.record.Report
import simplylucaapps.girocalendar.ui.record.ReportType

/** Segnalazione condivisa nel calendario. Documento `calendars/{code}/reports/{byUid_atMillis}`. */
data class SharedReport(val id: String, val type: ReportType, val lat: Double, val lon: Double, val atMillis: Long, val byUid: String, val byName: String, val trailId: String = "", val note: String = "")

/** Voto di un utente su una segnalazione: [confirm] = «c'è ancora», altrimenti «pericolo rimosso». Un voto per utente (l'ultimo vale). */
data class ReportVote(val uid: String, val confirm: Boolean, val atMillis: Long, val name: String)

/** Esito dei voti: [active] = da mostrare/annunciare; [confirms] = utenti che l'hanno confermata; [freshAt] = ultimo segno di vita (creazione o ultima conferma). */
data class ReportState(val active: Boolean, val confirms: Int, val removed: Int, val freshAt: Long)

/** Segnalazioni sul sentiero condivise col gruppo del calendario: chi è loggato le invia, tutti le vedono (ultimi 30 giorni). */
object ReportsRepository {
    private const val DAYS = 60L
    /** Dopo quanti giorni senza conferme una segnalazione scade: fango/acqua passano presto, il resto resta più a lungo. */
    fun ttlDays(t: ReportType) = if (t == ReportType.FLOODED) 7L else 60L
    private fun col(code: String) = FirebaseFirestore.getInstance().collection("calendars").document(code).collection("reports")

    /** Id che coincide per la copia locale e quella condivisa: serve a non mostrare due volte la stessa segnalazione. */
    fun keyOf(uid: String, atMillis: Long) = uid + "_" + atMillis

    /** Invia la segnalazione al calendario [code], se l'utente è loggato. Offline Firestore la tiene in coda e la manda alla riconnessione. */
    fun share(code: String, r: Report): Boolean {
        val u = FirebaseAuth.getInstance().currentUser ?: return false
        if (code.isBlank()) return false
        val name = u.displayName?.takeIf { it.isNotBlank() } ?: u.email?.substringBefore('@').orEmpty()
        col(code).document(keyOf(u.uid, r.atMillis)).set(
            mapOf("type" to r.type.name, "lat" to r.lat, "lon" to r.lon, "atMillis" to r.atMillis, "byUid" to u.uid, "byName" to name.take(60), "note" to r.note.take(200), "trailId" to r.trailId)
        )
        return true
    }

    /** Invia al calendario tutte le segnalazioni locali (le già inviate si sovrascrivono con gli stessi dati: stesso id). Restituisce quante ne ha messe in coda. */
    fun shareAll(code: String, reports: List<Report>): Int {
        if (FirebaseAuth.getInstance().currentUser == null || code.isBlank()) return 0
        reports.forEach { share(code, it) }
        return reports.size
    }

    /** Elimina la copia condivisa di una propria segnalazione (se non esiste o non è tua, la richiesta viene rifiutata e basta). */
    fun delete(code: String, r: Report) {
        val u = FirebaseAuth.getInstance().currentUser ?: return
        if (code.isBlank()) return
        col(code).document(keyOf(u.uid, r.atMillis)).delete()
    }

    fun listen(code: String, onChange: (List<SharedReport>) -> Unit): ListenerRegistration? {
        if (code.isBlank()) return null
        val since = System.currentTimeMillis() - DAYS * 86_400_000L
        return col(code).whereGreaterThan("atMillis", since).orderBy("atMillis", Query.Direction.DESCENDING).limit(300)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { d ->
                    val lat = d.getDouble("lat") ?: return@mapNotNull null
                    val lon = d.getDouble("lon") ?: return@mapNotNull null
                    SharedReport(d.id, runCatching { ReportType.valueOf(d.getString("type") ?: "") }.getOrDefault(ReportType.OTHER),
                        lat, lon, d.getLong("atMillis") ?: 0L, d.getString("byUid").orEmpty(), d.getString("byName").orEmpty(), d.getString("trailId").orEmpty(), d.getString("note").orEmpty())
                } ?: return@addSnapshotListener
                onChange(list)
            }
    }

    /** Vota una segnalazione: [confirm] true = «c'è ancora», false = «pericolo rimosso». Serve il login. Restituisce false se non loggato. */
    fun vote(code: String, reportId: String, confirm: Boolean): Boolean {
        val u = FirebaseAuth.getInstance().currentUser ?: return false
        if (code.isBlank()) return false
        val name = u.displayName?.takeIf { it.isNotBlank() } ?: u.email?.substringBefore('@').orEmpty()
        col(code).document(reportId).collection("votes").document(u.uid)
            .set(mapOf("v" to (if (confirm) "confirm" else "removed"), "at" to System.currentTimeMillis(), "name" to name.take(60)))
        return true
    }

    fun listenVotes(code: String, reportId: String, onChange: (List<ReportVote>) -> Unit): ListenerRegistration =
        col(code).document(reportId).collection("votes").addSnapshotListener { snap, _ ->
            val l = snap?.documents?.mapNotNull { d ->
                ReportVote(d.id, d.getString("v") == "confirm", d.getLong("at") ?: return@mapNotNull null, d.getString("name").orEmpty())
            } ?: return@addSnapshotListener
            onChange(l)
        }

    /**
     * Regole: una conferma vale da data e «riaccende» la segnalazione (azzera i «rimosso» precedenti); la segnalazione è
     * rimossa se l'autore dice «rimosso», o se almeno 2 utenti diversi lo dicono dopo l'ultima conferma; scade dopo
     * [ttlDays] giorni dall'ultima conferma (o dalla creazione).
     */
    fun stateOf(r: SharedReport, votes: List<ReportVote>, now: Long = System.currentTimeMillis()): ReportState {
        val lastConfirm = votes.filter { it.confirm }.maxOfOrNull { it.atMillis } ?: 0L
        val removed = votes.filter { !it.confirm && it.atMillis > lastConfirm }
        val gone = removed.any { it.uid == r.byUid } || removed.size >= 2
        val fresh = maxOf(r.atMillis, lastConfirm)
        val expired = now - fresh > ttlDays(r.type) * 86_400_000L
        return ReportState(!gone && !expired, votes.count { it.confirm }, removed.size, fresh)
    }
}
