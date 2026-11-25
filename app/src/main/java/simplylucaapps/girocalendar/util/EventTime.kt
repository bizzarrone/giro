package simplylucaapps.girocalendar.util

import java.util.Calendar
import simplylucaapps.girocalendar.data.remote.firestore.RideEvent

/** Parsing data/ora di un [RideEvent] in epoch millis, e stato "passato/futuro". */
object EventTime {

    /** Epoch millis dell'inizio evento, o null se data/ora non parsabili. */
    fun startMillis(e: RideEvent): Long? {
        val d = e.data.split("/")
        if (d.size != 3) return null
        val day = d[0].toIntOrNull() ?: return null
        val month = d[1].toIntOrNull() ?: return null
        val year = d[2].toIntOrNull() ?: return null
        val t = e.ora.split(":", ".")
        val hour = t.getOrNull(0)?.toIntOrNull() ?: 0
        val min = t.getOrNull(1)?.toIntOrNull() ?: 0
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, min, 0)
        }.timeInMillis
    }

    /** Un evento resta tra le "prossime uscite" per tutto il suo giorno: passa a "già svolte"
     * solo dal giorno dopo (mezzanotte), non appena scocca l'orario di partenza. */
    fun isPast(e: RideEvent, now: Long = System.currentTimeMillis()): Boolean {
        val start = startMillis(e) ?: return false
        val endOfEventDay = Calendar.getInstance().apply {
            timeInMillis = start
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return endOfEventDay < now
    }

    // Fisse (non da Locale/SimpleDateFormat): l'abbreviazione a 3 lettere deve restare
    // sempre "LUN".."DOM" indipendentemente dalla lingua di sistema del dispositivo.
    private val WEEKDAY_LABELS = arrayOf("DOM", "LUN", "MAR", "MER", "GIO", "VEN", "SAB")

    private val WEEKDAY_LABELS_EN = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

    /** Giorno della settimana di [data] (formato "dd/MM/yyyy") come sigla di 3 lettere
     * maiuscole ("LUN".."DOM"), o null se la data non è parsabile. */
    fun weekdayLabel(data: String): String? {
        val d = data.split("/")
        if (d.size != 3) return null
        val day = d[0].toIntOrNull() ?: return null
        val month = d[1].toIntOrNull() ?: return null
        val year = d[2].toIntOrNull() ?: return null
        val cal = Calendar.getInstance().apply { clear(); set(year, month - 1, day) }
        val en = Str.ctx?.resources?.configuration?.locales?.get(0)?.language == "en"
        return (if (en) WEEKDAY_LABELS_EN else WEEKDAY_LABELS)[cal.get(Calendar.DAY_OF_WEEK) - 1]
    }
}
