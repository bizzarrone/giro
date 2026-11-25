package simplylucaapps.girocalendar.ui.createcalendar

import simplylucaapps.girocalendar.ui.common.CompactTextField
import androidx.compose.ui.res.stringResource

import simplylucaapps.girocalendar.R

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import simplylucaapps.girocalendar.ui.common.CalendarLogo
import simplylucaapps.girocalendar.ui.common.parseHexColor
import simplylucaapps.girocalendar.util.LogoImageProcessor

/** Tavolozza fissa per il pallino del calendario (stessa idea del logoColor esistente). */
private val LOGO_PALETTE = listOf(
    "#27AE60", "#2980B9", "#E74C3C", "#F2994A", "#9B59B6",
    "#16A085", "#D35400", "#2C3E50", "#C0392B", "#8E44AD",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCalendarScreen(
    vm: CreateCalendarViewModel,
    onCreated: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.refreshAvailability(context) }
    LaunchedEffect(state.createdCode) { state.createdCode?.let(onCreated) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.onLogoUploading(true)
        scope.launch {
            val data = LogoImageProcessor.compressToBase64(context, uri)
            vm.onLogoUploading(false)
            if (data != null) vm.onLogoData(data)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { stringResource(R.string.cc_title) },
                actions = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.Close, stringResource(R.string.action_cancel)) }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CalendarLogo(
                    code = state.name.ifBlank { "?" },
                    logoColor = state.logoColor,
                    logoData = state.logoData,
                    size = 48.dp,
                )
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { pickImage.launch("image/*") }, enabled = !state.uploadingLogo) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (state.uploadingLogo) R.string.cc_uploading else R.string.cc_upload_logo))
                }
            }
            Text(
                stringResource(R.string.cc_or_color),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LOGO_PALETTE.forEach { hex ->
                    val selected = state.logoColor.equals(hex, ignoreCase = true)
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(parseHexColor(hex) ?: Color.Gray)
                            .then(
                                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable { vm.onLogoColor(if (selected) "" else hex) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Text(
                stringResource(R.string.cc_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!state.signInAvailable) {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.cc_google_not_configured)) },
                    enabled = false,
                )
            } else if (state.signedIn) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.cc_signed_in)) },
                    supportingContent = { state.userLabel?.let { Text(it) } },
                    trailingContent = { TextButton(onClick = vm::signOut) { Text(stringResource(R.string.cc_sign_out)) } },
                )
            } else {
                Button(
                    onClick = { activity?.let { vm.signIn(it) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.cc_sign_in_google)) }
                Text(
                    stringResource(R.string.cc_signin_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            CompactTextField(
                value = state.name, onValueChange = vm::onName,
                label = { Text(stringResource(R.string.cc_name_label)) }, singleLine = true,
                placeholder = { Text(stringResource(R.string.cc_name_placeholder)) },
                enabled = state.signedIn, modifier = Modifier.fillMaxWidth(),
            )
            CompactTextField(
                value = state.description, onValueChange = vm::onDescription,
                label = { Text(stringResource(R.string.cc_desc_label)) },
                enabled = state.signedIn, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (state.listed) R.string.cc_public else R.string.cc_private),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(if (state.listed) R.string.cc_public_hint else R.string.cc_private_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.listed,
                    onCheckedChange = vm::onListed,
                    enabled = state.signedIn,
                )
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = vm::create,
                enabled = state.canCreate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.cc_create))
            }
            OutlinedButton(
                onClick = onBack,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}
