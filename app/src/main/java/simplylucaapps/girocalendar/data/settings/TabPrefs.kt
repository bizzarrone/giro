package simplylucaapps.girocalendar.data.settings

import android.content.Context
import simplylucaapps.girocalendar.R
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Tutte le schede della schermata del calendario. */
enum class TabId(private val labelRes: Int, private val noteRes: Int = 0) {
    HOME(R.string.tab_home, R.string.tabn_home),
    CALENDARS(R.string.tab_calendars, R.string.tabn_calendars),
    UPCOMING(R.string.tab_upcoming),
    PAST(R.string.tab_past),
    MONTH(R.string.tab_month),
    INFO(R.string.tab_notices, R.string.tabn_notices),
    TRAILS(R.string.tab_trails),
    MAP(R.string.tab_map),
    RECORD(R.string.tab_record, R.string.tabn_record),
    EPICS(R.string.tab_epics),
    CONTACTS(R.string.tab_contacts, R.string.tabn_contacts),
    WORKSHOP(R.string.tab_workshop, R.string.tabn_workshop),
    EMERGENCY(R.string.tab_emergency, R.string.tabn_emergency),
    MARKET(R.string.tab_market),
    SETTINGS(R.string.tab_settings),
    MYRIDES(R.string.tab_myrides);

    val label: String get() = simplylucaapps.girocalendar.util.Str.t(labelRes)
    val note: String get() = if (noteRes == 0) "" else simplylucaapps.girocalendar.util.Str.t(noteRes)
}

/** Azioni rapide delle schede Impostazioni e I miei giri: aprono i dialoghi del calendario (Preferenze, Tema, Il mio spazio). */
object QuickActions {
    var prefs by androidx.compose.runtime.mutableIntStateOf(0)
    var themes by androidx.compose.runtime.mutableIntStateOf(0)
    var myStorage by androidx.compose.runtime.mutableIntStateOf(0)
}

/** Ordine e visibilità delle schede (SharedPreferences + stato Compose). [load] all'avvio (MainActivity.onCreate). */
object TabPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("girocalendar_tabs", Context.MODE_PRIVATE)
    val order = mutableStateListOf<TabId>().apply { addAll(TabId.entries) }
    val hidden = mutableStateListOf<TabId>()
    /** Scheda su cui si apre un calendario (di default la Home); si sceglie con la stella nella Home in modifica. */
    var startTab by androidx.compose.runtime.mutableStateOf(TabId.HOME); private set
    /** Scheda da riaprire cambiando calendario dal menu a tendina (si consuma al primo uso). */
    var carryTab: TabId? = null
    fun setStart(c: Context, id: TabId) { startTab = id; sp(c).edit().putString("start", id.name).apply() }

    /** Barra schede in basso (prime 4 + «Altro») al posto della barra scorrevole in alto. */
    var bottomBar by androidx.compose.runtime.mutableStateOf(false); private set
    fun setBottomBar(c: Context, on: Boolean) { bottomBar = on; sp(c).edit().putBoolean("bottom_bar", on).apply() }

    fun load(c: Context) {
        bottomBar = sp(c).getBoolean("bottom_bar", false)
        val saved = sp(c).getString("order", "").orEmpty().split(',').mapNotNull { n -> TabId.entries.firstOrNull { it.name == n } }
        order.clear(); order.addAll(saved); order.addAll(TabId.entries.filter { it !in saved })  // schede nuove in coda
        if (TabId.HOME !in saved) { order.remove(TabId.HOME); order.add(0, TabId.HOME) }  // la Home è la prima anche per chi aveva già un ordine salvato
        // Migrazione: chi aveva «Apri all'avvio» attivo parte dalla vecchia scheda suggerita, gli altri dalla Home.
        startTab = TabId.entries.firstOrNull { it.name == sp(c).getString("start", "") }
            ?: if (sp(c).getBoolean("immediate", false)) TabId.entries.firstOrNull { it.name == sp(c).getString("suggested", "") } ?: TabId.HOME else TabId.HOME
        // Migrazione una tantum: Registra subito dopo Mappa anche per chi aveva già un ordine salvato.
        if (!sp(c).getBoolean("rec_after_map", false)) {
            val m = order.indexOf(TabId.MAP)
            if (m >= 0) { order.remove(TabId.RECORD); order.add(order.indexOf(TabId.MAP) + 1, TabId.RECORD); sp(c).edit().putString("order", order.joinToString(",") { it.name }).apply() }
            sp(c).edit().putBoolean("rec_after_map", true).apply()
        }
        emergencyLast()
        // Prima installazione (nessuna scelta salvata): la scheda "Calendari" parte nascosta.
        if (!sp(c).contains("hidden")) { hidden.clear(); hidden.add(TabId.CALENDARS); return }
        hidden.clear(); hidden.addAll(sp(c).getString("hidden", "").orEmpty().split(',').mapNotNull { n -> TabId.entries.firstOrNull { it.name == n } })
    }

    fun move(c: Context, id: TabId, delta: Int) {
        if (id == TabId.EMERGENCY) return   // Emergenza resta sempre l'ultima scheda
        val i = order.indexOf(id); val j = i + delta
        if (i < 0 || j !in order.indices || order[j] == TabId.EMERGENCY) return
        order.removeAt(i); order.add(j, id); persist(c)
    }

    /** Scambia di posto [a] e [b] nell'ordine (usato dal riordino nella Home); Emergenza resta sempre ultima. */
    fun swap(c: Context, a: TabId, b: TabId) {
        if (a == TabId.EMERGENCY || b == TabId.EMERGENCY) return
        val i = order.indexOf(a); val j = order.indexOf(b)
        if (i < 0 || j < 0) return
        order[i] = b; order[j] = a; persist(c)
    }

    /** Sposta [id] nel posto occupato da [target] (trascinamento nella Home); Emergenza resta ultima. */
    fun moveTo(c: Context, id: TabId, target: TabId) {
        if (id == TabId.EMERGENCY || target == TabId.EMERGENCY || id == target) return
        val i = order.indexOf(id); val j = order.indexOf(target)
        if (i < 0 || j < 0) return
        order.removeAt(i); order.add(j, id); persist(c)
    }

    /** Ripristina ordine, visibilità e scheda di apertura ai valori della prima installazione. */
    fun reset(c: Context) {
        order.clear(); order.addAll(TabId.entries); emergencyLast()
        hidden.clear(); hidden.add(TabId.CALENDARS)
        startTab = TabId.HOME; sp(c).edit().putString("start", TabId.HOME.name).apply()
        persist(c)
    }

    private fun emergencyLast() { order.remove(TabId.EMERGENCY); order.add(TabId.EMERGENCY) }

    fun setVisible(c: Context, id: TabId, visible: Boolean) {
        if (visible) hidden.remove(id) else if (id !in hidden) hidden.add(id)
        if (!visible && id == startTab) setStart(c, TabId.HOME)  // una scheda nascosta non può essere quella di apertura
        persist(c)
    }

    /** Schede da mostrare, nell'ordine scelto: quelle disponibili ora (dipende dai dati) e non nascoste. Mai vuoto. */
    fun visible(available: (TabId) -> Boolean): List<TabId> =
        order.filter { it !in hidden && available(it) }.ifEmpty { listOf(TabId.UPCOMING) }

    private fun persist(c: Context) {
        sp(c).edit().putString("order", order.joinToString(",") { it.name }).putString("hidden", hidden.joinToString(",") { it.name }).apply()
    }
}
