package simplylucaapps.girocalendar.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import simplylucaapps.girocalendar.R
import java.net.HttpURLConnection
import java.net.URL

/**
 * Notifiche push per le nuove proposte (solo per gli editor del calendario).
 * Il token FCM del dispositivo sta in `users/{uid}.fcmTokens`; quando qualcuno propone un'uscita il
 * client chiama la funzione Cloudflare `/api/notify-proposal`, che legge i token degli editor e
 * invia il push. Nessun servizio in primo piano: il sistema sveglia l'app solo per il messaggio.
 */
object Push {
    const val CHANNEL = "proposals"
    private const val ENDPOINT = "https://girocalendar.pages.dev/api/notify-proposal"

    /** Salva il token di questo dispositivo sull'account (se loggato). Idempotente. */
    fun register(context: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { saveToken(uid, it) }
        }
    }

    fun saveToken(uid: String, token: String) {
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
    }

    /** Toglie il token dall'account (prima del logout) e poi chiama [then], comunque vada. */
    fun unregister(then: () -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) { then(); return }
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { t ->
                val token = t.result
                if (!t.isSuccessful || token == null) { then(); return@addOnCompleteListener }
                FirebaseFirestore.getInstance().collection("users").document(uid)
                    .set(mapOf("fcmTokens" to FieldValue.arrayRemove(token)), SetOptions.merge())
                    .addOnCompleteListener { then() }
            }
        }.onFailure { then() }
    }

    /** Chiede al sito di notificare gli editor della proposta appena creata (fire-and-forget). */
    fun notifyProposal(code: String, id: String) {
        Thread {
            runCatching {
                val c = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true; connectTimeout = 8000; readTimeout = 8000
                    setRequestProperty("Content-Type", "application/json")
                }
                c.outputStream.use { it.write(org.json.JSONObject().put("code", code).put("id", id).toString().toByteArray()) }
                c.responseCode
                c.disconnect()
            }
        }.start()
    }

    fun hasNotificationPermission(context: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

class ProposalMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        FirebaseAuth.getInstance().currentUser?.uid?.let { Push.saveToken(it, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        if (d["type"] != "proposal" || !Push.hasNotificationPermission(this)) return
        val code = d["code"].orEmpty()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(Push.CHANNEL, getString(R.string.push_channel), NotificationManager.IMPORTANCE_HIGH))
        val open = Intent(Intent.ACTION_VIEW, Uri.parse("girocalendar://c/$code")).setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(this, code.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val who = d["who"].orEmpty()
        val text = d["title"].orEmpty() + if (who.isNotBlank()) " — $who" else ""
        val n = NotificationCompat.Builder(this, Push.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.push_title, d["calName"].orEmpty().ifBlank { code }))
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(pi).build()
        runCatching { nm.notify((code + text).hashCode(), n) }
    }
}
