package simplylucaapps.girocalendar.ui.emergency

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Contatto da avvisare in emergenza, con i canali scelti. */
data class EmergencyContact(
    val id: String, val name: String, val phone: String,
    val whatsapp: Boolean = true, val telegram: Boolean = false, val sms: Boolean = true,
)

/** Impostazioni della funzione Emergenza (SharedPreferences + stato Compose). [load] all'avvio (MainActivity.onCreate). */
object EmergencyPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("girocalendar_emergency", Context.MODE_PRIVATE)

    /** Abilitata dalle Preferenze: mostra la scheda EMERGENZA (ultima). */
    var enabled by mutableStateOf(false); private set
    /** Rilevamento cadute attivo (servizio in primo piano). */
    var armed by mutableStateOf(false); private set
    var countdownSec by mutableIntStateOf(10); private set
    /** 0 bassa, 1 media, 2 alta. */
    var sensitivity by mutableIntStateOf(1); private set
    val contacts = mutableStateListOf<EmergencyContact>()
    /** Modalità prova del rilevamento (soglie basse, ignora GPS): solo per provare a casa. Non persistente. */
    var testMode by mutableStateOf(false); private set
    fun changeTestMode(v: Boolean) { testMode = v }
    /** Nome che compare nei messaggi ("{nome} ha avuto una caduta…"). */
    var name by mutableStateOf(""); private set
    /** Testi personalizzati dei messaggi; vuoto = predefinito. Segnaposto: {nome} {motivo} {link} {coordinate} {ora} {batteria}. */
    var msgReal by mutableStateOf(""); private set
    var msgTest by mutableStateOf(""); private set


    fun load(c: Context) {
        val p = sp(c)
        enabled = p.getBoolean("enabled", true)
        armed = p.getBoolean("armed", false) && enabled
        countdownSec = p.getInt("countdown", 10).coerceIn(5, 120)
        sensitivity = p.getInt("sensitivity", 1).coerceIn(0, 2)
        name = p.getString("name", "").orEmpty()
        msgReal = p.getString("msg_real", "").orEmpty()
        msgTest = p.getString("msg_test", "").orEmpty()
        contacts.clear()
        runCatching {
            val a = JSONArray(p.getString("contacts", "[]"))
            for (i in 0 until a.length()) a.getJSONObject(i).let {
                contacts.add(EmergencyContact(it.getString("id"), it.optString("name"), it.optString("phone"),
                    it.optBoolean("wa", true), it.optBoolean("tg", false), it.optBoolean("sms", true)))
            }
        }
    }

    fun setEnabled(c: Context, v: Boolean) { enabled = v; sp(c).edit().putBoolean("enabled", v).apply(); if (!v) setArmed(c, false) }
    fun setArmed(c: Context, v: Boolean) { armed = v; sp(c).edit().putBoolean("armed", v).apply() }
    fun setCountdown(c: Context, v: Int) { countdownSec = v.coerceIn(5, 120); sp(c).edit().putInt("countdown", countdownSec).apply() }
    fun setSensitivity(c: Context, v: Int) { sensitivity = v.coerceIn(0, 2); sp(c).edit().putInt("sensitivity", sensitivity).apply() }

    fun setMsgReal(c: Context, v: String) { msgReal = v; sp(c).edit().putString("msg_real", v).apply() }
    fun setMsgTest(c: Context, v: String) { msgTest = v; sp(c).edit().putString("msg_test", v).apply() }

    fun setName(c: Context, v: String) { name = v; sp(c).edit().putString("name", v).apply() }

    /** Massimo contatti di emergenza aggiungibili (chi ne ha già di più li mantiene, ma non può aggiungerne). */
    const val MAX_CONTACTS = 5

    fun saveContact(c: Context, ct: EmergencyContact) {
        val i = contacts.indexOfFirst { it.id == ct.id }
        if (i >= 0) contacts[i] = ct else if (contacts.size < MAX_CONTACTS) contacts.add(ct)
        persist(c)
    }
    fun removeContact(c: Context, id: String) { contacts.removeAll { it.id == id }; persist(c) }
    private fun persist(c: Context) {
        val a = JSONArray()
        contacts.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone).put("wa", it.whatsapp).put("tg", it.telegram).put("sms", it.sms)) }
        sp(c).edit().putString("contacts", a.toString()).apply()
    }
}
