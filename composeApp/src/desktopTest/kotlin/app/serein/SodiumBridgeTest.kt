package app.serein

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertTrue

class SodiumBridgeTest {
    @Test fun ed25519SignatureAndXChaChaTampering() {
        val seed = SodiumBridge.newSeed()
        val public = SodiumBridge.publicFromSeed(seed)
        val message = "authenticated test".encodeToByteArray()
        val signature = SodiumBridge.sign(seed, message)
        assertTrue(SodiumBridge.verify(public, message, signature))
        assertFalse(SodiumBridge.verify(public, "tampered".encodeToByteArray(), signature))
        val key = ByteArray(32) { it.toByte() }
        val nonce = SodiumBridge.nonce()
        val aad = "domain-bound".encodeToByteArray()
        val sealed = SodiumBridge.seal(key, nonce, aad, message)
        assertContentEquals(message, SodiumBridge.open(key, nonce, aad, sealed))
        assertFails { SodiumBridge.open(key, nonce, "wrong".encodeToByteArray(), sealed) }
        sealed[0] = (sealed[0].toInt() xor 1).toByte()
        assertFails { SodiumBridge.open(key, nonce, aad, sealed) }
        seed.fill(0)
    }
}
