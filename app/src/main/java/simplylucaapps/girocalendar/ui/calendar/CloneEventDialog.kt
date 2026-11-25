package simplylucaapps.girocalendar.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.data.remote.firestore.RideCalendar
import simplylucaapps.girocalendar.ui.common.CalendarLogo

/**
 * Picker "Clona in un mio calendario": mostra i calendari dell'utente corrente (owner o
 * editor, esclusa la vista aperta ora — filtrato già in [CalendarViewModel.myCalendars]) e
 * clona l'evento in quello scelto. Nessuna scrittura fino al tap su un calendario.
 */
@Composable
fun CloneEventDialog(
    onDismiss: () -> Unit,
    loadCalendars: ((List<RideCalendar>) -> Unit) -> Unit,
    onPick: (String) -> Unit,
    currentCode: String = "",
) {
    var calendars by remember { mutableStateOf<List<RideCalendar>?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { loadCalendars { calendars = it } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clone_event_title)) },
        text = {
            when {
                calendars == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                calendars!!.isEmpty() -> Text(
                    stringResource(R.string.clone_event_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    calendars!!.forEach { cal ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = picking == null) { picking = cal.code; onPick(cal.code) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CalendarLogo(cal.name.ifBlank { cal.code }, cal.logoColor, cal.logoData, size = 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                cal.name.ifBlank { cal.code } +
                                    if (cal.code == currentCode) " (${stringResource(R.string.clone_event_current)})" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (picking == cal.code) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = picking == null) { Text(stringResource(R.string.action_close)) }
        },
    )
}
