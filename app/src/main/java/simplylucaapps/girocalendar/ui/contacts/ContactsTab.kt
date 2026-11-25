package simplylucaapps.girocalendar.ui.contacts

import simplylucaapps.girocalendar.ui.common.CompactTextField
import androidx.compose.ui.res.stringResource

import simplylucaapps.girocalendar.R

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.Dp
import simplylucaapps.girocalendar.ui.common.CircleCropDialog
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import simplylucaapps.girocalendar.data.remote.auth.GoogleAuthRepository
import simplylucaapps.girocalendar.data.remote.firestore.CONTACT_LINK_KEYS
import simplylucaapps.girocalendar.data.remote.firestore.ContactCard
import simplylucaapps.girocalendar.data.remote.firestore.ContactsRepository
import simplylucaapps.girocalendar.data.remote.firestore.GroupInfo
import simplylucaapps.girocalendar.data.settings.RecentCalendar

class ContactsViewModel(
    private val repo: ContactsRepository,
    private val code: String,
    auth: GoogleAuthRepository,
) : ViewModel() {
    val myUid: StateFlow<String?> = auth.observeUser().map { it?.uid }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), auth.currentUser?.uid)
    val cards: StateFlow<List<ContactCard>> = repo.observe(code)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val group: StateFlow<GroupInfo?> = repo.observeGroup(code)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveGroup(g: GroupInfo, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch { repo.saveGroup(code, g).onSuccess { onResult(true, null) }.onFailure { onResult(false, it.message) } }
    }

    fun deleteGroup(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch { repo.deleteGroup(code).onSuccess { onResult(true, null) }.onFailure { onResult(false, it.message) } }
    }

    fun save(card: ContactCard, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.save(code, card).onSuccess { onResult(true, null) }.onFailure { onResult(false, it.message) }
        }
    }

    fun delete(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.delete(code).onSuccess { onResult(true, null) }.onFailure { onResult(false, it.message) }
        }
    }

    /** Scheda dell'utente in un altro calendario, per precompilare il form. */
    fun cardFrom(otherCode: String, onResult: (ContactCard?, String?) -> Unit) {
        viewModelScope.launch {
            repo.myCardIn(otherCode)
                .onSuccess { onResult(it, if (it == null) "no_card" else null) }
                .onFailure { onResult(null, "read_fail") }
        }
    }

    /** Clona la scheda [card] nella scheda dell'utente in un altro calendario [otherCode] (la sostituisce se esiste). */
    fun cloneTo(otherCode: String, card: ContactCard, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.save(otherCode, card).onSuccess { onResult(true, null) }.onFailure { onResult(false, it.message) }
        }
    }

    companion object {
        fun factory(repo: ContactsRepository, code: String, auth: GoogleAuthRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { ContactsViewModel(repo, code, auth) } }
    }
}

private val LINK_LABELS = mapOf(
    "whatsapp" to "WhatsApp", "telegram" to "Telegram", "strava" to "Strava", "komoot" to "Komoot",
    "garmin" to "Garmin", "instagram" to "Instagram", "facebook" to "Facebook",
    "youtube" to "YouTube", "tiktok" to "TikTok",
)
private val LINK_HINTS = mapOf(
    "whatsapp" to R.string.k_hint_wa, "telegram" to R.string.k_hint_user,
    "strava" to R.string.k_hint_athlete, "komoot" to R.string.k_hint_userid,
    "garmin" to R.string.k_hint_profile, "instagram" to R.string.k_hint_user,
    "facebook" to R.string.k_hint_profile, "youtube" to R.string.k_hint_channel, "tiktok" to R.string.k_hint_user,
)

/** Trasforma un nome utente / ID / numero nel link da aprire; un link completo resta com'è. */
internal fun linkUrl(key: String, raw: String): String {
    val v = raw.trim()
    if (v.startsWith("http://") || v.startsWith("https://")) return v
    val h = v.removePrefix("@")
    return when (key) {
        "whatsapp" -> "https://wa.me/" + v.filter { it.isDigit() }
        "telegram" -> "https://t.me/$h"
        "strava" -> "https://www.strava.com/athletes/$h"
        "komoot" -> "https://www.komoot.com/user/$h"
        "garmin" -> "https://connect.garmin.com/modern/profile/$h"
        "instagram" -> "https://www.instagram.com/$h"
        "facebook" -> "https://www.facebook.com/$h"
        "youtube" -> "https://www.youtube.com/@$h"
        "tiktok" -> "https://www.tiktok.com/@$h"
        else -> if (v.contains('.')) "https://$v" else v
    }
}

@Composable
fun ContactsTab(
    vm: ContactsViewModel, recents: List<RecentCalendar>, currentCode: String, canEditGroup: Boolean = false,
    logoKey: String = "", logoColor: String = "", logoData: String = "",
) {
    val cards by vm.cards.collectAsState()
    val group by vm.group.collectAsState()
    var editingGroup by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ContactCard?>(null) }
    val myUid by vm.myUid.collectAsState()
    val mine = cards.firstOrNull { it.uid == myUid }
    val others = cards.filter { it.uid != myUid }
        .sortedBy { (it.nome + " " + it.cognome).lowercase() }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmShare by remember { mutableStateOf(false) }
    val context = LocalContext.current
    fun toast(m: String) = android.widget.Toast.makeText(context, m, android.widget.Toast.LENGTH_SHORT).show()
    val msgSaveFailed = stringResource(R.string.k_save_failed)
    val msgDeleteFailed = stringResource(R.string.k_delete_failed)
    val msgCloneFail = stringResource(R.string.k_clone_fail)
    val msgNoCard = stringResource(R.string.k_no_card)
    val msgReadFail = stringResource(R.string.k_read_fail)
    val msgCloned = stringResource(R.string.k_cloned)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp, 8.dp, 10.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item("group") { GroupCardView(group, canEditGroup, logoKey.ifBlank { currentCode }, logoColor, logoData, onEdit = { editingGroup = true }) }
            if (mine == null && others.isEmpty()) {
                item("empty") {
                    Text(
                        stringResource(R.string.k_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            if (mine == null && others.isNotEmpty()) {
                item("banner") {
                    androidx.compose.material3.Card(
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(stringResource(R.string.k_banner), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            TextButton(onClick = { editing = true }) { Text(stringResource(R.string.k_my_card)) }
                        }
                    }
                }
            }
            mine?.let { c -> item("mine") { ContactCardView(c, isMine = true, onEdit = { editing = true }, onOpen = { detail = c }, onShare = {
                if (c.shared) shareCardLink(context, currentCode, c.uid) else confirmShare = true
            }) } }
            items(others, key = { it.uid }) { c -> ContactCardView(c, isMine = false, onEdit = null, onOpen = { detail = c }) }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = true },
            icon = { Icon(if (mine == null) Icons.Default.Add else Icons.Default.Edit, null) },
            text = { Text(stringResource(if (mine == null) R.string.k_my_card else R.string.k_edit_my_card)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (editing) {
        ContactEditDialog(
            initial = mine ?: ContactCard(),
            isNew = mine == null,
            otherCalendars = recents.filter { it.code != currentCode },
            onCopy = { code, onCard -> vm.cardFrom(code) { c, err -> if (err != null) toast(when (err) { "no_card" -> msgNoCard; "read_fail" -> msgReadFail; else -> err }); onCard(c) } },
            onClone = { r, c -> vm.cloneTo(r.code, c) { ok, err -> toast(if (ok) msgCloned.format(r.name.ifBlank { r.code }) else err ?: msgCloneFail) } },
            onDismiss = { editing = false },
            onSave = { card -> vm.save(card) { ok, err -> if (ok) editing = false else toast(err ?: msgSaveFailed) } },
            onDelete = if (mine != null) ({ confirmDelete = true }) else null,
        )
    }
    detail?.let { c ->
        ContactDetailDialog(c, isMine = c.uid == myUid, onEdit = { detail = null; editing = true }, onDismiss = { detail = null })
    }
    if (editingGroup) {
        GroupEditDialog(
            initial = group ?: GroupInfo(),
            exists = group != null,
            onDismiss = { editingGroup = false },
            onSave = { g -> vm.saveGroup(g) { ok, err -> if (ok) editingGroup = false else toast(err ?: msgSaveFailed) } },
            onDelete = { vm.deleteGroup { ok, err -> if (ok) editingGroup = false else toast(err ?: msgDeleteFailed) } },
        )
    }
    if (confirmShare && mine != null) {
        AlertDialog(
            onDismissRequest = { confirmShare = false },
            title = { Text(stringResource(R.string.k_share_q)) },
            text = { Text(stringResource(R.string.k_share_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmShare = false
                    vm.save(mine.copy(shared = true)) { ok, err ->
                        if (ok) shareCardLink(context, currentCode, mine.uid) else toast(err ?: msgSaveFailed)
                    }
                }) { Text(stringResource(R.string.k_create_share)) }
            },
            dismissButton = { TextButton(onClick = { confirmShare = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.k_delete_q)) },
            text = { Text(stringResource(R.string.k_delete_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete { ok, err -> if (ok) editing = false else toast(err ?: msgDeleteFailed) }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Card «GRUPPO» in cima ai contatti: i link del gruppo del calendario. Gli editor (con la modifica attiva) la creano e la modificano. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupCardView(g: GroupInfo?, canEdit: Boolean, logoKey: String, logoColor: String, logoData: String, onEdit: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    if (g == null || g.isEmpty) {
        if (canEdit) OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.gr_add))
        }
        return
    }
    val neutral = MaterialTheme.colorScheme.secondaryContainer
    val onNeutral = MaterialTheme.colorScheme.onSecondaryContainer
    // sfondo leggermente diverso dalle schede dei contatti, così il GRUPPO (sempre in testa) si distingue
    ElevatedCard(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // l'immagine del gruppo è il logo del calendario
                simplylucaapps.girocalendar.ui.common.CalendarLogo(logoKey, logoColor, logoData, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(g.title.ifBlank { androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.gr_title) }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    if (g.desc.isNotBlank()) Text(g.desc, style = MaterialTheme.typography.bodySmall)
                }
                if (canEdit) IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.action_edit), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                CONTACT_LINK_KEYS.forEach { k ->
                    val v = g.links[k].orEmpty()
                    val brand = LINK_BRAND[k]
                    if (v.isNotBlank() && brand != null) SocialChip(LINK_LABELS[k] ?: k, brand.first, { open(linkUrl(k, v)) }, showLabel = false) {
                        Icon(androidx.compose.ui.res.painterResource(brand.second), null, Modifier.size(15.dp), tint = androidx.compose.ui.graphics.Color.White)
                    }
                }
                if (g.altroUrl.isNotBlank()) SocialChip(g.altroLabel.ifBlank { "Link" }, neutral, { open(linkUrl("altro", g.altroUrl)) }, showLabel = false) {
                    Icon(Icons.Default.Link, null, Modifier.size(14.dp), tint = onNeutral)
                }
            }
        }
    }
}

@Composable
private fun GroupEditDialog(initial: GroupInfo, exists: Boolean, onDismiss: () -> Unit, onSave: (GroupInfo) -> Unit, onDelete: () -> Unit) {
    var g by remember { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.gr_title)) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.gr_help), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(g.title, { g = g.copy(title = it) }, label = { Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.gr_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(g.desc, { g = g.copy(desc = it) }, label = { Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.gr_desc)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                CONTACT_LINK_KEYS.forEach { k ->
                    OutlinedTextField(g.links[k].orEmpty(), { v -> g = g.copy(links = g.links + (k to v)) },
                        label = { Text(LINK_LABELS[k] ?: k) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(g.altroLabel, { g = g.copy(altroLabel = it) }, label = { Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.gr_other_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(g.altroUrl, { g = g.copy(altroUrl = it) }, label = { Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.gr_other_link)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = !g.isEmpty, onClick = { onSave(g) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = {
            Row {
                if (exists) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.gr_del_title)) },
        text = { Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.gr_del_msg)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Contatto in grande (tocco sulla scheda): foto, nome e un pulsante ampio per ogni modo di contattarlo. */
@Composable
private fun ContactDetailDialog(c: ContactCard, isMine: Boolean, onEdit: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    val neutral = MaterialTheme.colorScheme.secondaryContainer
    val onNeutral = MaterialTheme.colorScheme.onSecondaryContainer
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    fun Big(label: String, value: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, onClick: () -> Unit, icon: @Composable () -> Unit) {
        // tocco = apre; tocco lungo = copia il valore negli appunti
        Surface(
            shape = RoundedCornerShape(14.dp), color = bg, contentColor = fg,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(14.dp)).combinedClickable(
                onClick = onClick,
                onLongClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText(label, value.trim()))
                    android.widget.Toast.makeText(context, "$label copiato", android.widget.Toast.LENGTH_SHORT).show()
                },
            ),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                icon(); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.Bold)
                    if (value.isNotBlank()) Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(c.photoData, c.nome, 160.dp)
                Text("${c.nome} ${c.cognome}".trim(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (c.soprannome.isNotBlank()) Text("“${c.soprannome}”", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.ct_hold_copy), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.phone.isNotBlank()) Big(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.k_phone), c.phone.trim(), neutral, onNeutral, { open("tel:" + c.phone.trim()) }) { Icon(Icons.Default.Call, null) }
                if (c.email.isNotBlank()) Big("Email", c.email.trim(), neutral, onNeutral, { open("mailto:" + c.email.trim()) }) { Icon(Icons.Default.Email, null) }
                CONTACT_LINK_KEYS.forEach { k ->
                    val v = c.links[k].orEmpty()
                    val brand = LINK_BRAND[k]
                    if (v.isNotBlank() && brand != null) Big(LINK_LABELS[k] ?: k, v, brand.first, androidx.compose.ui.graphics.Color.White, { open(linkUrl(k, v)) }) {
                        Icon(androidx.compose.ui.res.painterResource(brand.second), null, Modifier.size(24.dp), tint = androidx.compose.ui.graphics.Color.White)
                    }
                }
                if (c.altroUrl.isNotBlank()) Big(c.altroLabel.ifBlank { androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.gr_other) }, c.altroUrl, neutral, onNeutral, { open(linkUrl("altro", c.altroUrl)) }) { Icon(Icons.Default.Link, null) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        dismissButton = if (isMine) ({ TextButton(onClick = onEdit) { Text(stringResource(R.string.k_edit_my_card)) } }) else null,
    )
}

/** Colore e simbolo del "marchio" di ogni social, per il pulsante tondo compatto. */
private val LINK_BRAND = mapOf(
    "whatsapp" to (androidx.compose.ui.graphics.Color(0xFF25D366) to simplylucaapps.girocalendar.R.drawable.ic_brand_whatsapp),
    "telegram" to (androidx.compose.ui.graphics.Color(0xFF229ED9) to simplylucaapps.girocalendar.R.drawable.ic_brand_telegram),
    "strava" to (androidx.compose.ui.graphics.Color(0xFFFC4C02) to simplylucaapps.girocalendar.R.drawable.ic_brand_strava),
    "komoot" to (androidx.compose.ui.graphics.Color(0xFF6AA127) to simplylucaapps.girocalendar.R.drawable.ic_brand_komoot),
    "garmin" to (androidx.compose.ui.graphics.Color(0xFF007CC3) to simplylucaapps.girocalendar.R.drawable.ic_brand_garmin),
    "instagram" to (androidx.compose.ui.graphics.Color(0xFFC13584) to simplylucaapps.girocalendar.R.drawable.ic_brand_instagram),
    "facebook" to (androidx.compose.ui.graphics.Color(0xFF1877F2) to simplylucaapps.girocalendar.R.drawable.ic_brand_facebook),
    "youtube" to (androidx.compose.ui.graphics.Color(0xFFFF0000) to simplylucaapps.girocalendar.R.drawable.ic_brand_youtube),
    "tiktok" to (androidx.compose.ui.graphics.Color(0xFF111111) to simplylucaapps.girocalendar.R.drawable.ic_brand_tiktok),
)

/** Icona tonda piccola + nome del social, vicini; tocco = apre il contatto/link. */
@Composable
private fun SocialChip(label: String, bg: androidx.compose.ui.graphics.Color, onClick: () -> Unit, showLabel: Boolean = true, icon: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 3.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { icon() }
        if (showLabel) Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactCardView(c: ContactCard, isMine: Boolean, onEdit: (() -> Unit)?, onShare: (() -> Unit)? = null, onOpen: (() -> Unit)? = null) {
    val context = LocalContext.current
    fun open(url: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    var zoomPhoto by remember { mutableStateOf(false) }
    if (zoomPhoto) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { zoomPhoto = false }) {
            Avatar(c.photoData, c.nome, 300.dp, onClick = { zoomPhoto = false })
        }
    }
    val neutral = MaterialTheme.colorScheme.secondaryContainer
    val onNeutral = MaterialTheme.colorScheme.onSecondaryContainer
    ElevatedCard(Modifier.fillMaxWidth().then(if (onOpen != null) Modifier.clickable { onOpen() } else Modifier)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(c.photoData, c.nome, 36.dp, onClick = null)
            // Nome sopra e, sempre sotto, le iconcine (anche se ci starebbero sulla stessa riga).
            Column(Modifier.weight(1f)) {
                Text(
                    buildString {
                        append("${c.nome} ${c.cognome}".trim())
                        if (c.soprannome.isNotBlank()) append(" “${c.soprannome}”")
                    },
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (c.phone.isNotBlank()) SocialChip("Tel.", neutral, { onOpen?.invoke() }, showLabel = false) {
                    Icon(Icons.Default.Call, null, Modifier.size(14.dp), tint = onNeutral)
                }
                if (c.email.isNotBlank()) SocialChip("Email", neutral, { onOpen?.invoke() }, showLabel = false) {
                    Icon(Icons.Default.Email, null, Modifier.size(14.dp), tint = onNeutral)
                }
                CONTACT_LINK_KEYS.forEach { k ->
                    val v = c.links[k].orEmpty()
                    val brand = LINK_BRAND[k]
                    if (v.isNotBlank() && brand != null) SocialChip(LINK_LABELS[k] ?: k, brand.first, { onOpen?.invoke() }, showLabel = false) {
                        Icon(androidx.compose.ui.res.painterResource(brand.second), null, Modifier.size(15.dp),
                            tint = androidx.compose.ui.graphics.Color.White)
                    }
                }
                if (c.altroUrl.isNotBlank()) SocialChip(c.altroLabel.ifBlank { androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.gr_other) }, neutral, { onOpen?.invoke() }, showLabel = false) {
                    Icon(Icons.Default.Link, null, Modifier.size(14.dp), tint = onNeutral)
                }
            }
            }
            if (isMine && onShare != null) {
                IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Share, stringResource(R.string.k_share_my_card), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (isMine) {
                Text(stringResource(R.string.k_you), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onEdit?.invoke() }.padding(4.dp))
            }
        }
    }
}

@Composable
private fun ContactEditDialog(
    initial: ContactCard,
    isNew: Boolean,
    otherCalendars: List<RecentCalendar>,
    onCopy: (String, (ContactCard?) -> Unit) -> Unit,
    onClone: (RecentCalendar, ContactCard) -> Unit,
    onDismiss: () -> Unit,
    onSave: (ContactCard) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var card by remember { mutableStateOf(initial) }
    var pickSource by remember { mutableStateOf(0) }  // 0 chiuso, 1 = copia da…, 2 = clona in…
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) cropUri = uri }
    fun setLink(k: String, v: String) { card = card.copy(links = card.links + (k to v)) }
    val ctx = LocalContext.current
    val msgGoogleFail = stringResource(R.string.k_google_photo_fail)
    val scope = rememberCoroutineScope()
    val googlePhoto = remember {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
            ?.replace(Regex("=s\\d+(-c)?$"), "=s512-c")
    }
    var loadingGoogle by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.k_my_card else R.string.k_edit_my_card)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    stringResource(R.string.k_form_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(card.photoData, card.nome, 64.dp)
                    OutlinedButton(onClick = { pickPhoto.launch("image/*") }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(if (card.photoData.isBlank()) R.string.k_add_photo else R.string.k_change_photo), maxLines = 1)
                    }
                    if (card.photoData.isNotBlank()) {
                        IconButton(onClick = { card = card.copy(photoData = "") }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.k_remove_photo), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (!googlePhoto.isNullOrBlank()) {
                    OutlinedButton(
                        enabled = !loadingGoogle,
                        onClick = {
                            loadingGoogle = true
                            scope.launch {
                                val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    runCatching {
                                        val file = java.io.File(ctx.cacheDir, "google_photo.jpg")
                                        java.net.URL(googlePhoto).openStream().use { i -> file.outputStream().use { o -> i.copyTo(o) } }
                                        file
                                    }.getOrNull()
                                }
                                loadingGoogle = false
                                if (f != null) cropUri = Uri.fromFile(f)
                                else android.widget.Toast.makeText(ctx, msgGoogleFail, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(if (loadingGoogle) R.string.k_downloading else R.string.k_use_google_photo)) }
                }
                Row(Modifier.fillMaxWidth().clickable { card = card.copy(shared = !card.shared) },
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(card.shared, { card = card.copy(shared = it) })
                    Text(stringResource(R.string.k_shareable), style = MaterialTheme.typography.bodySmall)
                }
                if (otherCalendars.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickSource = 1 }, modifier = Modifier.weight(1f).height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text(stringResource(R.string.k_copy_from), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                        OutlinedButton(onClick = { pickSource = 2 }, enabled = card.isValid, modifier = Modifier.weight(1f).height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text(stringResource(R.string.k_clone_to), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
                CompactTextField(card.nome, { card = card.copy(nome = it) }, label = { Text(stringResource(R.string.k_name), fontSize = 12.sp) },
                    singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp))
                CompactTextField(card.cognome, { card = card.copy(cognome = it) }, label = { Text(stringResource(R.string.k_surname), fontSize = 12.sp) },
                    singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp))
                CompactTextField(card.soprannome, { card = card.copy(soprannome = it) }, label = { Text(stringResource(R.string.k_nickname), fontSize = 12.sp) },
                    singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp))
                CompactTextField(card.email, { card = card.copy(email = it) }, label = { Text("Email", fontSize = 12.sp) },
                    singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                CompactTextField(card.phone, { card = card.copy(phone = it) }, label = { Text(stringResource(R.string.k_phone), fontSize = 12.sp) },
                    singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                CONTACT_LINK_KEYS.forEach { k ->
                    CompactTextField(
                        card.links[k].orEmpty(), { setLink(k, it) },
                        label = { Text(LINK_LABELS[k] ?: k, fontSize = 12.sp) },
                        placeholder = { Text(LINK_HINTS[k]?.let { stringResource(it) }.orEmpty(), fontSize = 12.sp) },
                        singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    )
                }
                CompactTextField(card.altroLabel, { card = card.copy(altroLabel = it) }, label = { Text(stringResource(R.string.k_other_name), fontSize = 12.sp) },
                    singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp))
                CompactTextField(card.altroUrl, { card = card.copy(altroUrl = it) }, label = { Text(stringResource(R.string.k_other_link), fontSize = 12.sp) },
                    singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                if (onDelete != null) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text(stringResource(R.string.k_delete_my_card))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(card) }, enabled = card.isValid) { Text(androidx.compose.ui.res.stringResource(simplylucaapps.girocalendar.R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    cropUri?.let { u ->
        CircleCropDialog(u, onDismiss = { cropUri = null }, onDone = { data -> card = card.copy(photoData = data); cropUri = null })
    }
    if (pickSource != 0) {
        val cloning = pickSource == 2
        AlertDialog(
            onDismissRequest = { pickSource = 0 },
            title = { Text(stringResource(if (cloning) R.string.k_clone_to else R.string.k_copy_from)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (cloning) Text(stringResource(R.string.k_clone_msg), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    otherCalendars.forEach { r ->
                        TextButton(onClick = {
                            pickSource = 0
                            if (cloning) onClone(r, card)
                            else onCopy(r.code) { c -> if (c != null) card = c.copy(uid = initial.uid, shared = initial.shared) }
                        }, modifier = Modifier.fillMaxWidth()) { Text(r.name.ifBlank { r.code }) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickSource = 0 }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Foto profilo in un cerchio; senza foto, l'iniziale del nome su sfondo colorato. */
@Composable
private fun Avatar(photoData: String, name: String, size: Dp, onClick: (() -> Unit)? = null) {
    val bmp = remember(photoData) {
        if (photoData.isBlank()) null else runCatching {
            val b = android.util.Base64.decode(photoData, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
        }.getOrNull()
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        else Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Condivide il link web pubblico della sola scheda (pagina card.html, nessun accesso al calendario). */
private fun shareCardLink(context: android.content.Context, code: String, uid: String) {
    val url = "https://girocalendar.pages.dev/card.html?c=$code&u=$uid"
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, url)
    }
    context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.k_share_my_card)))
}
