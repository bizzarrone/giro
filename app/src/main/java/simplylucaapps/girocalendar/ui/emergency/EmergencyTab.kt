package simplylucaapps.girocalendar.ui.emergency

import simplylucaapps.girocalendar.ui.common.CompactTextField
import androidx.compose.ui.res.stringResource

import simplylucaapps.girocalendar.R

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat

private val RED = Color(0xFFC62828)

/** Chiede i permessi utili (posizione, SMS, notifiche) e poi esegue [then]. */
@Composable
private fun rememberEmergencyPermissions(then: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val sms = r[Manifest.permission.SEND_SMS] ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
        then(sms)
    }
    return {
        val need = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.SEND_SMS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isEmpty()) then(true) else launcher.launch(need.toTypedArray())
    }
}

@Composable
fun EmergencyTab() {
    val context = LocalContext.current
    val suggestedName by remember { simplylucaapps.girocalendar.data.settings.AppSettings(context).myName }.collectAsState(initial = "")
    var editing by remember { mutableStateOf<EmergencyContact?>(null) }
    LaunchedEffect(suggestedName) { if (EmergencyPrefs.name.isBlank() && suggestedName.isNotBlank()) EmergencyPrefs.setName(context, suggestedName) }

    val msgNoSms = stringResource(R.string.et_no_sms)
    val reasonManualTest = stringResource(R.string.em_reason_manual_test)
    val reasonManual = stringResource(R.string.em_reason_manual)
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val askPerms = rememberEmergencyPermissions { smsOk ->
        if (!smsOk) EmergencyEngine.say(msgNoSms)
        pendingAction?.invoke(); pendingAction = null
    }
    fun withPerms(a: () -> Unit) { pendingAction = a; askPerms() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("bigToggle") {
            val on = EmergencyPrefs.armed
            Button(
                onClick = {
                    if (on) { EmergencyPrefs.setArmed(context, false); EmergencyService.stop(context) }
                    else withPerms { EmergencyPrefs.setArmed(context, true); EmergencyService.start(context) }
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (on) Color(0xFFFF1A1A) else Color(0xFF9E9E9E), contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(150.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(if (on) R.string.et_on else R.string.et_off), fontSize = 48.sp, fontWeight = FontWeight.Black)
                    Text(stringResource(if (on) R.string.et_on_sub else R.string.et_off_sub), fontSize = 14.sp)
                }
            }
        }
        item("fsWarn") {
            // Android 14+: l'allarme può aprirsi da solo a schermo bloccato solo se l'utente concede "Notifiche a schermo intero".
            val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
            fun allowed() = Build.VERSION.SDK_INT < 34 || context.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()
            var fsOk by remember { mutableStateOf(allowed()) }
            DisposableEffect(lifecycle) {
                val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) fsOk = allowed() }
                lifecycle.addObserver(obs); onDispose { lifecycle.removeObserver(obs) }
            }
            if (!fsOk) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.et_fs_title), fontWeight = FontWeight.Bold)
                    InfoNote(stringResource(R.string.et_fs_msg))
                    Button(onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.et_open_setting)) }
                }
            }
        }
        item("detect") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.et_detect), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(if (EmergencyPrefs.armed) R.string.et_armed_line else R.string.et_off_line), color = if (EmergencyPrefs.armed) RED else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    InfoNote(stringResource(R.string.et_detect_desc))
                    Text(stringResource(R.string.et_sensitivity), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(R.string.et_low, R.string.et_mid, R.string.et_high).forEachIndexed { i, l ->
                            FilterChip(selected = EmergencyPrefs.sensitivity == i, onClick = {
                                EmergencyPrefs.setSensitivity(context, i)
                                if (EmergencyPrefs.armed) EmergencyService.start(context)
                            }, label = { Text(stringResource(l)) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.et_seconds), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { EmergencyPrefs.setCountdown(context, EmergencyPrefs.countdownSec - 5) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(36.dp)) { Text("−") }
                        Text("${EmergencyPrefs.countdownSec}", Modifier.width(48.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        OutlinedButton(onClick = { EmergencyPrefs.setCountdown(context, EmergencyPrefs.countdownSec + 5) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(36.dp)) { Text("+") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.et_testmode), style = MaterialTheme.typography.bodyMedium)
                            InfoNote(stringResource(R.string.et_testmode_desc))
                        }
                        Switch(checked = EmergencyPrefs.testMode, onCheckedChange = { EmergencyPrefs.changeTestMode(it); if (EmergencyPrefs.armed) EmergencyService.start(context) })
                    }
                    CompactTextField(EmergencyPrefs.name, { EmergencyPrefs.setName(context, it) }, label = { Text(stringResource(R.string.et_your_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) {
                        Text(stringResource(R.string.et_battery))
                    }
                }
            }
        }
        item("msgs") {
            var open by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.et_messages), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { open = !open }) { Text(stringResource(if (open) R.string.action_close else R.string.et_customize)) }
                    }
                    if (open) {
                        Text(stringResource(R.string.et_placeholders), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MessageEditor(stringResource(R.string.et_msg_real), EmergencyPrefs.msgReal, stringResource(R.string.em_default_real), false) { EmergencyPrefs.setMsgReal(context, it) }
                        MessageEditor(stringResource(R.string.et_msg_test), EmergencyPrefs.msgTest, stringResource(R.string.em_default_test), true) { EmergencyPrefs.setMsgTest(context, it) }
                        Text(stringResource(R.string.et_emoji_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item("buttons") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { withPerms { EmergencyEngine.trigger(context, true, reasonManualTest) } }, modifier = Modifier.weight(1f).height(84.dp)) {
                    Text(stringResource(R.string.et_btn_test), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Button(onClick = { withPerms { EmergencyEngine.trigger(context, false, reasonManual) } }, colors = ButtonDefaults.buttonColors(containerColor = RED), modifier = Modifier.weight(1f).height(84.dp)) {
                    Text(stringResource(R.string.et_btn_real), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
        item("liveLog") { LiveLogCard(stringResource(R.string.et_doing), Modifier.fillMaxWidth(), collapsible = true) }
        item("manual") { ManualSendList() }
        item("contactsHdr") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.et_contacts), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FilledTonalButton(
                    onClick = { editing = EmergencyContact(java.util.UUID.randomUUID().toString(), "", "") },
                    enabled = EmergencyPrefs.contacts.size < EmergencyPrefs.MAX_CONTACTS,
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.action_add))
                }
            }
        }
        if (EmergencyPrefs.contacts.size >= EmergencyPrefs.MAX_CONTACTS) item("maxc") { Text(stringResource(R.string.et_max_contacts, EmergencyPrefs.MAX_CONTACTS), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (EmergencyPrefs.contacts.isEmpty()) item("noc") { Text(stringResource(R.string.et_no_contacts), style = MaterialTheme.typography.bodySmall, color = RED) }
        items(EmergencyPrefs.contacts.toList(), key = { it.id }) { c ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name.ifBlank { stringResource(R.string.et_unnamed) }, fontWeight = FontWeight.Bold)
                        Text(c.phone, style = MaterialTheme.typography.bodySmall)
                        Text(listOfNotNull("WhatsApp".takeIf { c.whatsapp }, "Telegram".takeIf { c.telegram }, "SMS".takeIf { c.sms }).joinToString(" · ").ifBlank { stringResource(R.string.et_no_channel) },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { editing = c }) { Icon(Icons.Default.Edit, stringResource(R.string.action_edit)) }
                    IconButton(onClick = { EmergencyPrefs.removeContact(context, c.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
        item("hint") {
            InfoNote(stringResource(R.string.et_hint))
            Spacer(Modifier.height(90.dp))
        }
    }
    editing?.let { ContactDialog(it, onDismiss = { editing = null }, onSave = { EmergencyPrefs.saveContact(context, it); editing = null }) }
}

@Composable
private fun ManualSendList() {
    val context = LocalContext.current
    if (EmergencyEngine.manual.isEmpty()) return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.et_manual_title), fontWeight = FontWeight.Bold)
            EmergencyEngine.manual.forEach { m ->
                Button(onClick = { EmergencyEngine.launchManual(context, m) }, modifier = Modifier.fillMaxWidth()) { Text((if (m.done) "✓ " else "") + m.label) }
            }
        }
    }
}

/** Registro in tempo reale di tutto quello che fa la funzione. */
@Composable
fun LiveLogCard(title: String, modifier: Modifier = Modifier, maxHeight: androidx.compose.ui.unit.Dp = 260.dp, dark: Boolean = false, collapsible: Boolean = false) {
    var open by rememberSaveable { mutableStateOf(!collapsible) }
    val lines = EmergencyEngine.log
    val scroll = rememberScrollState()
    LaunchedEffect(lines.size) { scroll.animateScrollTo(scroll.maxValue) }
    Card(modifier, colors = if (dark) CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)) else CardDefaults.cardColors()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth().then(if (collapsible) Modifier.clickable { open = !open } else Modifier), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = if (dark) Color.White else Color.Unspecified)
                if (collapsible) Text(if (open) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (open) Column(Modifier.padding(top = 4.dp).heightIn(max = maxHeight).verticalScroll(scroll)) {
                if (lines.isEmpty()) Text(stringResource(R.string.et_nothing), style = MaterialTheme.typography.bodySmall, color = if (dark) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant)
                lines.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 15.sp, color = if (dark) Color(0xFFE0E0E0) else Color.Unspecified) }
            }
        }
    }
}

@Composable
private fun ContactDialog(initial: EmergencyContact, onDismiss: () -> Unit, onSave: (EmergencyContact) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var phone by remember { mutableStateOf(initial.phone) }
    var wa by remember { mutableStateOf(initial.whatsapp) }
    var tg by remember { mutableStateOf(initial.telegram) }
    var sms by remember { mutableStateOf(initial.sms) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        if (r.resultCode == Activity.RESULT_OK && uri != null) runCatching {
            context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) { phone = c.getString(0).orEmpty(); if (name.isBlank()) name = c.getString(1).orEmpty() }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.name.isBlank() && initial.phone.isBlank()) R.string.et_new_contact else R.string.et_edit_contact)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pick.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.et_pick_contact)) }
                CompactTextField(name, { name = it }, label = { Text(stringResource(R.string.et_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CompactTextField(phone, { phone = it }, label = { Text(stringResource(R.string.et_phone)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.et_channels), style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(wa, { wa = it }); Text("WhatsApp") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(tg, { tg = it }); Text("Telegram") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(sms, { sms = it }); Text(stringResource(R.string.et_sms_chan)) }
            }
        },
        confirmButton = { TextButton(enabled = EmergencyEngine.normalize(phone).length >= 6, onClick = { onSave(initial.copy(name = name.trim(), phone = phone.trim(), whatsapp = wa, telegram = tg, sms = sms)) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Schermata a tutto schermo mostrata durante l'allarme / l'invio, sopra qualunque scheda. */
@Composable
fun EmergencyAlertHost() {
    val st = EmergencyEngine.state
    if (st == EmergencyState.IDLE) return
    val context = LocalContext.current
    Dialog(onDismissRequest = { }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        val bg = when { st == EmergencyState.COUNTDOWN -> RED; EmergencyEngine.isTest -> Color(0xFF37474F); else -> Color(0xFF263238) }
        Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(if (EmergencyEngine.isTest) R.string.et_alert_test else R.string.et_alert_real), color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)
            Text(EmergencyEngine.reason, color = Color.White.copy(alpha = .85f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            when (st) {
                EmergencyState.COUNTDOWN -> {
                    Text("${EmergencyEngine.countdownLeft}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 110.sp)
                    Text(stringResource(R.string.et_secs_to_send), color = Color.White)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { EmergencyEngine.cancel() }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = RED), modifier = Modifier.fillMaxWidth().height(84.dp), shape = RoundedCornerShape(16.dp)) {
                        Text(stringResource(R.string.et_im_ok), fontWeight = FontWeight.Black, fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { EmergencyEngine.sendNow() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.et_send_now)) }
                }
                EmergencyState.SENDING -> { CircularProgressIndicator(color = Color.White); Text(stringResource(R.string.et_sending), color = Color.White, modifier = Modifier.padding(8.dp)) }
                else -> Text(stringResource(if (EmergencyEngine.log.any { it.contains("ANNULLATO") || it.contains("CANCELLED") }) R.string.et_cancelled else R.string.et_done), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Spacer(Modifier.height(10.dp))
            LiveLogCard(stringResource(R.string.et_doing_now), Modifier.weight(1f).fillMaxWidth(), maxHeight = 600.dp, dark = true)
            if (EmergencyEngine.manual.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                EmergencyEngine.manual.forEach { m ->
                    Button(onClick = { EmergencyEngine.launchManual(context, m) }, modifier = Modifier.fillMaxWidth()) { Text((if (m.done) "✓ " else "") + m.label + stringResource(R.string.et_tap_open)) }
                }
            }
            if (st == EmergencyState.DONE) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { EmergencyEngine.dismiss() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.et_close)) }
            }
        }
    }
}


/** Campo di modifica di un messaggio + anteprima con dati di esempio e ripristino del testo predefinito. */
@Composable
private fun MessageEditor(label: String, current: String, default: String, test: Boolean, onChange: (String) -> Unit) {
    val text = current.ifBlank { default }
    val ctxP = LocalContext.current
    val reasonTest = stringResource(R.string.em_reason_test)
    val reasonFall = stringResource(R.string.em_reason_fall)
    CompactTextField(text, { onChange(it) }, label = { Text(label) }, minLines = 4, modifier = Modifier.fillMaxWidth())
    val sample = remember(text) {
        val l = android.location.Location("x").apply { latitude = 45.64; longitude = 8.35; accuracy = 8f }
        EmergencyEngine.render(ctxP, text, EmergencyPrefs.name, if (test) reasonTest else reasonFall, l, 80, java.util.Date())
    }
    Text(stringResource(R.string.et_preview, sample), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (current.isNotBlank() && current != default) TextButton(onClick = { onChange("") }) { Text(stringResource(R.string.et_reset_default)) }
}

/** Spiegazione di funzionamento compatta: una riga «ⓘ Info» che l'utente espande solo se vuole leggerla. */
@Composable
private fun InfoNote(text: String) {
    var open by rememberSaveable(text) { mutableStateOf(false) }
    Column(Modifier.clickable { open = !open }) {
        Text((if (open) "ⓘ ▲ " else "ⓘ ▼ ") + stringResource(R.string.et_info), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (open) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}
