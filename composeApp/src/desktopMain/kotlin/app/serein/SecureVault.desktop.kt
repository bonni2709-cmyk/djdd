package app.serein

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import javax.crypto.Cipher
import com.goterl.lazysodium.SodiumJava
import com.sun.jna.NativeLong
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable password-encrypted vault; no plaintext key is stored on disk. */
actual class SecureVault actual constructor(private val passphrase: String) {
    private val path: Path = Path.of(System.getProperty("user.home"), ".serein", "vault-v1")
    private val sodium by lazy { SodiumJava() }
    // Read-only legacy KDF for vaults written before v0.4; successful unlock migrates to Argon2id.
    private fun deriveLegacy(salt: ByteArray): SecretKeySpec {
        require(passphrase.length >= 12) { "Use a passphrase of at least 12 characters" }
        val chars = passphrase.toCharArray()
        val spec = PBEKeySpec(chars, salt, 310_000, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally { spec.clearPassword(); chars.fill('\u0000') }
    }

    private fun deriveArgon2id(salt: ByteArray): SecretKeySpec {
        require(passphrase.length >= 12) { "Use a passphrase of at least 12 characters" }
        require(salt.size == 16)
        val password = passphrase.encodeToByteArray()
        val key = ByteArray(32)
        try {
            // libsodium Argon2id13; 64 MiB and 3 passes. Failure never falls back to PBKDF2.
            check(sodium.crypto_pwhash(key, 32L, password, password.size.toLong(), salt,
                3L, NativeLong(64L * 1024 * 1024), 2) == 0) { "Argon2id key derivation failed" }
            return SecretKeySpec(key, "AES")
        } finally { password.fill(0); key.fill(0) }
    }

    actual fun read(): ByteArray? {
        if (!Files.exists(path)) return null
        val blob = Files.readAllBytes(path)
        require(blob.size >= 45 && blob[0].toInt() in 1..2) { "Invalid vault" }
        val version = blob[0].toInt()
        val salt = blob.copyOfRange(1, 17)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, if (version == 1) deriveLegacy(salt) else deriveArgon2id(salt),
            GCMParameterSpec(128, blob.copyOfRange(17, 29)))
        cipher.updateAAD("serein-vault-v$version".toByteArray())
        val plaintext = cipher.doFinal(blob.copyOfRange(29, blob.size))
        if (version == 1) write(plaintext) // Atomically migrate after the old password is verified.
        return plaintext
    }

    actual fun write(plaintext: ByteArray) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveArgon2id(salt))
        cipher.updateAAD("serein-vault-v2".toByteArray())
        val bytes = byteArrayOf(2) + salt + cipher.iv + cipher.doFinal(plaintext)
        Files.createDirectories(path.parent)
        val temp = Files.createTempFile(path.parent, "vault-", ".tmp")
        try {
            Files.write(temp, bytes)
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }

    actual fun erase() { Files.deleteIfExists(path) }
}
