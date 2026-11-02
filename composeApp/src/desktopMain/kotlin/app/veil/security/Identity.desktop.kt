package app.veil.ui

import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.security.SecureRandom
import java.util.Base64

private object DesktopIdentityVault {
    var keyPair: AsymmetricCipherKeyPair? = null
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun createLocalIdentity(): LocalIdentity {
    val generator = Ed25519KeyPairGenerator()
    generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
    val pair = generator.generateKeyPair()
    DesktopIdentityVault.keyPair = pair
    val publicBytes = (pair.public as Ed25519PublicKeyParameters).encoded
    val publicKey = Base64.getUrlEncoder().withoutPadding().encodeToString(publicBytes)
    val fingerprint = publicBytes.take(6).joinToString("") { "%02X".format(it) }
    return LocalIdentity(publicKey, fingerprint.chunked(4).joinToString(" "))
}
