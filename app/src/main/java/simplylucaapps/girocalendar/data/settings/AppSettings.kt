package simplylucaapps.girocalendar.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "girocalendar_settings")

const val MAX_PINNED = 5

@Serializable
data class RecentCalendar(
    val code: String,
    val name: String,
    val isOwner: Boolean,
    val logoColor: String = "",
    val logoData: String = "",
    val ownerEmail: String = "",
)

/**
 * L'ultimo codice calendario aperto, in SharedPreferences (lettura sincrona sicura sul main
 * thread) — serve a MainActivity per scegliere la destinazione iniziale senza bloccare su DataStore.
 */
object BootPrefs {
    private const val FILE = "girocalendar_boot"
    private const val KEY_LAST = "last_code"
    private const val KEY_THEME = "theme_id"
    private const val KEY_TEXT_SIZE = "text_size"
    private const val KEY_FONT_STYLE = "font_style"
    private const val KEY_LANGUAGE = "language"
    private fun p(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** App di navigazione per il parcheggio: "ask" (chiedi ogni volta), "maps", "waze", "other" (selettore di sistema). */
    fun navApp(context: Context): String = p(context).getString("nav_app", "ask") ?: "ask"
    fun setNavApp(context: Context, v: String) = p(context).edit().putString("nav_app", v).apply()

    /** Suggerimenti a popup: spenti da «Non mostrare più», riaccesi dal menu. */
    fun tipsOff(context: Context): Boolean = p(context).getBoolean("tips_off", false)
    fun setTipsOff(context: Context, v: Boolean) = p(context).edit().putBoolean("tips_off", v).apply()

    /** Rotazione dei suggerimenti per utente: pesca uno non ancora visto (per uid, o «anon»); finiti tutti si
     *  ricomincia senza riproporre subito l'ultimo. Gli id sono hash del testo: reggono le modifiche all'elenco. */
    fun nextTip(context: Context, uid: String?, tips: List<String>): Int {
        if (tips.isEmpty()) return 0
        val ids = tips.map { it.hashCode().toString() }
        val key = "tips_seen2_" + (uid ?: "anon")
        var seen = (p(context).getString(key, "") ?: "").split(",").filter { it in ids }
        var pool = ids.indices.filter { ids[it] !in seen }
        if (pool.isEmpty()) { val last = seen.lastOrNull(); seen = emptyList(); pool = ids.indices.filter { ids[it] != last }.ifEmpty { listOf(0) } }
        val n = pool.random()
        p(context).edit().putString(key, (seen + ids[n]).joinToString(",")).apply()
        return n
    }

    /** Ultima vista (centro e zoom) della mappa dei trail: la mappa si riapre dove l'avevi lasciata. */
    fun mapView(context: Context): Triple<Double, Double, Double>? {
        val v = p(context).getString("map_view", null)?.split(",")?.mapNotNull { it.toDoubleOrNull() } ?: return null
        if (v.size != 3 || v[0] !in -85.0..85.0 || v[1] !in -180.0..180.0 || v[2] !in 3.0..20.0) return null
        return Triple(v[0], v[1], v[2])
    }
    fun setMapView(context: Context, v: Triple<Double, Double, Double>) =
        p(context).edit().putString("map_view", "${v.first},${v.second},${v.third}").apply()

    fun lastCode(context: Context): String? = p(context).getString(KEY_LAST, null)
    fun setLastCode(context: Context, code: String) = p(context).edit().putString(KEY_LAST, code).apply()
    fun clear(context: Context) = p(context).edit().remove(KEY_LAST).apply()

    /** tema letto sync all'avvio per non far lampeggiare il default */
    fun themeId(context: Context): String = p(context).getString(KEY_THEME, "sentiero") ?: "sentiero"
    fun setThemeId(context: Context, id: String) = p(context).edit().putString(KEY_THEME, id).apply()

    /** stile font letto sync all'avvio per non far lampeggiare il default */
    fun fontStyleId(context: Context): String = p(context).getString(KEY_FONT_STYLE, "classic") ?: "classic"
    fun setFontStyleId(context: Context, id: String) = p(context).edit().putString(KEY_FONT_STYLE, id).apply()

    /** Lingua: "system" (segue il sistema), "it", "en". Default "system". */
    fun languageId(context: Context): String = p(context).getString(KEY_LANGUAGE, "system") ?: "system"
    fun setLanguageId(context: Context, id: String) = p(context).edit().putString(KEY_LANGUAGE, id).apply()

    /**
     * Fattore di scala testo (0.5–1.5, slider), letto sync in
     * [android.app.Activity.attachBaseContext] PRIMA che Compose parta.
     */
    fun textScale(context: Context): Float =
        parseTextScale(p(context).getString(KEY_TEXT_SIZE, null))
    fun setTextScale(context: Context, scale: Float) =
        p(context).edit().putString(KEY_TEXT_SIZE, scale.toString()).apply()
}

/** "1.15" -> 1.15f; assente/non valido -> 1f. */
internal fun parseTextScale(raw: String?): Float = when (raw) {
    null -> 1f
    else -> raw.toFloatOrNull()?.coerceIn(0.5f, 1.5f) ?: 1f
}

/**
 * Preferenze locali: la lista dei codici visti di recente / creati (per la schermata iniziale),
 * e l'ultimo codice aperto. Niente di sensibile.
 */
class AppSettings(private val context: Context) {

    private val recentsKey = stringPreferencesKey("recent_calendars")
    private val lastCodeKey = stringPreferencesKey("last_code")
    private val pinnedKey = stringPreferencesKey("pinned_calendars")
    private val themeKey = stringPreferencesKey("theme_id")
    private val textSizeKey = stringPreferencesKey("text_size")
    private val fontStyleKey = stringPreferencesKey("font_style")
    private val languageKey = stringPreferencesKey("language")
    private val showFieldIconsKey = booleanPreferencesKey("show_field_icons")
    private val audioAutoplayKey = booleanPreferencesKey("audio_autoplay")
    private val difficultyScaleKey = stringPreferencesKey("difficulty_scale")
    private val myNameKey = stringPreferencesKey("my_name")

    val themeId: Flow<String> = context.dataStore.data.map { it[themeKey] ?: "sentiero" }
    val textScale: Flow<Float> = context.dataStore.data.map { parseTextScale(it[textSizeKey]) }
    val fontStyleId: Flow<String> = context.dataStore.data.map { it[fontStyleKey] ?: "classic" }
    suspend fun setFontStyleId(id: String) {
        BootPrefs.setFontStyleId(context, id)
        context.dataStore.edit { it[fontStyleKey] = id }
    }

    /** Lingua UI: "system" | "it" | "en". Il cambio runtime è applicato da chi chiama
     * [setLanguageId] tramite AppCompatDelegate.setApplicationLocales (vedi PreferencesDialog). */
    val languageId: Flow<String> = context.dataStore.data.map { it[languageKey] ?: "system" }
    suspend fun setLanguageId(id: String) {
        BootPrefs.setLanguageId(context, id)
        context.dataStore.edit { it[languageKey] = id }
    }

    /** Icone grigie accanto ai campi evento (data, località, proponente, link…). Default ON. */
    val showFieldIcons: Flow<Boolean> = context.dataStore.data.map { it[showFieldIconsKey] ?: true }
    suspend fun setShowFieldIcons(on: Boolean) {
        context.dataStore.edit { it[showFieldIconsKey] = on }
    }

    /** true = barra schede a riga singola scorrevole; false = tutte le schede su due righe. Default ON. */

    /** Riproduce automaticamente l'audio dell'evento (se presente) aprendo la sua scheda
     * dettaglio, senza dover toccare il pulsante Play. Default ON. */
    val audioAutoplay: Flow<Boolean> = context.dataStore.data.map { it[audioAutoplayKey] ?: true }
    suspend fun setAudioAutoplay(on: Boolean) {
        context.dataStore.edit { it[audioAutoplayKey] = on }
    }

    /** Come mostrare i badge di difficoltà (uscite e trail): "sts" (S0-S5, colori Spaccasassi
     * Eventi) o "trailforks" (White/Green/Blue/Red/Black/Proline, come il sito
     * trailforks.com). Il dato salvato resta sempre l'id "s0".."s5": questa preferenza cambia
     * solo etichetta e colore mostrati (vedi [simplylucaapps.girocalendar.data.remote.firestore.Difficulty]).
     * Default "sts". */
    val difficultyScale: Flow<String> = context.dataStore.data.map { it[difficultyScaleKey] ?: "sts" }
    suspend fun setDifficultyScale(scale: String) {
        context.dataStore.edit { it[difficultyScaleKey] = scale }
    }

    /**
     * Il nome usato l'ultima volta per "Partecipa" (equivalente al cookie mio_nome di
     * Spaccasassi Eventi): precompila il campo la volta successiva, anche per un ospite
     * senza login. Se l'utente ha fatto login Google, il nome del profilo ha comunque
     * priorità (vedi ProposeEventScreen/EventDetailSheet).
     */
    val myName: Flow<String> = context.dataStore.data.map { it[myNameKey] ?: "" }
    suspend fun setMyName(name: String) {
        context.dataStore.edit { it[myNameKey] = name }
    }

    suspend fun setThemeId(id: String) {
        BootPrefs.setThemeId(context, id)
        context.dataStore.edit { it[themeKey] = id }
    }

    suspend fun setTextScale(scale: Float) {
        BootPrefs.setTextScale(context, scale)
        context.dataStore.edit { it[textSizeKey] = scale.toString() }
    }

    val recents: Flow<List<RecentCalendar>> = context.dataStore.data.map { prefs ->
        prefs[recentsKey]?.let { runCatching { Json.decodeFromString<List<RecentCalendar>>(it) }.getOrNull() } ?: emptyList()
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)

    /** Calendari scelti dall'utente per il cambio rapido (menu a tendina), max [MAX_PINNED]. */
    val pinned: Flow<List<RecentCalendar>> = context.dataStore.data.map { prefs ->
        prefs[pinnedKey]?.let { runCatching { Json.decodeFromString<List<RecentCalendar>>(it) }.getOrNull() } ?: emptyList()
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)

    /** Seleziona/deseleziona un calendario. Ritorna false se il limite è raggiunto. */
    suspend fun setPinned(cal: RecentCalendar, on: Boolean): Boolean {
        var ok = true
        context.dataStore.edit { prefs ->
            val cur = prefs[pinnedKey]?.let {
                runCatching { Json.decodeFromString<List<RecentCalendar>>(it) }.getOrNull()
            } ?: emptyList()
            val without = cur.filterNot { it.code == cal.code }
            val next = if (!on) without else if (cur.any { it.code == cal.code }) cur.map { if (it.code == cal.code) cal else it }
                else if (cur.size >= MAX_PINNED) { ok = false; cur } else without + cal
            prefs[pinnedKey] = Json.encodeToString(next)
        }
        return ok
    }

    val lastCode: Flow<String?> = context.dataStore.data.map { it[lastCodeKey] }

    suspend fun rememberCalendar(
        code: String, name: String, isOwner: Boolean,
        logoColor: String = "", logoData: String = "", ownerEmail: String = "",
    ) {
        BootPrefs.setLastCode(context, code)
        context.dataStore.edit { prefs ->
            val current = prefs[recentsKey]?.let {
                runCatching { Json.decodeFromString<List<RecentCalendar>>(it) }.getOrNull()
            } ?: emptyList()
            val updated = (listOf(RecentCalendar(code, name, isOwner, logoColor, logoData, ownerEmail)) +
                current.filterNot { it.code == code }).take(20)
            prefs[recentsKey] = Json.encodeToString(updated)
            prefs[lastCodeKey] = code
        }
    }

    suspend fun forgetCalendar(code: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[recentsKey]?.let {
                runCatching { Json.decodeFromString<List<RecentCalendar>>(it) }.getOrNull()
            } ?: emptyList()
            prefs[recentsKey] = Json.encodeToString(current.filterNot { it.code == code })
            if (prefs[lastCodeKey] == code) prefs.remove(lastCodeKey)
        }
        // L'avvio legge BootPrefs: senza questo riaprirebbe il calendario appena eliminato.
        if (BootPrefs.lastCode(context) == code) BootPrefs.clear(context)
    }
}
