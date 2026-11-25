package simplylucaapps.girocalendar

import android.app.Application
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent

/**
 * Su alcuni dispositivi (es. Android 10 / MT6580) Firestore a volte chiude il proprio thread di lavoro e poi
 * fa crashare il thread principale con "Internal error in Cloud Firestore ... RejectedExecutionException".
 * Dopo quell'errore Firestore non è più utilizzabile nel processo: invece della finestra di crash,
 * riavviamo l'app da soli.
 */
class GiroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            if (isFirestoreExecutorDead(e)) {
                runCatching {
                    val launch = packageManager.getLaunchIntentForPackage(packageName)!!
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    val pi = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
                    (getSystemService(ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC, System.currentTimeMillis() + 300, pi)
                }
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            } else prev?.uncaughtException(t, e)
        }
    }

    private fun isFirestoreExecutorDead(e: Throwable): Boolean {
        var c: Throwable? = e
        var internal = false
        var rejected = false
        var n = 0
        while (c != null && n++ < 8) {
            if (c.message?.contains("Internal error in Cloud Firestore") == true) internal = true
            if (c is java.util.concurrent.RejectedExecutionException) rejected = true
            c = c.cause
        }
        return internal && rejected
    }
}
