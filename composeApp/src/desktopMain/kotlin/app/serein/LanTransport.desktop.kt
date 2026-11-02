package app.serein

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Semaphore
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Experimental direct-LAN handshake: signed ephemeral X25519, HKDF-SHA256, XChaCha20-Poly1305.
 * Each connection carries one message. Peer identity must be pinned out-of-band; no ratchet or
 * metadata privacy. Native libsodium operations fail closed if the library is unavailable.
 */
actual class LanTransport actual constructor(
    private val identity: Identity,
    private val knownKeys: () -> Set<String>,
    private val onMessage: (String, String, Long?) -> Boolean
) {
    private var server: ServerSocket? = null
    private val inboundSlots = Semaphore(8)
    private val outboundSlots = Semaphore(4)
    private val magic = "serein-lan-v3".toByteArray(Charsets.US_ASCII)

    actual fun start(): String? = try {
        server = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(LAN_PORT)) }
        val listening = server!!
        Thread({
            while (!listening.isClosed) {
                try {
                    val socket = listening.accept()
                    if (!inboundSlots.tryAcquire()) { socket.close(); continue }
                    Thread({
                        try { receive(socket) }
                        catch (_: Exception) { socket.close() }
                        finally { inboundSlots.release() }
                    }, "serein-peer").apply { isDaemon = true; start() }
                } catch (_: Exception) { if (listening.isClosed) break }
            }
        }, "serein-listener").apply { isDaemon = true; start() }
        null
    } catch (_: Exception) { "Port $LAN_PORT unavailable. LAN reception is off." }

    actual fun stop() { server?.close(); server = null }

    actual fun send(contact: Contact, text: String, expiresAtMillis: Long?, onResult: (String?) -> Unit) {
        if (!outboundSlots.tryAcquire()) { onResult("Too many active connections."); return }
        Thread({
            val error = try {
                require(contact.host.matches(Regex("[0-9.]+"))) { "Enter a numeric private IPv4 address." }
                val address = InetAddress.getByName(contact.host)
                require(address is java.net.Inet4Address && address.isSiteLocalAddress) { "Peer must be on a private IPv4 LAN." }
                require(text.isNotBlank() && text.encodeToByteArray().size <= 16_384) { "Message must be 1–16384 bytes." }
                require(expiresAtMillis == null || expiresAtMillis in (System.currentTimeMillis() + 1000)..(System.currentTimeMillis() + 7L * 86_400_000)) { "Invalid expiry time." }
                val plaintext = Json.encodeToString(LanPayload(text = text, expiresAtMillis = expiresAtMillis)).encodeToByteArray()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, LAN_PORT), 5000)
                    socket.soTimeout = 8000
                    val input = DataInputStream(socket.getInputStream())
                    val output = DataOutputStream(socket.getOutputStream())
                    val (public, secret) = SodiumBridge.ephemeralKeypair()
                    try {
                        val localHello = hello(public)
                        output.write(localHello); output.flush()
                        val remoteHello = ByteArray(128).also { input.readFully(it) }
                        val peerKey = verify(remoteHello)
                        require(peerKey == contact.publicKey) { "Peer key does not match the pinned contact." }
                        val key = sessionKey(secret, remoteHello, localHello, remoteHello)
                        try {
                            val nonce = SodiumBridge.nonce()
                            val frame = nonce + SodiumBridge.seal(key, nonce, localHello + remoteHello, plaintext)
                            output.writeInt(frame.size); output.write(frame); output.flush()
                        } finally { key.fill(0) }
                        require(input.readUnsignedByte() == 1) { "Peer did not acknowledge message." }
                    } finally { secret.fill(0) }
                }
                null
            } catch (e: Exception) { e.message ?: "LAN connection failed." }
            finally { outboundSlots.release() }
            onResult(error)
        }, "serein-send").apply { isDaemon = true; start() }
    }

    private fun receive(socket: Socket) {
        socket.use {
            it.soTimeout = 8000
            val input = DataInputStream(it.getInputStream())
            val output = DataOutputStream(it.getOutputStream())
            val remoteHello = ByteArray(128).also { bytes -> input.readFully(bytes) }
            val peerKey = verify(remoteHello)
            require(peerKey in knownKeys()) { "Unknown peer" }
            val (public, secret) = SodiumBridge.ephemeralKeypair()
            try {
                val localHello = hello(public)
                output.write(localHello); output.flush()
                val key = sessionKey(secret, remoteHello, remoteHello, localHello)
                try {
                    val size = input.readInt()
                    require(size in 41..20_000) { "Invalid frame size" }
                    val frame = ByteArray(size).also { bytes -> input.readFully(bytes) }
                    val envelope = Json.decodeFromString<LanPayload>(SodiumBridge.open(key,
                        frame.copyOfRange(0, 24), remoteHello + localHello,
                        frame.copyOfRange(24, size)).decodeToString())
                    require(envelope.version == 1 && envelope.text.isNotBlank() &&
                        envelope.text.encodeToByteArray().size <= 16_384) { "Invalid message" }
                    val now = System.currentTimeMillis()
                    require(envelope.expiresAtMillis == null ||
                        envelope.expiresAtMillis in now..(now + 7L * 86_400_000)) { "Expired or invalid message" }
                    require(onMessage(peerKey, envelope.text, envelope.expiresAtMillis)) { "Message could not be saved" }
                    output.writeByte(1); output.flush()
                } finally { key.fill(0) }
            } finally { secret.fill(0) }
        }
    }

    private fun hello(ephemeralPublic: ByteArray): ByteArray {
        val signature = SodiumBridge.sign(identity.secretSeed, magic + ephemeralPublic)
        return decodeBase64Url(identity.publicKey) + ephemeralPublic + signature
    }

    private fun verify(hello: ByteArray): String {
        require(hello.size == 128)
        val public = hello.copyOfRange(0, 32)
        require(SodiumBridge.verify(public, magic + hello.copyOfRange(32, 64),
            hello.copyOfRange(64, 128))) { "Invalid peer signature" }
        return public.toBase64Url()
    }

    private fun sessionKey(secret: ByteArray, peerHello: ByteArray,
                           clientHello: ByteArray, serverHello: ByteArray): ByteArray {
        val shared = SodiumBridge.sharedSecret(secret, peerHello.copyOfRange(32, 64))
        try {
            val salt = MessageDigest.getInstance("SHA-256").digest(magic + clientHello + serverHello)
            val prk = hmac(salt, shared)
            try { return hmac(prk, "serein-lan-v3 xchacha20poly1305".toByteArray() + byteArrayOf(1)) }
            finally { prk.fill(0) }
        } finally { shared.fill(0) }
    }

    private fun hmac(key: ByteArray, message: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(message)
}
