package app.serein

import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSmokeTest {
    @Test fun nativeLibsodiumVaultAndPrivateScreen() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val identity = generateIdentity() // Native library must actually load on x86_64 Android.
        try {
            val text = "Android runtime test".encodeToByteArray()
            val signature = SodiumBridge.sign(identity.secretSeed, text)
            assertTrue(SodiumBridge.verify(decodeBase64Url(identity.publicKey), text, signature))
            val key = ByteArray(32) { it.toByte() }
            val nonce = SodiumBridge.nonce()
            val sealed = SodiumBridge.seal(key, nonce, byteArrayOf(1), text)
            assertArrayEquals(text, SodiumBridge.open(key, nonce, byteArrayOf(1), sealed))
            val repo = LocalRepository(SecureVault(""))
            assertEquals(identityFromSeed(decodeBase64Url(repo.data.seed)).publicKey,
                LocalRepository(SecureVault("")).data.let { identityFromSeed(decodeBase64Url(it.seed)).publicKey })
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                }
            }
            repo.erase()
        } finally { identity.clear() }
    }
}
