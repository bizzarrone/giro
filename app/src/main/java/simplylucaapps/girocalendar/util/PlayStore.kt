package simplylucaapps.girocalendar.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Apre la pagina dell'app su Google Play, in modo esplicito (com.android.vending): su telefoni
 * Xiaomi/POCO il link market:// altrimenti finisce in Mi Store/GetApps. Ripiego: link generico, poi sito web. */
object PlayStore {
    fun openAppPage(context: Context) {
        val pkg = context.packageName.removeSuffix(".debug")
        fun view(uri: String, pkgName: String? = null) = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            if (pkgName != null) setPackage(pkgName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(view("market://details?id=$pkg", "com.android.vending")) }
            .recoverCatching { context.startActivity(view("market://details?id=$pkg")) }
            .recoverCatching { context.startActivity(view("https://play.google.com/store/apps/details?id=$pkg")) }
    }
}
