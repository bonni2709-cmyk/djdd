package app.serein

/** Prototype-only: the seed exists solely in process memory; no identity persistence yet. */
class Identity(val publicKey: String, val fingerprint: String, val secretSeed: ByteArray) {
    fun clear() = secretSeed.fill(0)
}

expect fun generateIdentity(): Identity
