package app.serein

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Experimental direct-LAN, one message per TCP connection. Identity signs an ephemeral X25519
 * key for every connection. The receiving side must already have the sender's Ed25519 key pinned.
 * No offline queue, ratchet, replay database, or metadata privacy: NOT a production protocol.
 */
actual class LanTransport actual constructor(
    private val identity: Identity,
    private val knownKeys: () -> Set<String>,
    private val onMessage: (String, String) -> Boolean
) {
    private var server: ServerSocket? = null
    private val magic = "serein-lan-v1".toByteArray(Charsets.US_ASCII)

    actual fun start(): String? = try {
        server = ServerSocket(LAN_PORT).apply { reuseAddress = true }
        val listening = server!!
        Thread({
            while (!listening.isClosed) {
                try {
                    val socket = listening.accept()
                    Thread({ try { receive(socket) } catch (_: Exception) { socket.close() } }, "serein-peer").apply {
                        isDaemon = true; start()
                    }
                } catch (_: Exception) { if (listening.isClosed) break }
            }
        }, "serein-listener").apply { isDaemon = true; start() }
        null
    } catch (_: Exception) { "Port $LAN_PORT unavailable. LAN reception is off." }

    actual fun stop() { server?.close(); server = null }

    actual fun send(contact: Contact, text: String, onResult: (String?) -> Unit) {
        Thread({
            val error = try {
                require(contact.host.isNotBlank()) { "Set a peer LAN IP address in Contacts." }
                require(text.isNotBlank() && text.encodeToByteArray().size <= 16_384) { "Message must be 1–16384 bytes." }
                val address = InetAddress.getByName(contact.host)
                // Require a numeric IPv4 LAN address; never resolve untrusted hostnames.
                require(contact.host.matches(Regex("[0-9.]+")) && address.isSiteLocalAddress) {
                    "Use a private IPv4 LAN address (e.g. 192.168.1.20)."
                }
                Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(address, LAN_PORT), 5000)
                    socket.soTimeout = 8000
                    val input = DataInputStream(socket.getInputStream())
                    val output = DataOutputStream(socket.getOutputStream())
                    val ephemeral = X25519PrivateKeyParameters(SecureRandom())
                    val localHello = hello(ephemeral)
                    output.write(localHello); output.flush()
                    val remoteHello = ByteArray(128).also { input.readFully(it) }
                    val peerKey = verify(remoteHello)
                    require(peerKey == contact.publicKey) { "Peer key does not match this contact." }
                    val key = sessionKey(ephemeral, remoteHello, localHello, remoteHello)
                    val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                    cipher.updateAAD(localHello + remoteHello)
                    val frame = nonce + cipher.doFinal(text.encodeToByteArray())
                    key.fill(0)
                    output.writeInt(frame.size); output.write(frame); output.flush()
                    // Explicit acknowledgement: success means the receiving app persisted the frame.
                    require(input.readUnsignedByte() == 1) { "Peer did not acknowledge message." }
                }
                null
            } catch (e: Exception) { e.message ?: "LAN connection failed." }
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
            val ephemeral = X25519PrivateKeyParameters(SecureRandom())
            val localHello = hello(ephemeral)
            output.write(localHello); output.flush()
            val key = sessionKey(ephemeral, remoteHello, remoteHello, localHello)
            val size = input.readInt()
            require(size in 29..16_412) { "Invalid frame size" }
            val frame = ByteArray(size).also { bytes -> input.readFully(bytes) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, frame.copyOfRange(0, 12)))
            cipher.updateAAD(remoteHello + localHello)
            val message = cipher.doFinal(frame.copyOfRange(12, size)).decodeToString()
            key.fill(0)
            require(message.isNotBlank()) { "Empty message" }
            require(onMessage(peerKey, message)) { "Message could not be saved" }
            output.writeByte(1); output.flush()
        }
    }

    private fun hello(ephemeral: X25519PrivateKeyParameters): ByteArray {
        val public = ByteArray(32).also { ephemeral.generatePublicKey().encode(it, 0) }
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(identity.secretSeed, 0))
        signer.update(magic + public, 0, magic.size + public.size)
        return decodeBase64Url(identity.publicKey) + public + signer.generateSignature()
    }

    private fun verify(hello: ByteArray): String {
        require(hello.size == 128)
        val signer = Ed25519Signer()
        signer.init(false, Ed25519PublicKeyParameters(hello, 0))
        val signed = magic + hello.copyOfRange(32, 64)
        signer.update(signed, 0, signed.size)
        require(signer.verifySignature(hello.copyOfRange(64, 128))) { "Invalid peer signature" }
        return hello.copyOfRange(0, 32).toBase64Url()
    }

    private fun sessionKey(ephemeral: X25519PrivateKeyParameters, peerHello: ByteArray,
                           clientHello: ByteArray, serverHello: ByteArray): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(ephemeral)
        val shared = ByteArray(32)
        agreement.calculateAgreement(X25519PublicKeyParameters(peerHello, 32), shared, 0)
        require(shared.any { it != 0.toByte() }) { "Invalid peer key" }
        val transcript = MessageDigest.getInstance("SHA-256").digest(magic + clientHello + serverHello)
        val key = ByteArray(32)
        HKDFBytesGenerator(SHA256Digest()).apply {
            init(HKDFParameters(shared, transcript, "serein-lan-v1 aes-gcm".toByteArray()))
            generateBytes(key, 0, key.size)
        }
        shared.fill(0)
        return key
    }
}
