package app.serein

/** Native libsodium platform API. Verify all return codes; fail closed on missing native libs. */
expect object SodiumBridge {
    fun newSeed(): ByteArray
    fun publicFromSeed(seed: ByteArray): ByteArray
    fun sign(seed: ByteArray, message: ByteArray): ByteArray
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean
    fun ephemeralKeypair(): Pair<ByteArray, ByteArray> // public, secret
    fun sharedSecret(secretKey: ByteArray, peerPublic: ByteArray): ByteArray
    fun nonce(): ByteArray
    fun seal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray
    fun open(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray
}
