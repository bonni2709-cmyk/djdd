package app.serein

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val canvas = Color(0xFFF5F3EE)
private val textColor = Color(0xFF1E2926)
private val secondary = Color(0xFF6F7B74)
private val sage = Color(0xFF405F54)
private val border = Color(0xFFE2E5DF)

@Composable
fun SereinApp() {
    var passphrase by remember { mutableStateOf("") }
    var repository by remember { mutableStateOf<LocalRepository?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf<VaultData?>(null) }
    val isDesktop = isDesktopPlatform()

    fun unlock() {
        try {
            val repo = LocalRepository(SecureVault(passphrase))
            repository = repo
            current = repo.data
            passphrase = ""
            error = null
        } catch (_: Exception) { error = "Unable to open vault. Check the passphrase or vault data." }
    }
    LaunchedEffect(Unit) { if (!isDesktop) unlock() }

    val theme = lightColorScheme(primary = sage, onPrimary = Color.White, background = canvas,
        surface = Color.White, onBackground = textColor, onSurface = textColor)
    MaterialTheme(colorScheme = theme) {
        Surface(color = canvas) {
            if (repository == null || current == null) {
                UnlockScreen(isDesktop, passphrase, { passphrase = it }, error, ::unlock)
            } else {
                val repo = repository!!
                val data = current!!
                val seed = remember(data.seed) { decodeBase64Url(data.seed) }
                val identity = remember(data.seed) { identityFromSeed(seed) }
                DisposableEffect(identity) { onDispose { seed.fill(0); identity.clear() } }
                fun update(newData: VaultData) {
                    try { repo.save(newData); current = newData; error = null }
                    catch (_: Exception) { error = "Changes could not be saved." }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 760.dp
                    if (wide) {
                        Row(Modifier.fillMaxSize()) {
                            Sidebar(page, { page = it; selected = null })
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                Workspace(page, data, identity, selected, { selected = it }, ::update,
                                    error, Modifier.widthIn(max = 820.dp))
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                Workspace(page, data, identity, selected, { selected = it }, ::update,
                                    error, Modifier.fillMaxWidth())
                            }
                            NavigationBar(containerColor = Color.White) {
                                listOf("Chats", "Contacts", "Identity").forEachIndexed { index, label ->
                                    NavigationBarItem(selected = page == index, onClick = { page = index; selected = null },
                                        icon = { Text(listOf("◌", "⊕", "◇")[index], fontSize = 22.sp) },
                                        label = { Text(label) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnlockScreen(desktop: Boolean, passphrase: String, onChange: (String) -> Unit,
                         error: String?, unlock: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 420.dp)) {
            Text("serein", fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
            Text("PRIVATE BY DESIGN", letterSpacing = 2.sp, fontSize = 10.sp, color = secondary)
            Spacer(Modifier.height(64.dp))
            Text(if (desktop) "Unlock your space" else "Opening vault", fontSize = 30.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Text(if (desktop) "Enter a passphrase (12+ characters). On first use, this creates an encrypted identity on this computer. Never lose your passphrase."
                else "Your identity is encrypted using Android Keystore.", color = secondary)
            if (desktop) {
                Spacer(Modifier.height(26.dp))
                OutlinedTextField(value = passphrase, onValueChange = onChange, label = { Text("Passphrase") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Button(onClick = unlock, enabled = passphrase.length >= 12, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Serein")
                }
            }
            if (error != null) { Spacer(Modifier.height(16.dp)); Text(error, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun Sidebar(page: Int, select: (Int) -> Unit) {
    Column(Modifier.width(236.dp).fillMaxHeight().background(Color.White).padding(24.dp)) {
        Text("serein", fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
        Text("PRIVATE BY DESIGN", letterSpacing = 2.sp, fontSize = 10.sp, color = secondary)
        Spacer(Modifier.height(68.dp))
        listOf("Chats", "Contacts", "Identity").forEachIndexed { index, title ->
            val active = page == index
            TextButton(onClick = { select(index) }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = if (active) sage else secondary)) {
                Text(listOf("◌", "⊕", "◇")[index]); Spacer(Modifier.width(16.dp))
                Text(title, modifier = Modifier.weight(1f), fontSize = 16.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.weight(1f))
        Text("LOCAL-ONLY  ·  0.2", color = secondary, fontSize = 10.sp)
    }
}

@Composable
private fun Workspace(page: Int, data: VaultData, identity: Identity, selected: String?,
                      select: (String?) -> Unit, update: (VaultData) -> Unit, error: String?, modifier: Modifier) {
    Column(modifier.fillMaxHeight().padding(horizontal = 24.dp, vertical = 22.dp)) {
        Spacer(Modifier.height(16.dp))
        if (page == 0) {
            Heading("Conversations", "Private space for your contacts")
            Spacer(Modifier.height(20.dp))
            if (selected == null) {
                Notice("Local drafts only", "Messages saved here are NOT sent to other devices. Network delivery is not available yet.")
                Spacer(Modifier.height(22.dp))
                if (data.contacts.isEmpty()) EmptyState("No conversations yet", "Add a contact to write a local draft.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(data.contacts, key = { it.publicKey }) { contact ->
                        val draft = data.drafts.find { it.contactKey == contact.publicKey }?.text
                        ContactRow(contact, draft ?: "No draft", { select(contact.publicKey) })
                    }
                }
            } else {
                val contact = data.contacts.find { it.publicKey == selected }
                if (contact == null) select(null) else DraftScreen(contact,
                    data.drafts.find { it.contactKey == selected }?.text.orEmpty(),
                    { value -> update(data.copy(drafts = data.drafts.filterNot { it.contactKey == selected } + Draft(selected, value))) },
                    { select(null) })
            }
        } else if (page == 1) {
            ContactsScreen(data, update)
        } else {
            IdentityScreen(data, identity, update)
        }
        if (error != null) { Spacer(Modifier.height(10.dp)); Text(error, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun Heading(title: String, caption: String) {
    Text(title, fontSize = 30.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(5.dp))
    Text(caption, color = secondary, fontSize = 14.sp)
}

@Composable
private fun Notice(title: String, detail: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFE9EFE9), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = sage, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp)); Text(detail, color = sage, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("◇", fontSize = 36.sp, color = sage)
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp)); Text(detail, color = secondary, fontSize = 13.sp)
    }
}

@Composable
private fun ContactRow(contact: Contact, detail: String, open: () -> Unit) {
    Surface(onClick = open, shape = RoundedCornerShape(16.dp), color = Color.White,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Color(0xFFE5EDE7), CircleShape), contentAlignment = Alignment.Center) {
                Text(contact.nickname.take(1).uppercase(), color = sage, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(contact.nickname, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis, color = secondary, fontSize = 12.sp)
            }
            Text("›", color = secondary, fontSize = 24.sp)
        }
    }
}

@Composable
private fun DraftScreen(contact: Contact, initial: String, save: (String) -> Unit, back: () -> Unit) {
    var draft by remember(contact.publicKey) { mutableStateOf(initial) }
    TextButton(onClick = back) { Text("‹  Conversations") }
    Spacer(Modifier.height(14.dp))
    Text(contact.nickname, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    Text("LOCAL DRAFT · NOT DELIVERED", color = secondary, letterSpacing = 1.sp, fontSize = 10.sp)
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(draft, { draft = it }, label = { Text("Write a private draft") },
        minLines = 4, maxLines = 10, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    Button(onClick = { save(draft) }) { Text("Save on this device") }
    Spacer(Modifier.height(14.dp))
    Text("This is not a chat. The draft is encrypted at rest and visible only here.", color = secondary, fontSize = 12.sp)
}

@Composable
private fun ContactsScreen(data: VaultData, update: (VaultData) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var nickname by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var contactError by remember { mutableStateOf<String?>(null) }
    Heading("Contacts", "People you choose to remember")
    Spacer(Modifier.height(24.dp))
    Button(onClick = { adding = true }) { Text("Add by public key") }
    Spacer(Modifier.height(20.dp))
    Notice("Verify in person", "A public key pasted from an untrusted source does not prove a person's identity. Compare fingerprints over a trusted channel.")
    Spacer(Modifier.height(18.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(data.contacts, key = { it.publicKey }) { contact ->
            ContactRow(contact, contact.publicKey.take(14) + "…", { adding = false })
        }
    }
    if (adding) AlertDialog(onDismissRequest = { adding = false },
        title = { Text("Add a contact") },
        text = {
            Column {
                OutlinedTextField(nickname, { nickname = it }, label = { Text("Name") }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(key, { key = it.trim() }, label = { Text("Ed25519 public key · Base64 URL") },
                    minLines = 2, maxLines = 3)
                if (contactError != null) Text(contactError!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = {
            val valid = try { decodeBase64Url(key).size == 32 } catch (_: Exception) { false }
            contactError = when {
                nickname.trim().isEmpty() -> "Enter a name"
                !valid -> "Enter a valid 32-byte Ed25519 public key"
                data.contacts.any { it.publicKey == key } -> "Contact already exists"
                else -> null
            }
            if (contactError == null) {
                update(data.copy(contacts = data.contacts + Contact(key, nickname.trim())))
                nickname = ""; key = ""; adding = false
            }
        }) { Text("Add") } }, dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } })
}

@Composable
private fun IdentityScreen(data: VaultData, identity: Identity, update: (VaultData) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Heading("Your identity", "A persistent key, created on this device")
    Spacer(Modifier.height(26.dp))
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text("ED25519  /  PUBLIC KEY", color = sage, fontSize = 11.sp, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(28.dp))
            Text("Fingerprint", color = secondary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(identity.fingerprint.chunked(4).joinToString("  "), fontSize = 25.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(25.dp)); HorizontalDivider(color = border); Spacer(Modifier.height(18.dp))
            Text("Public key · Base64 URL", color = secondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(identity.publicKey, fontSize = 15.sp, lineHeight = 23.sp)
        }
    }
    Spacer(Modifier.height(20.dp))
    Notice("Encrypted on this device", "Android uses Keystore; desktop uses a passphrase-derived key. There is no account recovery. No security audit has been performed.")
    Spacer(Modifier.height(18.dp))
    OutlinedButton(onClick = { confirm = true }) { Text("Reset identity and local data") }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Reset everything?") },
        text = { Text("This removes contacts and local drafts and creates a new identity. The old key cannot be recovered.") },
        confirmButton = { TextButton(onClick = {
            val replacement = generateIdentity()
            try { update(VaultData(seed = replacement.secretSeed.toBase64Url())) }
            finally { replacement.clear(); confirm = false }
        }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

expect fun isDesktopPlatform(): Boolean
