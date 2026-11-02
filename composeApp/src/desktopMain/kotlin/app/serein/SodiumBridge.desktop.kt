package app.serein

import com.goterl.lazysodium.SodiumJava

actual object SodiumBridge {
    private val sodium by lazy { SodiumJava() }
    actual fun newSeed() = ByteArray(32).also { sodium.randombytes_buf(it, it.size) }
    actual fun publicFromSeed(seed: ByteArray): ByteArray {
        require(seed.size == 32)
        val publicKey = ByteArray(32)
        val secretKey = ByteArray(64)
        try {
            check(sodium.crypto_sign_seed_keypair(publicKey, secretKey, seed) == 0)
            return publicKey
        } finally { secretKey.fill(0) }
    }
    actual fun sign(seed: ByteArray, message: ByteArray): ByteArray {
        require(seed.size == 32)
        val secretKey = ByteArray(64)
        try {
            check(sodium.crypto_sign_seed_keypair(ByteArray(32), secretKey, seed) == 0)
            val signature = ByteArray(64)
            check(sodium.crypto_sign_detached(signature, null, message, message.size.toLong(), secretKey) == 0)
            return signature
        } finally { secretKey.fill(0) }
    }
    actual fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean =
        publicKey.size == 32 && signature.size == 64 &&
            sodium.crypto_sign_verify_detached(signature, message, message.size.toLong(), publicKey) == 0

    actual fun ephemeralKeypair(): Pair<ByteArray, ByteArray> {
        val public = ByteArray(32)
        val secret = ByteArray(32)
        check(sodium.crypto_box_keypair(public, secret) == 0)
        return public to secret
    }
    actual fun sharedSecret(secretKey: ByteArray, peerPublic: ByteArray): ByteArray {
        require(secretKey.size == 32 && peerPublic.size == 32)
        val shared = ByteArray(32)
        check(sodium.crypto_scalarmult(shared, secretKey, peerPublic) == 0) { "Invalid ephemeral peer key" }
        return shared
    }
    actual fun nonce() = ByteArray(24).also { sodium.randombytes_buf(it, it.size) }
    actual fun seal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        require(key.size == 32 && nonce.size == 24)
        val out = ByteArray(plaintext.size + 16)
        val length = longArrayOf(0)
        check(sodium.crypto_aead_xchacha20poly1305_ietf_encrypt(out, length, plaintext,
            plaintext.size.toLong(), aad, aad.size.toLong(), null, nonce, key) == 0)
        check(length[0] == out.size.toLong())
        return out
    }
    actual fun open(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        require(key.size == 32 && nonce.size == 24 && ciphertext.size >= 16)
        val out = ByteArray(ciphertext.size - 16)
        val length = longArrayOf(0)
        check(sodium.crypto_aead_xchacha20poly1305_ietf_decrypt(out, length, null, ciphertext,
            ciphertext.size.toLong(), aad, aad.size.toLong(), nonce, key) == 0) { "Authentication failed" }
        check(length[0] == out.size.toLong())
        return out
    }
}
