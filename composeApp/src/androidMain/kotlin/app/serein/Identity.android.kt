package app.serein

import java.security.MessageDigest
import java.util.Base64

actual fun generateIdentity(): Identity {
    val seed = SodiumBridge.newSeed()
    val publicBytes = SodiumBridge.publicFromSeed(seed)
    val fingerprint = MessageDigest.getInstance("SHA-256").digest(publicBytes)
        .take(6).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return Identity(Base64.getUrlEncoder().withoutPadding().encodeToString(publicBytes), fingerprint, seed)
}

actual fun ByteArray.toBase64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)

actual fun decodeBase64Url(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

actual fun identityFromSeed(seed: ByteArray): Identity {
    require(seed.size == 32) { "Invalid Ed25519 seed" }
    val publicBytes = SodiumBridge.publicFromSeed(seed)
    val fingerprint = MessageDigest.getInstance("SHA-256").digest(publicBytes)
        .take(6).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return Identity(publicBytes.toBase64Url(), fingerprint, seed.copyOf())
}

actual fun isDesktopPlatform(): Boolean = false

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun withRepositoryLock(repository: LocalRepository, block: () -> Unit) { synchronized(repository) { block() } }

actual fun localLanAddress(): String = try {
    java.net.NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp }
        .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("en") || it.name.startsWith("eth")) 0 else 1 }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<java.net.Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
        ?.hostAddress ?: "Not connected to a private LAN"
} catch (_: Exception) { "Unavailable" }
