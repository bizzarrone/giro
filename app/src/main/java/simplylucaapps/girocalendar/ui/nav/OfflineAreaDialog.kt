package simplylucaapps.girocalendar.ui.nav

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import simplylucaapps.girocalendar.R
import kotlin.math.cos

@SuppressLint("MissingPermission")
private fun lastKnown(ctx: Context): Location? {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
}

/** Punti a griglia (passo 1 km) in un quadrato di lato 2*[radiusKm] attorno al centro: con la fascia di 600 m coprono tutta la zona. */
private fun gridPoints(lat: Double, lon: Double, radiusKm: Int): Pair<DoubleArray, DoubleArray> {
    val la = ArrayList<Double>(); val lo = ArrayList<Double>()
    val dLat = 1000.0 / 111_132.0
    val dLon = 1000.0 / (111_320.0 * cos(Math.toRadians(lat)))
    for (i in -radiusKm..radiusKm) for (j in -radiusKm..radiusKm) { la.add(lat + i * dLat); lo.add(lon + j * dLon) }
    return la.toDoubleArray() to lo.toDoubleArray()
}

/** Scarica le tessere della zona attorno alla posizione attuale, per usare la mappa senza rete. */
@Composable
fun OfflineAreaDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var center by remember { mutableStateOf(lastKnown(context)) }
    var asked by remember { mutableStateOf(false) }
    var radius by remember { mutableStateOf(5) }
    var layers by remember { mutableStateOf(setOf(NavLayer.OSM)) }
    var plan by remember { mutableStateOf<Pair<Int, List<Triple<Int, Int, Int>>>?>(null) }
    var prog by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var stop by remember { mutableStateOf(false) }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { center = lastKnown(context); asked = true }
    LaunchedEffect(Unit) { if (center == null) perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    LaunchedEffect(center, radius) {
        val c = center ?: return@LaunchedEffect
        val (la, lo) = gridPoints(c.latitude, c.longitude, radius)
        plan = withContext(Dispatchers.Default) { OfflineMaps.plan(la, lo, 16) }
    }
    val sel = layers.toList()
    val p = prog
    if (p != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.of_title)) },
            text = { Column { Text(stringResource(R.string.of_progress, p.first, p.second)); LinearProgressIndicator(progress = { if (p.second == 0) 0f else p.first / p.second.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) } },
            confirmButton = { TextButton(onClick = { stop = true }) { Text(stringResource(R.string.of_stop)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.oa_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (center == null) Text(stringResource(if (asked) R.string.oa_nopos else R.string.oa_wait))
                else {
                    Text(stringResource(R.string.oa_radius))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 10).forEach { r -> FilterChip(selected = radius == r, onClick = { radius = r }, label = { Text("$r km") }) }
                    }
                    Text(stringResource(R.string.oa_layers))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(NavLayer.OSM, NavLayer.CYCLE, NavLayer.TOPO).forEach { l ->
                            FilterChip(selected = l in layers, onClick = { layers = if (l in layers) (layers - l).ifEmpty { layers } else layers + l }, label = { Text(l.id) })
                        }
                    }
                    plan?.let { (maxZ, tiles) ->
                        val n = tiles.size * sel.size
                        Text(stringResource(R.string.oa_confirm, n, OfflineMaps.estimateMb(n), OfflineMaps.MIN_Z, maxZ), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = plan != null && center != null, onClick = {
                val (_, tiles) = plan ?: return@TextButton
                stop = false; prog = 0 to tiles.size * sel.size
                scope.launch {
                    val n = withContext(Dispatchers.IO) { OfflineMaps.download(context, sel, tiles, { stop }) { d, t -> prog = d to t } }
                    Toast.makeText(context, context.getString(R.string.of_done, n), Toast.LENGTH_LONG).show()
                    prog = null; onDismiss()
                }
            }) { Text(stringResource(R.string.of_go)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
