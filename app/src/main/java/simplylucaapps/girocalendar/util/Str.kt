package simplylucaapps.girocalendar.util

import android.content.Context

/**
 * Accesso alle stringhe localizzate da codice senza Context (repository, ViewModel). Va
 * inizializzato dalla MainActivity (un contesto Activity rispetta la lingua scelta nelle
 * Preferenze anche prima di Android 13); se non c'è, restituisce stringa vuota.
 */
object Str {
    @Volatile var ctx: Context? = null
    fun t(id: Int, vararg a: Any): String = ctx?.getString(id, *a) ?: ""
}
