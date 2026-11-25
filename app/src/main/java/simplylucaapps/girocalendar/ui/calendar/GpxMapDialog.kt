package simplylucaapps.girocalendar.ui.calendar

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import simplylucaapps.girocalendar.util.GpxTrack
import kotlin.math.roundToInt

/**
 * Popup a tutto schermo con la mappa del percorso (Leaflet + tile OpenStreetMap, via WebView —
 * nessuna chiave API richiesta, stesso approccio "niente backend" del resto dell'app). Mostra
 * la traccia, partenza/arrivo evidenziati, una freccia nel punto centrale per il senso di
 * marcia, e un riepilogo statistiche (distanza, quota min/max, dislivello +/-).
 */
@Composable
fun GpxMapDialog(track: GpxTrack, titolo: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                GpxWebMap(track, Modifier.fillMaxSize())

                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopStart).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shadowElevation = 4.dp,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Text(
                            titolo.ifBlank { "Percorso" },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), androidx.compose.foundation.shape.CircleShape),
                    ) {
                        Icon(Icons.Default.Close, androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.action_close), tint = Color.White)
                    }
                }

                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 6.dp,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatItem("📏", "%.1f km".format(track.distanceKm))
                        if (track.minEleM != null && track.maxEleM != null) {
                            StatItem("⛰️", "${track.minEleM.roundToInt()}–${track.maxEleM.roundToInt()} m")
                        }
                        StatItem("🔺", "+${track.ascentM.roundToInt()} m")
                        StatItem("🔻", "-${track.descentM.roundToInt()} m")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(emoji: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GpxWebMap(track: GpxTrack, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val html = remember(track) { buildMapHtml(track) }
    val touchWv = remember { arrayOfNulls<WebView>(1) }
    simplylucaapps.girocalendar.ui.trails.CancelTouchesOnLifecycle { touchWv[0] }
    AndroidView(
        modifier = modifier,
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        Log.e("GpxMapDialog", "onReceivedError url=${request?.url} desc=${error?.description}")
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                        Log.d("GpxMapDialog", "console: ${msg.message()} @${msg.sourceId()}:${msg.lineNumber()}")
                        return true
                    }
                }
                tag = html
                loadDataWithBaseURL("https://unpkg.com/", html, "text/html", "utf-8", null)
            }
        },
        // Ricarica solo se la traccia è cambiata (prima ricaricava la pagina a ogni ricomposizione, subito dopo il primo caricamento).
        update = { touchWv[0] = it; if (it.tag != html) { it.tag = html; it.loadDataWithBaseURL("https://unpkg.com/", html, "text/html", "utf-8", null) } },
        onRelease = { it.destroy() },
    )
}

/**
 * Pagina HTML autonoma con Leaflet caricato da CDN (unpkg) e tile OpenStreetMap: nessun
 * backend/chiave necessaria, coerente con l'approccio "solo client" del resto dell'app. I
 * punti della traccia sono incorporati come JSON inline nello script.
 */
private fun buildMapHtml(track: GpxTrack): String {
    val coords = track.points.joinToString(",") { "[${it.lat},${it.lon}]" }
    val mid = track.points.getOrNull(track.points.size / 2)
    val midNext = track.points.getOrNull((track.points.size / 2) + 1) ?: mid
    val start = track.points.first()
    val end = track.points.last()

    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
          <style>
            /* 100vh invece di 100%: un height percentuale a catena su html/body/#map risolve
               a 0 in alcuni WebView Android (visto in test: clientHeight del div restava 0
               anche con Leaflet caricato correttamente), perché richiede che OGNI antenato
               abbia già un'altezza esplicita al momento del layout — 100vh è relativo al
               viewport reale ed è la soluzione standard raccomandata per Leaflet a schermo
               intero. */
            html, body { height: 100%; margin: 0; padding: 0; }
            #map { height: 100vh; width: 100vw; margin: 0; padding: 0; }
            .dir-arrow { font-size: 22px; transform-origin: center; }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <script>
            // Fix esplicito in pixel: alcuni WebView Android non risolvono correttamente
            // height:100vh/100% su un div dentro html/body, lasciando il container a 0 di
            // altezza (verificato con clientHeight in test). window.innerHeight/innerWidth
            // sono sempre affidabili, indipendentemente dalla catena CSS.
            var mapDiv = document.getElementById('map');
            mapDiv.style.height = window.innerHeight + 'px';
            mapDiv.style.width = window.innerWidth + 'px';
            console.log('MAP_DIV_SIZE_AFTER_FIX: ' + mapDiv.clientWidth + 'x' + mapDiv.clientHeight);

            var pts = [$coords];
            // zoomControl: false — i pulsanti +/- (pensati per desktop) finirebbero sempre
            // sotto il titolo o la barra statistiche sovrapposti da Compose sugli angoli
            // dello schermo; il pinch-to-zoom con le dita resta comunque disponibile.
            var map = L.map('map', { zoomControl: false });
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
              attribution: '&copy; OpenStreetMap contributors',
              maxZoom: 19,
            }).addTo(map);

            var line = L.polyline(pts, { color: '#2980B9', weight: 4 }).addTo(map);
            map.fitBounds(line.getBounds(), { padding: [24, 24] });
            // Rete di sicurezza: se il container ha cambiato dimensione dopo l'inizializzazione
            // (o Leaflet ha calcolato le tile su una dimensione momentaneamente errata), forza
            // un ricalcolo — a costo quasi nullo se già tutto è a posto.
            setTimeout(function() { map.invalidateSize(); }, 100);

            var greenIcon = L.divIcon({ html: '🟢', className: '', iconSize: [24, 24], iconAnchor: [12, 12] });
            var redIcon = L.divIcon({ html: '🔴', className: '', iconSize: [24, 24], iconAnchor: [12, 12] });
            L.marker([${start.lat}, ${start.lon}], { icon: greenIcon }).addTo(map).bindPopup('Partenza');
            L.marker([${end.lat}, ${end.lon}], { icon: redIcon }).addTo(map).bindPopup('Arrivo');

            ${if (mid != null && midNext != null && mid != midNext) """
            var bearing = (function(a, b) {
              var toRad = Math.PI / 180, toDeg = 180 / Math.PI;
              var lat1 = a[0] * toRad, lat2 = b[0] * toRad, dLon = (b[1] - a[1]) * toRad;
              var y = Math.sin(dLon) * Math.cos(lat2);
              var x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
              return (Math.atan2(y, x) * toDeg + 360) % 360;
            })([${mid.lat}, ${mid.lon}], [${midNext.lat}, ${midNext.lon}]);
            var arrowIcon = L.divIcon({
              html: '<div class="dir-arrow" style="transform: rotate(' + bearing + 'deg)">⬆️</div>',
              className: '', iconSize: [24, 24], iconAnchor: [12, 12],
            });
            L.marker([${mid.lat}, ${mid.lon}], { icon: arrowIcon }).addTo(map);
            """ else ""}
          </script>
        </body>
        </html>
    """.trimIndent()
}
