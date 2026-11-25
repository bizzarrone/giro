package simplylucaapps.girocalendar.ui.emergency

import simplylucaapps.girocalendar.R

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.CancellationSignal
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.SmsManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

enum class EmergencyState { IDLE, COUNTDOWN, SENDING, DONE }

/** Un invio che richiede un tocco dell'utente (WhatsApp/Telegram non si possono inviare in automatico). */
data class ManualSend(val label: String, val intent: Intent, var done: Boolean = false)

/**
 * Motore dell'emergenza: allarme sonoro → conto alla rovescia annullabile → posizione → invio ai contatti.
 * Tutto quello che fa viene scritto in [log] e mostrato a schermo.
 */
object EmergencyEngine {
    var state by mutableStateOf(EmergencyState.IDLE); private set
    var isTest by mutableStateOf(false); private set
    var countdownLeft by mutableIntStateOf(0); private set
    var reason by mutableStateOf(""); private set
    val log = mutableStateListOf<String>()
    val manual = mutableStateListOf<ManualSend>()
    /** Ultima posizione nota (aggiornata dal servizio di rilevamento mentre è armato). */
    @Volatile var lastLocation: Location? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var player: MediaPlayer? = null
    private var savedAlarmVol = -1
    private var appCtx: Context? = null
    private var receiverRegistered = false
    const val ACTION_SMS_SENT = "simplylucaapps.girocalendar.EMERGENCY_SMS_SENT"
    const val CHANNEL = "emergency_alert"
    const val NOTIF_ALERT = 4801

    private fun t(id: Int, vararg a: Any): String = appCtx?.getString(id, *a) ?: ""
    fun say(msg: String) {
        log.add(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()) + "  " + msg)
        while (log.size > 300) log.removeAt(0)
    }

    private fun hasPerm(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    /** Avvia la sequenza. [test] = messaggio di prova (nessuna emergenza reale). */
    fun trigger(context: Context, test: Boolean, why: String) {
        if (state == EmergencyState.COUNTDOWN || state == EmergencyState.SENDING) return
        val c = context.applicationContext; appCtx = c
        log.clear(); manual.clear()
        isTest = test; reason = why
        val secs = if (test) minOf(EmergencyPrefs.countdownSec, 5) else EmergencyPrefs.countdownSec
        countdownLeft = secs; state = EmergencyState.COUNTDOWN
        say(if (test) t(R.string.em_test_started, why) else t(R.string.em_started, why))
        say(t(R.string.em_alarm_on, secs))
        startAlarm(c, loud = !test)
        postAlertNotification(c)
        job?.cancel()
        job = scope.launch {
            while (countdownLeft > 0) { delay(1000); countdownLeft-- ; if (countdownLeft in 1..3) say("… $countdownLeft") }
            sendAll(c)
        }
    }

    fun cancel() {
        if (state != EmergencyState.COUNTDOWN) return
        job?.cancel(); stopAlarm(); appCtx?.let { clearNotification(it) }
        say(t(R.string.em_cancelled))
        state = EmergencyState.DONE
    }

    fun sendNow() {
        if (state != EmergencyState.COUNTDOWN) return
        val c = appCtx ?: return
        job?.cancel()
        say(t(R.string.em_send_now))
        job = scope.launch { sendAll(c) }
    }

    fun dismiss() { if (state == EmergencyState.DONE) { state = EmergencyState.IDLE } }

    private suspend fun sendAll(c: Context) {
        state = EmergencyState.SENDING
        stopAlarm(); clearNotification(c)
        say(t(R.string.em_countdown_done))
        val contacts = EmergencyPrefs.contacts.toList()
        if (contacts.isEmpty()) { say(t(R.string.em_no_contacts)); state = EmergencyState.DONE; return }
        say(t(R.string.em_finding_gps))
        val loc = currentLocation(c)
        if (loc != null) say(t(R.string.em_position, "%.5f".format(Locale.US, loc.latitude), "%.5f".format(Locale.US, loc.longitude), loc.accuracy.toInt(), (System.currentTimeMillis() - loc.time) / 1000))
        else say(t(R.string.em_position_none))
        val data = hasDataNetwork(c)
        say(t(if (data) R.string.em_net_yes else R.string.em_net_no))
        val name = EmergencyPrefs.name
        val chat = buildMessage(c, isTest, reason, loc, name, emoji = true)
        val sms = buildMessage(c, isTest, reason, loc, name, emoji = false)
        say(t(R.string.em_message_chars, sms.length)); sms.lines().forEach { say("   $it") }
        registerReceiver(c)
        for (ct in contacts) {
            val num = normalize(ct.phone)
            if (num.isEmpty()) { say(t(R.string.em_bad_number, ct.name)); continue }
            if (ct.sms || !data) sendSms(c, ct, "+$num", sms)
            if (data && ct.whatsapp) {
                manual.add(ManualSend(t(R.string.em_wa_to, ct.name), waIntent(c, num, chat)))
                say(t(R.string.em_wa_ready, ct.name))
            }
            if (data && ct.telegram) {
                manual.add(ManualSend(t(R.string.em_tg_to, ct.name), tgIntent(chat)))
                say(t(R.string.em_tg_ready, ct.name))
            }
        }
        if (manual.isNotEmpty()) say(t(R.string.em_manual_note))
        say(t(if (isTest) R.string.em_test_done else R.string.em_send_done))
        state = EmergencyState.DONE
    }

    // ── messaggio ──────────────────────────────────────────────────────────────────────────────────────────
    fun buildMessage(c: Context, test: Boolean, why: String, loc: Location?, name: String, emoji: Boolean): String {
        val bat = (c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val custom = if (test) EmergencyPrefs.msgTest else EmergencyPrefs.msgReal
        val tpl = custom.ifBlank { c.getString(if (test) R.string.em_default_test else R.string.em_default_real) }
        val out = render(c, tpl, name, why, loc, bat, Date())
        return if (emoji) out else stripEmoji(out)
    }

    /** Sostituisce i segnaposto {nome} {motivo} {link} {coordinate} {ora} {batteria}. */
    fun render(c: Context, tpl: String, name: String, why: String, loc: Location?, battery: Int, at: Date): String {
        val link = if (loc != null) "https://maps.google.com/?q=%.6f,%.6f".format(Locale.US, loc.latitude, loc.longitude) else c.getString(R.string.em_not_available)
        val coord = if (loc != null) c.getString(R.string.em_coord_fmt, "%.6f".format(Locale.US, loc.latitude), "%.6f".format(Locale.US, loc.longitude), loc.accuracy.toInt()) else c.getString(R.string.em_not_available_pl)
        return tpl.replace("{nome}", name.ifBlank { c.getString(R.string.em_a_cyclist) }).replace("{motivo}", why).replace("{link}", link)
            .replace("{coordinate}", coord).replace("{ora}", SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(at))
            .replace("{batteria}", battery.toString())
    }

    /** Per gli SMS: via emoji e simboli (obbligherebbero la codifica a 70 caratteri per parte). */
    fun stripEmoji(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i); i += Character.charCount(cp)
            if (cp > 0xFFFF || cp in 0x2190..0x27BF || cp == 0xFE0F || cp in 0x2B00..0x2BFF) continue
            append(Character.toChars(cp))
        }
    }.lines().joinToString("\n") { it.trim() }.trim()

    /** Numero → cifre internazionali senza "+": senza prefisso si assume l'Italia (39). */
    fun normalize(raw: String): String {
        var s = raw.trim(); val plus = s.startsWith("+")
        s = s.filter { it.isDigit() }
        if (s.isEmpty()) return ""
        if (plus) return s
        if (s.startsWith("00")) return s.drop(2)
        return "39$s"
    }

    private fun waIntent(c: Context, num: String, text: String): Intent {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num?text=" + Uri.encode(text)))
        for (p in listOf("com.whatsapp", "com.whatsapp.w4b")) if (installed(c, p)) { i.setPackage(p); break }
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    private fun tgIntent(text: String): Intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        .setPackage("org.telegram.messenger").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    private fun installed(c: Context, pkg: String) = runCatching { c.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    fun launchManual(c: Context, m: ManualSend) {
        runCatching { c.startActivity(m.intent); m.done = true; say(t(R.string.em_opened, m.label)) }
            .onFailure { say(t(R.string.em_open_fail, m.label)) }
    }

    // ── SMS ────────────────────────────────────────────────────────────────────────────────────────────────
    private fun registerReceiver(c: Context) {
        if (receiverRegistered) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, i: Intent) {
                val nm = i.getStringExtra("name").orEmpty(); val part = i.getIntExtra("part", 0); val total = i.getIntExtra("total", 1)
                val tag = if (total > 1) t(R.string.em_part, part + 1, total) else ""
                if (resultCode == Activity.RESULT_OK) say(t(R.string.em_sms_ok, nm, tag))
                else say(t(R.string.em_sms_err, nm, tag, resultCode, smsError(resultCode)))
            }
        }
        ContextCompat.registerReceiver(c, r, IntentFilter(ACTION_SMS_SENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }
    private fun smsError(code: Int) = when (code) {
        SmsManager.RESULT_ERROR_NO_SERVICE -> t(R.string.em_err_nosignal); SmsManager.RESULT_ERROR_RADIO_OFF -> t(R.string.em_err_airplane)
        SmsManager.RESULT_ERROR_NULL_PDU -> t(R.string.em_err_empty); else -> t(R.string.em_err_generic)
    }
    private fun sendSms(c: Context, ct: EmergencyContact, number: String, text: String) {
        if (!hasPerm(c, Manifest.permission.SEND_SMS)) { say(t(R.string.em_sms_denied, ct.name)); return }
        runCatching {
            val sm = if (Build.VERSION.SDK_INT >= 31) c.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            val parts = sm.divideMessage(text)
            val sent = ArrayList<PendingIntent>()
            parts.indices.forEach { i ->
                val it = Intent(ACTION_SMS_SENT).setPackage(c.packageName).putExtra("name", ct.name).putExtra("part", i).putExtra("total", parts.size)
                sent.add(PendingIntent.getBroadcast(c, (ct.id.hashCode() and 0xffff) * 16 + i, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            sm.sendMultipartTextMessage(number, null, parts, sent, null)
            say(t(R.string.em_sms_sending, ct.name, number, parts.size))
        }.onFailure { say(t(R.string.em_sms_failed, ct.name, it.message ?: "")) }
    }

    // ── posizione / rete ───────────────────────────────────────────────────────────────────────────────────
    private fun hasDataNetwork(c: Context): Boolean {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private suspend fun currentLocation(c: Context): Location? {
        if (!hasPerm(c, Manifest.permission.ACCESS_FINE_LOCATION) && !hasPerm(c, Manifest.permission.ACCESS_COARSE_LOCATION)) { say(t(R.string.em_no_loc_perm)); return null }
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fresh = withTimeoutOrNull(7000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val sig = CancellationSignal()
                cont.invokeOnCancellation { sig.cancel() }
                val provs = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
                if (provs.isEmpty()) { cont.resume(null); return@suspendCancellableCoroutine }
                var left = provs.size
                provs.forEach { p ->
                    runCatching {
                        LocationManagerCompat.getCurrentLocation(lm, p, sig, ContextCompat.getMainExecutor(c)) { l ->
                            if (l != null && cont.isActive) cont.resume(l) else if (--left == 0 && cont.isActive) cont.resume(null)
                        }
                    }.onFailure { if (--left == 0 && cont.isActive) cont.resume(null) }
                }
            }
        }
        if (fresh != null) return fresh
        say(t(R.string.em_no_fix))
        val cand = buildList {
            lastLocation?.let { add(it) }
            runCatching { lm.getProviders(true).forEach { p -> lm.getLastKnownLocation(p)?.let { add(it) } } }
        }
        return cand.maxByOrNull { it.time }
    }

    // ── allarme sonoro ─────────────────────────────────────────────────────────────────────────────────────
    private fun startAlarm(c: Context, loud: Boolean) {
        runCatching {
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (loud) { savedAlarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM); am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0) }
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(c, uri); isLooping = true; prepare(); start()
            }
        }.onFailure { say(t(R.string.em_alarm_unavail, it.message ?: "")) }
        runCatching {
            val v = c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 300), 0))
        }
    }
    private fun stopAlarm() {
        runCatching { player?.stop(); player?.release() }; player = null
        appCtx?.let { c ->
            runCatching { (c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel() }
            if (savedAlarmVol >= 0) runCatching { (c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVol, 0) }
        }
        savedAlarmVol = -1
    }

    // ── notifica a schermo intero (apre l'app anche con schermo bloccato) ─────────────────────────────────────
    private fun postAlertNotification(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, c.getString(R.string.em_channel), NotificationManager.IMPORTANCE_HIGH))
        val open = Intent(c, simplylucaapps.girocalendar.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("emergency", true)
        val pi = PendingIntent.getActivity(c, 7, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getBroadcast(c, 8, Intent(c, EmergencyCancelReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n: Notification = NotificationCompat.Builder(c, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(c.getString(if (isTest) R.string.em_notif_test else R.string.em_notif_real))
            .setContentText(c.getString(R.string.em_notif_text)).setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true).setContentIntent(pi).setFullScreenIntent(pi, true).addAction(android.R.drawable.ic_menu_close_clear_cancel, c.getString(R.string.em_notif_cancel), cancel).build()
        runCatching { nm.notify(NOTIF_ALERT, n) }
    }
    private fun clearNotification(c: Context) { runCatching { c.getSystemService(NotificationManager::class.java).cancel(NOTIF_ALERT) } }
}

/** Pulsante ANNULLA nella notifica. */
class EmergencyCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { EmergencyEngine.cancel() }
}
