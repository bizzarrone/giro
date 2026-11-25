package simplylucaapps.girocalendar.ui.emergency

import simplylucaapps.girocalendar.R

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat

/** Servizio in primo piano: tiene attivo il rilevamento cadute (accelerometro + GPS) anche a schermo spento. */
class EmergencyService : Service(), SensorEventListener {
    private var detector: FallDetector? = null
    private var wake: PowerManager.WakeLock? = null
    private var locListener: LocationListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { EmergencyPrefs.setArmed(this, false); stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.sv_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, EmergencyService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.sv_title)).setContentText(getString(R.string.sv_text)).setOngoing(true)
            .setContentIntent(open).addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.sv_off), stop).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(NOTIF_ID, n)
        if (detector == null) {
            detector = FallDetector(EmergencyPrefs.sensitivity) { why -> EmergencyEngine.trigger(applicationContext, false, getString(R.string.sv_why, why)) }.also { it.ctx = applicationContext; it.log = { m -> EmergencyEngine.say(m) } }
            wake = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "girocalendar:fall").apply { setReferenceCounted(false); acquire() }
            val sm = getSystemService(SENSOR_SERVICE) as SensorManager
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                ?: EmergencyEngine.say(getString(R.string.sv_no_accel))
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val l = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    EmergencyEngine.lastLocation = loc
                    if (loc.hasSpeed()) detector?.onSpeed(SystemClock.elapsedRealtime(), loc.speed)
                }
                @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {}
            }
            locListener = l
            runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 0f, l, Looper.getMainLooper()) }
            EmergencyEngine.say(getString(R.string.sv_active, getString(listOf(R.string.sv_sens_low, R.string.sv_sens_mid, R.string.sv_sens_high)[EmergencyPrefs.sensitivity])))
        }
        detector?.sensitivity = EmergencyPrefs.sensitivity
        detector?.testMode = EmergencyPrefs.testMode
        // Controllo di vita del sensore: dopo 6 s dice se l'accelerometro sta davvero mandando dati (a schermo spento alcuni telefoni li sospendono).
        val d = detector; val n0 = d?.samples ?: 0
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            if (detector === d && d != null) {
                val rate = (d.samples - n0) / 6
                EmergencyEngine.say(if (rate > 5) getString(R.string.sv_accel_ok, rate) + if (EmergencyPrefs.testMode) getString(R.string.sv_test_on) else "" else getString(R.string.sv_accel_bad, rate))
            }
        }, 6000)
        return START_STICKY
    }

    override fun onSensorChanged(e: SensorEvent) { detector?.onSample(SystemClock.elapsedRealtime(), e.values[0], e.values[1], e.values[2]) }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onDestroy() {
        runCatching { (getSystemService(SENSOR_SERVICE) as SensorManager).unregisterListener(this) }
        locListener?.let { runCatching { (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(it) } }
        runCatching { wake?.release() }
        detector = null; locListener = null
        EmergencyEngine.say(getString(R.string.sv_deactivated))
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "fall_detection"; const val NOTIF_ID = 4800; const val ACTION_STOP = "stop"
        fun start(c: Context) = androidx.core.content.ContextCompat.startForegroundService(c, Intent(c, EmergencyService::class.java))
        fun stop(c: Context) { c.stopService(Intent(c, EmergencyService::class.java)) }
    }
}
