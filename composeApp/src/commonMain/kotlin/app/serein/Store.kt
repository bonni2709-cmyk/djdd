package app.serein

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Stored only inside an AES-GCM encrypted vault; never log or export this structure. */
@Serializable
data class VaultData(
    val version: Int = 1,
    val seed: String,
    val contacts: List<Contact> = emptyList(),
    val drafts: List<Draft> = emptyList()
)

@Serializable
data class Contact(val publicKey: String, val nickname: String)

/** Local-only unsent drafts, NOT delivered messages. */
@Serializable
data class Draft(val contactKey: String, val text: String)

expect class SecureVault(passphrase: String) {
    fun read(): ByteArray?
    fun write(plaintext: ByteArray)
    fun erase()
}

class LocalRepository(private val vault: SecureVault) {
    private val json = Json { ignoreUnknownKeys = false }
    var data: VaultData = vault.read()?.let { json.decodeFromString<VaultData>(it.decodeToString()) }
        ?: generateIdentity().let { newIdentity ->
            try { VaultData(seed = newIdentity.secretSeed.toBase64Url()).also { save(it) } }
            finally { newIdentity.clear() }
        }
        private set

    fun save(updated: VaultData) {
        check(updated.version == 1) { "Unsupported vault version" }
        vault.write(json.encodeToString(updated).encodeToByteArray())
        data = updated
    }

    fun erase() = vault.erase()
}

expect fun ByteArray.toBase64Url(): String
expect fun decodeBase64Url(value: String): ByteArray
expect fun identityFromSeed(seed: ByteArray): Identity
