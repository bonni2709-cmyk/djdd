package app.veil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Ink = Color(0xFF182522)
private val Muted = Color(0xFF78827D)
private val Canvas = Color(0xFFF5F5F0)
private val Paper = Color(0xFFFFFFFF)
private val Accent = Color(0xFF46685A)
private val AccentSoft = Color(0xFFE9F0EA)
private val Border = Color(0xFFE7E9E3)
private val Danger = Color(0xFF9E5146)

private data class Contact(val id: String, val label: String, val key: String, val tint: Color)
private data class ChatMessage(val id: Long, val body: String, val mine: Boolean, val sentAt: Long, val expiry: Long?)

@Composable
fun App() {
    val clipboard = LocalClipboardManager.current
    var identity by remember { mutableStateOf(createLocalIdentity()) }
    var contacts by remember { mutableStateOf(emptyList<Contact>()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf(emptyMap<String, List<ChatMessage>>()) }
    var draft by remember { mutableStateOf("") }
    var keyInput by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var sos by remember { mutableStateOf(false) }
    var expirySeconds by remember { mutableStateOf(0L) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            val now = currentTimeMillis()
            messages = messages.mapValues { (_, list) -> list.filter { it.expiry == null || it.expiry > now } }
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            if (sos) {
                LockScreen(onReturn = { sos = false }, onReset = {
                    contacts = emptyList()
                    messages = emptyMap()
                    selectedId = null
                    identity = createLocalIdentity()
                    sos = false
                })
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val compact = maxWidth < 720.dp
                    if (compact && selectedId != null) {
                        val active = contacts.firstOrNull { it.id == selectedId }
                        if (active != null) {
                            ConversationScreen(
                                contact = active,
                                identity = identity,
                                messages = messages[active.id].orEmpty(),
                                draft = draft,
                                expirySeconds = expirySeconds,
                                copied = copied,
                                onBack = { selectedId = null },
                                onDraft = { draft = it },
                                onSend = {
                                    val text = draft.trim()
                                    if (text.isNotEmpty()) {
                                        val now = currentTimeMillis()
                                        val item = ChatMessage(now, text, true, now, expirySeconds.takeIf { it > 0 }?.let { now + it * 1_000 })
                                        messages = messages + (active.id to (messages[active.id].orEmpty() + item))
                                        draft = ""
                                    }
                                },
                                onExpiry = { expirySeconds = if (expirySeconds == 0L) 60 else if (expirySeconds == 60L) 3_600 else 0 },
                                onCopy = { clipboard.setText(AnnotatedString(identity.publicKey)); copied = true },
                                onSos = { sos = true }
                            )
                        } else {
                            selectedId = null
                        }
                    } else {
                        Row(Modifier.fillMaxSize().padding(if (compact) 0.dp else 20.dp)) {
                            Sidebar(
                                identity = identity,
                                contacts = contacts,
                                selectedId = selectedId,
                                compact = compact,
                                onSelect = { selectedId = it },
                                onAdd = { showAdd = true },
                                onCopy = { clipboard.setText(AnnotatedString(identity.publicKey)); copied = true },
                                copied = copied,
                                onSos = { sos = true }
                            )
                            if (!compact) Spacer(Modifier.width(16.dp))
                            if (compact) {
                                InboxScreen(contacts, selectedId, onSelect = { selectedId = it }, onAdd = { showAdd = true })
                            } else {
                                val active = contacts.firstOrNull { it.id == selectedId }
                                if (active != null) {
                                    ConversationScreen(
                                        contact = active,
                                        identity = identity,
                                        messages = messages[active.id].orEmpty(),
                                        draft = draft,
                                        expirySeconds = expirySeconds,
                                        copied = copied,
                                        onBack = null,
                                        onDraft = { draft = it },
                                        onSend = {
                                            val text = draft.trim()
                                            if (text.isNotEmpty()) {
                                                val now = currentTimeMillis()
                                                val item = ChatMessage(now, text, true, now, expirySeconds.takeIf { it > 0 }?.let { now + it * 1_000 })
                                                messages = messages + (active.id to (messages[active.id].orEmpty() + item))
                                                draft = ""
                                            }
                                        },
                                        onExpiry = { expirySeconds = if (expirySeconds == 0L) 60 else if (expirySeconds == 60L) 3_600 else 0 },
                                        onCopy = { clipboard.setText(AnnotatedString(identity.publicKey)); copied = true },
                                        onSos = { sos = true }
                                    )
                                } else {
                                    WelcomePanel(identity, copied, onCopy = { clipboard.setText(AnnotatedString(identity.publicKey)); copied = true }, onSos = { sos = true })
                                }
                            }
                        }
                    }
                }
            }

            if (showAdd && !sos) {
                AddContactDialog(
                    value = keyInput,
                    onValue = { keyInput = it; copied = false },
                    onDismiss = { showAdd = false; keyInput = "" },
                    onAdd = {
                        val key = keyInput.trim()
                        if (key.length >= 24) {
                            val person = Contact(
                                id = key.take(18),
                                label = "Contact ${contacts.size + 1}",
                                key = key,
                                tint = listOf(Color(0xFF839A82), Color(0xFFC49A79), Color(0xFF8294A4))[contacts.size % 3]
                            )
                            contacts = contacts + person
                            selectedId = person.id
                            showAdd = false
                            keyInput = ""
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun Sidebar(
    identity: LocalIdentity,
    contacts: List<Contact>,
    selectedId: String?,
    compact: Boolean,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onCopy: () -> Unit,
    copied: Boolean,
    onSos: () -> Unit
) {
    Column(
        Modifier
            .then(if (compact) Modifier.fillMaxSize() else Modifier.width(286.dp).fillMaxHeight())
            .clip(RoundedCornerShape(if (compact) 0.dp else 24.dp))
            .background(Paper)
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("veil", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp)
                Text("PRIVATE MESSENGER", color = Muted, fontSize = 9.sp, letterSpacing = 1.2.sp)
            }
            IconButton(onClick = onSos) { Icon(Icons.Default.Security, "Lock session", tint = Muted) }
        }
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR CHATS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.25.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onAdd, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Add, "Add contact", tint = Accent, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        if (contacts.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Your space is quiet", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(5.dp))
                Text("Add someone by their public key.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(13.dp))
                OutlinedButton(onClick = onAdd, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.PersonAddAlt1, null, modifier = Modifier.size(17.dp), tint = Accent)
                    Spacer(Modifier.width(8.dp)); Text("Add contact", color = Accent)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(contacts, key = { it.id }) { contact ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (selectedId == contact.id) AccentSoft else Color.Transparent)
                            .clickable { onSelect(contact.id) }.padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(contact.label, contact.tint, 42)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(contact.label, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Public key contact", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F8F4)), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp)); Text("Your device identity", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text(identity.fingerprint, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text(identity.publicKey.take(22) + "…", color = Muted, fontSize = 10.sp, maxLines = 1)
                TextButton(onClick = onCopy, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Icon(Icons.Default.ContentCopy, null, tint = Accent, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp)); Text(if (copied) "Copied" else "Copy public key", color = Accent, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("LOCAL DEMO  ·  NO NETWORK", color = Muted, fontSize = 9.sp, letterSpacing = 0.8.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun InboxScreen(contacts: List<Contact>, selectedId: String?, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Canvas).padding(28.dp)) {
        Text("Messages", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp)
        Spacer(Modifier.height(6.dp))
        Text("Conversations stay on this device in this preview.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        contacts.forEach { contact ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paper).clickable { onSelect(contact.id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(contact.label, contact.tint, 46)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(contact.label, color = Ink, fontWeight = FontWeight.Medium)
                    Text("Start a local conversation", color = Muted, fontSize = 12.sp)
                }
                Text("›", color = Muted, fontSize = 24.sp)
            }
            Spacer(Modifier.height(9.dp))
        }
        if (contacts.isEmpty()) {
            Spacer(Modifier.weight(1f))
            Column(Modifier.align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMark()
                Spacer(Modifier.height(14.dp))
                Text("A little more private.", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text("Your conversations will appear here.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(18.dp))
                Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = Accent), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("Add a contact")
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun WelcomePanel(identity: LocalIdentity, copied: Boolean, onCopy: () -> Unit, onSos: () -> Unit) {
    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Paper).padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BrandMark(68)
        Spacer(Modifier.height(22.dp))
        Text("A quieter kind of connection.", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp)
        Spacer(Modifier.height(8.dp))
        Text("No phone number. No account. Just your device identity.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(26.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Canvas), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.width(360.dp).padding(20.dp)) {
                Text("YOUR PUBLIC ID", color = Muted, fontSize = 10.sp, letterSpacing = 1.1.sp)
                Spacer(Modifier.height(9.dp))
                Text(identity.publicKey, color = Ink, fontSize = 13.sp)
                Spacer(Modifier.height(9.dp))
                Text("Fingerprint  ${identity.fingerprint}", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)); Text(if (copied) "Copied" else "Copy public ID") }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("This preview creates an Ed25519 identity in memory. It is not yet persisted or connected to a messaging network.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        TextButton(onClick = onSos) { Icon(Icons.Default.Security, null); Spacer(Modifier.width(8.dp)); Text("Lock this session") }
    }
}

@Composable
private fun ConversationScreen(
    contact: Contact,
    identity: LocalIdentity,
    messages: List<ChatMessage>,
    draft: String,
    expirySeconds: Long,
    copied: Boolean,
    onBack: (() -> Unit)?,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onExpiry: () -> Unit,
    onCopy: () -> Unit,
    onSos: () -> Unit
) {
    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Paper)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                Spacer(Modifier.width(4.dp))
            }
            Avatar(contact.label, contact.tint, 42)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(contact.label, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Local conversation · not connected", color = Muted, fontSize = 11.sp)
            }
            IconButton(onClick = onExpiry) {
                Icon(Icons.Default.Timer, "Message expiry", tint = if (expirySeconds > 0) Accent else Muted)
            }
            IconButton(onClick = onSos) { Icon(Icons.Default.Security, "Lock session", tint = Muted) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
        if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Avatar(contact.label, contact.tint, 58)
                Spacer(Modifier.height(14.dp))
                Text("Your conversation starts here", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text("Messages are local-only in this prototype.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text("Expiry: ${expiryLabel(expirySeconds)}", color = Accent, fontSize = 11.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp), reverseLayout = true) {
                items(messages.reversed(), key = { it.id }) { message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start) {
                        Column(Modifier.widthInCompat(), horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start) {
                            Text(message.body, Modifier.clip(RoundedCornerShape(17.dp)).background(if (message.mine) Accent else Canvas).padding(horizontal = 15.dp, vertical = 11.dp), color = if (message.mine) Color.White else Ink, fontSize = 14.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(if (message.expiry != null) "Self-destructing" else "Just now", color = Muted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExpiry) { Icon(Icons.Default.Timer, "Set expiry", tint = if (expirySeconds > 0) Accent else Muted) }
            Row(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Canvas).padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(value = draft, onValueChange = onDraft, modifier = Modifier.weight(1f), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 14.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), decorationBox = { inner ->
                    if (draft.isEmpty()) Text("Write a message…", color = Muted, fontSize = 14.sp)
                    inner()
                })
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onSend, modifier = Modifier.size(34.dp).clip(CircleShape).background(Accent)) { Icon(Icons.AutoMirrored.Filled.Send, "Send locally", tint = Color.White, modifier = Modifier.size(17.dp)) }
            }
        }
        Text("Expiry: ${expiryLabel(expirySeconds)}  ·  Identity ${identity.fingerprint}", color = Muted, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
    }
}

@Composable
private fun AddContactDialog(value: String, onValue: (String) -> Unit, onDismiss: () -> Unit, onAdd: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paper).padding(22.dp)) {
            Text("Add a contact", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Paste their public key to create a local contact.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            BasicTextField(
                value = value,
                onValueChange = onValue,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Canvas).padding(14.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 13.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text("Public key", color = Muted, fontSize = 13.sp)
                    inner()
                }
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onAdd, enabled = value.trim().length >= 24, colors = ButtonDefaults.buttonColors(containerColor = Accent), shape = RoundedCornerShape(11.dp)) { Text("Add contact") }
            }
        }
    }
}

@Composable
private fun LockScreen(onReturn: () -> Unit, onReset: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink).windowInsetsPadding(WindowInsets.navigationBars).padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(68.dp).clip(CircleShape).background(Color(0xFF2A3C35)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Security, null, tint = Color(0xFFC5D6C8), modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Session hidden", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Your local preview is out of sight.", color = Color(0xFFB7C1BA), fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onReturn, colors = ButtonDefaults.buttonColors(containerColor = Accent), shape = RoundedCornerShape(12.dp)) { Text("Return to session") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onReset) { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFE1B8B1)); Spacer(Modifier.width(6.dp)); Text("Clear local session", color = Color(0xFFE1B8B1)) }
        Spacer(Modifier.height(20.dp))
        Text("Prototype lock screen only; does not change the app icon or erase device storage.", color = Color(0xFF8F9A92), fontSize = 11.sp)
    }
}

@Composable
private fun BrandMark(size: Int = 34) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp)).background(Accent), contentAlignment = Alignment.Center) {
        Text("v", color = Color.White, fontSize = (size * 0.62).sp, fontWeight = FontWeight.Medium, letterSpacing = (-2).sp)
    }
}

@Composable
private fun Avatar(name: String, color: Color, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = color, fontSize = (size * 0.38).sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Modifier.widthInCompat(): Modifier = this.then(Modifier.width(280.dp))

private fun expiryLabel(seconds: Long): String = when (seconds) {
    60L -> "1 minute"
    3_600L -> "1 hour"
    else -> "Off"
}
