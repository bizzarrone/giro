package simplylucaapps.girocalendar.ui.gpx

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject

/** Loghi dei costruttori di una zona (occhio «L»): nome, logo (immagine) e link. */
private fun buildersJson(b: List<simplylucaapps.girocalendar.data.remote.firestore.ZoneBuilder>): JSONArray =
    JSONArray().also { a -> b.forEach { x -> a.put(JSONObject().put("name", x.name).put("logo", x.logo).put("url", x.url)) } }

/** Volo 3D sul tracciato (MapLibre GL nel WebView, stessa pagina `flight3d.js` del sito): rilievo, texture OSM, camera che segue il percorso. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Flight3DDialog(
    track: GTrack,
    markers: JSONArray? = null,
    zones: List<Pair<String, List<Pair<Double, Double>>>> = emptyList(),
    zoneBuilders: Map<String, List<simplylucaapps.girocalendar.data.remote.firestore.ZoneBuilder>> = emptyMap(),
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val dismiss by rememberUpdatedState(onDismiss)
    val html = remember(track) {
        val stride = maxOf(1, track.pts.size / 4000)
        val arr = JSONArray().also { a ->
            track.pts.forEachIndexed { i, p ->
                if (i % stride == 0 || i == track.pts.size - 1)
                    a.put(JSONArray().put(p.lat).put(p.lon).put(p.ele ?: JSONObject.NULL).put(p.time?.toEpochMilli() ?: JSONObject.NULL))
            }
        }
        val extraJs = if (markers == null) "null" else {
            val zArr = JSONArray().also { a -> zones.forEach { (n, pts) -> a.put(JSONObject().put("name", n).put("pts", JSONArray().also { p -> pts.forEach { (la, lo) -> p.put(JSONArray().put(la).put(lo)) } }).put("builders", buildersJson(zoneBuilders[n].orEmpty()))) } }
            "{ trails: " + "$markers" + ".filter(function (m) { return m.path && m.path.length > 1; }).map(function (m) { return { name: m.name, color: m.diff, closed: m.closed, path: m.path }; }), zones: $zArr }"
        }
        val lang = ctx.resources.configuration.locales[0].language
        val js = ctx.assets.open("flight3d.js").bufferedReader().use { it.readText() }
        """<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;height:100%;background:#111}</style></head><body>
<script>window.GC_LANG = "${if (lang == "en") "en" else "it"}"; window.TT_GPX_W = ${simplylucaapps.girocalendar.ui.trails.GpxWidth.w}; window.GC_POIS = ${simplylucaapps.girocalendar.ui.trails.poisJson()};</script>
<script>$js</script>
<script>
var raw = $arr;
var full = raw.map(function (a) { return { lat: a[0], lon: a[1], ele: a[2], t: a[3] }; });
Flight3D.onClose = function () { Android.close(); };
var extra = $extraJs;
Flight3D.open({ name: ${org.json.JSONObject.quote(track.name)}, full: full, extra: extra });
</script></body></html>"""
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FlightWebView(html) { dismiss() }
    }
}

/** Vista 3D della zona corrente: trail (colore = difficoltà) e perimetri delle zone sul rilievo, camera libera. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Flight3DAreaDialog(
    markers: JSONArray,
    view: Triple<Double, Double, Double>?,
    zones: List<Pair<String, List<Pair<Double, Double>>>>,
    zoneBuilders: Map<String, List<simplylucaapps.girocalendar.data.remote.firestore.ZoneBuilder>> = emptyMap(),
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val dismiss by rememberUpdatedState(onDismiss)
    val html = remember(markers, view, zones) {
        val lang = ctx.resources.configuration.locales[0].language
        val js = ctx.assets.open("flight3d.js").bufferedReader().use { it.readText() }
        val first = markers.optJSONObject(0)
        val lat = view?.first ?: first?.optDouble("lat") ?: 45.8
        val lon = view?.second ?: first?.optDouble("lon") ?: 8.8
        val zoom = view?.third ?: 12.0
        val zArr = JSONArray().also { a -> zones.forEach { (n, pts) -> a.put(JSONObject().put("name", n).put("pts", JSONArray().also { p -> pts.forEach { (la, lo) -> p.put(JSONArray().put(la).put(lo)) } }).put("builders", buildersJson(zoneBuilders[n].orEmpty()))) } }
        """<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;height:100%;background:#111}</style></head><body>
<script>window.GC_LANG = "${if (lang == "en") "en" else "it"}"; window.TT_GPX_W = ${simplylucaapps.girocalendar.ui.trails.GpxWidth.w}; window.GC_POIS = ${simplylucaapps.girocalendar.ui.trails.poisJson()};</script>
<script>$js</script>
<script>
var raw = $markers;
var trails = raw.filter(function (m) { return m.path && m.path.length > 1; }).map(function (m) { return { name: m.name, color: m.diff, closed: m.closed, path: m.path }; });
Flight3D.onClose = function () { Android.close(); };
Flight3D.openArea({ center: [$lat, $lon], zoom: $zoom, trails: trails, zones: $zArr });
</script></body></html>"""
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FlightWebView(html) { dismiss() }
    }
}

/** WebView che ospita il volo 3D: la posizione (pulsante "Localizzami") passa dal permesso di posizione dell'app. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FlightWebView(html: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val closeNow by rememberUpdatedState(onClose)
    var wv by remember { mutableStateOf<WebView?>(null) }
    val locate = { wv?.evaluateJavascript("if (window.f3DoLocate) f3DoLocate();", null); Unit }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) locate()
        else android.widget.Toast.makeText(ctx, ctx.getString(simplylucaapps.girocalendar.R.string.mp_perm_denied), android.widget.Toast.LENGTH_LONG).show()
    }
    val askPermission = {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) locate() else launcher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val askNow by rememberUpdatedState(askPermission)
    simplylucaapps.girocalendar.ui.trails.CancelTouchesOnLifecycle { wv }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            WebView(it).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setGeolocationEnabled(true)
                addJavascriptInterface(object {
                    @JavascriptInterface fun close() { Handler(Looper.getMainLooper()).post { closeNow() } }
                    @JavascriptInterface fun openUrl(u: String) { Handler(Looper.getMainLooper()).post { simplylucaapps.girocalendar.util.openWebLink(ctx, u) } }
                    @JavascriptInterface fun locPerm() { Handler(Looper.getMainLooper()).post { askNow() } }
                }, "Android")
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: android.webkit.GeolocationPermissions.Callback) { callback.invoke(origin, true, false) }
                }
                webViewClient = WebViewClient()
                wv = this
                loadDataWithBaseURL("https://unpkg.com/", html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}
