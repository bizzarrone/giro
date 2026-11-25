package simplylucaapps.girocalendar

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import simplylucaapps.girocalendar.data.remote.auth.GoogleAuthRepository
import simplylucaapps.girocalendar.data.remote.firestore.CalendarRepository
import simplylucaapps.girocalendar.data.settings.AppSettings
import simplylucaapps.girocalendar.data.settings.BootPrefs
import simplylucaapps.girocalendar.ui.calendar.CalendarScreen
import simplylucaapps.girocalendar.ui.calendar.CalendarViewModel
import simplylucaapps.girocalendar.ui.createcalendar.CreateCalendarScreen
import simplylucaapps.girocalendar.ui.createcalendar.CreateCalendarViewModel
import simplylucaapps.girocalendar.ui.search.SearchScreen
import simplylucaapps.girocalendar.ui.search.SearchViewModel
import simplylucaapps.girocalendar.ui.theme.GiroCalendarTheme

// AppCompatActivity (non ComponentActivity): AppCompatDelegate.setApplicationLocales()
// richiede che l'Activity estenda AppCompatActivity per applicare davvero il cambio lingua
// alla UI corrente — con una ComponentActivity pura la chiamata non ha effetto pratico,
// anche su Android 13+ col LocaleConfig dichiarato (verificato: risultava sempre "system").
class MainActivity : AppCompatActivity() {

    private val calendarRepo by lazy { CalendarRepository() }
    private val authRepo by lazy { GoogleAuthRepository() }
    private val trailRepo by lazy { simplylucaapps.girocalendar.data.remote.firestore.TrailRepository() }

    // Applica il fontScale alla Configuration dell'Activity (non solo a un LocalDensity di
    // Compose): i Dialog creano una nuova Android Window che eredita la density da qui, non
    // da un CompositionLocalProvider impostato più in basso nella composizione.
    override fun attachBaseContext(newBase: Context?) {
        val base = newBase ?: return super.attachBaseContext(newBase)
        val scale = BootPrefs.textScale(base)
        val config = Configuration(base.resources.configuration).apply { fontScale *= scale }
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        showOverLockIfEmergency(intent)
        handleIncomingTrack(intent)
    }

    /** File GPX/KML/GeoJSON/TCX aperto o condiviso da un'altra app: lo si legge subito (il permesso sull'URI è temporaneo) e
     * la scheda Mappa lo mostra come "Tuo GPX". */
    private fun handleIncomingTrack(i: android.content.Intent?) {
        val uri: android.net.Uri = when (i?.action) {
            android.content.Intent.ACTION_VIEW -> i.data?.takeIf { it.scheme == "content" || it.scheme == "file" }
            android.content.Intent.ACTION_SEND ->
                if (android.os.Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
                else @Suppress("DEPRECATION") i.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
            else -> null
        } ?: return
        runCatching {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
            if (bytes.size > 20 * 1024 * 1024) return
            val name = runCatching {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull() ?: uri.lastPathSegment ?: "GPX"
            simplylucaapps.girocalendar.ui.trails.IncomingGpx.pending = name.substringBeforeLast('.') to bytes
        }
    }

    /** Allarme emergenza: l'app si apre anche con schermo bloccato/spento per poter annullare. */
    private fun showOverLockIfEmergency(i: android.content.Intent?) {
        if (i?.getBooleanExtra("emergency", false) == true) { setShowWhenLocked(true); setTurnScreenOn(true) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        simplylucaapps.girocalendar.util.Str.ctx = this
        // Cache Firestore su disco più capiente (default 100 MB, con eviction): le card evento contengono
        // immagini base64, e con una cache piccola gli eventi vengono scartati e si rimane in attesa del
        // server. Con 500 MB il calendario si apre subito dai dati locali e poi si aggiorna da solo.
        // Deve essere impostata prima del primo uso di Firestore.
        runCatching {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().firestoreSettings = com.google.firebase.firestore.firestoreSettings {
                setLocalCacheSettings(com.google.firebase.firestore.persistentCacheSettings { setSizeBytes(500L * 1024 * 1024) })
            }
        }
        enableEdgeToEdge()
        runCatching { simplylucaapps.girocalendar.ui.trails.LastRide.maintain(this) }
        simplylucaapps.girocalendar.data.settings.WorkshopIconPrefs.load(this)
        simplylucaapps.girocalendar.ui.emergency.EmergencyPrefs.load(this)
        simplylucaapps.girocalendar.data.settings.TabPrefs.load(this)
        if (simplylucaapps.girocalendar.ui.emergency.EmergencyPrefs.armed) runCatching { simplylucaapps.girocalendar.ui.emergency.EmergencyService.start(this) }
        showOverLockIfEmergency(intent)
        handleIncomingTrack(intent)

        val data = intent?.data
        var deepLinkCode: String? = null
        if (data != null && data.scheme == "girocalendar" && data.host == "c") {
            deepLinkCode = data.lastPathSegment?.uppercase()
        } else if (data != null && data.scheme == "https" && data.host == "girocalendar.pages.dev") {
            // Link web: https://girocalendar.pages.dev/#/c/<CODE>[/<eventId>]
            val parts = data.fragment.orEmpty().trim('/').split('/')
            if (parts.size >= 2 && parts[0] == "c" && parts[1].isNotBlank()) {
                deepLinkCode = parts[1].uppercase()
                pendingEventId = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
            }
        }

        // Riapri l'ultimo calendario visto (SharedPreferences, lettura sincrona sicura).
        val startCode = deepLinkCode ?: BootPrefs.lastCode(this)
        val bootTheme = BootPrefs.themeId(this)
        val bootFontStyle = BootPrefs.fontStyleId(this)

        // Applica la lingua scelta in Preferenze (AppCompat la persiste anche da sé nelle
        // run successive, ma la riapplichiamo qui esplicitamente per restare in sync con
        // BootPrefs, l'unica fonte di verità che l'utente vede nel dialog Preferenze).
        val bootLanguage = BootPrefs.languageId(this)
        AppCompatDelegate.setApplicationLocales(
            if (bootLanguage == "system") LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(bootLanguage)
        )

        setContent {
            val context = LocalContext.current
            val settings = remember { AppSettings(context.applicationContext) }
            val themeId by settings.themeId.collectAsState(initial = bootTheme)
            val fontStyleId by settings.fontStyleId.collectAsState(initial = bootFontStyle)
            val difficultyScale by settings.difficultyScale.collectAsState(initial = "sts")

            GiroCalendarTheme(themeId = themeId, fontStyleId = fontStyleId, difficultyScale = difficultyScale) {
                simplylucaapps.girocalendar.util.UpdatePrompt()
                AppNav(
                    startCode = startCode,
                    calendarRepo = calendarRepo,
                    authRepo = authRepo,
                    trailRepo = trailRepo,
                    settings = settings,
                    onTextSizeApplied = { recreate() },
                )
            }
        }
    }
}

/** Evento da aprire appena il calendario di destinazione di un link web è caricato. */
internal var pendingEventId: String? = null

private object Routes {
    const val SEARCH = "search"
    const val CREATE = "create"
    const val CALENDAR = "calendar/{code}"
    fun calendar(code: String) = "calendar/$code"
}

@Composable
private fun AppNav(
    startCode: String?,
    calendarRepo: CalendarRepository,
    authRepo: GoogleAuthRepository,
    trailRepo: simplylucaapps.girocalendar.data.remote.firestore.TrailRepository,
    settings: AppSettings,
    onTextSizeApplied: () -> Unit,
) {
    val nav = rememberNavController()
    val navContext = LocalContext.current
    val start = if (startCode != null) Routes.calendar(startCode) else Routes.SEARCH
    var pendingCalendarName by remember { mutableStateOf("") }

    // Registra (o riconferma) email->uid dell'utente loggato, così un owner può invitare
    // questo utente come collaboratore scrivendone l'email invece del codice utente.
    val currentUser by authRepo.observeUser().collectAsState(initial = null)
    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null) calendarRepo.registerEmailIndex()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NavHost(navController = nav, startDestination = start) {

            composable(Routes.SEARCH) {
                val vm: SearchViewModel =
                    viewModel(factory = SearchViewModel.factory(calendarRepo, authRepo, settings))
                val scope = rememberCoroutineScope()
                val themeId by settings.themeId.collectAsState(initial = simplylucaapps.girocalendar.ui.theme.DEFAULT_THEME_ID)
                SearchScreen(
                    vm = vm,
                    onOpenCode = { nav.navigate(Routes.calendar(it)) },
                    onCreateCalendar = { name -> pendingCalendarName = name; nav.navigate(Routes.CREATE) },
                    isDark = simplylucaapps.girocalendar.ui.theme.themeById(themeId).dark,
                    onToggleDark = {
                        scope.launch { settings.setThemeId(simplylucaapps.girocalendar.ui.theme.oppositeBrightness(themeId)) }
                    },
                )
            }

            composable(Routes.CREATE) {
                val vm: CreateCalendarViewModel =
                    viewModel(factory = CreateCalendarViewModel.factory(calendarRepo, authRepo, settings))
                LaunchedEffect(Unit) {
                    if (pendingCalendarName.isNotBlank()) vm.onName(pendingCalendarName)
                }
                CreateCalendarScreen(
                    vm = vm,
                    onCreated = { code -> nav.navigate(Routes.calendar(code)) { popUpTo(Routes.SEARCH) } },
                    onBack = { nav.popBackStack() },
                )
            }

            composable(Routes.CALENDAR) { backStack ->
                val code = backStack.arguments?.getString("code").orEmpty()
                val vm: CalendarViewModel =
                    viewModel(key = "cal-$code", factory = CalendarViewModel.factory(calendarRepo, settings, code))
                val scope = rememberCoroutineScope()
                val currentTheme by settings.themeId.collectAsState(initial = simplylucaapps.girocalendar.ui.theme.DEFAULT_THEME_ID)
                val textScale by settings.textScale.collectAsState(initial = 1f)
                val showFieldIcons by settings.showFieldIcons.collectAsState(initial = true)
                val audioAutoplay by settings.audioAutoplay.collectAsState(initial = true)
                val fontStyleId by settings.fontStyleId.collectAsState(initial = "classic")
                // Fonte di verità = BootPrefs (scritto in modo sincrono): il DataStore può restare
                // indietro perché setApplicationLocales ricrea l'Activity prima che la sua
                // scrittura asincrona finisca, e il dialog mostrava ancora "Sistema".
                var languageId by remember { mutableStateOf(BootPrefs.languageId(navContext)) }
                val difficultyScale by settings.difficultyScale.collectAsState(initial = "sts")
                val savedName by settings.myName.collectAsState(initial = "")
                val suggestedName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: savedName
                val recents by settings.recents.collectAsState(initial = emptyList())
                CalendarScreen(
                    vm = vm,
                    trailRepo = trailRepo,
                    authRepo = authRepo,
                    recents = recents,
                    pinned = settings.pinned.collectAsState(initial = emptyList()).value,
                    currentThemeId = currentTheme,
                    onThemeChange = { id -> scope.launch { settings.setThemeId(id) } },
                    currentTextScale = textScale,
                    onTextScaleChange = { scale ->
                        // Stesso bug (e stesso fix) già visto per la lingua: onTextSizeApplied
                        // chiama recreate() in modo sincrono e immediato, che rilegge subito
                        // BootPrefs.textScale in attachBaseContext — se la scrittura di BootPrefs
                        // restasse solo dentro la coroutine di settings.setTextScale, la
                        // ricreazione avverrebbe prima che sia stata eseguita, applicando ancora
                        // il fontScale precedente (il DataStore si aggiorna comunque, quindi il
                        // valore "vero" salvato risulta corretto ma mai applicato visivamente).
                        BootPrefs.setTextScale(navContext, scale)
                        scope.launch { settings.setTextScale(scale) }
                        onTextSizeApplied()
                    },
                    showFieldIcons = showFieldIcons,
                    onShowFieldIconsChange = { on -> scope.launch { settings.setShowFieldIcons(on) } },
                    audioAutoplay = audioAutoplay,
                    onAudioAutoplayChange = { on -> scope.launch { settings.setAudioAutoplay(on) } },
                    fontStyleId = fontStyleId,
                    onFontStyleChange = { id -> scope.launch { settings.setFontStyleId(id) } },
                    languageId = languageId,
                    onLanguageChange = { id ->
                        // BootPrefs va scritto in modo sincrono PRIMA di setApplicationLocales:
                        // quest'ultimo può ricreare l'Activity immediatamente (cambio di
                        // configurazione), e onCreate rilegge BootPrefs.languageId per
                        // riapplicare la lingua — se la scrittura fosse solo dentro la
                        // coroutine di setLanguageId, la ricreazione potrebbe avvenire prima
                        // che sia stata eseguita, mostrando ancora il valore precedente.
                        BootPrefs.setLanguageId(navContext, id)
                        languageId = id
                        scope.launch { settings.setLanguageId(id) }
                        AppCompatDelegate.setApplicationLocales(
                            if (id == "system") LocaleListCompat.getEmptyLocaleList()
                            else LocaleListCompat.forLanguageTags(id)
                        )
                    },
                    difficultyScale = difficultyScale,
                    onDifficultyScaleChange = { scale -> scope.launch { settings.setDifficultyScale(scale) } },
                    suggestedName = suggestedName,
                    onSaveMyName = { name -> scope.launch { settings.setMyName(name) } },
                    onOpenOther = {
                        nav.navigate(Routes.SEARCH) { launchSingleTop = true }
                    },
                    onOpenCalendar = { newCode ->
                        nav.navigate(Routes.calendar(newCode)) {
                            popUpTo(Routes.CALENDAR) { inclusive = true }
                        }
                    },
                    // Va alla SearchScreen (Account/Stai usando/Calendari pubblici/Crea…),
                    // non direttamente a "Crea nuovo calendario": l'utente potrebbe voler
                    // invece scegliere un pubblico o aggiungerne uno esistente per codice.
                    onCreateCalendar = { nav.navigate(Routes.SEARCH) { launchSingleTop = true } },
                    onBack = {
                        if (!nav.popBackStack()) {
                            nav.navigate(Routes.SEARCH) { popUpTo(0) }
                        }
                    },
                )
            }
        }
    }
}
