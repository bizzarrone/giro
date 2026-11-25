package simplylucaapps.girocalendar.ui.market

import simplylucaapps.girocalendar.ui.common.CompactTextField
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.data.remote.firestore.Listing
import simplylucaapps.girocalendar.data.remote.firestore.MARKET_CATEGORIES
import simplylucaapps.girocalendar.data.remote.firestore.MARKET_MAX_PHOTOS
import simplylucaapps.girocalendar.data.remote.firestore.MarketRepository
import simplylucaapps.girocalendar.util.MarketImage

class MarketViewModel(private val repo: MarketRepository, private val code: String) : ViewModel() {
    /** Annunci del calendario + quelli visibili in tutti i calendari; i venduti in fondo, poi i più recenti. */
    val items: StateFlow<List<Listing>> = combine(repo.observeCalendar(code), repo.observeGlobal()) { a, b ->
        (a + b).distinctBy { it.id }.sortedWith(compareBy<Listing> { it.sold }.thenByDescending { it.createdAtMillis })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myUid: String? get() = repo.uid
    val myName: String get() = repo.displayName
    suspend fun loadPhotos(l: Listing): List<String> = repo.loadPhotos(l)
    fun save(l: Listing, photos: List<String>?, previousScope: String?, onResult: (Boolean) -> Unit) { viewModelScope.launch { onResult(repo.save(l, photos, previousScope).isSuccess) } }
    fun setSold(l: Listing) { viewModelScope.launch { repo.setSold(l, !l.sold) } }
    fun delete(l: Listing) { viewModelScope.launch { repo.delete(l) } }

    companion object {
        fun factory(repo: MarketRepository, code: String): ViewModelProvider.Factory =
            viewModelFactory { initializer { MarketViewModel(repo, code) } }
    }
}

private fun catLabelRes(c: String) = when (c) {
    "bike" -> R.string.mk_cat_bike; "parts" -> R.string.mk_cat_parts; "clothing" -> R.string.mk_cat_clothing; else -> R.string.mk_cat_other
}

@Composable
private fun PhotoImage(base64: String, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop, sample: Int = 1) {
    val bmp = remember(base64, sample) { MarketImage.fromBase64(base64, sample)?.asImageBitmap() }
    if (bmp != null) Image(bmp, null, modifier, contentScale = scale)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
}

/** Finestra compatta: poco margine, angoli arrotondati, contenuto che scorre. */
@Composable
private fun CompactDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 10.dp).heightIn(max = 640.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(10.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
        }
    }
}

/** Scheda MERCATINO: annunci con foto, descrizione e contatto, per questo calendario o per tutti. */
@Composable
fun MarketTab(vm: MarketViewModel, code: String, signedIn: Boolean, isEditor: Boolean, blocked: Boolean) {
    val items by vm.items.collectAsState()
    val context = LocalContext.current
    var filter by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<Listing?>(null) }
    var editing by remember { mutableStateOf<Listing?>(null) }
    var creating by remember { mutableStateOf(false) }
    val msgSignIn = stringResource(R.string.mk_sign_in)
    val msgBlocked = stringResource(R.string.bl_blocked_snack)
    val shown = items.filter { filter == null || it.category == filter }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.mk_all), maxLines = 1, style = MaterialTheme.typography.labelMedium) }) }
                items(MARKET_CATEGORIES) { c -> FilterChip(selected = filter == c, onClick = { filter = if (filter == c) null else c }, label = { Text(stringResource(catLabelRes(c)), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium) }) }
            }
            if (shown.isEmpty()) Text(stringResource(R.string.mk_empty), Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(contentPadding = PaddingValues(10.dp, 2.dp, 10.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { l -> ListingCard(l) { detail = l } }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { if (!signedIn) Toast.makeText(context, msgSignIn, Toast.LENGTH_SHORT).show() else if (blocked) Toast.makeText(context, msgBlocked, Toast.LENGTH_SHORT).show() else creating = true },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.mk_new)) },
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    detail?.let { l ->
        val mine = vm.myUid != null && vm.myUid == l.ownerUid
        ListingDetailDialog(
            l, vm, canEdit = mine && !blocked, canDelete = mine || (l.scope == "calendar" && isEditor),
            onEdit = { detail = null; editing = l },
            onToggleSold = { vm.setSold(l); detail = null },
            onDelete = { vm.delete(l); detail = null },
            onDismiss = { detail = null },
        )
    }
    if (creating || editing != null) {
        val base = editing
        ListingEditDialog(
            initial = base ?: Listing(fromCode = code), vm = vm,
            onDismiss = { creating = false; editing = null },
            onSave = { l, photos ->
                vm.save(l, photos, base?.scope) { ok ->
                    if (ok) { creating = false; editing = null } else Toast.makeText(context, context.getString(R.string.k_save_failed), Toast.LENGTH_LONG).show()
                }
            },
        )
    }
}

@Composable
private fun ListingCard(l: Listing, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(9.dp))) {
                if (l.coverB64.isNotBlank()) PhotoImage(l.coverB64, Modifier.fillMaxSize(), sample = 2)
                else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("🚲", style = MaterialTheme.typography.headlineSmall) }
                if (l.sold) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.mk_sold), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(l.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (l.price.isNotBlank()) Text(l.price, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (l.ownerName.isNotBlank()) Text("👤 ${l.ownerName}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(catLabelRes(l.category)) + " · " + stringResource(if (l.scope == "all") R.string.mk_scope_all_short else R.string.mk_scope_cal_short),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun waNumber(raw: String): String? {
    var s = raw.trim(); val plus = s.startsWith("+"); s = s.filter { it.isDigit() }
    if (s.length < 6) return null
    return if (plus) s else if (s.startsWith("00")) s.drop(2) else "39$s"
}

@Composable
private fun ListingDetailDialog(
    l: Listing, vm: MarketViewModel, canEdit: Boolean, canDelete: Boolean,
    onEdit: () -> Unit, onToggleSold: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var photos by remember(l.id) { mutableStateOf<List<String>>(emptyList()) }
    var viewerAt by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(l.id) { photos = vm.loadPhotos(l) }
    val shownPhotos = photos.ifEmpty { listOfNotNull(l.coverB64.takeIf { it.isNotBlank() }) }
    CompactDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(l.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, stringResource(R.string.action_close), modifier = Modifier.size(18.dp)) }
        }
        if (shownPhotos.isNotEmpty()) {
            val pager = rememberPagerState(pageCount = { shownPhotos.size })
            HorizontalPager(pager, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(9.dp))) { i ->
                PhotoImage(shownPhotos[i], Modifier.fillMaxSize().clickable { viewerAt = i }, ContentScale.Fit)
            }
            if (shownPhotos.size > 1) Text("${pager.currentPage + 1} / ${shownPhotos.size}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        if (l.ownerName.isNotBlank()) Text("👤 ${l.ownerName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (l.price.isNotBlank()) Text(l.price, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            if (l.sold) Text(stringResource(R.string.mk_sold), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        if (l.description.isNotBlank()) androidx.compose.foundation.text.selection.SelectionContainer { Text(l.description, style = MaterialTheme.typography.bodySmall) }
        // contatti: telefono (con WhatsApp se indicato), email, altro; per gli annunci vecchi si ricava dal testo libero
        val legacy = l.phone.isBlank() && l.email.isBlank() && l.other.isBlank()
        val phone = if (legacy) l.contact.takeIf { waNumber(it) != null && '@' !in it }.orEmpty() else l.phone
        val email = if (legacy) l.contact.split(' ').firstOrNull { '@' in it }.orEmpty() else l.email
        val other = if (legacy) (if (phone.isBlank() && email.isBlank()) l.contact else "") else l.other
        val wa = waNumber(phone)
        val pad = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
        androidx.compose.foundation.text.selection.SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.mk_contact), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (phone.isNotBlank()) Text("📞 $phone", style = MaterialTheme.typography.bodyMedium)
                if (email.isNotBlank()) Text("✉ $email", style = MaterialTheme.typography.bodyMedium)
                if (other.isNotBlank()) Text(other, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (wa != null) OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+$wa"))) } }, contentPadding = pad) { Text(stringResource(R.string.mk_call), style = MaterialTheme.typography.labelMedium) }
            if (wa != null && (l.whatsapp || legacy)) OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$wa"))) } }, contentPadding = pad) { Text("WhatsApp", style = MaterialTheme.typography.labelMedium) }
            if (email.isNotBlank()) OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))) } }, contentPadding = pad) { Text("Email", style = MaterialTheme.typography.labelMedium) }
        }
        run {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(0.dp), itemVerticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = {
                    val url = "https://girocalendar.pages.dev/annuncio.html?" + (if (l.scope == "all") "" else "c=${l.fromCode}&") + "id=${l.id}"
                    val text = "🚲 ${l.title}" + (if (l.price.isNotBlank()) " — ${l.price}" else "") + (if (l.ownerName.isNotBlank()) "\n👤 ${l.ownerName}" else "") + "\n$url"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, context.getString(R.string.action_share)))
                }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Share, stringResource(R.string.action_share), modifier = Modifier.size(18.dp)) }
                IconButton(onClick = {
                    val url = "https://girocalendar.pages.dev/annuncio.html?" + (if (l.scope == "all") "" else "c=${l.fromCode}&") + "id=${l.id}"
                    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("link", url))
                    Toast.makeText(context, context.getString(R.string.mk_link_copied), Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Link, stringResource(R.string.mk_copy_link), modifier = Modifier.size(18.dp)) }
                if (canEdit) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Edit, stringResource(R.string.action_edit), modifier = Modifier.size(18.dp)) }
                    TextButton(onClick = onToggleSold, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(stringResource(if (l.sold) R.string.mk_unmark_sold else R.string.mk_mark_sold), style = MaterialTheme.typography.labelMedium) }
                }
                if (canDelete) IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
            }
        }
        // categoria, ambito e autore: in fondo, in piccolo
        Text(
            stringResource(catLabelRes(l.category)) + " · " + stringResource(if (l.scope == "all") R.string.mk_scope_all else R.string.mk_scope_cal),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    viewerAt?.let { PhotoViewer(shownPhotos, it) { viewerAt = null } }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.mk_delete_q)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Foto a tutto schermo: scorri tra le foto, pizzica per ingrandire, doppio tocco per tornare. */
@Composable
private fun PhotoViewer(photos: List<String>, start: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val pager = rememberPagerState(initialPage = start, pageCount = { photos.size })
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = true) { i ->
                var scale by remember { mutableFloatStateOf(1f) }
                var off by remember { mutableStateOf(Offset.Zero) }
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                off = if (scale > 1f) off + pan else Offset.Zero
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = 1f; off = Offset.Zero }) }
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y },
                ) { PhotoImage(photos[i], Modifier.fillMaxSize(), ContentScale.Fit) }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
                Icon(Icons.Default.Close, stringResource(R.string.action_close), tint = Color.White)
            }
            if (photos.size > 1) Text("${pager.currentPage + 1} / ${photos.size}", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListingEditDialog(initial: Listing, vm: MarketViewModel, onDismiss: () -> Unit, onSave: (Listing, List<String>?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initial.title) }
    var desc by remember { mutableStateOf(initial.description) }
    var price by remember { mutableStateOf(initial.price.filter { it.isDigit() || it == ',' || it == '.' }) }
    // annunci vecchi (solo testo libero): si distribuisce nei tre campi
    val legacyC = initial.phone.isBlank() && initial.email.isBlank() && initial.other.isBlank() && initial.contact.isNotBlank()
    var phone by remember { mutableStateOf(if (legacyC) initial.contact.takeIf { '@' !in it && waNumber(it) != null }.orEmpty() else initial.phone) }
    var whatsapp by remember { mutableStateOf(initial.whatsapp || (legacyC && phone.isNotBlank())) }
    var email by remember { mutableStateOf(if (legacyC) initial.contact.split(' ').firstOrNull { '@' in it }.orEmpty() else initial.email) }
    var other by remember { mutableStateOf(if (legacyC) (if (phone.isBlank() && email.isBlank()) initial.contact else "") else initial.other) }
    var sellerName by remember { mutableStateOf(initial.ownerName.ifBlank { vm.myName }) }
    var category by remember { mutableStateOf(initial.category) }
    var allCals by remember { mutableStateOf(initial.scope == "all") }
    val photos = remember { mutableStateListOf<String>() }
    var photosChanged by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(initial.id.isNotBlank() && initial.totalPhotos > 0) }
    var busy by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var catMenu by remember { mutableStateOf(false) }
    val badImg = stringResource(R.string.mk_photo_fail)
    LaunchedEffect(initial.id) { if (loading) { photos.addAll(vm.loadPhotos(initial)); loading = false } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val room = MARKET_MAX_PHOTOS - photos.size
        if (uris.isEmpty() || room <= 0) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            var failed = false
            uris.take(room).forEach { u ->
                val bmp = MarketImage.decode(context, u)
                if (bmp != null) { photos.add(MarketImage.encodeFull(bmp)); photosChanged = true } else failed = true
            }
            busy = false
            if (failed) Toast.makeText(context, badImg, Toast.LENGTH_SHORT).show()
        }
    }
    val fieldMod = Modifier.fillMaxWidth().heightIn(min = 46.dp)
    val small = MaterialTheme.typography.bodySmall
    CompactDialog(onDismiss) {
        Text(stringResource(if (initial.id.isBlank()) R.string.mk_new_title else R.string.mk_edit_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        CompactTextField(title, { title = it.take(120) }, label = { Text(stringResource(R.string.mk_f_title)) }, singleLine = true, textStyle = small, modifier = fieldMod)
        CompactTextField(sellerName, { sellerName = it.take(60) }, label = { Text(stringResource(R.string.mk_f_name)) }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), modifier = fieldMod)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                OutlinedButton(onClick = { catMenu = true }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(46.dp)) {
                    Text(stringResource(catLabelRes(category)), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                }
                DropdownMenu(catMenu, { catMenu = false }) {
                    MARKET_CATEGORIES.forEach { c -> DropdownMenuItem(text = { Text(stringResource(catLabelRes(c))) }, onClick = { category = c; catMenu = false }) }
                }
            }
            CompactTextField(price, { price = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(10) }, label = { Text(stringResource(R.string.mk_f_price), maxLines = 1, overflow = TextOverflow.Ellipsis) }, suffix = { Text("€", fontWeight = FontWeight.Bold) }, singleLine = true, textStyle = small, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), modifier = Modifier.weight(1f).heightIn(min = 46.dp))
        }
        CompactTextField(desc, { desc = it.take(2000) }, label = { Text(stringResource(R.string.mk_f_desc)) }, minLines = 2, maxLines = 4, textStyle = small, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactTextField(phone, { phone = it.take(40) }, label = { Text(stringResource(R.string.mk_f_phone), maxLines = 1) }, singleLine = true, textStyle = small, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone), modifier = Modifier.weight(1f).heightIn(min = 46.dp))
            Row(Modifier.clickable { whatsapp = !whatsapp }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(whatsapp, { whatsapp = it }); Text("WhatsApp", style = MaterialTheme.typography.labelMedium)
            }
        }
        CompactTextField(email, { email = it.take(100) }, label = { Text(stringResource(R.string.mk_f_email), maxLines = 1) }, singleLine = true, textStyle = small, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email), modifier = fieldMod)
        CompactTextField(other, { other = it.take(120) }, label = { Text(stringResource(R.string.mk_f_other), maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true, textStyle = small, modifier = fieldMod)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            photos.forEachIndexed { i, p ->
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(8.dp))) {
                    PhotoImage(p, Modifier.fillMaxSize(), sample = 4)
                    IconButton(onClick = { photos.removeAt(i); photosChanged = true }, modifier = Modifier.align(Alignment.TopEnd).size(20.dp).background(Color.Black.copy(alpha = 0.6f))) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                    IconButton(
                        onClick = { if (i > 0) { photos.add(0, photos.removeAt(i)); photosChanged = true } },
                        modifier = Modifier.align(Alignment.TopStart).size(22.dp).background(Color.Black.copy(alpha = 0.6f)),
                    ) {
                        Icon(if (i == 0) Icons.Default.Star else Icons.Default.StarBorder, stringResource(R.string.mk_primary_photo), tint = if (i == 0) Color(0xFFFFC107) else Color.White, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = { editIndex = i }, modifier = Modifier.align(Alignment.BottomStart).size(22.dp).background(Color.Black.copy(alpha = 0.6f))) {
                        Icon(Icons.Default.Edit, stringResource(R.string.mk_edit_photo), tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
            }
            if (photos.size < MARKET_MAX_PHOTOS && !loading) OutlinedButton(onClick = { pick.launch("image/*") }, enabled = !busy, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(58.dp)) {
                Text(if (busy) stringResource(R.string.cc_uploading) else stringResource(R.string.mk_add_photos, photos.size, MARKET_MAX_PHOTOS), style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.fillMaxWidth().clickable { allCals = !allCals }, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (allCals) R.string.mk_scope_all else R.string.mk_scope_cal), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Switch(allCals, { allCals = it }, modifier = Modifier.graphicsLayer { scaleX = 0.8f; scaleY = 0.8f })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            TextButton(enabled = title.isNotBlank() && (phone.isNotBlank() || email.isNotBlank() || other.isNotBlank()) && !busy && !loading, onClick = {
                onSave(initial.copy(ownerName = sellerName.trim(), title = title, description = desc, price = if (price.isBlank()) "" else "${price.trim()} €", contact = "", phone = phone, whatsapp = whatsapp && phone.isNotBlank(), email = email, other = other, category = category, scope = if (allCals) "all" else "calendar"),
                    if (photosChanged) photos.toList() else null)
            }) { Text(stringResource(R.string.action_save)) }
        }
    }
    editIndex?.let { i -> PhotoEditorDialog(photos[i], { editIndex = null }) { photos[i] = it; photosChanged = true; editIndex = null } }
}

/** Ruota (90°) e ritaglia (trascinando gli angoli o il riquadro) una foto. */
@Composable
private fun PhotoEditorDialog(b64: String, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    var bmp by remember { mutableStateOf(MarketImage.fromBase64(b64)) }
    var l by remember { mutableFloatStateOf(0f) }; var t by remember { mutableFloatStateOf(0f) }
    var r by remember { mutableFloatStateOf(1f) }; var b by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current
    fun resetCrop() { l = 0f; t = 0f; r = 1f; b = 1f }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val bm = bmp
                    if (bm != null) {
                        val aspect = bm.width.toFloat() / bm.height
                        val padFit = 30.dp   // margine attorno alla foto (stesso valore di `pad` sotto): il totale deve stare nello schermo
                        val boxW = minOf(maxWidth - padFit * 2, (maxHeight - padFit * 2) * aspect); val boxH = boxW / aspect
                        val wPx = with(density) { boxW.toPx() }; val hPx = with(density) { boxH.toPx() }
                        val touch = with(density) { 60.dp.toPx() }
                        val pad = 30.dp; val padPx = with(density) { pad.toPx() }
                        var active by remember { mutableIntStateOf(0) }   // 1-4 angoli TL TR BL BR, 5 sposta, 6-9 lati sx dx su giù
                        // l'area toccabile è più grande dell'immagine (margine), così gli angoli sul bordo si afferrano bene
                        Box(Modifier.size(boxW + pad * 2, boxH + pad * 2)) {
                            Image(bm.asImageBitmap(), null, Modifier.padding(pad).fillMaxSize(), contentScale = ContentScale.FillBounds)
                            Canvas(
                                Modifier.fillMaxSize().pointerInput(wPx, hPx) {
                                    detectDragGestures(
                                        onDragStart = { p0 ->
                                            val p = Offset(p0.x - padPx, p0.y - padPx)
                                            val hs = listOf(
                                                Offset(l * wPx, t * hPx), Offset(r * wPx, t * hPx), Offset(l * wPx, b * hPx), Offset(r * wPx, b * hPx),
                                                Offset(l * wPx, (t + b) / 2 * hPx), Offset(r * wPx, (t + b) / 2 * hPx), Offset((l + r) / 2 * wPx, t * hPx), Offset((l + r) / 2 * wPx, b * hPx),
                                            )
                                            val near = hs.withIndex().minByOrNull { (i, c) -> (c - p).getDistance() + if (i >= 4) touch * 0.25f else 0f }!!
                                            active = if ((near.value - p).getDistance() < touch) (if (near.index < 4) near.index + 1 else near.index + 2)
                                            else if (p.x in l * wPx..r * wPx && p.y in t * hPx..b * hPx) 5 else 0
                                        },
                                        onDrag = { ch, d ->
                                            ch.consume()
                                            val dx = d.x / wPx; val dy = d.y / hPx; val min = 0.08f
                                            when (active) {
                                                1 -> { l = (l + dx).coerceIn(0f, r - min); t = (t + dy).coerceIn(0f, b - min) }
                                                2 -> { r = (r + dx).coerceIn(l + min, 1f); t = (t + dy).coerceIn(0f, b - min) }
                                                3 -> { l = (l + dx).coerceIn(0f, r - min); b = (b + dy).coerceIn(t + min, 1f) }
                                                4 -> { r = (r + dx).coerceIn(l + min, 1f); b = (b + dy).coerceIn(t + min, 1f) }
                                                5 -> { val w = r - l; val h = b - t; l = (l + dx).coerceIn(0f, 1f - w); r = l + w; t = (t + dy).coerceIn(0f, 1f - h); b = t + h }
                                                6 -> l = (l + dx).coerceIn(0f, r - min)
                                                7 -> r = (r + dx).coerceIn(l + min, 1f)
                                                8 -> t = (t + dy).coerceIn(0f, b - min)
                                                9 -> b = (b + dy).coerceIn(t + min, 1f)
                                            }
                                        },
                                    )
                                },
                            ) {
                                val ox = padPx; val oy = padPx
                                val dim = Color.Black.copy(alpha = 0.55f)
                                val x0 = ox + l * wPx; val y0 = oy + t * hPx; val x1 = ox + r * wPx; val y1 = oy + b * hPx
                                drawRect(dim, Offset(ox, oy), Size(wPx, y0 - oy)); drawRect(dim, Offset(ox, y1), Size(wPx, oy + hPx - y1))
                                drawRect(dim, Offset(ox, y0), Size(x0 - ox, y1 - y0)); drawRect(dim, Offset(x1, y0), Size(ox + wPx - x1, y1 - y0))
                                drawRect(Color.White, Offset(x0, y0), Size(x1 - x0, y1 - y0), style = Stroke(2.dp.toPx()))
                                val handle = listOf(Offset(x0, y0), Offset(x1, y0), Offset(x0, y1), Offset(x1, y1), Offset(x0, (y0 + y1) / 2), Offset(x1, (y0 + y1) / 2), Offset((x0 + x1) / 2, y0), Offset((x0 + x1) / 2, y1))
                                handle.forEachIndexed { i, c ->
                                    val rad = if (i < 4) 13.dp.toPx() else 9.dp.toPx()
                                    drawCircle(Color.Black.copy(alpha = 0.6f), rad + 2.dp.toPx(), c); drawCircle(Color.White, rad, c)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { bmp = bmp?.let { MarketImage.rotate(it, -90f) }; resetCrop() }) { Icon(Icons.Default.RotateLeft, stringResource(R.string.mk_rotate_l), tint = Color.White) }
                    IconButton(onClick = { bmp = bmp?.let { MarketImage.rotate(it, 90f) }; resetCrop() }) { Icon(Icons.Default.RotateRight, stringResource(R.string.mk_rotate_r), tint = Color.White) }
                    TextButton(onClick = { resetCrop() }) { Text(stringResource(R.string.mk_crop_reset), color = Color.White) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = Color.White) }
                    TextButton(onClick = { bmp?.let { onApply(MarketImage.encodeFull(MarketImage.crop(it, l, t, r, b))) } }) { Text(stringResource(R.string.mk_apply), color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}
