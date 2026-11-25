package simplylucaapps.girocalendar.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.data.remote.firestore.RideEvent
import simplylucaapps.girocalendar.ui.trails.TrailMapTab
import simplylucaapps.girocalendar.ui.trails.TrailViewModel

/**
 * Scheda unica «Mappa»: un interruttore in alto passa tra **Esplora** (la mappa dei trail, degli eventi e dei GPX,
 * con il 3D) e **Naviga** (GPS, voce, cruscotto, REC e apertura di un GPX). Al posto delle due schede separate
 * Mappa e Naviga; la modalità scelta si ricorda anche ruotando lo schermo.
 */
@Composable
fun MapNavTab(vm: TrailViewModel, events: List<RideEvent>, calendarCode: String) {
    var navMode by rememberSaveable { mutableStateOf(false) }
    val st by vm.state.collectAsStateWithLifecycle()
    // i chip stanno SOPRA la mappa (non in una fascia separata): la mappa occupa tutta la scheda
    Box(Modifier.fillMaxSize()) {
        // la mappa sta sotto i chip anche in Naviga (i pulsanti in basso si alzano da soli, vedi NavContent)
        Box(Modifier.fillMaxSize()) {
            if (navMode) NavMapTab(events, calendarCode) else TrailMapTab(vm = vm, events = events)
        }
        androidx.compose.material3.Surface(
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 8.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            color = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shadowElevation = 6.dp,
        ) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !navMode, onClick = { navMode = false }, label = { Text("🗺️ " + stringResource(R.string.mn_explore)) })
                FilterChip(selected = navMode, onClick = { navMode = true }, label = { Text("🧭 " + stringResource(R.string.nv_navigate)) })
            }
        }
        // LOG delle modifiche (solo admin, solo in Esplora): in basso a destra, sopra la mappa
        if (!navMode && st.isAdmin) androidx.compose.material3.FilledTonalButton(
            onClick = { simplylucaapps.girocalendar.ui.trails.MapLogButton.tick++ },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(end = 10.dp, bottom = 12.dp).height(34.dp),
        ) { Text("LOG", style = androidx.compose.material3.MaterialTheme.typography.labelMedium) }
    }
}
