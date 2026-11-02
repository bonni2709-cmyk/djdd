package app.serein

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters

actual fun generateIdentity(): Identity {
    val key = Ed25519PrivateKeyParameters(SecureRandom())
    val publicBytes = key.generatePublicKey().encoded
    val fingerprint = MessageDigest.getInstance("SHA-256").digest(publicBytes)
        .take(6).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return Identity(Base64.getUrlEncoder().withoutPadding().encodeToString(publicBytes), fingerprint, key.encoded)
}

actual fun ByteArray.toBase64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)

actual fun decodeBase64Url(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

actual fun identityFromSeed(seed: ByteArray): Identity {
    require(seed.size == 32) { "Invalid Ed25519 seed" }
    val key = Ed25519PrivateKeyParameters(seed, 0)
    val publicBytes = key.generatePublicKey().encoded
    val fingerprint = MessageDigest.getInstance("SHA-256").digest(publicBytes)
        .take(6).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return Identity(publicBytes.toBase64Url(), fingerprint, seed.copyOf())
}

actual fun isDesktopPlatform(): Boolean = true

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
