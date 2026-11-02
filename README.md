# Serein

Serein is an **experimental** local-first Kotlin Multiplatform / Compose messenger prototype. It has persistent on-device Ed25519 identities, contacts, a responsive chat UI, and **direct online text delivery on the same private IPv4 LAN**. Both devices must be online with the app open, have each other's public keys saved, and know each other's current LAN IP address. It is **not ready for sensitive communications**; the custom transport protocol has not been independently audited.

## Try two devices

1. Install on Android/Windows/Linux. Desktop users choose a passphrase of at least 12 characters; Android uses its Keystore.
2. On each device open **Identity**, copy/select the Base64 URL public key. Compare the entire key through an independent trusted channel (short fingerprints are for visual orientation only).
3. Open **Contacts** on each device and add the other's public key and the private IPv4 address shown under "Your LAN address" on the other's device. Use the same Wi-Fi/LAN; allow TCP port **45717** through your firewall and disable Wi-Fi client isolation. Internet routing is not implemented.
4. Open **Chats**, select the contact and send a short text. A send is shown only after receiving an acknowledgement that the peer accepted and saved it. There is no offline queue, retry, sync or delivery after both apps close.

## Structure / build

- `composeApp/src/commonMain`: responsive UI, encrypted local contacts/message model, vault/transport contracts.
- `composeApp/src/androidMain`: Android entry point, Android Keystore AES-256-GCM vault, Ed25519 and experimental LAN TCP transport.
- `composeApp/src/desktopMain`: JVM desktop entry point, passphrase-derived AES-256-GCM vault, Ed25519 and experimental LAN TCP transport.
- `.github/workflows/build.yml`: CI builds Android **debug APK**, Windows EXE and Linux DEB. Artifacts expire after 14 days.

Requires JDK 17 + Android SDK for Android builds. `./gradlew :composeApp:assembleDebug` or `./gradlew :composeApp:packageDistributionForCurrentOS` (Windows/Linux).

## Threat model and limits

The experimental LAN handshake signs a fresh X25519 ephemeral public key with the local Ed25519 key, checks the peer against a pinned contact key, derives a session key using HKDF-SHA256 and encrypts one message with AES-256-GCM per connection. The protocol exposes both peers' IP addresses and traffic metadata. It has **no audited ratchet**, no forward secrecy guarantee after message receipt on a compromised device, no verification UX beyond manual key comparison, no groups, no anti-spam, no peer discovery, no offline delivery, and no key transparency. Do not treat this as a reviewed end-to-end encryption protocol. The APK is debug-signed. A firewall or router may prevent LAN connections.

The local vault uses Android Keystore AES-GCM (hardware-backed only if supported by the device) or desktop PBKDF2-HMAC-SHA256 (310,000 iterations) + AES-GCM. Desktop passwords cannot be recovered. Android backup is disabled. A reset changes the identity and erases local contacts/messages. Crypto primitives come from Bouncy Castle / Java, **not libsodium**. **SQLCipher**, QR scanning, Bluetooth/Wi-Fi Direct mesh and multihop, Tor, Nearby Connections, Multipeer Connectivity, WebRTC, voice/video, stickers, SOS, iOS and macOS packaging are not implemented. These need platform-specific work and independent security review before any privacy claims.

## QR contact enrollment and macOS

Identity screen displays a QR code containing **only** the public key. Android's Contacts screen can scan it with its camera; the other peer must still be verified in person and their current LAN IP entered manually. Desktop users can paste the public key; webcam capture is not implemented. Compose Desktop's macOS DMG is built on the macOS GitHub Actions runner; this is a JVM desktop build, **not an iOS app**.

LAN reception limits concurrent inbound sockets to eight, reads at most 16 KiB of message plaintext per connection, and keeps at most 1,000 messages in the encrypted local vault. This is a bounded prototype, not a production denial-of-service defense.
