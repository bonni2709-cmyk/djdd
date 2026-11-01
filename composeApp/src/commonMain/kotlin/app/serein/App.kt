package app.serein

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val paper = Color(0xFFF6F4EF)
private val ink = Color(0xFF242B2A)
private val muted = Color(0xFF69736F)
private val accent = Color(0xFF526A60)
private val line = Color(0xFFE6E6DF)

@Composable
fun SereinApp() {
    var identity by remember { mutableStateOf(generateIdentity()) }
    var page by remember { mutableStateOf(0) }
    DisposableEffect(identity) {
        val current = identity
        onDispose { current.clear() }
    }
    MaterialTheme(colorScheme = lightColorScheme(
        background = paper, surface = Color.White, primary = accent,
        onBackground = ink, onSurface = ink, onPrimary = Color.White
    )) {
        Surface(color = paper) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 760.dp
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.width(232.dp).fillMaxHeight().background(Color.White)
                                .padding(horizontal = 24.dp, vertical = 36.dp)
                        ) {
                            Brand()
                            Spacer(Modifier.height(72.dp))
                            NavItem("01", "Identity", page == 0) { page = 0 }
                            Spacer(Modifier.height(10.dp))
                            NavItem("02", "About", page == 1) { page = 1 }
                            Spacer(Modifier.weight(1f))
                            Text("LOCAL PROTOTYPE  ·  0.1", color = muted, fontSize = 10.sp)
                        }
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Content(page, identity, { identity = generateIdentity() }, Modifier.widthIn(max = 680.dp))
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)) { Brand() }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            Content(page, identity, { identity = generateIdentity() }, Modifier.fillMaxWidth())
                        }
                        NavigationBar(containerColor = Color.White) {
                            NavigationBarItem(selected = page == 0, onClick = { page = 0 },
                                icon = { Text("◇", fontSize = 22.sp) }, label = { Text("Identity") })
                            NavigationBarItem(selected = page == 1, onClick = { page = 1 },
                                icon = { Text("○", fontSize = 22.sp) }, label = { Text("About") })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Brand() {
    Column {
        Text("serein", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = ink)
        Text("PRIVATE BY DESIGN", fontSize = 10.sp, letterSpacing = 2.sp, color = muted)
    }
}

@Composable
private fun NavItem(number: String, title: String, active: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(contentColor = if (active) accent else muted)) {
        Text(number, fontSize = 11.sp)
        Spacer(Modifier.width(14.dp))
        Text(title, modifier = Modifier.weight(1f), fontSize = 15.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun Content(page: Int, identity: Identity, regenerate: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxHeight().padding(horizontal = 24.dp, vertical = 28.dp)) {
        Spacer(Modifier.height(28.dp))
        Text(if (page == 0) "Your identity" else "A quiet beginning", fontSize = 32.sp,
            fontWeight = FontWeight.Medium, color = ink)
        Spacer(Modifier.height(10.dp))
        Text(if (page == 0) "Created on this device. No phone number, no account."
            else "Serein is an early cross-platform identity prototype.", color = muted, fontSize = 15.sp)
        Spacer(Modifier.height(36.dp))
        if (page == 0) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                    Text("ED25519  /  SESSION KEY", fontSize = 11.sp, letterSpacing = 1.4.sp, color = accent)
                    Spacer(Modifier.height(28.dp))
                    Text("Fingerprint", fontSize = 13.sp, color = muted)
                    Spacer(Modifier.height(6.dp))
                    Text(identity.fingerprint.chunked(4).joinToString("  "), fontSize = 26.sp,
                        fontWeight = FontWeight.Medium, color = ink)
                    Spacer(Modifier.height(26.dp))
                    HorizontalDivider(color = line)
                    Spacer(Modifier.height(20.dp))
                    Text("Public key · Base64 URL", fontSize = 13.sp, color = muted)
                    Spacer(Modifier.height(8.dp))
                    Text(identity.publicKey, fontSize = 14.sp, color = ink,
                        maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
                    Spacer(Modifier.height(28.dp))
                    Button(onClick = regenerate, shape = RoundedCornerShape(10.dp)) {
                        Text("Generate new identity")
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("This key is temporary: it is lost when the app closes. Do not use it for real conversations.",
                fontSize = 13.sp, color = muted, lineHeight = 20.sp)
        } else {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("What works now", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text("Local Ed25519 key generation · responsive Compose interface · Android and desktop builds.", color = muted)
                    HorizontalDivider(color = line)
                    Text("Not yet available", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text("Persistent encrypted storage, contact verification, messaging, mesh routing, Tor, calls, media, and SOS mode. No security audit has been performed.", color = muted)
                }
            }
        }
    }
}
