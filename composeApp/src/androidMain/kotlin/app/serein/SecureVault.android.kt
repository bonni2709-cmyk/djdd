package app.serein

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal lateinit var appContext: Context

/** Android Keystore wrapping key; Android may back it with hardware depending on device. */
actual class SecureVault actual constructor(passphrase: String) {
    private val file get() = appContext.getFileStreamPath("serein-vault-v1")
    private val alias = "serein-vault-v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }

    actual fun read(): ByteArray? {
        if (!file.exists()) return null
        val blob = file.readBytes()
        require(blob.size >= 29 && blob[0] == 1.toByte()) { "Invalid vault" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob.copyOfRange(1, 13)))
        cipher.updateAAD("serein-vault-v1".toByteArray())
        return cipher.doFinal(blob.copyOfRange(13, blob.size))
    }

    actual fun write(plaintext: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD("serein-vault-v1".toByteArray())
        val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext)
        // AtomicFile avoids accepting a partially written vault after interrupted writes.
        val atomic = android.util.AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(encrypted); atomic.finishWrite(stream) }
        catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }

    actual fun erase() {
        file.delete()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        store.deleteEntry(alias)
    }
}

actual fun hasStoredVault(): Boolean = appContext.getFileStreamPath("serein-vault-v1").exists()
