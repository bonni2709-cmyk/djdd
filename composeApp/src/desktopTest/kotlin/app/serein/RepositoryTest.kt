package app.serein

import java.nio.file.Files
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
            assertEquals(identity.publicKey, parseContactQr(CONTACT_SCHEME + identity.publicKey))
            assertEquals(null, parseContactQr(CONTACT_SCHEME + "garbage"))
        } finally { identity.clear() }
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
            assertFails { LocalRepository(SecureVault("another long passphrase")) }
            val file = Files.readAllBytes(tempHome.resolve(".serein/vault-v1"))
            assertFalse(file.toString(Charsets.UTF_8).contains("Hello"))
            assertTrue(file.size > 40)
            own.clear()
        } finally { System.setProperty("user.home", previousHome) }
    }
}

// QR parsing is shared across Android and desktop; never accept a QR containing a private key.
