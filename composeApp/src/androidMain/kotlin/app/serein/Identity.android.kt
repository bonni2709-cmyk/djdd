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
