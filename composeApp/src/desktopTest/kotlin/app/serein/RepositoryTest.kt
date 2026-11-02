package app.serein

import java.nio.file.Files
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryTest {
    @Test
    fun qrContainsOnlyPublicKey() {
        val identity = generateIdentity()
        try {
            val grid = encodeContactQr(identity.publicKey)
            assertTrue(grid.size >= 21)
            val width = grid.size * 5
            val pixels = IntArray(width * width) { i ->
                if (grid.dark((i % width) / 5, (i / width) / 5)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
            val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(
                RGBLuminanceSource(width, width, pixels))))
            assertEquals(CONTACT_SCHEME + identity.publicKey, result.text)
            assertEquals(identity.publicKey, parseContactQr(result.text))
            assertEquals(null, parseContactQr(CONTACT_SCHEME + "garbage"))
        } finally { identity.clear() }
    }

    @Test
    fun legacyVaultMigratesOnlyAfterSuccessfulPassword() {
        val previousHome = System.getProperty("user.home")
        val tempHome = Files.createTempDirectory("serein-migrate-")
        System.setProperty("user.home", tempHome.toString())
        try {
            val passphrase = "a sufficiently long passphrase"
            val salt = ByteArray(16) { it.toByte() }
            val nonce = ByteArray(12) { (it + 30).toByte() }
            val spec = PBEKeySpec(passphrase.toCharArray(), salt, 310_000, 256)
            val key = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded, "AES")
            spec.clearPassword()
            val original = VaultData(seed = generateIdentity().let { id ->
                try { id.secretSeed.toBase64Url() } finally { id.clear() }
            })
            val plaintext = kotlinx.serialization.json.Json.encodeToString(
                VaultData.serializer(), original).encodeToByteArray()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
            cipher.updateAAD("serein-vault-v1".toByteArray())
            val path = tempHome.resolve(".serein/vault-v1")
            Files.createDirectories(path.parent)
            Files.write(path, byteArrayOf(1) + salt + nonce + cipher.doFinal(plaintext))
            assertFails { LocalRepository(SecureVault("wrong but long passphrase")) }
            assertEquals(1.toByte(), Files.readAllBytes(path)[0])
            assertEquals(original.seed, LocalRepository(SecureVault(passphrase)).data.seed)
            assertEquals(2.toByte(), Files.readAllBytes(path)[0])
        } finally { System.setProperty("user.home", previousHome) }
    }

    @Test
    fun identitySurvivesRestartAndWrongPasswordCannotDecrypt() {
        val previousHome = System.getProperty("user.home")
        val tempHome = Files.createTempDirectory("serein-test-")
        System.setProperty("user.home", tempHome.toString())
        try {
            val passphrase = "a long secret test passphrase"
            val first = LocalRepository(SecureVault(passphrase))
            val original = first.data.seed
            val own = identityFromSeed(decodeBase64Url(original))
            val contact = Contact("ZmFrZQ", "Test", "192.168.1.2")
            first.save(first.data.copy(contacts = listOf(contact),
                messages = listOf(ChatMessage(contact.publicKey, "Hello", false, 123))))
            val reopened = LocalRepository(SecureVault(passphrase))
            assertEquals(original, reopened.data.seed)
            assertEquals(own.publicKey, identityFromSeed(decodeBase64Url(reopened.data.seed)).publicKey)
            assertEquals("Hello", reopened.data.messages.single().text)
            assertEquals(contact, reopened.data.contacts.single())
            reopened.save(reopened.data.copy(messages = reopened.data.messages +
                ChatMessage(contact.publicKey, "expired", false, 1, 2)))
            assertEquals(listOf("Hello"), LocalRepository(SecureVault(passphrase)).data.messages.map { it.text })
            assertFails { LocalRepository(SecureVault("another long passphrase")) }
            val file = Files.readAllBytes(tempHome.resolve(".serein/vault-v1"))
            assertEquals(2.toByte(), file[0]) // libsodium Argon2id vault, not legacy PBKDF2
            assertFalse(file.toString(Charsets.UTF_8).contains("Hello"))
            assertTrue(file.size > 40)
            own.clear()
        } finally { System.setProperty("user.home", previousHome) }
    }
}

// QR parsing is shared across Android and desktop; never accept a QR containing a private key.
