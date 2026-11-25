package simplylucaapps.girocalendar.ui.search

import simplylucaapps.girocalendar.ui.common.CompactTextField
import androidx.compose.ui.res.stringResource

import simplylucaapps.girocalendar.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import simplylucaapps.girocalendar.ui.calendar.EditorsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    vm: SearchViewModel,
    onOpenCode: (String) -> Unit,
    onCreateCalendar: (String) -> Unit,
    isDark: Boolean = false,
    onToggleDark: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    var showMyCode by remember { mutableStateOf(false) }
    var showBrowse by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var addingExisting by remember { mutableStateOf(false) }
    var creatingCalendar by remember { mutableStateOf(false) }
    var newCalendarName by remember { mutableStateOf("") }
    val queryFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }
    val msgOpFailed = stringResource(R.string.s_op_failed)
    val msgCalDeleted = stringResource(R.string.s_cal_deleted)
    val msgDeleteFailed = stringResource(R.string.s_delete_failed)
    val msgEditorAdded = stringResource(R.string.s_editor_added)

    val msgPinLimit = stringResource(R.string.s_pin_limit, simplylucaapps.girocalendar.data.settings.MAX_PINNED)
    fun isPinned(code: String) = state.pinned.any { it.code == code }
    fun pin(c: simplylucaapps.girocalendar.data.settings.RecentCalendar) =
        vm.togglePinned(c) { toast(msgPinLimit) }

    LaunchedEffect(Unit) { vm.refreshMyCalendars() }
    LaunchedEffect(state.resolvedCode) {
        state.resolvedCode?.let { onOpenCode(it); vm.consumeResolved() }
    }
    // Login riuscito dal dialog "Accedi con Google": chiudilo subito invece di lasciare
    // all'utente il tap in più su "Chiudi", tornando direttamente sulla schermata con
    // l'elenco calendari (già dietro al dialog). Solo sulla TRANSIZIONE a loggato — se il
    // dialog è aperto perché già loggato (es. per vedere il proprio codice utente o eliminare
    // l'account), non deve richiudersi da solo.
    var wasSignedIn by remember { mutableStateOf(state.signedIn) }
    LaunchedEffect(state.signedIn) {
        if (state.signedIn && !wasSignedIn) showMyCode = false
        wasSignedIn = state.signedIn
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Giro", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Calendar",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleDark) {
                Icon(
                    if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    stringResource(R.string.action_toggle_theme),
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // ── 1. ACCOUNT ──
        ElevatedCard(onClick = { showMyCode = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountAvatar(state.photoUrl, state.signedIn)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (state.signedIn) R.string.s_account else R.string.cc_sign_in_google),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (state.signedIn) (state.userLabel ?: stringResource(R.string.cc_signed_in))
                        else stringResource(R.string.s_need_for),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))

        val recentOthers = state.displayedRecents.drop(1)
        val hasRecents = recentOthers.isNotEmpty()
        val hasPublic = state.allCalendars.isNotEmpty()
        val hasCurrent = state.displayedRecents.isNotEmpty()

        // ── 2. CALENDARI PUBBLICI: sempre visibili se ce ne sono, anche senza login (lettura
        // pubblica consentita dalle Firestore rules) — ordinati per popolarità in
        // CalendarRepository.allCalendars(). ──
        if (hasCurrent || hasRecents || hasPublic) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                state.displayedRecents.firstOrNull()?.let { current ->
                    Text(
                        stringResource(R.string.s_using),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenCode(current.code) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            simplylucaapps.girocalendar.ui.common.CalendarLogo(
                                current.code, current.logoColor, current.logoData, size = 32.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(current.name.ifBlank { current.code }, style = MaterialTheme.typography.titleSmall)
                                Text(current.code, style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { pin(current) }) {
                                Icon(if (isPinned(current.code)) Icons.Default.Star else Icons.Default.StarBorder, stringResource(R.string.s_pin_cal),
                                    tint = if (isPinned(current.code)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.showInfo(current.code) }) {
                                Icon(Icons.Default.Info, stringResource(R.string.s_info_cal),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (hasPublic) {
                    Text(
                        stringResource(R.string.s_public_cals),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        state.allCalendars.forEach { cal ->
                            PublicCalendarRow(cal, onClick = { onOpenCode(cal.code) }, onInfo = { vm.showInfo(cal.code) },
                                pinned = isPinned(cal.code), onPin = { pin(simplylucaapps.girocalendar.data.settings.RecentCalendar(cal.code, cal.name, false, cal.logoColor, cal.logoData)) })
                        }
                    }
                }

                if (hasRecents) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(if (state.signedIn) R.string.s_recent_acc else R.string.s_recent),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        recentOthers.forEach { r ->
                            RecentCalendarRow(r, onClick = { onOpenCode(r.code) }, onInfo = { vm.showInfo(r.code) },
                                pinned = isPinned(r.code), onPin = { pin(r) })
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        // ── 3. AGGIUNGI CALENDARIO ESISTENTE (per codice, es. un calendario non pubblico) ──
        OutlinedButton(
            onClick = {
                addingExisting = !addingExisting
                if (addingExisting) { creatingCalendar = false; scope.launch { queryFocusRequester.requestFocus() } }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.s_add_existing))
        }
        if (addingExisting) {
            Spacer(Modifier.height(8.dp))
            CompactTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text("es. K7M2P9") },
                singleLine = true,
                isError = state.error != null,
                supportingText = state.error?.let { { Text(it) } },
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.loadAllCalendars(); showBrowse = true }) {
                            Icon(Icons.Default.Search, stringResource(R.string.s_browse_all))
                        }
                        FilledIconButton(
                            onClick = vm::search,
                            enabled = state.query.isNotBlank() && !state.loading,
                        ) {
                            if (state.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.s_open))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(queryFocusRequester),
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── 4. CREA NUOVO CALENDARIO ──
        OutlinedButton(
            onClick = {
                creatingCalendar = !creatingCalendar
                if (creatingCalendar) addingExisting = false
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.s_create_new))
        }
        if (creatingCalendar) {
            Spacer(Modifier.height(8.dp))
            CompactTextField(
                value = newCalendarName,
                onValueChange = { newCalendarName = it },
                label = { Text(stringResource(R.string.s_cal_group_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (newCalendarName.isNotBlank()) onCreateCalendar(newCalendarName) },
                ),
                trailingIcon = {
                    FilledIconButton(
                        onClick = { onCreateCalendar(newCalendarName) },
                        enabled = newCalendarName.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.s_continue))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = { activity?.finishAndRemoveTask() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.s_exit))
        }
        Text(
            "Giro v${simplylucaapps.girocalendar.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
    }

    if (showBrowse) {
        BrowseCalendarsDialog(
            calendars = state.allCalendars,
            loading = state.loadingAll,
            onOpen = { showBrowse = false; onOpenCode(it) },
            onDismiss = { showBrowse = false },
        )
    }

    if (showMyCode) {
        MyCodeDialog(
            uid = state.myUid,
            signedIn = state.signedIn,
            userLabel = state.userLabel,
            photoUrl = state.photoUrl,
            signingIn = state.signingIn,
            signInError = state.signInError,
            onSignIn = { activity?.let { vm.signIn(it) } },
            onSignOut = { vm.signOut() },
            onCreateCalendar = { showMyCode = false; onCreateCalendar("") },
            onDeleteAccount = { showMyCode = false; vm.loadOwnedForDeletion(); showDeleteAccount = true },
            onDismiss = { showMyCode = false; vm.consumeSignInError() },
        )
    }

    if (showDeleteAccount) {
        DeleteAccountDialog(
            owned = state.ownedForDeletion,
            loading = state.loadingOwnedForDeletion,
            deleting = state.deletingAccount,
            error = state.deleteAccountError,
            onSignInAgain = { activity?.let { vm.signIn(it) } },
            onConfirm = { nominations -> vm.deleteAccount(nominations) },
            onDismiss = { showDeleteAccount = false; vm.consumeAccountDeletionError() },
        )
    }
    LaunchedEffect(state.accountDeleted) {
        if (state.accountDeleted) { showDeleteAccount = false }
    }

    state.infoCode?.let { code ->
        var showInfoEditors by remember(code) { mutableStateOf(false) }
        CalendarInfoDialog(
            code = code,
            calendar = state.infoCalendar,
            eventCount = state.infoEventCount,
            loading = state.loadingInfo,
            isOwner = state.infoCalendar != null && state.myUid != null && (state.infoCalendar?.ownerUid == state.myUid || state.isSuper),
            isSuper = state.isSuper,
            onSetListed = { l -> vm.setListed(code, l) { ok, err -> if (!ok) toast(err ?: msgOpFailed) } },
            onDeleteCalendar = { vm.deleteCalendarAdmin(code) { ok, err -> toast(if (ok) msgCalDeleted else err ?: msgDeleteFailed) } },
            onManageEditors = { showInfoEditors = true },
            onDismiss = { vm.dismissInfo() },
        )
        if (showInfoEditors) {
            val calendar = state.infoCalendar
            EditorsDialog(
                editors = calendar?.editors.orEmpty(),
                editorLabels = calendar?.editorLabels.orEmpty(),
                ownerUid = calendar?.ownerUid.orEmpty(),
                onDismiss = { showInfoEditors = false },
                onAdd = { input, onDone ->
                    vm.addEditor(code, input) { ok, msg ->
                        if (ok) toast(msg ?: msgEditorAdded)
                        onDone(ok, msg)
                    }
                },
                onRemove = { uid -> vm.removeEditor(code, uid) { _, _ -> } },
                onOpen = { vm.backfillEditorLabels(code) },
            )
        }
    }

    SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** Info tecniche di un calendario: admin, collaboratori, data creazione, statistiche. */
@Composable
internal fun CalendarInfoDialog(
    code: String,
    calendar: simplylucaapps.girocalendar.data.remote.firestore.RideCalendar?,
    eventCount: Int?,
    loading: Boolean,
    isOwner: Boolean,
    isSuper: Boolean = false,
    onSetListed: (Boolean) -> Unit = {},
    onDeleteCalendar: () -> Unit = {},
    onManageEditors: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.s_delete_cal_q)) },
            text = { Text(stringResource(R.string.s_delete_cal_msg)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteCalendar() }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(calendar?.name?.ifBlank { code } ?: code) },
        text = {
            if (loading && calendar == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (calendar == null) {
                Text(
                    stringResource(R.string.s_info_load_fail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.s_code), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(calendar.code, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        IconButton(onClick = {
                            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("Codice", calendar.code))
                            android.widget.Toast.makeText(ctx, ctx.getString(R.string.info_copied), android.widget.Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ContentCopy, stringResource(R.string.info_copy), Modifier.size(18.dp))
                        }
                    }
                    // Admin (in rosso, «(Admin)») e sotto i collaboratori.
                    val adminName = calendar.ownerEmail.ifBlank { calendar.editorLabels[calendar.ownerUid].orEmpty() }
                    val names = calendar.editors.filter { it != calendar.ownerUid }.mapNotNull { calendar.editorLabels[it]?.takeIf { l -> l.isNotBlank() } } // senza login: nessun nome
                    Column {
                        Text(stringResource(R.string.s_team), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (adminName.isNotBlank()) Text("$adminName (${stringResource(R.string.s_admin)})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = androidx.compose.ui.graphics.Color(0xFFD32F2F))
                        names.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                    if (calendar.createdAtMillis > 0L) {
                        val df = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ITALY) }
                        InfoRow(stringResource(R.string.s_created_on), df.format(java.util.Date(calendar.createdAtMillis)))
                    }
                    if (isOwner) {
                        OutlinedButton(onClick = onManageEditors, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Group, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.s_manage_collab))
                        }
                    }
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.s_stats),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    InfoRow(stringResource(R.string.s_total_rides), eventCount?.toString() ?: "…")
                    InfoRow(stringResource(R.string.s_opens), calendar.openCount.toString())
                    InfoRow(stringResource(R.string.s_visibility), stringResource(if (calendar.listed) R.string.s_public_short else R.string.s_private_short))
                    if (isSuper) {
                        HorizontalDivider()
                        OutlinedButton(onClick = { onSetListed(!calendar.listed) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (calendar.listed) R.string.s_make_private else R.string.s_make_public))
                        }
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.s_delete_cal)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** Riga di un calendario recente: linea sottile in basso, senza bordo/sfondo. */
@Composable
private fun RecentCalendarRow(
    r: simplylucaapps.girocalendar.data.settings.RecentCalendar,
    onClick: () -> Unit,
    onInfo: () -> Unit,
    pinned: Boolean,
    onPin: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            simplylucaapps.girocalendar.ui.common.CalendarLogo(r.code, r.logoColor, r.logoData, size = 26.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name.ifBlank { r.code }, style = MaterialTheme.typography.titleSmall)
                Text(r.code, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPin, modifier = Modifier.size(32.dp)) {
                Icon(if (pinned) Icons.Default.Star else Icons.Default.StarBorder, stringResource(R.string.s_pin_cal),
                    tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onInfo, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Info, stringResource(R.string.s_info_cal),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun PublicCalendarRow(
    cal: simplylucaapps.girocalendar.data.remote.firestore.RideCalendar,
    onClick: () -> Unit,
    onInfo: () -> Unit,
    pinned: Boolean,
    onPin: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            simplylucaapps.girocalendar.ui.common.CalendarLogo(cal.code, cal.logoColor, cal.logoData, size = 26.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(cal.name.ifBlank { cal.code }, style = MaterialTheme.typography.titleSmall)
                Text(cal.code, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPin, modifier = Modifier.size(32.dp)) {
                Icon(if (pinned) Icons.Default.Star else Icons.Default.StarBorder, stringResource(R.string.s_pin_cal),
                    tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onInfo, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Info, stringResource(R.string.s_info_cal),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun AccountAvatar(photoUrl: String?, signedIn: Boolean) {
    val size = 40.dp
    val ring = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Box(
        Modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, ring, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (signedIn && !photoUrl.isNullOrBlank()) {
            coil3.compose.AsyncImage(
                model = photoUrl,
                contentDescription = stringResource(R.string.s_profile_photo),
                modifier = Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Default.AccountCircle, null,
                modifier = Modifier.size(size),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MyCodeDialog(
    uid: String?,
    signedIn: Boolean,
    userLabel: String?,
    photoUrl: String?,
    signingIn: Boolean,
    signInError: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onCreateCalendar: () -> Unit,
    onDeleteAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.s_account)) },
        text = {
            Column {
                if (signedIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountAvatar(photoUrl, true)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            userLabel ?: stringResource(R.string.cc_signed_in),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.s_my_code_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (uid != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                uid,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { copyToClipboard(context, uid) }) {
                                Icon(Icons.Default.ContentCopy, stringResource(R.string.s_copy))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onCreateCalendar, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.s_create_a_new))
                    }

                    OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.s_unlink))
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = onDeleteAccount,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.s_delete_account))
                    }
                } else {
                    Text(
                        stringResource(R.string.s_signin_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth(), enabled = !signingIn) {
                        if (signingIn) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(stringResource(R.string.cc_sign_in_google))
                        }
                    }
                    signInError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.s_signin_failed, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Giro v${simplylucaapps.girocalendar.BuildConfig.VERSION_NAME} " +
                        "(${simplylucaapps.girocalendar.BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("uid", text))
}

@Composable
private fun BrowseCalendarsDialog(
    calendars: List<simplylucaapps.girocalendar.data.remote.firestore.RideCalendar>,
    loading: Boolean,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val filtered = remember(calendars, q) {
        val n = q.trim().lowercase()
        if (n.isBlank()) calendars
        else calendars.filter {
            it.name.lowercase().contains(n) || it.code.lowercase().contains(n) ||
                it.description.lowercase().contains(n)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.s_all_cals)) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            ) {
                CompactTextField(
                    value = q, onValueChange = { q = it },
                    label = { Text(stringResource(R.string.s_search_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                when {
                    loading && calendars.isEmpty() ->
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    filtered.isEmpty() ->
                        Text(
                            if (calendars.isEmpty()) stringResource(R.string.s_none_public)
                            else stringResource(R.string.s_no_result, q),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        filtered.forEach { t ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onOpen(t.code) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                simplylucaapps.girocalendar.ui.common.CalendarLogo(
                                    t.code, t.logoColor, t.logoData, size = 26.dp,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        t.name.ifBlank { t.code },
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    if (t.description.isNotBlank()) Text(
                                        t.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    if (t.ownerEmail.isNotBlank()) Text(
                                        "✉ ${t.ownerEmail}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    t.code,
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/**
 * Eliminazione account: mostra cosa succede a ogni calendario amministrato prima di
 * chiedere conferma. Con altri collaboratori → passa in automatico al più anziano.
 * Owner unico → si può nominare un successore (campo UID) o lasciare che venga eliminato.
 */
@Composable
private fun DeleteAccountDialog(
    owned: List<simplylucaapps.girocalendar.data.remote.firestore.RideCalendar>,
    loading: Boolean,
    deleting: Boolean,
    error: String?,
    onSignInAgain: () -> Unit,
    onConfirm: (nominations: Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val solo = owned.filter { it.editors.size <= 1 }
    val withHeir = owned.filter { it.editors.size > 1 }
    val nominations = remember { mutableStateMapOf<String, String>() }
    var confirmStep by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text(stringResource(R.string.s_delete_account)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(
                        stringResource(R.string.s_del_acc_msg),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (withHeir.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.s_will_transfer),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        withHeir.forEach { t ->
                            Text(
                                "• ${t.name.ifBlank { t.code }} (${t.code})",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    if (solo.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.s_only_admin),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            stringResource(R.string.s_nominate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                        )
                        solo.forEach { t ->
                            Column(Modifier.padding(bottom = 10.dp)) {
                                Text(t.name.ifBlank { t.code }, style = MaterialTheme.typography.bodyMedium)
                                CompactTextField(
                                    value = nominations[t.code] ?: "",
                                    onValueChange = { nominations[t.code] = it.trim() },
                                    label = { Text(stringResource(R.string.s_user_code_opt)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    if (owned.isEmpty()) {
                        Text(
                            stringResource(R.string.s_no_admin_cals),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        if ((it.contains("accesso di recente") || it.contains("signed in recently"))) {
                            TextButton(onClick = onSignInAgain) { Text(stringResource(R.string.s_sign_in_again)) }
                        }
                    }
                    if (confirmStep) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.s_confirm_step),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && !deleting,
                onClick = {
                    if (!confirmStep) confirmStep = true
                    else onConfirm(nominations.toMap())
                },
            ) {
                if (deleting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.error)
                else Text(
                    stringResource(if (confirmStep) R.string.s_delete_final else R.string.s_continue),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deleting) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
