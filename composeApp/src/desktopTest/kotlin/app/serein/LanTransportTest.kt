package app.serein

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanTransportTest {
    @Test
    fun authenticatedMessageReachesPinnedPeer() {
        val host = localLanAddress()
        if (!host.matches(Regex("[0-9.]+"))) return // CI host may have no private IPv4 address.
        val sender = generateIdentity()
        val recipient = generateIdentity()
        val received = CountDownLatch(1)
        var payload: String? = null
        val server = LanTransport(recipient, { setOf(sender.publicKey) }) { key, message ->
            if (key == sender.publicKey) { payload = message; received.countDown(); true } else false
        }
        val client = LanTransport(sender, { setOf(recipient.publicKey) }) { _, _ -> false }
        try {
            assertNull(server.start(), "TCP port $LAN_PORT must be available for the test")
            val completed = CountDownLatch(1)
            var failure: String? = "not finished"
            client.send(Contact(recipient.publicKey, "Peer", host), "Encrypted LAN test") {
                failure = it; completed.countDown()
            }
            assertTrue(completed.await(12, TimeUnit.SECONDS), "Send timed out")
            assertNull(failure)
            assertTrue(received.await(2, TimeUnit.SECONDS))
            assertEquals("Encrypted LAN test", payload)
        } finally {
            client.stop(); server.stop(); sender.clear(); recipient.clear()
        }
    }
}
