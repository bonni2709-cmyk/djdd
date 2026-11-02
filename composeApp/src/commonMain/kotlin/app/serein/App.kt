package app.serein

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// Atelier: ink, smoked olive and matte brass. No neon or electric blue.
private val canvas = Color(0xFF111916)
private val textColor = Color(0xFFF1EFE7)
private val secondary = Color(0xFFA5B3AC)
private val sage = Color(0xFFD7B886)
private val border = Color(0xFF34423A)
private val card = Color(0xFF1C2722)
private val elevated = Color(0xFF26342C)
private val onAccent = Color(0xFF211E18)

@Composable
fun SereinApp() {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var repository by remember { mutableStateOf<LocalRepository?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf<VaultData?>(null) }
    var lanError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isDesktop = isDesktopPlatform()
    val firstDesktopLaunch = remember { isDesktop && !hasStoredVault() }

    fun unlock() {
        try {
            if (firstDesktopLaunch && passphrase != confirmation) {
                error = "Пароли не совпадают"
                return
            }
            val repo = LocalRepository(SecureVault(passphrase))
            repository = repo
            current = repo.data
            passphrase = ""
            confirmation = ""
            error = null
        } catch (_: Exception) { error = "Не удалось открыть хранилище. Проверьте пароль или данные." }
    }
    LaunchedEffect(Unit) { if (!isDesktop) unlock() }

    val theme = darkColorScheme(primary = sage, onPrimary = onAccent, background = canvas,
        surface = card, onBackground = textColor, onSurface = textColor,
        surfaceVariant = elevated, onSurfaceVariant = secondary, outline = border,
        error = Color(0xFFEBAFA0))
    MaterialTheme(colorScheme = theme) {
        Surface(color = canvas) {
            if (repository == null || current == null) {
                UnlockScreen(isDesktop, firstDesktopLaunch, passphrase, { passphrase = it },
                    confirmation, { confirmation = it }, error, ::unlock)
            } else {
                val repo = repository!!
                val data = current!!
                val seed = remember(data.seed) { decodeBase64Url(data.seed) }
                val identity = remember(data.seed) { identityFromSeed(seed) }
                DisposableEffect(identity) { onDispose { seed.fill(0); identity.clear() } }
                val transport = remember(repo, identity) {
                    LanTransport(identity, {
                        var keys = emptySet<String>()
                        withRepositoryLock(repo) { keys = repo.data.contacts.map { it.publicKey }.toSet() }
                        keys
                    },
                        { key, message, expiresAt ->
                            var saved = false
                            withRepositoryLock(repo) {
                                try {
                                    require(repo.data.contacts.any { it.publicKey == key })
                                    val updated = repo.data.copy(messages = (repo.data.messages +
                                        ChatMessage(key, message, false, nowMillis(), expiresAt)).takeLast(1000))
                                    repo.save(updated)
                                    saved = true
                                } catch (_: Exception) { saved = false }
                            }
                            if (saved) scope.launch { current = repo.data }
                            else scope.launch { lanError = "Полученное сообщение не удалось сохранить." }
                            saved
                        })
                }
                DisposableEffect(transport) {
                    lanError = transport.start()
                    onDispose { transport.stop() }
                }
                LaunchedEffect(repo) {
                    while (true) {
                        delay(15_000)
                        withRepositoryLock(repo) {
                            val valid = repo.data.messages.filter { it.expiresAtMillis == null || it.expiresAtMillis > nowMillis() }
                            if (valid.size != repo.data.messages.size) repo.save(repo.data.copy(messages = valid))
                        }
                        if (current !== repo.data) current = repo.data
                    }
                }
                fun send(contact: Contact, message: String, expiresAt: Long?) {
                    transport.send(contact, message, expiresAt) { failure -> scope.launch {
                        if (failure != null) lanError = failure
                        else try {
                            withRepositoryLock(repo) {
                                repo.save(repo.data.copy(messages = (repo.data.messages +
                                    ChatMessage(contact.publicKey, message, true, nowMillis(), expiresAt)).takeLast(1000)))
                            }
                            current = repo.data; lanError = null
                        } catch (_: Exception) { lanError = "Собеседник принял сообщение, но сохранить его здесь не удалось." }
                    } }
                }
                fun update(newData: VaultData) {
                    try {
                        withRepositoryLock(repo) {
                            repo.save(if (newData.seed == repo.data.seed)
                                newData.copy(messages = repo.data.messages.filter { message ->
                                    newData.contacts.any { it.publicKey == message.contactKey }
                                }) else newData)
                        }
                        current = repo.data; error = null
                    }
                    catch (_: Exception) { error = "Изменения не удалось сохранить." }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 760.dp
                    if (wide) {
                        Row(Modifier.fillMaxSize()) {
                            Sidebar(page, { page = it; selected = null })
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                Workspace(page, data, identity, selected, { selected = it }, ::update,
                                    error ?: lanError, ::send, Modifier.widthIn(max = 820.dp))
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                Workspace(page, data, identity, selected, { selected = it }, ::update,
                                    error ?: lanError, ::send, Modifier.fillMaxWidth())
                            }
                            NavigationBar(containerColor = card) {
                                listOf("Чаты", "Контакты", "Профиль").forEachIndexed { index, label ->
                                    NavigationBarItem(selected = page == index, onClick = { page = index; selected = null },
                                        icon = { Text(listOf("◈", "⊕", "◇")[index], fontSize = 22.sp) },
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
private fun UnlockScreen(desktop: Boolean, firstLaunch: Boolean,
                         passphrase: String, onChange: (String) -> Unit,
                         confirmation: String, onConfirm: (String) -> Unit,
                         error: String?, unlock: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 420.dp)) {
            Text("SEREIN", fontSize = 40.sp, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Light, letterSpacing = 4.sp)
            Text("ЛИЧНОЕ ПРОСТРАНСТВО", letterSpacing = 2.sp, fontSize = 10.sp, color = sage)
            Spacer(Modifier.height(64.dp))
            Text(if (desktop) (if (firstLaunch) "Создайте профиль" else "С возвращением")
                else "Открываем хранилище", fontSize = 30.sp, fontFamily = FontFamily.Serif)
            Spacer(Modifier.height(12.dp))
            Text(if (desktop) (if (firstLaunch) "Придумайте пароль от 12 символов и повторите его. Без пароля восстановить профиль будет невозможно."
                else "Введите пароль от вашего зашифрованного профиля.")
                else "Ваш профиль защищён Android Keystore.", color = secondary)
            if (desktop) {
                Spacer(Modifier.height(26.dp))
                OutlinedTextField(value = passphrase, onValueChange = onChange, label = { Text("Пароль") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                if (firstLaunch) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = confirmation, onValueChange = onConfirm,
                        label = { Text("Повторите пароль") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    if (confirmation.isNotEmpty() && confirmation != passphrase) {
                        Text("Пароли не совпадают", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = unlock, enabled = passphrase.length >= 12 &&
                    (!firstLaunch || passphrase == confirmation), modifier = Modifier.fillMaxWidth()) {
                    Text(if (firstLaunch) "Создать зашифрованный профиль" else "Открыть Serein")
                }
            }
            if (error != null) { Spacer(Modifier.height(16.dp)); Text(error, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun Sidebar(page: Int, select: (Int) -> Unit) {
    Column(Modifier.width(250.dp).fillMaxHeight().background(card).padding(26.dp)) {
        Text("SEREIN", fontSize = 28.sp, fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Light, letterSpacing = 4.sp)
        Text("ЛИЧНОЕ ПРОСТРАНСТВО", letterSpacing = 2.sp, fontSize = 10.sp, color = sage)
        Spacer(Modifier.height(68.dp))
        listOf("Чаты", "Контакты", "Профиль").forEachIndexed { index, title ->
            val active = page == index
            TextButton(onClick = { select(index) }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = if (active) sage else secondary)) {
                Text(listOf("◈", "⊕", "◇")[index]); Spacer(Modifier.width(16.dp))
                Text(title, modifier = Modifier.weight(1f), fontSize = 16.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = border)
        Spacer(Modifier.height(16.dp))
        Text("ПРЯМАЯ СВЯЗЬ  ·  0.4", color = secondary, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun Workspace(page: Int, data: VaultData, identity: Identity, selected: String?,
                      select: (String?) -> Unit, update: (VaultData) -> Unit, error: String?,
                      send: (Contact, String, Long?) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxHeight().padding(horizontal = 24.dp, vertical = 22.dp)) {
        Spacer(Modifier.height(16.dp))
        if (page == 0) {
            Heading("Переписки", "Прямая связь в одной локальной сети")
            Spacer(Modifier.height(20.dp))
            if (selected == null) {
                Notice("Локальная сеть · бета", "Оба устройства должны быть онлайн в одной частной сети. Сверьте публичные ключи и укажите IP. Офлайн-доставки и Tor пока нет.")
                Spacer(Modifier.height(22.dp))
                if (data.contacts.isEmpty()) EmptyState("Пока нет переписок", "Добавьте контакт, чтобы начать диалог.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(data.contacts, key = { it.publicKey }) { contact ->
                        val latest = data.messages.lastOrNull { it.contactKey == contact.publicKey }?.text
                        ContactRow(contact, latest ?: "Сообщений пока нет", { select(contact.publicKey) })
                    }
                }
            } else {
                val contact = data.contacts.find { it.publicKey == selected }
                if (contact == null) select(null) else ChatScreen(contact,
                    data.messages.filter { it.contactKey == selected }, { text, expiry -> send(contact, text, expiry) }, { select(null) })
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
    Text(title, fontSize = 35.sp, fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal, lineHeight = 40.sp)
    Spacer(Modifier.height(5.dp))
    Text(caption, color = secondary, fontSize = 14.sp)
}

@Composable
private fun Notice(title: String, detail: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = elevated,
        border = BorderStroke(1.dp, border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = sage, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
            Spacer(Modifier.height(4.dp)); Text(detail, color = secondary, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("◈", fontSize = 42.sp, color = sage)
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp)); Text(detail, color = secondary, fontSize = 13.sp)
    }
}

@Composable
private fun ContactRow(contact: Contact, detail: String, open: () -> Unit) {
    Surface(onClick = open, shape = RoundedCornerShape(16.dp), color = card,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(elevated, CircleShape), contentAlignment = Alignment.Center) {
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
private fun ColumnScope.ChatScreen(contact: Contact, messages: List<ChatMessage>, send: (String, Long?) -> Unit, back: () -> Unit) {
    var draft by remember(contact.publicKey) { mutableStateOf("") }
    var expiration by remember(contact.publicKey) { mutableStateOf(0L) }
    var expirationMenu by remember { mutableStateOf(false) }
    LaunchedEffect(messages.lastOrNull()) {
        if (messages.lastOrNull()?.outgoing == true && messages.lastOrNull()?.text == draft) draft = ""
    }
    TextButton(onClick = back) { Text("‹  Переписки") }
    Spacer(Modifier.height(14.dp))
    Text(contact.nickname, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    Text("ЛОКАЛЬНАЯ СЕТЬ  ·  ${contact.host.ifBlank { "IP НЕ ЗАДАН" }}:$LAN_PORT", color = secondary, letterSpacing = 1.sp, fontSize = 10.sp)
    Spacer(Modifier.height(16.dp))
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(messages) { message ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start) {
                Surface(shape = RoundedCornerShape(16.dp), color = if (message.outgoing) sage else card) {
                    Column(Modifier.widthIn(max = 300.dp).padding(14.dp)) {
                        Text(message.text, color = if (message.outgoing) onAccent else textColor)
                        if (message.expiresAtMillis != null) {
                            Spacer(Modifier.height(5.dp))
                            Text("◷  Исчезающее", fontSize = 10.sp,
                                color = if (message.outgoing) onAccent else secondary)
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(draft, { draft = it }, label = { Text("Сообщение") },
        minLines = 1, maxLines = 4, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            TextButton(onClick = { expirationMenu = true }) {
                Text(if (expiration == 0L) "◷  Без таймера" else when (expiration) {
                    3_600_000L -> "◷  1 час"; 86_400_000L -> "◷  1 день"; else -> "◷  7 дней"
                })
            }
            DropdownMenu(expanded = expirationMenu, onDismissRequest = { expirationMenu = false }) {
                listOf(0L to "Без таймера", 3_600_000L to "1 час", 86_400_000L to "1 день",
                    7L * 86_400_000 to "7 дней").forEach { (millis, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { expiration = millis; expirationMenu = false })
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = { if (draft.isNotBlank()) send(draft, if (expiration == 0L) null else nowMillis() + expiration) },
            enabled = draft.isNotBlank() && draft.encodeToByteArray().size <= 16_384) { Text("Отправить") }
    }
    Spacer(Modifier.height(14.dp))
    Text("Удаление по таймеру — только на устройствах. Собеседник может сохранить копию. Протокол не прошёл независимый аудит.",
        color = secondary, fontSize = 12.sp)
}

@Composable
private fun ContactsScreen(data: VaultData, update: (VaultData) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var nickname by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var qrScanResult by remember { mutableStateOf<String?>(null) }
    var host by remember { mutableStateOf("") }
    var contactError by remember { mutableStateOf<String?>(null) }
    Heading("Контакты", "Только те, кому вы доверяете")
    Spacer(Modifier.height(24.dp))
    Notice("Ваш адрес в сети · порт $LAN_PORT", localLanAddress())
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { nickname = ""; key = ""; host = ""; editing = null; adding = true }) { Text("Добавить") }
        OutlinedButton(onClick = {
            qrScanResult = "Ожидание QR-кода с камеры…"
            scanContactQr { result ->
                if (result == null) qrScanResult = "Камера недоступна или QR-код не найден"
                else {
                    val parsed = parseContactQr(result)
                    if (parsed != null) {
                        key = parsed; nickname = ""; host = ""; editing = null; adding = true
                    } else qrScanResult = "Неверный QR-код Serein"
                }
            }
        }) { Text("Сканировать QR") }
    }
    if (qrScanResult != null) Text(qrScanResult!!, color = MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(20.dp))
    Notice("Сверьте ключи лично", "Оба собеседника добавляют ключ и IP друг друга. Публичный ключ нужно сверить по независимому доверенному каналу.")
    Spacer(Modifier.height(18.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(data.contacts, key = { it.publicKey }) { contact ->
            ContactRow(contact, "${contact.host.ifBlank { "IP не указан" }} · ${contact.publicKey.take(12)}…", {
                editing = contact.publicKey; nickname = contact.nickname; key = contact.publicKey
                host = contact.host; contactError = null; adding = true
            })
        }
    }
    if (adding) AlertDialog(onDismissRequest = { adding = false },
        title = { Text(if (editing == null) "Новый контакт" else "Изменить контакт") },
        text = {
            Column {
                OutlinedTextField(nickname, { nickname = it }, label = { Text("Имя") }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(key, { key = it.trim() }, label = { Text("Публичный ключ Ed25519 · Base64 URL") },
                    minLines = 2, maxLines = 3)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(host, { host = it.trim() }, label = { Text("Частный IPv4-адрес") },
                    singleLine = true)
                if (contactError != null) Text(contactError!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = {
            val valid = try { decodeBase64Url(key).size == 32 } catch (_: Exception) { false }
            contactError = when {
                nickname.trim().isEmpty() -> "Введите имя"
                !valid -> "Введите корректный публичный ключ Ed25519"
                data.contacts.any { it.publicKey == key && it.publicKey != editing } -> "Контакт уже добавлен"
                host.isNotEmpty() && !host.matches(Regex("[0-9.]+")) -> "Укажите IPv4-адрес цифрами"
                else -> null
            }
            if (contactError == null) {
                update(data.copy(contacts = data.contacts.filterNot { it.publicKey == editing } +
                    Contact(key, nickname.trim(), host)))
                nickname = ""; key = ""; host = ""; adding = false
            }
        }) { Text(if (editing == null) "Добавить" else "Сохранить") } }, dismissButton = {
            Row {
                if (editing != null) TextButton(onClick = {
                    update(data.copy(contacts = data.contacts.filterNot { it.publicKey == editing },
                        messages = data.messages.filterNot { it.contactKey == editing }))
                    adding = false; editing = null
                }) { Text("Удалить") }
                TextButton(onClick = { adding = false }) { Text("Отмена") }
            }
        })
}

@Composable
private fun IdentityScreen(data: VaultData, identity: Identity, update: (VaultData) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    Heading("Ваш профиль", "Ключ создан и хранится на этом устройстве")
    Spacer(Modifier.height(26.dp))
    Surface(shape = RoundedCornerShape(20.dp), color = card,
        border = BorderStroke(1.dp, border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text("ED25519  /  ПУБЛИЧНЫЙ КЛЮЧ", color = sage, fontSize = 11.sp, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(28.dp))
            Text("Отпечаток", color = secondary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(identity.fingerprint.chunked(4).joinToString("  "), fontSize = 25.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(25.dp)); HorizontalDivider(color = border); Spacer(Modifier.height(18.dp))
            Text("Публичный ключ · Base64 URL", color = secondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(identity.publicKey, fontSize = 15.sp, lineHeight = 23.sp)
            }
            Spacer(Modifier.height(20.dp))
            ContactQr(identity.publicKey)
        }
    }
    Spacer(Modifier.height(20.dp))
    Notice("Зашифровано на устройстве", "Android использует Keystore, компьютер — ключ из пароля. Сообщения используют libsodium и XChaCha20-Poly1305. Протокол экспериментальный, независимого аудита нет.")
    Spacer(Modifier.height(18.dp))
    OutlinedButton(onClick = { confirm = true }) { Text("Удалить профиль и данные") }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Удалить всё?") },
        text = { Text("Контакты и сообщения будут удалены, появится новый ключ. Старый восстановить нельзя.") },
        confirmButton = { TextButton(onClick = {
            val replacement = generateIdentity()
            try { update(VaultData(seed = replacement.secretSeed.toBase64Url())) }
            finally { replacement.clear(); confirm = false }
        }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } })
    }
}

expect fun isDesktopPlatform(): Boolean

@Composable
private fun ContactQr(publicKey: String) {
    val grid = remember(publicKey) { encodeContactQr(publicKey) }
    Canvas(Modifier.size(194.dp).background(Color.White)) {
        val unit = size.minDimension / grid.size
        for (y in 0 until grid.size) for (x in 0 until grid.size) {
            if (grid.dark(x, y)) drawRect(Color(0xFF111916), Offset(x * unit, y * unit), Size(unit, unit))
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("Покажите код собеседнику. Обязательно сверьте ключ лично.", color = secondary, fontSize = 12.sp)
}
