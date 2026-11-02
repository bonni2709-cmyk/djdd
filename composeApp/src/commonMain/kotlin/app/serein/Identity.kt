package app.serein

/** Keep the seed out of UI/export; the encrypted vault persists it separately. */
class Identity(val publicKey: String, val fingerprint: String, val secretSeed: ByteArray) {
    fun clear() = secretSeed.fill(0)
}

expect fun generateIdentity(): Identity
