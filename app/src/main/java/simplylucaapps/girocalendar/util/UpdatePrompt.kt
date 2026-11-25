package simplylucaapps.girocalendar.util

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import simplylucaapps.girocalendar.BuildConfig
import simplylucaapps.girocalendar.R

/** Popup «Aggiornamento disponibile» all'avvio: una sola volta per avvio del processo, con «Aggiorna» (apre Play Store) e «Più tardi». */
private var promptShown = false

@Composable
fun UpdatePrompt() {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!promptShown && UpdateCheck.isUpdateAvailable(ctx, BuildConfig.VERSION_CODE)) { promptShown = true; show = true }
    }
    if (!show) return
    AlertDialog(
        onDismissRequest = { show = false },
        title = { Text(stringResource(R.string.update_available)) },
        text = { Text(stringResource(R.string.update_prompt_msg)) },
        confirmButton = {
            TextButton(onClick = {
                show = false
                PlayStore.openAppPage(ctx)
            }) { Text(stringResource(R.string.update_now)) }
        },
        dismissButton = { TextButton(onClick = { show = false }) { Text(stringResource(R.string.update_later)) } },
    )
}
