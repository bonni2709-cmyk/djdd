package app.serein

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable password-encrypted vault; no plaintext key is stored on disk. */
actual class SecureVault actual constructor(private val passphrase: String) {
    private val path: Path = Path.of(System.getProperty("user.home"), ".serein", "vault-v1")
    private fun derive(salt: ByteArray): SecretKeySpec {
        require(passphrase.length >= 12) { "Use a passphrase of at least 12 characters" }
        val chars = passphrase.toCharArray()
        val spec = PBEKeySpec(chars, salt, 310_000, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally { spec.clearPassword(); chars.fill('\u0000') }
    }

    actual fun read(): ByteArray? {
        if (!Files.exists(path)) return null
        val blob = Files.readAllBytes(path)
        require(blob.size >= 45 && blob[0] == 1.toByte()) { "Invalid vault" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derive(blob.copyOfRange(1, 17)),
            GCMParameterSpec(128, blob.copyOfRange(17, 29)))
        cipher.updateAAD("serein-vault-v1".toByteArray())
        return cipher.doFinal(blob.copyOfRange(29, blob.size))
    }

    actual fun write(plaintext: ByteArray) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(salt))
        cipher.updateAAD("serein-vault-v1".toByteArray())
        val bytes = byteArrayOf(1) + salt + cipher.iv + cipher.doFinal(plaintext)
        Files.createDirectories(path.parent)
        val temp = Files.createTempFile(path.parent, "vault-", ".tmp")
        try {
            Files.write(temp, bytes)
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }

    actual fun erase() { Files.deleteIfExists(path) }
}
