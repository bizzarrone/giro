package simplylucaapps.girocalendar.ui.calendar

import androidx.compose.material.icons.filled.*
import androidx.compose.material3.NavigationBarItem
import simplylucaapps.girocalendar.ui.common.CompactTextField
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.*
import simplylucaapps.girocalendar.data.settings.TabId
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.data.remote.firestore.RideCalendar
import simplylucaapps.girocalendar.data.remote.firestore.RideEvent
import simplylucaapps.girocalendar.ui.common.DifficultyBadge
import simplylucaapps.girocalendar.ui.common.RichText
import simplylucaapps.girocalendar.ui.theme.Bebas
import simplylucaapps.girocalendar.ui.theme.LocalGcColors
import simplylucaapps.girocalendar.util.EventExport
import simplylucaapps.girocalendar.util.EventTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    vm: CalendarViewModel,
    trailRepo: simplylucaapps.girocalendar.data.remote.firestore.TrailRepository,
    authRepo: simplylucaapps.girocalendar.data.remote.auth.GoogleAuthRepository,
    onBack: () -> Unit,
    currentThemeId: String = simplylucaapps.girocalendar.ui.theme.DEFAULT_THEME_ID,
    onThemeChange: (String) -> Unit = {},
    onOpenOther: () -> Unit = {},
    currentTextScale: Float = 1f,
    onTextScaleChange: (Float) -> Unit = {},
    showFieldIcons: Boolean = true,
    onShowFieldIconsChange: (Boolean) -> Unit = {},
    audioAutoplay: Boolean = true,
    onAudioAutoplayChange: (Boolean) -> Unit = {},
    fontStyleId: String = "classic",
    onFontStyleChange: (String) -> Unit = {},
    languageId: String = "system",
    onLanguageChange: (String) -> Unit = {},
    difficultyScale: String = "sts",
    onDifficultyScaleChange: (String) -> Unit = {},
    /** Nome per "Partecipa": quello del profilo Google se loggato, altrimenti l'ultimo
     * salvato localmente (ospite senza login). */
    suggestedName: String = "",
    onSaveMyName: (String) -> Unit = {},
    /** Calendari aperti di recente (per la scheda "CALENDARI" a sinistra di PROSSIME, solo
     * in modalità swipe): cambiare rapidamente calendario senza tornare alla ricerca. */
    recents: List<simplylucaapps.girocalendar.data.settings.RecentCalendar> = emptyList(),
    pinned: List<simplylucaapps.girocalendar.data.settings.RecentCalendar> = emptyList(),
    onOpenCalendar: (String) -> Unit = {},
    onCreateCalendar: () -> Unit = {},
) {
    val activity = LocalContext.current as? android.app.Activity
    val onExitApp: () -> Unit = { activity?.finishAndRemoveTask() }
    val state by vm.state.collectAsStateWithLifecycle()
    val isEditor = state.canEdit
    val editEnabled = isEditor
    // rememberSaveable (non remember): sopravvive quando questa composable resta nel back
    // stack di NavHost mentre si naviga a "Nuovo calendario" e si torna indietro — con
    // remember semplice, lo stato tornava al default EVENTS anche se il pager (che ha il suo
    // stato salvato) restava visivamente sulla tab "Calendari", disallineando il FAB.
    // Chiave = state.code: passando da un calendario all'altro dalla tab "Calendari" (stessa
    // route pattern "calendar/{code}", solo l'argomento cambia) Navigation Compose può riusare
    // lo stesso slot rememberSaveable — senza questa key, activePage/pagerState restavano
    // "congelati" sulla tab Calendari del calendario precedente invece di aprire Prossime.
    var activePage by rememberSaveable(state.code) { mutableStateOf(ActivePage.EVENTS) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    simplylucaapps.girocalendar.ui.emergency.EmergencyAlertHost()
    val scope = rememberCoroutineScope()
    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }
    val blockedSnack = stringResource(R.string.bl_blocked_snack)
    val signInToPropose = stringResource(R.string.pe_sign_in)
    val archivedMsg = stringResource(R.string.event_archived)
    val linkedMsg = stringResource(R.string.lk_done)
    val unlinkedMsg = stringResource(R.string.lk_removed)
    val genericErrorMsg = stringResource(R.string.generic_error)
    val documentTooLargeMsg = stringResource(R.string.event_document_too_large)
    val proposalSentMsg = stringResource(R.string.proposal_sent)
    val proposalSendErrorMsg = stringResource(R.string.proposal_send_error)
    val editorAddedMsg = stringResource(R.string.editor_added)
    val eventPublishedMsg = stringResource(R.string.event_published)
    val eventRepublishedMsg = stringResource(R.string.event_republished)
    val eventClonedMsg = stringResource(R.string.event_cloned)
    val epicAddedMsg = stringResource(R.string.epic_added)

    var editingEvent by remember { mutableStateOf<RideEvent?>(null) }
    // L'audio non è più nello stato eventi osservato (documento separato, vedi
    // CalendarViewModel.saveEvent): va caricato a parte quando si apre l'editor su un evento
    // che ne ha uno. null finché non è (eventualmente) arrivato.
    var editingEventAudio by remember { mutableStateOf<simplylucaapps.girocalendar.data.remote.firestore.EventAudio?>(null) }
    LaunchedEffect(editingEvent?.id) {
        editingEventAudio = null
        val ev = editingEvent
        if (ev != null && ev.hasAudio) {
            editingEventAudio = vm.loadEventAudio(ev.id)
        }
    }
    // Solo l'id, non l'oggetto: l'evento va ricavato da state.events a ogni ricomposizione,
    // così quando arriva un aggiornamento (es. un nuovo partecipante) il dettaglio aperto lo
    // riflette subito invece di restare fermo sulla foto dell'evento al momento dell'apertura.
    var detailEventId by remember { mutableStateOf<String?>(null) }
    val detailEvent = detailEventId?.let { id -> state.events.find { it.id == id } }
    // Link web a un evento (https://girocalendar.pages.dev/#/c/CODE/ID): apre la scheda appena
    // gli eventi sono caricati.
    LaunchedEffect(state.events) {
        val want = simplylucaapps.girocalendar.pendingEventId
        if (want != null && state.events.any { it.id == want }) {
            detailEventId = want
            simplylucaapps.girocalendar.pendingEventId = null
        }
    }
    var cloneTarget by remember { mutableStateOf<RideEvent?>(null) }
    var deleteAnnouncementId by remember { mutableStateOf<String?>(null) }
    var showNewEvent by remember { mutableStateOf(false) }
    var showNewJob by remember { mutableStateOf(false) }
    var showEditors by remember { mutableStateOf(false) }
    var showBlacklist by remember { mutableStateOf(false) }
    // lista nera: con "Escludi modifica" (o accesso negato) la matita della mappa resta spenta e avvisa
    LaunchedEffect(state.myBlock) { simplylucaapps.girocalendar.ui.trails.TrailEdit.blocked = state.myBlock.isNotEmpty(); if (state.myBlock.isNotEmpty()) simplylucaapps.girocalendar.ui.trails.TrailEdit.on = false }
    var showCalendarInfo by remember { mutableStateOf(false) }
    var showThemes by remember { mutableStateOf(false) }
    var showPreferences by remember { mutableStateOf(false) }
    var showAnnouncement by remember { mutableStateOf(false) }
    var editingAnnouncement by remember { mutableStateOf<simplylucaapps.girocalendar.data.remote.firestore.Announcement?>(null) }
    var showProposeEvent by remember { mutableStateOf(false) }
    var showProposals by remember { mutableStateOf(false) }
    // Notifiche push delle nuove proposte (solo editor): registra il token e chiede il permesso una volta.
    val notifPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(isEditor) {
        if (!isEditor) return@LaunchedEffect
        simplylucaapps.girocalendar.push.Push.register(context)
        val sp = context.getSharedPreferences("push_prefs", android.content.Context.MODE_PRIVATE)
        if (!simplylucaapps.girocalendar.push.Push.hasNotificationPermission(context) && !sp.getBoolean("asked", false)) {
            sp.edit().putBoolean("asked", true).apply()
            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Nuova proposta arrivata mentre l'app è aperta: avviso subito visibile.
    val newProposalMsg = stringResource(R.string.proposal_new_snack)
    val seeLabel = stringResource(R.string.proposal_see)
    var lastProposalCount by remember(state.code) { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.proposals.size, state.proposalsLoaded, isEditor) {
        if (!isEditor || !state.proposalsLoaded) { lastProposalCount = null; return@LaunchedEffect }
        val prev = lastProposalCount; val n = state.proposals.size
        lastProposalCount = n
        if (prev != null && n > prev) {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(newProposalMsg, seeLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) showProposals = true
        }
    }
    var reviewingProposal by remember { mutableStateOf<simplylucaapps.girocalendar.data.remote.firestore.RideProposal?>(null) }
    var showArchive by remember { mutableStateOf(false) }
    var repescatingEvent by remember { mutableStateOf<RideEvent?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    // Suggerimenti: uno a caso, una volta per avvio dell'app all'apertura di un calendario (spenti da «Non mostrare più»).
    val tipCtx = androidx.compose.ui.platform.LocalContext.current
    val bundledTips = androidx.compose.ui.res.stringArrayResource(R.array.tips).toList()
    // Elenco da tips.json (scaricato in background, al più ogni 6 ore), con quelli dell'app come riserva.
    var tipList by remember { mutableStateOf(simplylucaapps.girocalendar.util.TipsStore.load(tipCtx, bundledTips)) }
    LaunchedEffect(Unit) {
        if (simplylucaapps.girocalendar.util.TipsStore.refreshIfStale(tipCtx)) tipList = simplylucaapps.girocalendar.util.TipsStore.load(tipCtx, bundledTips)
    }
    var tipIndex by remember { mutableStateOf<Int?>(null) }
    var showAllTips by remember { mutableStateOf(false) }
    LaunchedEffect(state.code) {
        kotlinx.coroutines.delay(1500)
        if (!TipsSession.shown && !simplylucaapps.girocalendar.data.settings.BootPrefs.tipsOff(tipCtx) && tipList.isNotEmpty()) {
            TipsSession.shown = true; tipIndex = simplylucaapps.girocalendar.data.settings.BootPrefs.nextTip(tipCtx, com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid, tipList)
        }
    }
    var showDonate by remember { mutableStateOf(false) }
    var showOfflineArea by remember { mutableStateOf(false) }
    var showLinkEvent by remember { mutableStateOf(false) }
    var showMyStorage by remember { mutableStateOf(false) }
    // Scheda «Impostazioni»: le Preferenze come pagina piatta (si può continuare a scorrere tra le schede)
    val settingsPage: @Composable () -> Unit = {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.menu_preferences), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { showThemes = true }) { Text("🎨", fontSize = 24.sp) }   // scelta del tema
            }
            PreferencesContent(
                currentTextScale = currentTextScale,
                onTextScaleChange = onTextScaleChange,
                showFieldIcons = showFieldIcons,
                onShowFieldIconsChange = onShowFieldIconsChange,
                audioAutoplay = audioAutoplay,
                onAudioAutoplayChange = onAudioAutoplayChange,
                fontStyleId = fontStyleId,
                onFontStyleChange = onFontStyleChange,
                languageId = languageId,
                onLanguageChange = onLanguageChange,
                difficultyScale = difficultyScale,
                onDifficultyScaleChange = onDifficultyScaleChange,
                modifier = Modifier.weight(1f).padding(top = 8.dp),
            )
        }
    }
    var qaprefsSeen by remember { mutableIntStateOf(simplylucaapps.girocalendar.data.settings.QuickActions.prefs) }
    androidx.compose.runtime.LaunchedEffect(simplylucaapps.girocalendar.data.settings.QuickActions.prefs) { val v = simplylucaapps.girocalendar.data.settings.QuickActions.prefs; if (v != qaprefsSeen) { qaprefsSeen = v; showPreferences = true } }
    var qathemesSeen by remember { mutableIntStateOf(simplylucaapps.girocalendar.data.settings.QuickActions.themes) }
    androidx.compose.runtime.LaunchedEffect(simplylucaapps.girocalendar.data.settings.QuickActions.themes) { val v = simplylucaapps.girocalendar.data.settings.QuickActions.themes; if (v != qathemesSeen) { qathemesSeen = v; showThemes = true } }
    var qamyStorageSeen by remember { mutableIntStateOf(simplylucaapps.girocalendar.data.settings.QuickActions.myStorage) }
    androidx.compose.runtime.LaunchedEffect(simplylucaapps.girocalendar.data.settings.QuickActions.myStorage) { val v = simplylucaapps.girocalendar.data.settings.QuickActions.myStorage; if (v != qamyStorageSeen) { qamyStorageSeen = v; showMyStorage = true } }
    var menuOpen by remember { mutableStateOf(false) }

    var editingEpic by remember { mutableStateOf<RideEvent?>(null) }
    var editingEpicAudio by remember { mutableStateOf<simplylucaapps.girocalendar.data.remote.firestore.EventAudio?>(null) }
    LaunchedEffect(editingEpic?.id) {
        editingEpicAudio = null
        val ev = editingEpic
        if (ev != null && ev.hasAudio) {
            editingEpicAudio = vm.loadEpicAudio(ev.id)
        }
    }
    var showNewEpic by remember { mutableStateOf(false) }
    var detailEpicId by remember { mutableStateOf<String?>(null) }
    val detailEpic = detailEpicId?.let { id -> state.epics.find { it.id == id } }
    // Evento (normale o archiviato) da proporre per "Aggiungi a Epici": apre un piccolo
    // conferma invece di clonare subito, per evitare doppioni da un tap accidentale.
    var addToEpicsSource by remember { mutableStateOf<RideEvent?>(null) }
    // Epico da "Pianifica": apre l'editor di un evento NUOVO precompilato con questi dati,
    // data/ora vuote da riempire — l'epico stesso non viene toccato (resta in epics/).
    var planningEpic by remember { mutableStateOf<RideEvent?>(null) }

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(5 * 60_000); now = System.currentTimeMillis() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            when (activePage) {
                ActivePage.CALENDARS -> ExtendedFloatingActionButton(
                    onClick = onCreateCalendar,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text(stringResource(R.string.action_new_calendar)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
                ActivePage.EVENTS -> if (editEnabled) {
                    ExtendedFloatingActionButton(
                        onClick = { showNewEvent = true },
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text(stringResource(R.string.default_event_title)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                ActivePage.MAP -> {}
                ActivePage.EMERGENCY -> {}
                ActivePage.RECORD -> {}
                ActivePage.HOME -> {}
                ActivePage.TRAILS -> {} // ha già il proprio FAB "Aggiungi trail" interno
                ActivePage.CONTACTS -> {} // FAB interno alla tab
                ActivePage.MARKET -> {} // FAB interno alla tab
                ActivePage.INFO -> if (editEnabled) {
                    ExtendedFloatingActionButton(
                        onClick = { showAnnouncement = true },
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text(stringResource(R.string.announcement_label)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                ActivePage.WORKSHOP -> ExtendedFloatingActionButton(
                    onClick = { showNewJob = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text(stringResource(R.string.workshop_add_fab)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
                ActivePage.EPICS -> if (editEnabled) {
                    ExtendedFloatingActionButton(
                        onClick = { showNewEpic = true },
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text(stringResource(R.string.tab_epics)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.notFound -> ErrorBox(stringResource(R.string.calendar_not_found, state.code), onBack)
                state.error != null -> ErrorBox(state.error!!)
                else -> CalendarBody(
                    vm = vm,
                    settingsPage = settingsPage,
                    trailRepo = trailRepo,
                    authRepo = authRepo,
                    state = state,
                    now = now,
                    editEnabled = editEnabled,
                    isEditor = isEditor,
                    showFieldIcons = showFieldIcons,
                    recents = recents,
                    pinned = pinned,
                    onOpenCalendar = onOpenCalendar,
                    onActivePage = { activePage = it },
                    showNewJob = showNewJob,
                    onNewJobDismiss = { showNewJob = false },
                    isDark = simplylucaapps.girocalendar.ui.theme.themeById(currentThemeId).dark,
                    onToggleDark = {
                        onThemeChange(simplylucaapps.girocalendar.ui.theme.oppositeBrightness(currentThemeId))
                    },
                    audioAutoplay = audioAutoplay,
                    onAudioAutoplayChange = onAudioAutoplayChange,
                    onBack = onBack,
                    onMenu = { menuOpen = true },
                    onEditEvent = { editingEvent = it },
                    onShowDetail = { detailEventId = it.id },
                    onEditEpic = { editingEpic = it },
                    onShowEpicDetail = { detailEpicId = it.id },
                    onEditAnnouncement = { editingAnnouncement = it },
                    onDeleteAnnouncement = { id -> deleteAnnouncementId = id },
                    onMoveAnnouncement = { id, d -> vm.moveAnnouncement(id, d) { ok, err -> if (!ok && err != null) toast(err) } },
                    menuContent = {
                        CalendarMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            hasEvents = state.events.isNotEmpty(),
                            isOwner = state.isOwner,
                            canEdit = editEnabled,
                            proposalsCount = state.proposals.size,
                            onOpenOther = { menuOpen = false; onOpenOther() },
                            onProposeEvent = { menuOpen = false; if (state.myUid == null) toast(signInToPropose) else if (state.myBlock.isNotEmpty()) toast(blockedSnack) else showProposeEvent = true },
                            onNewEvent = { menuOpen = false; showNewEvent = true },
                            onThemes = { menuOpen = false; showThemes = true },
                            onPreferences = { menuOpen = false; showPreferences = true },
                            onShareCalendar = {
                                menuOpen = false
                                val link = "https://girocalendar.pages.dev/#/c/${state.code}"
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, link)
                                }
                                context.startActivity(android.content.Intent.createChooser(send, context.getString(R.string.menu_share_calendar)))
                            },
                            onCalendarInfo = { menuOpen = false; showCalendarInfo = true },
                            onEditors = { menuOpen = false; showEditors = true },
                            onBlacklist = { menuOpen = false; showBlacklist = true },
                            onAnnouncement = { menuOpen = false; showAnnouncement = true },
                            onProposals = { menuOpen = false; showProposals = true },
                            onArchive = { menuOpen = false; showArchive = true },
                            onInfo = { menuOpen = false; showInfo = true },
                            onHelp = { menuOpen = false; showHelp = true },
                            onTips = { menuOpen = false; simplylucaapps.girocalendar.data.settings.BootPrefs.setTipsOff(tipCtx, false); tipIndex = simplylucaapps.girocalendar.data.settings.BootPrefs.nextTip(tipCtx, com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid, tipList) },
                            onOfflineArea = { menuOpen = false; showOfflineArea = true },
                            onLinkEvent = { menuOpen = false; showLinkEvent = true },
                            onMyStorage = if (state.myUid != null) ({ menuOpen = false; showMyStorage = true }) else null,
                            onExitApp = { menuOpen = false; onExitApp() },
                        )
                    },
                )
            }
        }
    }

    if (showNewEvent || editingEvent != null) {
        Dialog(
            onDismissRequest = { showNewEvent = false; editingEvent = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                EventEditScreen(
                    initial = editingEvent?.let { ev ->
                        if (ev.hasAudio) ev.copy(
                            audioData = editingEventAudio?.audioData ?: ev.audioData,
                            audioMimeType = editingEventAudio?.audioMimeType ?: ev.audioMimeType,
                        ) else ev
                    },
                    onClose = { showNewEvent = false; editingEvent = null },
                    onSave = { e, audioChanged ->
                        vm.saveEvent(e, audioChanged) { ok, err, tooLarge ->
                            showNewEvent = false; editingEvent = null
                            if (!ok) toast(if (tooLarge) documentTooLargeMsg else (err ?: genericErrorMsg))
                        }
                    },
                    onDelete = editingEvent?.let { m -> { vm.deleteEvent(m.id) { _, _ -> editingEvent = null } } },
                    onArchive = editingEvent?.let { m ->
                        {
                            vm.archiveEvent(m) { ok, err ->
                                editingEvent = null
                                toast(if (ok) archivedMsg else (err ?: genericErrorMsg))
                            }
                        }
                    },
                    suggestedName = suggestedName,
                    onRemoveParticipant = editingEvent?.let { m ->
                        { name ->
                            vm.removeParticipant(m.id, name) { ok, err -> if (!ok && err != null) toast(err) }
                        }
                    },
                )
            }
        }
    }

    if (showProposeEvent) {
        Dialog(
            onDismissRequest = { showProposeEvent = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                ProposeEventScreen(
                    onClose = { showProposeEvent = false },
                    onSubmit = { p ->
                        vm.submitProposal(p) { ok, err, tooLarge ->
                            // Documento troppo grande: resta aperto il form per alleggerire gli allegati.
                            if (ok || !tooLarge) showProposeEvent = false
                            toast(if (ok) proposalSentMsg else if (tooLarge) documentTooLargeMsg else (err ?: proposalSendErrorMsg))
                        }
                    },
                    suggestedName = suggestedName,
                )
            }
        }
    }

    detailEvent?.let { e ->
        // commenti della scheda aperta: solo se loggato (le rules negano la lettura agli altri)
        var evComments by remember(e.id) { mutableStateOf<List<simplylucaapps.girocalendar.data.remote.firestore.EventComment>>(emptyList()) }
        LaunchedEffect(e.id, state.myUid) {
            if (vm.canComment && state.myUid != null) vm.observeEventComments(e.id).collect { evComments = it }
        }
        Dialog(
            onDismissRequest = { detailEventId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                EventDetailSheet(
                    event = e,
                    canEdit = editEnabled && e.fromCode.isEmpty(),
                    calendarCode = state.code,
                    showFieldIcons = showFieldIcons,
                    audioAutoplay = audioAutoplay,
                    suggestedName = suggestedName,
                    loadAudio = { vm.loadEventAudio(e.id) },
                    // Clonare non richiede essere editor DI QUESTO calendario: basta essere
                    // loggati e avere almeno un calendario proprio dove copiare l'evento (il
                    // picker gestisce già il caso "nessun calendario", vedi CloneEventDialog).
                    onClone = if (state.myUid != null) ({ cloneTarget = e }) else null,
                    routeMap = { track, titolo, dismiss ->
                        val routeVm: simplylucaapps.girocalendar.ui.trails.TrailViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                            factory = simplylucaapps.girocalendar.ui.trails.TrailViewModel.factory(trailRepo, authRepo)
                        )
                        simplylucaapps.girocalendar.ui.trails.EventRouteMap(routeVm, track, titolo, dismiss)
                    },
                    onAddToEpics = if (editEnabled && e.fromCode.isEmpty()) ({ addToEpicsSource = e }) else null,
                    onEdit = { detailEventId = null; editingEvent = e },
                    onDismiss = { detailEventId = null },
                    onParticipate = { name, status ->
                        onSaveMyName(name)
                        vm.setParticipation(e.id, name, status) { ok, err -> if (!ok && err != null) toast(err) }
                    },
                    onRemoveParticipant = { name ->
                        vm.setParticipation(e.id, name, "no") { ok, err -> if (!ok && err != null) toast(err) }
                    },
                    canComment = vm.canComment && state.myUid != null,
                    comments = evComments,
                    myUid = state.myUid,
                    canModerate = state.canEdit && e.fromCode.isEmpty(),
                    onUnlink = if (editEnabled && e.fromCode.isNotEmpty()) ({ detailEventId = null; vm.unlinkEvent(e.linkId) { ok, err -> toast(if (ok) unlinkedMsg else (err ?: genericErrorMsg)) } }) else null,
                    onAddComment = { t -> vm.addEventComment(e.id, t) { ok, err -> if (!ok && err != null) toast(err) } },
                    onEditComment = { id, t -> vm.editEventComment(e.id, id, t) { ok, err -> if (!ok && err != null) toast(err) } },
                    onDeleteComment = { id -> vm.deleteEventComment(e.id, id) { ok, err -> if (!ok && err != null) toast(err) } },
                )
            }
        }
    }

    deleteAnnouncementId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteAnnouncementId = null },
            title = { Text(stringResource(R.string.announcement_delete_q)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteAnnouncementId = null
                    vm.deleteAnnouncement(id) { ok, err -> if (!ok && err != null) toast(err) }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteAnnouncementId = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    cloneTarget?.let { ev ->
        CloneEventDialog(
            onDismiss = { cloneTarget = null },
            loadCalendars = { onResult -> vm.myCalendars(onResult) },
            currentCode = state.code,
            onPick = { targetCode ->
                vm.cloneEvent(ev, targetCode) { ok, err ->
                    cloneTarget = null
                    toast(if (ok) eventClonedMsg else (err ?: genericErrorMsg))
                }
            },
        )
    }

    if (showBlacklist) {
        BlacklistDialog(
            blocks = state.blocks,
            onDismiss = { showBlacklist = false },
            onAdd = { input, level, done -> vm.addBlock(input, level) { ok, msg -> done(ok, msg) } },
            onRemove = { uid -> vm.removeBlock(uid) },
        )
    }
    if (state.myBlock == "ban") {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.bl_banned_title)) },
            text = { Text(stringResource(R.string.bl_banned_msg)) },
            confirmButton = { TextButton(onClick = onOpenOther) { Text(stringResource(R.string.action_new_calendar)) } },
        )
    }
    if (showEditors) {
        EditorsDialog(
            editors = state.calendar?.editors.orEmpty(),
            editorLabels = state.calendar?.editorLabels.orEmpty(),
            ownerUid = state.calendar?.ownerUid.orEmpty(),
            onDismiss = { showEditors = false },
            onAdd = { input, onDone ->
                vm.addEditor(input) { ok, msg ->
                    if (ok) toast(msg ?: editorAddedMsg)
                    onDone(ok, msg)
                }
            },
            onRemove = { uid -> vm.removeEditor(uid) { _, _ -> } },
            onOpen = { state.calendar?.let { c -> vm.backfillEditorLabels(c) {} } },
        )
    }
    if (showCalendarInfo) {
        val cal = state.calendar
        CalendarEditDialog(
            code = state.code,
            name = cal?.name.orEmpty(),
            description = cal?.description.orEmpty(),
            listed = cal?.listed ?: true,
            logoColor = cal?.logoColor.orEmpty(),
            logoData = cal?.logoData.orEmpty(),
            openCount = cal?.openCount ?: 0L,
            onDismiss = { showCalendarInfo = false },
            onSave = { n, d, pub -> vm.saveCalendarInfo(n, d, pub) { ok, err -> showCalendarInfo = false; if (!ok && err != null) toast(err) } },
            onSaveLogo = { color, data -> vm.saveCalendarLogo(color, data) { _, err -> if (err != null) toast(err) } },
            // Solo l'owner (le rules lo impongono comunque). Dopo l'eliminazione si esce dal calendario.
            onDelete = if (state.isOwner) ({
                vm.deleteCalendarByCode(state.code) { ok, err ->
                    if (ok) { showCalendarInfo = false; onBack() } else if (err != null) toast(err)
                }
            }) else null,
        )
    }
    if (showThemes) {
        ThemePickerDialog(
            currentId = currentThemeId,
            onPick = { onThemeChange(it) },
            onDismiss = { showThemes = false },
        )
    }
    if (showPreferences) {
        PreferencesDialog(
            currentTextScale = currentTextScale,
            onTextScaleChange = onTextScaleChange,
            showFieldIcons = showFieldIcons,
            onShowFieldIconsChange = onShowFieldIconsChange,
            audioAutoplay = audioAutoplay,
            onAudioAutoplayChange = onAudioAutoplayChange,
            fontStyleId = fontStyleId,
            onFontStyleChange = onFontStyleChange,
            languageId = languageId,
            onLanguageChange = onLanguageChange,
            difficultyScale = difficultyScale,
            onDifficultyScaleChange = onDifficultyScaleChange,
            onThemes = { showPreferences = false; showThemes = true },
            onDismiss = { showPreferences = false },
        )
    }
    editingAnnouncement?.let { a ->
        AnnouncementEditDialog(
            initial = a,
            onDismiss = { editingAnnouncement = null },
            onSave = { title, text ->
                editingAnnouncement = null
                vm.updateAnnouncement(a.id, title, text) { ok, err -> if (!ok && err != null) toast(err) }
            },
        )
    }
    if (showAnnouncement) {
        AnnouncementEditorDialog(
            announcements = state.announcements,
            onDismiss = { showAnnouncement = false },
            onAdd = { title, text ->
                vm.addAnnouncement(title, text) { ok, err -> if (!ok && err != null) toast(err) }
            },
            onDelete = { id ->
                vm.deleteAnnouncement(id) { ok, err -> if (!ok && err != null) toast(err) }
            },
        )
    }
    if (showProposals) {
        ProposalsDialog(
            proposals = state.proposals,
            onDismiss = { showProposals = false },
            onApprove = { p ->
                vm.approveProposal(p) { ok, err -> if (!ok && err != null) toast(err) else toast(eventPublishedMsg) }
            },
            onReject = { p ->
                vm.rejectProposal(p.id) { ok, err -> if (!ok && err != null) toast(err) }
            },
            onReview = { p -> showProposals = false; reviewingProposal = p },
        )
    }

    reviewingProposal?.let { p ->
        Dialog(
            onDismissRequest = { reviewingProposal = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                EventEditScreen(
                    initial = RideEvent(
                        titolo = p.titolo, data = p.data, ora = p.ora, localita = p.localita,
                        ritrovo = p.ritrovo, proponente = p.proponente, difficolta = p.difficolta,
                        waProponente = p.waProponente, linkPercorso = p.linkPercorso,
                        link2 = p.link2, video = p.video, note = p.note,
                        cardBgData = p.cardBgData, photoData = p.photoData,
                        gpxData = p.gpxData, gpxFileName = p.gpxFileName,
                        audioData = p.audioData, audioMimeType = p.audioMimeType,
                    ),
                    onClose = { reviewingProposal = null },
                    onSave = { reviewed, audioChanged ->
                        vm.approveProposalReviewed(p.id, reviewed, audioChanged || reviewed.audioData.isNotBlank()) { ok, err, tooLarge ->
                            reviewingProposal = null
                            toast(if (ok) eventPublishedMsg else if (tooLarge) documentTooLargeMsg else (err ?: genericErrorMsg))
                        }
                    },
                    onDelete = null,
                )
            }
        }
    }

    if (showArchive) {
        ArchiveDialog(
            archivedEvents = state.archivedEvents,
            onDismiss = { showArchive = false },
            onRepesca = { e -> showArchive = false; repescatingEvent = e },
            onDelete = { e ->
                vm.deleteArchivedEvent(e.id) { ok, err -> if (!ok && err != null) toast(err) }
            },
            onAddToEpics = { e -> showArchive = false; addToEpicsSource = e },
        )
    }

    repescatingEvent?.let { e ->
        // Audio dell'archiviato (id reale, prima del .copy(id="") sotto): documento separato,
        // va caricato a parte come per editingEvent, altrimenti "Ripesca" lo perderebbe.
        var repescaAudio by remember(e.id) { mutableStateOf<simplylucaapps.girocalendar.data.remote.firestore.EventAudio?>(null) }
        LaunchedEffect(e.id) { if (e.hasAudio) repescaAudio = vm.loadEventAudio(e.id) }
        Dialog(
            onDismissRequest = { repescatingEvent = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                EventEditScreen(
                    // id vuoto: "Ripesca" crea un NUOVO evento con questi dati, non modifica
                    // quello archiviato (che resta in archived/ finché non lo si elimina).
                    initial = e.copy(
                        id = "", data = "", ora = "",
                        audioData = repescaAudio?.audioData ?: "",
                        audioMimeType = repescaAudio?.audioMimeType ?: "",
                    ),
                    onClose = { repescatingEvent = null },
                    onSave = { reviewed, audioChanged ->
                        // "Ripesca" crea un evento NUOVO (id vuoto): se l'archiviato aveva
                        // audio (ricaricato sopra in repescaAudio) va comunque copiato sul
                        // nuovo id anche se l'utente non l'ha esplicitamente ritoccato qui.
                        val forceAudio = audioChanged || reviewed.audioData.isNotBlank()
                        vm.saveEvent(reviewed, forceAudio) { ok, err, tooLarge ->
                            repescatingEvent = null
                            toast(if (ok) eventRepublishedMsg else if (tooLarge) documentTooLargeMsg else (err ?: genericErrorMsg))
                        }
                    },
                    onDelete = null,
                )
            }
        }
    }

    if (showNewEpic || editingEpic != null) {
        Dialog(
            onDismissRequest = { showNewEpic = false; editingEpic = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                EventEditScreen(
                    initial = editingEpic?.let { ev ->
                        if (ev.hasAudio) ev.copy(
                            audioData = editingEpicAudio?.audioData ?: ev.audioData,
                            audioMimeType = editingEpicAudio?.audioMimeType ?: ev.audioMimeType,
                        ) else ev
                    },
                    isEpic = true,
                    onClose = { showNewEpic = false; editingEpic = null },
                    onSave = { e, audioChanged ->
                        vm.saveEpic(e, audioChanged) { ok, err, tooLarge ->
                            showNewEpic = false; editingEpic = null
                            if (!ok) toast(if (tooLarge) documentTooLargeMsg else (err ?: genericErrorMsg))
                        }
                    },
                    onDelete = editingEpic?.let { m -> { vm.deleteEpic(m.id) { _, _ -> editingEpic = null } } },
                )
            }
        }
    }

    detailEpic?.let { e ->
        Dialog(
            onDismissRequest = { detailEpicId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                EventDetailSheet(
                    event = e,
                    canEdit = editEnabled,
                    calendarCode = state.code,
                    showFieldIcons = showFieldIcons,
                    audioAutoplay = audioAutoplay,
                    loadAudio = { vm.loadEpicAudio(e.id) },
                    isEpic = true,
                    onPlan = if (editEnabled) ({ detailEpicId = null; planningEpic = e }) else null,
                    onEdit = { detailEpicId = null; editingEpic = e },
                    onDismiss = { detailEpicId = null },
                )
            }
        }
    }

    planningEpic?.let { e ->
        // Audio dell'epico (id reale, prima del .copy(id="") sotto): documento separato,
        // va caricato a parte come per repescatingEvent, altrimenti "Pianifica" lo perderebbe.
        var planAudio by remember(e.id) { mutableStateOf<simplylucaapps.girocalendar.data.remote.firestore.EventAudio?>(null) }
        LaunchedEffect(e.id) { if (e.hasAudio) planAudio = vm.loadEpicAudio(e.id) }
        Dialog(
            onDismissRequest = { planningEpic = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                EventEditScreen(
                    // id vuoto: "Pianifica" crea un NUOVO evento con questi dati, non modifica
                    // l'epico (che resta in epics/ finché non lo si elimina esplicitamente).
                    initial = e.copy(
                        id = "", data = "", ora = "",
                        audioData = planAudio?.audioData ?: "",
                        audioMimeType = planAudio?.audioMimeType ?: "",
                    ),
                    onClose = { planningEpic = null },
                    onSave = { reviewed, audioChanged ->
                        val forceAudio = audioChanged || reviewed.audioData.isNotBlank()
                        vm.saveEvent(reviewed, forceAudio) { ok, err, tooLarge ->
                            planningEpic = null
                            toast(if (ok) eventPublishedMsg else if (tooLarge) documentTooLargeMsg else (err ?: genericErrorMsg))
                        }
                    },
                    onDelete = null,
                )
            }
        }
    }

    addToEpicsSource?.let { source ->
        AlertDialog(
            onDismissRequest = { addToEpicsSource = null },
            title = { Text(stringResource(R.string.epic_added_confirm_title, source.titolo.ifBlank { stringResource(R.string.default_event_title) })) },
            text = { Text(stringResource(R.string.epic_added_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.addToEpics(source) { ok, err ->
                        addToEpicsSource = null
                        toast(if (ok) epicAddedMsg else (err ?: genericErrorMsg))
                    }
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { TextButton(onClick = { addToEpicsSource = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (showInfo) InfoDialog(vm = vm, onDismiss = { showInfo = false })
    if (showHelp) HelpDialog(onDismiss = { showHelp = false })
    tipIndex?.let { i ->
        val tips = tipList
        AlertDialog(
            onDismissRequest = { tipIndex = null },
            title = {
                // Segreto: 3 tocchi rapidi sulla lampadina mostrano l'elenco di tutti i suggerimenti.
                var taps by remember { mutableStateOf(0) }
                var tapT by remember { mutableStateOf(0L) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💡", modifier = Modifier.clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                        val now = System.currentTimeMillis()
                        taps = if (now - tapT < 700) taps + 1 else 1; tapT = now
                        if (taps >= 3) { taps = 0; showAllTips = true }
                    })
                    Text(" " + stringResource(R.string.tip_title).removePrefix("💡").trim() + " ${i % tips.size + 1}/${tips.size}")
                }
            },
            text = { Text(tips[i % tips.size], style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val pad = PaddingValues(horizontal = 8.dp)
                    TextButton(contentPadding = pad, onClick = { simplylucaapps.girocalendar.data.settings.BootPrefs.setTipsOff(tipCtx, true); tipIndex = null }) { Text(stringResource(R.string.tip_off), maxLines = 1) }
                    TextButton(contentPadding = pad, onClick = { tipIndex = simplylucaapps.girocalendar.data.settings.BootPrefs.nextTip(tipCtx, com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid, tips) }) { Text(stringResource(R.string.tip_next), maxLines = 1) }
                    TextButton(contentPadding = pad, onClick = { tipIndex = null }) { Text(stringResource(R.string.action_close), maxLines = 1) }
                }
            },
        )
    }
    if (showAllTips) {
        AlertDialog(
            onDismissRequest = { showAllTips = false },
            title = { Text("💡 " + stringResource(R.string.tips_all)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    itemsIndexed(tipList) { n, t ->
                        Text("${n + 1}. $t", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAllTips = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
    if (showOfflineArea) simplylucaapps.girocalendar.ui.nav.OfflineAreaDialog(onDismiss = { showOfflineArea = false })
    if (showMyStorage) simplylucaapps.girocalendar.ui.trails.MyStorageDialog(onDismiss = { showMyStorage = false })
    if (showLinkEvent) LinkEventDialog(
        currentCode = state.code, alreadyLinked = vm.linkedIds(), findCalendars = { q, cb -> vm.findCalendars(q, cb) }, loadEvents = { c, cb -> vm.eventsOf(c, cb) }, onDismiss = { showLinkEvent = false },
        onPick = { c, e -> showLinkEvent = false; vm.linkEvent(c, e.id) { ok, err -> toast(if (ok) linkedMsg else (err ?: genericErrorMsg)) } },
    )

    if (showDonate) {
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        val donateUrl = "https://ko-fi.com/simplylucaapps"
        AlertDialog(
            onDismissRequest = { showDonate = false },
            title = { Text(stringResource(R.string.donate_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.donate_message))
                    Text(
                        donateUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        modifier = Modifier.padding(top = 8.dp).clickable { uriHandler.openUri(donateUrl) },
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showDonate = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

@Composable
private fun CalendarMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    hasEvents: Boolean,
    isOwner: Boolean,
    canEdit: Boolean,
    proposalsCount: Int,
    onOpenOther: () -> Unit,
    onNewEvent: () -> Unit,
    onThemes: () -> Unit,
    onPreferences: () -> Unit,
    onShareCalendar: () -> Unit,
    onCalendarInfo: () -> Unit,
    onEditors: () -> Unit,
    onBlacklist: () -> Unit,
    onAnnouncement: () -> Unit,
    onProposeEvent: () -> Unit,
    onProposals: () -> Unit,
    onArchive: () -> Unit,
    onInfo: () -> Unit,
    onHelp: () -> Unit,
    onTips: () -> Unit,
    onOfflineArea: () -> Unit,
    onLinkEvent: () -> Unit,
    onMyStorage: (() -> Unit)?,
    onExitApp: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_open_other)) },
            leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
            onClick = onOpenOther,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_propose_event)) },
            leadingIcon = { Icon(Icons.Default.Lightbulb, null) },
            onClick = onProposeEvent,
        )
        if (canEdit) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_new_event)) },
                leadingIcon = { Icon(Icons.Default.Add, null) },
                onClick = onNewEvent,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_link_event)) },
                leadingIcon = { Icon(Icons.Default.Link, null) },
                onClick = onLinkEvent,
            )
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.menu_proposals))
                        if (proposalsCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Badge { Text("$proposalsCount") }
                        }
                    }
                },
                leadingIcon = { Icon(Icons.Default.Inbox, null) },
                onClick = onProposals,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.archive_title)) },
                leadingIcon = { Icon(Icons.Default.Archive, null) },
                onClick = onArchive,
            )
        }
        HorizontalDivider()
        if (onMyStorage != null) DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_my_storage)) },
            leadingIcon = { Icon(Icons.Default.Folder, null) },
            onClick = onMyStorage,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_share_calendar)) },
            leadingIcon = { Icon(Icons.Default.Share, null) },
            onClick = onShareCalendar,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_preferences)) },
            leadingIcon = { Icon(Icons.Default.Settings, null) },
            onClick = onPreferences,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_tips)) },
            leadingIcon = { Icon(Icons.Default.Lightbulb, null) },
            onClick = onTips,
        )
        if (isOwner) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_calendar_info)) },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = onCalendarInfo,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_editors)) },
                leadingIcon = { Icon(Icons.Default.Group, null) },
                onClick = onEditors,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_blacklist)) },
                leadingIcon = { Icon(Icons.Default.Block, null) },
                onClick = onBlacklist,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_announcements)) },
                leadingIcon = { Icon(Icons.Default.Campaign, null) },
                onClick = onAnnouncement,
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_theme)) },
            leadingIcon = { Icon(Icons.Default.DarkMode, null) },
            onClick = onThemes,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_info)) },
            leadingIcon = { Icon(Icons.Default.Info, null) },
            onClick = onInfo,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_help)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, null) },
            onClick = onHelp,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_offline_area)) },
            leadingIcon = { Icon(Icons.Default.Download, null) },
            onClick = onOfflineArea,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_exit_app)) },
            leadingIcon = { Icon(Icons.Default.Close, null) },
            onClick = onExitApp,
        )
        // Versione in fondo al menu.
        Text(
            "Giro v${simplylucaapps.girocalendar.BuildConfig.VERSION_NAME} (${simplylucaapps.girocalendar.BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelSmall,
            color = LocalGcColors.current.text3,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
        )
        // Aggiornamento disponibile (confronto con version.json online), rosso lampeggiante.
        var updateAvailable by remember { mutableStateOf(false) }
        val updCtx = LocalContext.current
        LaunchedEffect(expanded) {
            if (expanded) updateAvailable = simplylucaapps.girocalendar.util.UpdateCheck
                .isUpdateAvailable(updCtx, simplylucaapps.girocalendar.BuildConfig.VERSION_CODE)
        }
        if (updateAvailable) {
            val ctx = LocalContext.current
            val blink by rememberInfiniteTransition(label = "upd").animateFloat(
                1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "updAlpha",
            )
            Text(
                stringResource(R.string.update_available),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE53935),
                modifier = Modifier.alpha(blink).fillMaxWidth()
                    .clickable {
                        simplylucaapps.girocalendar.util.PlayStore.openAppPage(ctx)
                    }
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun InfoDialog(vm: CalendarViewModel, onDismiss: () -> Unit) {
    val ver = simplylucaapps.girocalendar.BuildConfig.VERSION_NAME
    val ctx = LocalContext.current

    // Easter egg: 3 tap sull'icona entro breve tempo l'uno dall'altro fanno "rimbalzare" in
    // zoom l'icona con un suono, poi torna normale. Il contatore si azzera se passa troppo
    // tempo tra un tap e il successivo, per non scattare con tap sparsi nel tempo.
    val scope = rememberCoroutineScope()
    var tapCount by remember { mutableStateOf(0) }
    var lastTapAt by remember { mutableStateOf(0L) }
    val iconScale = remember { Animatable(1f) }
    fun onIconTap() {
        val now = System.currentTimeMillis()
        tapCount = if (now - lastTapAt > 1000) 1 else tapCount + 1
        lastTapAt = now
        if (tapCount >= 3) {
            tapCount = 0
            scope.launch {
                runCatching {
                    android.media.MediaPlayer.create(ctx, simplylucaapps.girocalendar.R.raw.halloween_intro)?.apply {
                        setOnCompletionListener { release() }
                        start()
                    }
                }
                iconScale.animateTo(1.5f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                iconScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            }
        }
    }

    val green = androidx.compose.ui.graphics.Color(0xFF2E7D32)
    val webUrl = "https://girocalendar.pages.dev/"
    val playUrl = "https://play.google.com/store/apps/details?id=simplylucaapps.girocalendar"
    @Composable
    fun LinkRow(label: String, name: String, url: String) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(green)
                    .clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f))
                Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White, maxLines = 1)
            }
            TextButton(onClick = {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("link", url))
                android.widget.Toast.makeText(ctx, ctx.getString(R.string.info_link_copied), android.widget.Toast.LENGTH_SHORT).show()
            }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.widthIn(min = 0.dp)) { Text(stringResource(R.string.info_copy), maxLines = 1, style = MaterialTheme.typography.labelLarge) }
            IconButton(onClick = {
                runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), null)) }
            }, modifier = Modifier.size(36.dp)) { Icon(androidx.compose.material.icons.Icons.Default.Share, stringResource(R.string.action_share), modifier = Modifier.size(20.dp)) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(simplylucaapps.girocalendar.R.drawable.info_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer(scaleX = iconScale.value, scaleY = iconScale.value)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onIconTap() },
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.menu_info), style = MaterialTheme.typography.titleMedium)
                        Text("Giro v$ver", style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.info_developed_by), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.info_tagline), style = MaterialTheme.typography.bodySmall)
                LinkRow(stringResource(R.string.info_web_version), "girocalendar.pages.dev", webUrl)
                LinkRow(stringResource(R.string.info_android_version), stringResource(R.string.info_open_play), playUrl)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.info_more_apps),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).clickable {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://simplylucaapps.pages.dev/"))) }
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** Storia rilasci: legge `assets/changelog.md` (stesso file CHANGELOG.md del repo) e lo mostra in un popup. */
@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_help)) },
        text = {
            // Righe consecutive SENZA riga vuota tra loro restano vicine (solo "a capo"
            // semplice, spazio minimo) — esattamente come nel testo originale, dove titolo e
            // sottotitolo di un paragrafo stanno appiccicati. Un punto elenco ("•") è sempre
            // trattato come voce a sé con spazio pieno, anche senza riga vuota che lo separi
            // dalla riga precedente: altrimenti l'intero elenco finirebbe compresso.
            val lines = stringResource(R.string.help_text).split("\n")
            Column(Modifier.verticalScroll(rememberScrollState())) {
                lines.forEachIndexed { i, line ->
                    if (line.isBlank()) return@forEachIndexed
                    val isBullet = line.trimStart().startsWith("•")
                    val prevBlank = i == 0 || lines[i - 1].isBlank()
                    Spacer(Modifier.height(if (isBullet || prevBlank) 14.dp else 2.dp))
                    RichText(line, modifier = if (isBullet) Modifier.padding(start = 12.dp) else Modifier)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun ThemePickerDialog(
    currentId: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_theme)) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                simplylucaapps.girocalendar.ui.theme.APP_THEMES.forEach { th ->
                    val selected = th.id == currentId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(th.id); onDismiss() }
                            .then(
                                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(th.previewBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(th.previewAccent))
                                Box(Modifier.size(10.dp).clip(CircleShape).background(th.previewAccent2))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("${th.icon}  ${stringResource(simplylucaapps.girocalendar.ui.theme.themeNameRes(th.id))}", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f))
                        if (selected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun PreferencesDialog(
    currentTextScale: Float,
    onTextScaleChange: (Float) -> Unit,
    showFieldIcons: Boolean,
    onShowFieldIconsChange: (Boolean) -> Unit,
    audioAutoplay: Boolean,
    onAudioAutoplayChange: (Boolean) -> Unit,
    fontStyleId: String,
    onFontStyleChange: (String) -> Unit,
    languageId: String,
    onLanguageChange: (String) -> Unit,
    difficultyScale: String,
    onDifficultyScaleChange: (String) -> Unit,
    onThemes: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.menu_preferences), modifier = Modifier.weight(1f))
                IconButton(onClick = onThemes) { Text("🎨", fontSize = 24.sp) }   // scelta del tema
            }
        },
        text = {
            PreferencesContent(
                currentTextScale = currentTextScale,
                onTextScaleChange = onTextScaleChange,
                showFieldIcons = showFieldIcons,
                onShowFieldIconsChange = onShowFieldIconsChange,
                audioAutoplay = audioAutoplay,
                onAudioAutoplayChange = onAudioAutoplayChange,
                fontStyleId = fontStyleId,
                onFontStyleChange = onFontStyleChange,
                languageId = languageId,
                onLanguageChange = onLanguageChange,
                difficultyScale = difficultyScale,
                onDifficultyScaleChange = onDifficultyScaleChange,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
internal fun PreferencesContent(
    currentTextScale: Float,
    onTextScaleChange: (Float) -> Unit,
    showFieldIcons: Boolean,
    onShowFieldIconsChange: (Boolean) -> Unit,
    audioAutoplay: Boolean,
    onAudioAutoplayChange: (Boolean) -> Unit,
    fontStyleId: String,
    onFontStyleChange: (String) -> Unit,
    languageId: String,
    onLanguageChange: (String) -> Unit,
    difficultyScale: String,
    onDifficultyScaleChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember(currentTextScale) { mutableStateOf(currentTextScale) }
    Column(modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.pref_text_size), style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { onTextScaleChange(sliderValue) },
                        valueRange = 0.5f..1.5f,
                        modifier = Modifier.width(120.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "%.2fx".format(sliderValue),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.widthIn(min = 34.dp),
                    )
                }

                PreferenceSwitchRow(
                    label = stringResource(R.string.pref_field_icons),
                    checked = showFieldIcons,
                    onCheckedChange = onShowFieldIconsChange,
                )

                val bbCtx = androidx.compose.ui.platform.LocalContext.current
                PreferenceSwitchRow(
                    label = stringResource(R.string.pref_bottom_bar),
                    checked = simplylucaapps.girocalendar.data.settings.TabPrefs.bottomBar,
                    onCheckedChange = { simplylucaapps.girocalendar.data.settings.TabPrefs.setBottomBar(bbCtx, it) },
                )

                PreferenceSwitchRow(
                    label = stringResource(R.string.pref_audio_autoplay),
                    checked = audioAutoplay,
                    onCheckedChange = onAudioAutoplayChange,
                )

                val prefCtx = androidx.compose.ui.platform.LocalContext.current
                var tipsOn by remember { mutableStateOf(!simplylucaapps.girocalendar.data.settings.BootPrefs.tipsOff(prefCtx)) }
                PreferenceSwitchRow(
                    label = stringResource(R.string.pref_tips),
                    checked = tipsOn,
                    onCheckedChange = { tipsOn = it; simplylucaapps.girocalendar.data.settings.BootPrefs.setTipsOff(prefCtx, !it) },
                )
                simplylucaapps.girocalendar.ui.home.SuggestedTabRow()

                MapCacheSizeRow()

                val navCtx = androidx.compose.ui.platform.LocalContext.current
                var navApp by remember { mutableStateOf(simplylucaapps.girocalendar.data.settings.BootPrefs.navApp(navCtx)) }
                val navOptions = listOf(
                    simplylucaapps.girocalendar.util.NavLauncher.ASK to stringResource(R.string.p_nav_ask),
                    simplylucaapps.girocalendar.util.NavLauncher.MAPS to "Google Maps",
                    simplylucaapps.girocalendar.util.NavLauncher.WAZE to "Waze",
                    simplylucaapps.girocalendar.util.NavLauncher.OTHER to stringResource(R.string.p_nav_other),
                )
                PreferenceDropdownRow(
                    label = stringResource(R.string.p_nav_label),
                    selectedLabel = navOptions.first { it.first == navApp }.second,
                    options = navOptions,
                    onSelect = { navApp = it; simplylucaapps.girocalendar.data.settings.BootPrefs.setNavApp(navCtx, it) },
                )

                PreferenceDropdownRow(
                    label = stringResource(R.string.pref_font),
                    selectedLabel = simplylucaapps.girocalendar.ui.theme.AppFontStyle.of(fontStyleId).label,
                    options = simplylucaapps.girocalendar.ui.theme.AppFontStyle.entries.map { it.id to it.label },
                    onSelect = onFontStyleChange,
                )

                PreferenceDropdownRow(
                    label = stringResource(R.string.pref_difficulty_scale),
                    selectedLabel = if (difficultyScale == "trailforks") stringResource(R.string.pref_difficulty_scale_trailforks)
                        else stringResource(R.string.pref_difficulty_scale_sts),
                    options = listOf(
                        "sts" to stringResource(R.string.pref_difficulty_scale_sts),
                        "trailforks" to stringResource(R.string.pref_difficulty_scale_trailforks),
                    ),
                    onSelect = onDifficultyScaleChange,
                )

                val gpxCtx = androidx.compose.ui.platform.LocalContext.current
                simplylucaapps.girocalendar.ui.trails.GpxWidth.load(gpxCtx)
                val gpxWOptions = listOf(
                    "2" to stringResource(R.string.pref_gpxw_2), "3" to stringResource(R.string.pref_gpxw_3), "4" to stringResource(R.string.pref_gpxw_4),
                    "6" to stringResource(R.string.pref_gpxw_6), "8" to stringResource(R.string.pref_gpxw_8),
                )
                PreferenceDropdownRow(
                    label = stringResource(R.string.pref_gpx_width),
                    selectedLabel = gpxWOptions.firstOrNull { it.first == simplylucaapps.girocalendar.ui.trails.GpxWidth.w.toString() }?.second ?: gpxWOptions[1].second,
                    options = gpxWOptions,
                    onSelect = { v -> simplylucaapps.girocalendar.ui.trails.GpxWidth.set(gpxCtx, v.toInt()) },
                )

                val lookCtx = androidx.compose.ui.platform.LocalContext.current
                simplylucaapps.girocalendar.ui.trails.MapLook.load(lookCtx)
                val zoneOpOptions = listOf(0, 10, 22, 35, 50, 70).map { it.toString() to "$it%" }
                PreferenceDropdownRow(
                    label = stringResource(R.string.pref_zone_opacity),
                    selectedLabel = "${simplylucaapps.girocalendar.ui.trails.MapLook.zoneOp}%",
                    options = zoneOpOptions,
                    onSelect = { v -> simplylucaapps.girocalendar.ui.trails.MapLook.setZoneOp(lookCtx, v.toInt()) },
                )
                val boxOptions = listOf("1" to stringResource(R.string.pref_on), "0" to stringResource(R.string.pref_off))
                PreferenceDropdownRow(
                    label = stringResource(R.string.pref_name_box),
                    selectedLabel = if (simplylucaapps.girocalendar.ui.trails.MapLook.nameBox) boxOptions[0].second else boxOptions[1].second,
                    options = boxOptions,
                    onSelect = { v -> simplylucaapps.girocalendar.ui.trails.MapLook.setNameBox(lookCtx, v == "1") },
                )

                val languageOptions = listOf(
                    "system" to "🌐 " + stringResource(R.string.pref_language_system),
                    "it" to "🇮🇹 " + stringResource(R.string.pref_language_it),
                    "en" to "🇬🇧 " + stringResource(R.string.pref_language_en),
                )
                PreferenceDropdownRow(
                    label = stringResource(R.string.pref_language),
                    selectedLabel = languageOptions.first { it.first == languageId }.second,
                    options = languageOptions,
                    onSelect = onLanguageChange,
                )
    }
}

@Composable
private fun PreferenceSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp)
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PreferenceDropdownRow(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 40.dp)
                .clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                selectedLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = { onSelect(id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun AnnouncementEditorDialog(
    announcements: List<simplylucaapps.girocalendar.data.remote.firestore.Announcement>,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var newTitle by remember { mutableStateOf("") }
    var newText by remember { mutableStateOf("") }
    val dateFmt = remember { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ITALIAN) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_announcements)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.announcement_visibility_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompactTextField(
                    value = newTitle, onValueChange = { newTitle = it },
                    label = { Text(stringResource(R.string.announcement_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CompactTextField(
                    value = newText, onValueChange = { newText = it },
                    label = { Text(stringResource(R.string.announcement_new)) },
                    supportingText = { Text(stringResource(R.string.announcement_format_hint)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                )
                if (announcements.isNotEmpty()) {
                    HorizontalDivider()
                    Text(stringResource(R.string.announcement_published), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    announcements.forEach { a ->
                        Column(
                            Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    if (a.title.isNotBlank()) {
                                        Text(a.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(2.dp))
                                    }
                                    Text(
                                        if (a.createdAtMillis > 0) dateFmt.format(java.util.Date(a.createdAtMillis)) else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    RichText(a.text, modifier = Modifier.fillMaxWidth())
                                }
                                IconButton(onClick = { onDelete(a.id) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, stringResource(R.string.action_delete), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(newTitle.trim(), newText.trim()); newTitle = ""; newText = "" },
                enabled = newText.isNotBlank(),
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** Modifica di una comunicazione esistente (titolo e testo). */
@Composable
private fun AnnouncementEditDialog(
    initial: simplylucaapps.girocalendar.data.remote.firestore.Announcement,
    onDismiss: () -> Unit,
    onSave: (title: String, text: String) -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var text by remember { mutableStateOf(initial.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.announcement_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactTextField(value = title, onValueChange = { title = it }, singleLine = true,
                    label = { Text(stringResource(R.string.announcement_title)) }, modifier = Modifier.fillMaxWidth())
                CompactTextField(value = text, onValueChange = { text = it },
                    label = { Text(stringResource(R.string.announcement_new)) },
                    supportingText = { Text(stringResource(R.string.announcement_format_hint)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title.trim(), text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Quale scheda dello swipe è attiva ora, per decidere cosa mostrare nel FAB in [CalendarScreen]. */
private const val PAST_COLLAPSED = 10

enum class ActivePage { EVENTS, CALENDARS, TRAILS, MAP, INFO, EPICS, CONTACTS, WORKSHOP, RECORD, EMERGENCY, MARKET, HOME }

@Composable
private fun CalendarBody(
    state: CalendarState,
    now: Long,
    editEnabled: Boolean,
    isEditor: Boolean,
    showFieldIcons: Boolean,
    isDark: Boolean,
    onToggleDark: () -> Unit,
    audioAutoplay: Boolean,
    onAudioAutoplayChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onEditEvent: (RideEvent) -> Unit,
    onShowDetail: (RideEvent) -> Unit,
    onEditEpic: (RideEvent) -> Unit,
    onShowEpicDetail: (RideEvent) -> Unit,
    onEditAnnouncement: (simplylucaapps.girocalendar.data.remote.firestore.Announcement) -> Unit,
    onDeleteAnnouncement: (String) -> Unit,
    onMoveAnnouncement: (String, Int) -> Unit = { _, _ -> },
    menuContent: @Composable () -> Unit,
    recents: List<simplylucaapps.girocalendar.data.settings.RecentCalendar> = emptyList(),
    pinned: List<simplylucaapps.girocalendar.data.settings.RecentCalendar> = emptyList(),
    settingsPage: @Composable () -> Unit = {},
    onOpenCalendar: (String) -> Unit = {},
    vm: CalendarViewModel,
    trailRepo: simplylucaapps.girocalendar.data.remote.firestore.TrailRepository,
    authRepo: simplylucaapps.girocalendar.data.remote.auth.GoogleAuthRepository,
    onActivePage: (ActivePage) -> Unit = {},
    showNewJob: Boolean = false,
    onNewJobDismiss: () -> Unit = {},
) {
    // Lasciando il calendario si libera il WebView della mappa tenuto in cache tra una scheda e l'altra.
    DisposableEffect(Unit) { onDispose { simplylucaapps.girocalendar.ui.trails.releaseMapTabCache() } }
    val vc = LocalGcColors.current
    var showQuickInfo by remember { mutableStateOf(false) }
    var showMoreInfo by remember { mutableStateOf(false) }

    data class Derived(val upcoming: List<RideEvent>, val past: List<RideEvent>, val nextId: String?)
    val d = remember(state.events, now / 300_000) {
        val start = HashMap<String, Long>(state.events.size)
        state.events.forEach { start[it.id] = EventTime.startMillis(it) ?: 0L }
        val up = state.events.filter { !EventTime.isPast(it, now) }
            .sortedBy { start[it.id] ?: Long.MAX_VALUE }
        val past = state.events.filter { EventTime.isPast(it, now) }
            .sortedByDescending { start[it.id] ?: 0L }
        Derived(up, past, up.firstOrNull()?.id)
    }

    // «casetta» in alto: porta alla Home (null = già sulla Home o Home non disponibile); lo imposta il pager, più sotto
    var homeShown by remember { mutableStateOf(false) }
    val homeGoRef = remember { arrayOf<() -> Unit>({}) }
    val curTabRef = remember { arrayOf<TabId?>(null) }
    Column(Modifier.fillMaxSize()) {
        // ── HEADER a gradiente ──
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(vc.heroStart, vc.heroEnd)))
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 2.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Row(
                    Modifier.weight(1f).clickable { showQuickInfo = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    simplylucaapps.girocalendar.ui.common.CalendarLogo(
                        state.calendar?.name.orEmpty().ifBlank { state.code },
                        state.calendar?.logoColor.orEmpty(),
                        state.calendar?.logoData.orEmpty(),
                        size = 30.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        (state.calendar?.name?.ifBlank { state.code } ?: state.code).uppercase(),
                        fontFamily = Bebas,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp, letterSpacing = 0.5.sp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (pinned.any { it.code != state.code }) {
                        var switchOpen by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { switchOpen = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.ArrowDropDown, stringResource(R.string.s_switch_cal), tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                            DropdownMenu(expanded = switchOpen, onDismissRequest = { switchOpen = false }) {
                                val items = if (pinned.any { it.code == state.code }) pinned
                                    else listOf(simplylucaapps.girocalendar.data.settings.RecentCalendar(
                                        state.code, state.calendar?.name.orEmpty(), false,
                                        state.calendar?.logoColor.orEmpty(), state.calendar?.logoData.orEmpty())) + pinned
                                items.forEach { c ->
                                    val cur = c.code == state.code
                                    DropdownMenuItem(
                                        text = { Text(c.name.ifBlank { c.code }, maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal) },
                                        leadingIcon = { simplylucaapps.girocalendar.ui.common.CalendarLogo(c.code, c.logoColor, c.logoData, size = 24.dp) },
                                        trailingIcon = { if (cur) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = { switchOpen = false; if (!cur) { simplylucaapps.girocalendar.data.settings.TabPrefs.carryTab = curTabRef[0]; onOpenCalendar(c.code) } },
                                    )
                                }
                            }
                        }
                    }
                }
                if (homeShown) run {
                    IconButton(onClick = { homeGoRef[0]() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Home, stringResource(R.string.tab_home), tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                IconButton(onClick = { onAudioAutoplayChange(!audioAutoplay) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (audioAutoplay) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        stringResource(if (audioAutoplay) R.string.audio_autoplay_on else R.string.audio_autoplay_off),
                        tint = if (audioAutoplay) Color.White else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = onToggleDark, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        stringResource(R.string.action_toggle_theme), tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Box {
                    IconButton(onClick = onMenu, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.menu_title), tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    if (isEditor && state.proposals.isNotEmpty()) {
                        PulsingDot(modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp))
                    }
                    menuContent()
                }
            }
            // La descrizione non è più mostrata nell'header (per guadagnare spazio verticale):
            // resta comunque consultabile nel dialog Info del calendario.
            Spacer(Modifier.height(6.dp))
        }

        val announcements = state.announcements

        val upcomingItems: LazyListScope.(showHeader: Boolean) -> Unit = { showHeader ->
            if (showHeader) {
                item("h-up") {
                    SectionHeader(
                        stringResource(R.string.section_upcoming), d.upcoming.size,
                        bg = MaterialTheme.colorScheme.secondary,
                        fg = MaterialTheme.colorScheme.onSecondary,
                        icon = Icons.Default.Schedule,
                    )
                }
            }
            if (d.upcoming.isEmpty()) {
                item("e-up") { EmptyLine(stringResource(R.string.section_upcoming_empty)) }
            } else {
                items(d.upcoming, key = { "u-" + it.id }, contentType = { "event" }) { e ->
                    Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                        EventCard(e, isNext = e.id == d.nextId, canEdit = editEnabled && e.fromCode.isEmpty(), showFieldIcons = showFieldIcons,
                            onEdit = { onEditEvent(e) }, onDetail = { onShowDetail(e) })
                    }
                }
            }
        }
        // Uscite passate: oltre le prime PAST_COLLAPSED (le più recenti) restano raccolte dietro
        // "Altre N uscite", per non appesantire lo scroll nei calendari con molto storico.
        var showAllPast by rememberSaveable(state.code) { mutableStateOf(false) }
        val pastItems: LazyListScope.(withDivider: Boolean, showHeader: Boolean) -> Unit = { withDivider, showHeader ->
            if (withDivider) {
                item("div-pl") {
                    HorizontalDivider(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
                        thickness = 2.dp,
                        color = vc.text3.copy(alpha = 0.35f),
                    )
                }
            }
            if (showHeader) {
                item("h-pl") {
                    SectionHeader(
                        stringResource(R.string.section_past), d.past.size,
                        bg = MaterialTheme.colorScheme.onSurface,
                        fg = MaterialTheme.colorScheme.surface,
                        icon = Icons.Default.CheckCircle,
                    )
                }
            }
            if (d.past.isEmpty()) {
                item("e-pl") { EmptyLine(stringResource(R.string.section_past_empty)) }
            } else {
                val shown = if (showAllPast) d.past else d.past.take(PAST_COLLAPSED)
                items(shown, key = { "p-" + it.id }, contentType = { "event" }) { e ->
                    Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                        EventCard(e, isNext = false, canEdit = editEnabled && e.fromCode.isEmpty(), showFieldIcons = showFieldIcons,
                            onEdit = { onEditEvent(e) }, onDetail = { onShowDetail(e) })
                    }
                }
                if (d.past.size > PAST_COLLAPSED) {
                    item("past-more") {
                        TextButton(
                            onClick = { showAllPast = !showAllPast },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        ) {
                            Text(
                                if (showAllPast) stringResource(R.string.past_less)
                                else stringResource(R.string.past_more, d.past.size - PAST_COLLAPSED)
                            )
                        }
                    }
                }
            }
        }
        val hasInfoTab = announcements.isNotEmpty() || editEnabled
        // La scheda "CALENDARI" (calendari recenti, per passare da uno all'altro senza tornare
        // alla ricerca) sta a sinistra di PROSSIME. Non mostrata se non c'è ancora nessun
        // recente (es. primo calendario mai aperto).
        val hasCalendarsTab = recents.isNotEmpty()
        // "OFFICINA": ultima, visibile solo con login Google (dati privati dell'utente).
        val signedIn by remember { kotlinx.coroutines.flow.flow { authRepo.observeUser().collect { emit(it != null) } } }
            .collectAsState(initial = authRepo.currentUser != null)
        // "CONTATTI": prima di Officina, per chiunque sia loggato.
        val contactsVisible = signedIn
        // "EMERGENZA": solo se abilitata (Preferenze → Tabs visibili).
        val emergencyVisible = simplylucaapps.girocalendar.ui.emergency.EmergencyPrefs.enabled
        // Schede mostrate: ordine e visibilità scelti dall'utente (Preferenze → Tabs visibili), filtrate da ciò che è disponibile ora.
        val tabAvailable: (TabId) -> Boolean = { id ->
            when (id) {
                TabId.CALENDARS -> hasCalendarsTab
                TabId.INFO -> hasInfoTab
                TabId.CONTACTS -> contactsVisible
                TabId.WORKSHOP -> signedIn
                TabId.PAST -> false   // le uscite passate sono in fondo a «Prossime» (barra PASSATI)
                TabId.MYRIDES -> signedIn
                TabId.EMERGENCY -> emergencyVisible
                TabId.RECORD -> false   // assorbita nella scheda Mappa (interruttore Esplora/Naviga)
                else -> true
            }
        }
        val tabs = simplylucaapps.girocalendar.data.settings.TabPrefs.visible(tabAvailable)
        // Contatti e Officina richiedono il login: senza, la Home li mostra comunque ma in grigio (tocco → «Login richiesto»).
        val lockedTabs = if (signedIn) emptyList() else listOf(TabId.CONTACTS, TabId.WORKSHOP, TabId.MYRIDES).filter { it !in simplylucaapps.girocalendar.data.settings.TabPrefs.hidden }
        // Schede nascoste ma disponibili: la Home le mostra in grigio durante la modifica, per poterle riattivare.
        val hiddenTabs = simplylucaapps.girocalendar.data.settings.TabPrefs.order.filter { it in simplylucaapps.girocalendar.data.settings.TabPrefs.hidden && it != TabId.EMERGENCY && tabAvailable(it) }
        // key(state.code): passando da un calendario all'altro dalla tab "Calendari" (stessa
        // route pattern "calendar/{code}") Navigation Compose può riusare lo stesso slot
        // rememberSaveable del PagerState — senza questa key, il pager restava sulla pagina
        // "Calendari" del calendario precedente invece di aprirsi su "Prossime".
        val pagerState = key(state.code) {
            rememberPagerState(
                initialPage = (
                    tabs.indexOf(simplylucaapps.girocalendar.data.settings.TabPrefs.carryTab ?: simplylucaapps.girocalendar.data.settings.TabPrefs.startTab).takeIf { it >= 0 }
                        ?: tabs.indexOf(simplylucaapps.girocalendar.data.settings.TabPrefs.startTab).takeIf { it >= 0 }
                        ?: tabs.indexOf(TabId.HOME).takeIf { it >= 0 } ?: tabs.indexOf(TabId.UPCOMING)
                    ).coerceAtLeast(0),
                pageCount = { tabs.size },
            )
        }
        LaunchedEffect(state.code) { simplylucaapps.girocalendar.data.settings.TabPrefs.carryTab = null }
        val scope = rememberCoroutineScope()
        // Salto a una scheda: se è lontana si salta direttamente, senza animare (e quindi senza comporre — e costruire —
        // tutte le schede intermedie, mappa compresa); se è accanto si anima.
        val goTo: (Int) -> Unit = { i ->
            scope.launch { if (kotlin.math.abs(i - pagerState.currentPage) > 1) pagerState.scrollToPage(i) else pagerState.animateScrollToPage(i) }
        }
        val currentTab = tabs.getOrNull(pagerState.currentPage)
        // Se l'elenco delle schede cambia (interruttori/ordine), si resta sulla stessa scheda.
        var lastTab by remember { mutableStateOf(currentTab) }
        LaunchedEffect(tabs) {
            val i = tabs.indexOf(lastTab)
            if (i >= 0 && i != pagerState.currentPage) pagerState.scrollToPage(i)
        }
        LaunchedEffect(currentTab) { if (currentTab != null) lastTab = currentTab }
        // Indietro: da una scheda qualunque si torna alla Home (si esce dall'app solo dalla Home)
        val homeIdx = tabs.indexOf(TabId.HOME)
        androidx.activity.compose.BackHandler(enabled = homeIdx >= 0 && pagerState.currentPage != homeIdx) { goTo(homeIdx) }
        val homeShownNow = homeIdx >= 0 && pagerState.currentPage != homeIdx
        SideEffect { curTabRef[0] = currentTab; homeGoRef[0] = { goTo(homeIdx) }; if (homeShown != homeShownNow) homeShown = homeShownNow }
        // Impostazioni e I miei giri aprono subito il loro dialogo
        LaunchedEffect(currentTab) {
            when (currentTab) {
                else -> {}
            }
        }
        // GPX arrivato da un'altra app: si passa alla scheda MAPPA, che lo apre come "Tuo GPX".
        LaunchedEffect(simplylucaapps.girocalendar.ui.trails.IncomingGpx.pending != null) {
            if (simplylucaapps.girocalendar.ui.trails.IncomingGpx.pending != null) tabs.indexOf(TabId.MAP).takeIf { it >= 0 }?.let { pagerState.scrollToPage(it) }
        }
        // La richiesta "vedi sulla mappa" vale una volta: lasciando la tab MAPPA si azzera.
        LaunchedEffect(currentTab) { if (currentTab != TabId.MAP) simplylucaapps.girocalendar.ui.trails.MapFocus.trailId = null }
        LaunchedEffect(currentTab) {
            onActivePage(
                when (currentTab) {
                    TabId.CALENDARS -> ActivePage.CALENDARS
                    TabId.INFO -> ActivePage.INFO
                    TabId.TRAILS -> ActivePage.TRAILS
                    TabId.MAP -> ActivePage.MAP
                    TabId.EPICS -> ActivePage.EPICS
                    TabId.CONTACTS -> ActivePage.CONTACTS
                    TabId.WORKSHOP -> ActivePage.WORKSHOP
                    TabId.EMERGENCY -> ActivePage.EMERGENCY
                    TabId.RECORD -> ActivePage.RECORD
                    TabId.HOME -> ActivePage.HOME
                    TabId.MARKET -> ActivePage.MARKET
                    TabId.SETTINGS, TabId.MYRIDES -> ActivePage.HOME
                    else -> ActivePage.EVENTS
                }
            )
        }
        Column(Modifier.fillMaxSize()) {
            val tabLabel: (TabId) -> String = { it.label }
            if (!simplylucaapps.girocalendar.data.settings.TabPrefs.bottomBar) SwipeTabs(
                singleRow = true,
                selected = pagerState.currentPage,
                labels = tabs.map(tabLabel),
                onSelect = goTo,
            )
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                val tid = tabs.getOrNull(page)
                if (tid == TabId.CALENDARS) {
                    RecentCalendarsTab(recents = recents, currentCode = state.code, onOpenCalendar = onOpenCalendar, onOpenCurrent = { tabs.indexOf(TabId.UPCOMING).takeIf { it >= 0 }?.let(goTo) }, onBack = onBack, vm = vm)
                } else if (tid == TabId.MONTH) {
                    MonthTab(state.events, onShowDetail)
                } else if (tid == TabId.INFO) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(announcements, key = { _, a -> "ann-" + a.id }) { idx, a ->
                            val canOrder = editEnabled && state.isOwner
                            Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                                AnnouncementBox(a, forceExpanded = true,
                                    onMoveUp = if (canOrder && idx > 0) ({ onMoveAnnouncement(a.id, -1) }) else null,
                                    onMoveDown = if (canOrder && idx < announcements.lastIndex) ({ onMoveAnnouncement(a.id, 1) }) else null,
                                    onEdit = if (editEnabled && state.isOwner) ({ onEditAnnouncement(a) }) else null,
                                    onDelete = if (editEnabled && state.isOwner) ({ onDeleteAnnouncement(a.id) }) else null)
                            }
                        }
                        item("bottom") { Spacer(Modifier.height(96.dp)) }
                    }
                } else if (tid == TabId.TRAILS) {
                    val trailVm: simplylucaapps.girocalendar.ui.trails.TrailViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                        factory = simplylucaapps.girocalendar.ui.trails.TrailViewModel.factory(trailRepo, authRepo)
                    )
                    simplylucaapps.girocalendar.ui.trails.TrailsTab(vm = trailVm, onShowOnMap = { tabs.indexOf(TabId.MAP).takeIf { it >= 0 }?.let(goTo) })
                } else if (tid == TabId.MAP) {
                    val mapVm: simplylucaapps.girocalendar.ui.trails.TrailViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                        factory = simplylucaapps.girocalendar.ui.trails.TrailViewModel.factory(trailRepo, authRepo)
                    )
                    simplylucaapps.girocalendar.ui.nav.MapNavTab(vm = mapVm, events = state.events, calendarCode = state.code)
                } else if (tid == TabId.CONTACTS) {
                    val contactsVm: simplylucaapps.girocalendar.ui.contacts.ContactsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                        key = "contacts-" + state.code,
                        factory = simplylucaapps.girocalendar.ui.contacts.ContactsViewModel.factory(
                            remember { simplylucaapps.girocalendar.data.remote.firestore.ContactsRepository() }, state.code, authRepo)
                    )
                    simplylucaapps.girocalendar.ui.contacts.ContactsTab(
                        contactsVm, recents, state.code, canEditGroup = editEnabled,
                        logoKey = state.calendar?.name.orEmpty().ifBlank { state.code },
                        logoColor = state.calendar?.logoColor.orEmpty(),
                        logoData = state.calendar?.logoData.orEmpty(),
                    )
                } else if (tid == TabId.MARKET) {
                    val marketVm: simplylucaapps.girocalendar.ui.market.MarketViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                        key = "market-" + state.code,
                        factory = simplylucaapps.girocalendar.ui.market.MarketViewModel.factory(
                            remember { simplylucaapps.girocalendar.data.remote.firestore.MarketRepository() }, state.code)
                    )
                    simplylucaapps.girocalendar.ui.market.MarketTab(marketVm, state.code, signedIn, state.canEdit, state.myBlock.isNotEmpty())
                } else if (tid == TabId.HOME) {
                    simplylucaapps.girocalendar.ui.home.HomeTab(tabs, hiddenTabs, lockedTabs) { id -> tabs.indexOf(id).takeIf { it >= 0 }?.let(goTo) }
                } else if (tid == TabId.RECORD) {
                    simplylucaapps.girocalendar.ui.nav.NavMapTab(state.events, state.code)
                } else if (tid == TabId.SETTINGS) {
                    settingsPage()
                } else if (tid == TabId.MYRIDES) {
                    // «Il mio spazio» dentro la scheda; toccando un file si apre nella Mappa
                    simplylucaapps.girocalendar.ui.trails.MyStorageContent(
                        onDismiss = {},
                        onOpen = { n, bytes -> simplylucaapps.girocalendar.ui.trails.IncomingGpx.pending = n to bytes },
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                    )
                } else if (tid == TabId.EMERGENCY) {
                    simplylucaapps.girocalendar.ui.emergency.EmergencyTab()
                } else if (tid == TabId.WORKSHOP) {
                    val wsVm: simplylucaapps.girocalendar.ui.workshop.WorkshopViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                        factory = simplylucaapps.girocalendar.ui.workshop.WorkshopViewModel.factory(
                            remember { simplylucaapps.girocalendar.data.remote.firestore.WorkshopRepository() }, authRepo)
                    )
                    simplylucaapps.girocalendar.ui.workshop.WorkshopTab(wsVm, showNewJob, onNewJobDismiss)
                } else if (tid == TabId.EPICS) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (state.epics.isEmpty()) {
                            item("empty") {
                                Box(Modifier.fillParentMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        stringResource(R.string.epic_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                        } else {
                            items(state.epics, key = { "epic-" + it.id }) { ep ->
                                Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                                    EventCard(
                                        e = ep,
                                        isNext = false,
                                        canEdit = editEnabled,
                                        showFieldIcons = showFieldIcons,
                                        onEdit = { onEditEpic(ep) },
                                        onDetail = { onShowEpicDetail(ep) },
                                        hideDateTime = true,
                                    )
                                }
                            }
                        }
                        item("bottom") { Spacer(Modifier.height(96.dp)) }
                    }
                } else {
                    var pastOpen by rememberSaveable(state.code) { mutableStateOf(false) }
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (tid == TabId.UPCOMING) {
                            upcomingItems(false)
                            // le uscite passate stanno in fondo a «Prossime», raccolte dietro una barra «PASSATI» che si apre al tocco
                            if (d.past.isNotEmpty()) {
                                item("h-past-toggle") {
                                    Box(Modifier.padding(top = 14.dp).clickable { pastOpen = !pastOpen }) {
                                        SectionHeader(
                                            stringResource(R.string.section_past), d.past.size,
                                            bg = MaterialTheme.colorScheme.onSurface,
                                            fg = MaterialTheme.colorScheme.surface,
                                            icon = Icons.Default.CheckCircle,
                                        )
                                        Icon(
                                            if (pastOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                                            tint = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.align(Alignment.Center).size(28.dp),
                                        )
                                    }
                                }
                                if (pastOpen) pastItems(false, false)
                            }
                        } else {
                            pastItems(false, false)
                        }
                        item("bottom") { Spacer(Modifier.height(96.dp)) }
                    }
                }
            }
            if (simplylucaapps.girocalendar.data.settings.TabPrefs.bottomBar) {
                BottomTabBar(
                    tabs = tabs, selected = pagerState.currentPage,
                    labelOf = tabLabel,
                    onSelect = goTo,
                )
            }
        }
    }

    if (showQuickInfo) {
        AlertDialog(
            onDismissRequest = { showQuickInfo = false },
            icon = {
                // un secondo tocco sul logo apre «Altre info»
                Box(Modifier.clip(CircleShape).clickable { showQuickInfo = false; showMoreInfo = true }) {
                    simplylucaapps.girocalendar.ui.common.CalendarLogo(
                        state.calendar?.name.orEmpty().ifBlank { state.code },
                        state.calendar?.logoColor.orEmpty(),
                        state.calendar?.logoData.orEmpty(),
                        size = 140.dp,
                    )
                }
            },
            title = { Text(state.calendar?.name?.ifBlank { state.code } ?: state.code) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.calendar?.description?.isNotBlank() == true) {
                        Text(state.calendar.description, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            stringResource(R.string.calendar_no_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.calendar?.ownerEmail?.isNotBlank() == true) {
                        Text(
                            stringResource(R.string.calendar_owner, state.calendar.ownerEmail),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQuickInfo = false }) { Text(stringResource(R.string.action_close)) } },
            dismissButton = { TextButton(onClick = { showQuickInfo = false; showMoreInfo = true }) { Text(stringResource(R.string.calendar_more_info)) } },
        )
    }
    // «Altre info»: le stesse informazioni della "i" nell'elenco dei calendari (sola lettura).
    if (showMoreInfo) {
        simplylucaapps.girocalendar.ui.search.CalendarInfoDialog(
            code = state.code,
            calendar = state.calendar,
            eventCount = state.events.size,
            loading = false,
            isOwner = false,
            onManageEditors = {},
            onDismiss = { showMoreInfo = false },
        )
    }
}

/** Pallino rosso lampeggiante: avvisa owner/editor di proposte in attesa senza dover aprire il menu. */
@Composable
private fun PulsingDot(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "pulsing-dot")
    val alpha by infinite.animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(700),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "pulsing-dot-alpha",
    )
    Box(
        modifier
            .size(10.dp)
            .background(Color(0xFFE53935).copy(alpha = alpha), CircleShape)
            .border(1.dp, Color.White.copy(alpha = alpha), CircleShape),
    )
}

/** Scheda "CALENDARI" (solo modalità swipe): elenco dei calendari aperti di recente, per
 * passare velocemente da uno all'altro senza tornare alla ricerca. Il calendario aperto ora
 * è evidenziato e non cliccabile (nessun motivo di "aprire" quello già visibile). */
@Composable
private fun RecentCalendarsTab(
    recents: List<simplylucaapps.girocalendar.data.settings.RecentCalendar>,
    currentCode: String,
    onOpenCalendar: (String) -> Unit,
    onOpenCurrent: () -> Unit = {},
    onBack: () -> Unit,
    vm: CalendarViewModel,
) {
    val scope = rememberCoroutineScope()
    var editingCode by remember { mutableStateOf<String?>(null) }
    var editingCalendar by remember { mutableStateOf<RideCalendar?>(null) }
    var showEditorsFor by remember { mutableStateOf<String?>(null) }
    // «i»: dettagli del calendario (admin, collaboratori, creazione, statistiche).
    var infoCode by remember { mutableStateOf<String?>(null) }
    var infoCal by remember { mutableStateOf<RideCalendar?>(null) }
    var infoEvents by remember { mutableStateOf<Int?>(null) }
    fun reloadEditing(code: String) {
        scope.launch { editingCalendar = vm.loadCalendarInfo(code) }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(recents, key = { it.code }) { r ->
            val isCurrent = r.code == currentCode
            // Caricate una volta per calendario mostrato, non a ogni ricomposizione: leggere
            // (aggregazione lato server, non scarica documenti) ma comunque una richiesta di
            // rete per riga, quindi vale la pena farla una sola volta finché la tab resta aperta.
            var stats by remember(r.code) { mutableStateOf<simplylucaapps.girocalendar.data.remote.firestore.CalendarStats?>(null) }
            LaunchedEffect(r.code) { stats = runCatching { vm.loadCalendarStats(r.code) }.getOrNull() }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { if (isCurrent) onOpenCurrent() else onOpenCalendar(r.code) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .then(
                            if (isCurrent) {
                                Modifier
                                    .border(2.dp, Color(0xFFFFC107), CircleShape)
                                    .padding(2.dp)
                            } else Modifier
                        ),
                ) {
                    simplylucaapps.girocalendar.ui.common.CalendarLogo(r.code, r.logoColor, r.logoData, size = 36.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name.ifBlank { r.code }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        stats?.let { s -> stringResource(R.string.recent_calendar_line, r.code, s.eventCount, s.openCount) } ?: r.code,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    infoCode = r.code; infoCal = null; infoEvents = stats?.eventCount
                    scope.launch { infoCal = vm.loadCalendarInfo(r.code) }
                }) { Icon(Icons.Default.Info, stringResource(R.string.menu_info), tint = MaterialTheme.colorScheme.primary) }
                if (r.isOwner) {
                    IconButton(onClick = {
                        editingCode = r.code
                        reloadEditing(r.code)
                    }) {
                        Icon(Icons.Default.Edit, stringResource(R.string.action_edit), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
        item("bottom") { Spacer(Modifier.height(96.dp)) }
    }

    infoCode?.let { ic ->
        simplylucaapps.girocalendar.ui.search.CalendarInfoDialog(
            code = ic, calendar = infoCal, eventCount = infoEvents, loading = infoCal == null, isOwner = false,
            onManageEditors = {}, onDismiss = { infoCode = null; infoCal = null },
        )
    }

    val code = editingCode
    val cal = editingCalendar
    if (code != null && cal != null) {
        CalendarEditDialog(
            code = code,
            name = cal.name,
            description = cal.description,
            listed = cal.listed,
            logoColor = cal.logoColor,
            logoData = cal.logoData,
            openCount = cal.openCount,
            onDismiss = { editingCode = null; editingCalendar = null },
            onSave = { name, description, listed ->
                vm.editCalendarInfo(code, name, description, listed) { _, _ -> reloadEditing(code) }
            },
            onSaveLogo = { logoColor, logoData ->
                vm.editCalendarLogo(code, logoColor, logoData) { _, _ -> reloadEditing(code) }
            },
            onManageEditors = { showEditorsFor = code },
            onDelete = {
                vm.deleteCalendarByCode(code) { ok, _ ->
                    if (ok) {
                        editingCode = null; editingCalendar = null
                        if (code == currentCode) onBack()
                    }
                }
            },
        )
    }

    val editorsCode = showEditorsFor
    val editorsCal = editingCalendar
    if (editorsCode != null && editorsCal != null) {
        EditorsDialog(
            editors = editorsCal.editors,
            editorLabels = editorsCal.editorLabels,
            ownerUid = editorsCal.ownerUid,
            onDismiss = { showEditorsFor = null },
            onAdd = { input, onResult ->
                vm.editAddEditor(editorsCode, input) { ok, msg -> onResult(ok, msg); if (ok) reloadEditing(editorsCode) }
            },
            onRemove = { uid ->
                vm.editRemoveEditor(editorsCode, uid) { _, _ -> reloadEditing(editorsCode) }
            },
            onOpen = { vm.backfillEditorLabels(editorsCal) { reloadEditing(editorsCode) } },
        )
    }
}

/** Tutte le schede visibili insieme su due righe bilanciate, larghezza uguale. */
@Composable
private fun TwoRowTabs(selected: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    val vc = LocalGcColors.current
    val perRow = (labels.size + 1) / 2
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
        labels.indices.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { i ->
                    val isSel = i == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(i) }
                            .background(if (isSel) MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f) else Color.Transparent)
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            labels[i].uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) MaterialTheme.colorScheme.secondary else vc.text3,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SwipeTabs(singleRow: Boolean, selected: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    if (!singleRow) { TwoRowTabs(selected, labels, onSelect); return }
    val vc = LocalGcColors.current
    // Barra scorrevole: con molte tab non stanno tutte in larghezza. Segue lo swipe del pager
    // (selected = pagina corrente) tenendo la tab attiva centrata.
    val scroll = androidx.compose.foundation.rememberScrollState()
    val offsets = remember { mutableStateMapOf<Int, Pair<Int, Int>>() } // indice -> (x, larghezza)
    var barWidth by remember { mutableStateOf(0) }
    LaunchedEffect(selected, offsets[selected], barWidth) {
        offsets[selected]?.let { (x, w) -> scroll.animateScrollTo((x + w / 2 - barWidth / 2).coerceAtLeast(0)) }
    }
    Row(
        Modifier.fillMaxWidth()
            .onSizeChanged { barWidth = it.width }
            .horizontalScroll(scroll)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val isSel = i == selected
            Row(
                Modifier
                    .onGloballyPositioned { offsets[i] = it.positionInParent().x.toInt() to it.size.width }
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(i) }
                    .background(if (isSel) MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f) else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isSel) MaterialTheme.colorScheme.secondary else vc.text3,
                    // Il grassetto da selezionato è più largo del testo normale: senza questo,
                    // con la dimensione testo aumentata nelle Preferenze l'etichetta andava a
                    // capo su due righe rompendo l'altezza della barra dei tab.
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val announcementDateFmt = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ITALIAN)

@Composable
private fun AnnouncementBox(
    announcement: simplylucaapps.girocalendar.data.remote.firestore.Announcement,
    forceExpanded: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    var openState by rememberSaveable { mutableStateOf(false) }
    val open = forceExpanded || openState
    val vc = LocalGcColors.current
    val shape = RoundedCornerShape(14.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(shape)
            .background(
                androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.secondary, 0.10f),
                shape,
            )
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.40f), shape),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (forceExpanded) Modifier else Modifier.clickable { openState = !openState })
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Campaign, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(announcement.title.ifBlank { stringResource(R.string.announcement_label) }, style = MaterialTheme.typography.titleMedium)
                if (announcement.createdAtMillis > 0) {
                    Text(
                        announcementDateFmt.format(java.util.Date(announcement.createdAtMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = vc.text3,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val ctx = LocalContext.current
            IconButton(onClick = {
                val txt = listOf(announcement.title, announcement.text).filter { it.isNotBlank() }.joinToString("\n")
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Avviso", txt))
                android.widget.Toast.makeText(ctx, ctx.getString(R.string.info_copied), android.widget.Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, stringResource(R.string.info_copy), Modifier.size(18.dp), tint = vc.text3)
            }
            if (onMoveUp != null) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, "Su", Modifier.size(22.dp), tint = vc.text3)
                }
            }
            if (onMoveDown != null) {
                IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.a_down), Modifier.size(22.dp), tint = vc.text3)
                }
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, stringResource(R.string.action_edit), Modifier.size(18.dp), tint = vc.text3)
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, stringResource(R.string.action_delete), Modifier.size(18.dp), tint = vc.text3)
                }
            }
            if (!forceExpanded) {
                Icon(Icons.Default.ExpandMore, null, tint = vc.text3,
                    modifier = Modifier.rotate(if (open) 180f else 0f))
            }
        }
        if (open) {
            Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                RichText(announcement.text, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, count: Int, bg: Color, fg: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = fg)
        Spacer(Modifier.width(8.dp))
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = fg)
        Spacer(Modifier.weight(1f))
        Text(
            if (count == 1) stringResource(R.string.event_count_one) else stringResource(R.string.event_count_many, count),
            style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.85f))
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalGcColors.current.text3,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ErrorBox(text: String, onBack: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onBack != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
        }
    }
}

@Composable
private fun EventCard(
    e: RideEvent,
    isNext: Boolean,
    canEdit: Boolean,
    showFieldIcons: Boolean,
    onEdit: () -> Unit,
    onDetail: () -> Unit,
    /** true per una scheda "Epici": nasconde la riga data/ora, che qui è sempre vuota. */
    hideDateTime: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (isNext) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val hasBg = e.cardBgData.isNotBlank()
    val linked = e.fromCode.isNotEmpty()
    val linkedInfo = if (linked) rememberLinkedCalInfo(e.fromCode) else null
    val stripeColor = if (linked) (linkedInfo?.dominant?.let { Color(it) } ?: simplylucaapps.girocalendar.ui.common.dotColor(e.fromCode, linkedInfo?.logoColor.orEmpty())) else null

    // Con sfondo foto: testo nero grassetto con text-shadow leggero per restare leggibile
    // su qualunque immagine, esattamente come .has-card-bg su Spaccasassi Eventi.
    val onBgTextColor = androidx.compose.ui.graphics.Color.Black
    val bgTextShadow = if (hasBg) androidx.compose.ui.graphics.Shadow(
        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f),
        blurRadius = 4f,
    ) else null

    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(if (isNext) 1.5.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onDetail)
            .drawWithContent {
                drawContent()
                if (stripeColor != null) drawRect(stripeColor, size = androidx.compose.ui.geometry.Size(7.dp.toPx(), size.height))
                if (e.annullata) {
                    val w = size.width; val h = size.height
                    drawLine(
                        Color(0x80D32F2F), androidx.compose.ui.geometry.Offset(w * 0.02f, h * 0.97f),
                        androidx.compose.ui.geometry.Offset(w * 0.98f, h * 0.03f),
                        strokeWidth = 12.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            },
    ) {
        if (hasBg) {
            // Decodifica fuori dal thread UI e sottocampionata (~720px): prima ogni scheda
            // decodificava la foto intera (~300 KB) durante la composizione e bloccava lo scroll.
            val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, e.cardBgData) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    runCatching {
                        val bytes = android.util.Base64.decode(e.cardBgData, android.util.Base64.DEFAULT)
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        var sample = 1
                        while (bounds.outWidth / (sample * 2) >= 720) sample *= 2
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                    }.getOrNull()
                }
            }
            if (bmp != null) {
                Image(
                    bmp!!, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            // overlay: gradiente chiaro sfumato dall'alto (32%..62%) fino a pieno in basso,
            // così il testo (nero) resta leggibile anche sulla parte più chiara della foto.
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f),
                        0.55f to androidx.compose.ui.graphics.Color.White.copy(alpha = 0.80f),
                        1f to androidx.compose.ui.graphics.Color.White.copy(alpha = 0.94f),
                    )
                )
            )
        }
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        e.titolo.ifBlank { stringResource(R.string.default_event_title) },
                        style = MaterialTheme.typography.titleMedium.copy(shadow = bgTextShadow),
                        color = if (hasBg) onBgTextColor else Color.Unspecified,
                        fontWeight = if (hasBg) FontWeight.Bold else null,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    if (linked) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        simplylucaapps.girocalendar.ui.common.CalendarLogo(e.fromCode, linkedInfo?.logoColor.orEmpty(), linkedInfo?.logoData.orEmpty(), size = 20.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.lk_from, e.fromName.ifBlank { e.fromCode }),
                            style = MaterialTheme.typography.labelMedium.copy(shadow = bgTextShadow), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                        )
                    }
                    if (!hideDateTime) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${e.data}${simplylucaapps.girocalendar.util.EventTime.weekdayLabel(e.data)?.let { " $it" } ?: ""}${if (e.ora.isNotBlank()) "  ·  ${e.ora}" else ""}${if (e.durataGiorni > 1) "  ·  ${stringResource(R.string.d_duration_days, e.durataGiorni)}" else ""}",
                            style = MaterialTheme.typography.labelMedium.copy(shadow = bgTextShadow),
                            color = if (hasBg) onBgTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (hasBg) FontWeight.Bold else null,
                        )
                    }
                }
                if (e.difficolta.isNotBlank() && !linked) {
                    DifficultyBadge(e.difficolta)
                }
                if (canEdit) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, stringResource(R.string.action_edit), modifier = Modifier.size(18.dp),
                            tint = if (hasBg) onBgTextColor else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (e.localita.isNotBlank() || (e.ritrovo.isNotBlank() && !e.ritrovo.startsWith("http"))) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showFieldIcons) {
                        Icon(Icons.Default.Place, null, modifier = Modifier.size(14.dp),
                            tint = if (hasBg) onBgTextColor else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                    }
                    val ritrovoText = if (e.ritrovo.startsWith("http")) "" else e.ritrovo   // il link di ritrovo («Parcheggio») sta solo nel dettaglio
                    Text(
                        listOfNotNull(e.localita.takeIf { it.isNotBlank() }, ritrovoText.takeIf { it.isNotBlank() })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall.copy(shadow = bgTextShadow),
                        color = if (hasBg) onBgTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (hasBg) FontWeight.Bold else null,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            if (e.proponente.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showFieldIcons) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(14.dp),
                            tint = if (hasBg) onBgTextColor else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(e.proponente, style = MaterialTheme.typography.bodySmall.copy(shadow = bgTextShadow),
                        color = if (hasBg) onBgTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (hasBg) FontWeight.Bold else null)
                }
            }
            if (e.linkPercorso.isNotBlank() || e.link2.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showFieldIcons) {
                        Icon(Icons.Default.Link, null, modifier = Modifier.size(14.dp),
                            tint = if (hasBg) onBgTextColor else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    val routeLabel = simplylucaapps.girocalendar.ui.common.platformNameForLink(e.linkPercorso)
                        ?.let { stringResource(R.string.editor_field_route_link_named, it) }
                        ?: stringResource(R.string.field_route)
                    val link2Label = simplylucaapps.girocalendar.ui.common.platformNameForLink(e.link2)
                        ?: stringResource(R.string.field_other_link)
                    Text(
                        listOfNotNull(
                            routeLabel.takeIf { e.linkPercorso.isNotBlank() },
                            link2Label.takeIf { e.link2.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall.copy(shadow = bgTextShadow),
                        color = if (hasBg) onBgTextColor else MaterialTheme.colorScheme.primary,
                        fontWeight = if (hasBg) FontWeight.Bold else null,
                    )
                }
            }
        }
    }
}

/** Colore per tipo di icona dell'Officina: tocca un'icona, poi scegli dalla tavolozza (∅ = predefinito). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkshopIconColorsPicker() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = simplylucaapps.girocalendar.data.settings.WorkshopIconPrefs
    var editIcon by remember { mutableStateOf<simplylucaapps.girocalendar.ui.workshop.JobIcon?>(null) }
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.pref_workshop_icon_colors), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            simplylucaapps.girocalendar.ui.workshop.JobIcon.entries.forEach { ji ->
                val sel = editIcon == ji
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .border(1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { editIcon = if (sel) null else ji },
                    contentAlignment = Alignment.Center,
                ) { Icon(ji.icon, ji.labelIt, Modifier.size(20.dp), tint = prefs.colorOf(ji.id)?.let(::Color) ?: LocalContentColor.current) }
            }
        }
        editIcon?.let { ji ->
            Text(ji.labelIt, style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val cur = prefs.colorOf(ji.id)
                listOf<Int?>(null, 0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(),
                    0xFF00897B.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFFD81B60.toInt(),
                    0xFF6D4C41.toInt(), 0xFF757575.toInt()).forEach { c ->
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .background(c?.let(::Color) ?: Color.Transparent)
                            .border(if (c == cur) 3.dp else 1.dp,
                                if (c == cur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { prefs.setIconColor(ctx, ji.id, c) },
                        contentAlignment = Alignment.Center,
                    ) { if (c == null) Text("∅", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}


/** Scheda "MESE": griglia del mese (lun-dom) con, in ogni giorno, le uscite in piccolo
 * (ora + titolo). Sfondo del giorno = immagine di un'uscita di quel giorno (la prima per
 * orario che ne ha una). Frecce per cambiare mese; si parte dal mese corrente. */
@Composable
private fun MonthTab(events: List<RideEvent>, onShowDetail: (RideEvent) -> Unit) {
    var offset by rememberSaveable { mutableStateOf(0) }
    val cal = remember(offset) {
        java.util.Calendar.getInstance().apply { set(java.util.Calendar.DAY_OF_MONTH, 1); add(java.util.Calendar.MONTH, offset) }
    }
    val year = cal.get(java.util.Calendar.YEAR)
    val month = cal.get(java.util.Calendar.MONTH)
    val title = remember(offset) {
        java.text.SimpleDateFormat("LLLL yyyy", java.util.Locale.getDefault()).format(cal.time)
            .replaceFirstChar { it.uppercase() }
    }
    val byDay = remember(events, year, month) {
        events.filter { !it.annullata }.mapNotNull { e ->
            val st = EventTime.startMillis(e) ?: return@mapNotNull null
            val c = java.util.Calendar.getInstance().apply { timeInMillis = st }
            if (c.get(java.util.Calendar.YEAR) == year && c.get(java.util.Calendar.MONTH) == month)
                c.get(java.util.Calendar.DAY_OF_MONTH) to (st to e) else null
        }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.sortedBy { it.first }.map { it.second } }
    }
    val daysInMonth = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val lead = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7 // lunedì = 0
    val rows = (lead + daysInMonth + 6) / 7
    val today = java.util.Calendar.getInstance()
    val isThisMonth = today.get(java.util.Calendar.YEAR) == year && today.get(java.util.Calendar.MONTH) == month
    val wd = remember { java.text.DateFormatSymbols.getInstance().shortWeekdays }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { offset-- }) { Text("‹", style = MaterialTheme.typography.titleLarge) }
            Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { offset++ }) { Text("›", style = MaterialTheme.typography.titleLarge) }
        }
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    wd[(i + 1) % 7 + 1].uppercase().take(3), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).height(84.dp).padding(1.dp)) {
                        if (day in 1..daysInMonth) {
                            MonthCell(day, byDay[day].orEmpty(), isThisMonth && day == today.get(java.util.Calendar.DAY_OF_MONTH), onShowDetail)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun MonthCell(day: Int, evs: List<RideEvent>, isToday: Boolean, onShowDetail: (RideEvent) -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    val bgData = evs.firstOrNull { it.cardBgData.isNotBlank() }?.cardBgData.orEmpty()
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, bgData) {
        value = if (bgData.isBlank()) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                val bytes = android.util.Base64.decode(bgData, android.util.Base64.DEFAULT)
                // sottocampionata: la cella è minuscola, inutile tenere in RAM l'immagine intera
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                    android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        Modifier.fillMaxSize().clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(if (isToday) 1.5.dp else 0.5.dp,
                if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape),
    ) {
        if (bmp != null) {
            Image(bmp!!, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.6f)))
        }
        Column(Modifier.padding(2.dp)) {
            Text(
                day.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = if (bmp != null) Color.Black else MaterialTheme.colorScheme.onSurface,
            )
            evs.take(3).forEach { e ->
                Text(
                    e.titolo,
                    fontSize = 8.sp, lineHeight = 9.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = if (bmp != null) Color.Black else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (bmp != null) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().clickable { onShowDetail(e) },
                )
            }
        }
    }
}


/** Preferenze → Cache mappa: dimensione massima della cache su disco delle tile + spazio usato + svuota. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapCacheSizeRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cache = simplylucaapps.girocalendar.util.MapTileCache
    var maxMb by remember { mutableStateOf(cache.maxMb(ctx)) }
    var used by remember { mutableStateOf(cache.usedBytes(ctx)) }
    fun mbLabel(mb: Int) = if (mb >= 1000) "1 GB" else "$mb MB"
    PreferenceDropdownRow(
        label = stringResource(R.string.p_cache_size),
        selectedLabel = mbLabel(maxMb),
        options = cache.SIZE_OPTIONS_MB.map { it.toString() to mbLabel(it) },
        onSelect = { v -> val mb = v.toInt(); cache.setMaxMb(ctx, mb); maxMb = mb; used = cache.usedBytes(ctx) },
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.p_cache_used, "%.1f".format(java.util.Locale.getDefault(), used / 1048576.0), maxMb),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        TextButton(onClick = { cache.clear(ctx); used = cache.usedBytes(ctx) }) { Text(stringResource(R.string.p_cache_clear)) }
    }
}

/** Un solo suggerimento automatico per avvio dell'app. */
private object TipsSession { var shown = false }


private fun tabIcon(id: TabId): androidx.compose.ui.graphics.vector.ImageVector = when (id) {
    TabId.HOME -> androidx.compose.material.icons.Icons.Filled.Home
    TabId.CALENDARS -> androidx.compose.material.icons.Icons.Filled.CalendarMonth
    TabId.UPCOMING -> androidx.compose.material.icons.Icons.Filled.Event
    TabId.PAST -> androidx.compose.material.icons.Icons.Filled.History
    TabId.MONTH -> androidx.compose.material.icons.Icons.Filled.CalendarViewMonth
    TabId.INFO -> androidx.compose.material.icons.Icons.Filled.Campaign
    TabId.TRAILS -> androidx.compose.material.icons.Icons.Filled.Terrain
    TabId.MAP -> androidx.compose.material.icons.Icons.Filled.Map
    TabId.EPICS -> androidx.compose.material.icons.Icons.Filled.EmojiEvents
    TabId.CONTACTS -> androidx.compose.material.icons.Icons.Filled.Contacts
    TabId.WORKSHOP -> androidx.compose.material.icons.Icons.Filled.Build
    TabId.RECORD -> androidx.compose.material.icons.Icons.Filled.FiberManualRecord
    TabId.SETTINGS -> androidx.compose.material.icons.Icons.Filled.Settings
    TabId.MYRIDES -> androidx.compose.material.icons.Icons.Filled.Folder
    TabId.EMERGENCY -> androidx.compose.material.icons.Icons.Filled.Warning
    TabId.MARKET -> androidx.compose.material.icons.Icons.Filled.Storefront
}

/** Barra schede in basso: le prime 4 dell'ordine scelto (Preferenze → Tabs visibili) + «Altro» con le restanti. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BottomTabBar(tabs: List<TabId>, selected: Int, labelOf: (TabId) -> String, onSelect: (Int) -> Unit) {
    val maxMain = 4
    val needMore = tabs.size > maxMain + 1
    val mainCount = if (needMore) maxMain else tabs.size
    var moreOpen by remember { mutableStateOf(false) }
    androidx.compose.material3.NavigationBar(windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)) {
        for (i in 0 until mainCount) {
            NavigationBarItem(
                selected = selected == i,
                onClick = { onSelect(i) },
                icon = { Icon(tabIcon(tabs[i]), contentDescription = null) },
                label = { Text(labelOf(tabs[i]), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            )
        }
        if (needMore) {
            NavigationBarItem(
                selected = selected >= mainCount,
                onClick = { moreOpen = true },
                icon = { Icon(androidx.compose.material.icons.Icons.Filled.MoreHoriz, contentDescription = null) },
                label = { Text(if (selected >= mainCount) labelOf(tabs[selected]) else stringResource(R.string.tab_more), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            )
        }
    }
    if (moreOpen) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { moreOpen = false }) {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
                for (i in mainCount until tabs.size) {
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(labelOf(tabs[i])) },
                        leadingContent = { Icon(tabIcon(tabs[i]), contentDescription = null) },
                        modifier = Modifier.clickable { moreOpen = false; onSelect(i) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = if (selected == i) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
        }
    }
}
