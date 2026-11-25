package simplylucaapps.girocalendar.data.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue

/**
 * Preferenze delle icone della tab Officina (SharedPreferences + stato Compose osservabile).
 * [load] va chiamato una volta all'avvio (MainActivity.onCreate).
 */
object WorkshopIconPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("girocalendar_workshop_icons", Context.MODE_PRIVATE)

    /** Rileva le icone dalla descrizione del lavoro. Default ON. */
    var autoIcons by mutableStateOf(true)
        private set

    /** Colore (ARGB) per tipo di icona; assente = colore del testo. */
    val iconColors = mutableStateMapOf<String, Int>()

    private const val RED = 0xFFE53935.toInt()

    /** Colori di partenza (se l'utente non ne ha scelto uno): corona/cassetta (ingranaggio) e catena rosse. */
    private val defaults = mapOf("chain" to RED, "chainring" to RED)

    /** Colore effettivo dell'icona [id]: scelta dell'utente, altrimenti il predefinito, altrimenti null (colore testo). */
    fun colorOf(id: String): Int? = iconColors[id] ?: defaults[id]

    fun load(c: Context) {
        autoIcons = sp(c).getBoolean("auto", true)
        iconColors.clear()
        sp(c).all.forEach { (k, v) -> if (k.startsWith("color_") && v is Int) iconColors[k.removePrefix("color_")] = v }
    }

    fun setAutoIcons(c: Context, v: Boolean) { autoIcons = v; sp(c).edit().putBoolean("auto", v).apply() }

    fun setIconColor(c: Context, id: String, argb: Int?) {
        if (argb == null) { iconColors.remove(id); sp(c).edit().remove("color_$id").apply() }
        else { iconColors[id] = argb; sp(c).edit().putInt("color_$id", argb).apply() }
    }
}
