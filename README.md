# Serein

Serein is a Kotlin Multiplatform / Compose **local-first prototype**. It is not yet a messenger. The application now has a usable conversation-shaped UI, persistent on-device Ed25519 identity, manually added contacts and encrypted **local unsent drafts**. It does **not send messages** or connect to other devices; the drafts are deliberately labeled as such. No security audit has been performed.

## Structure

- `composeApp/src/commonMain`: Compose mobile/desktop UI, typed local contact/draft model, vault contract.
- `composeApp/src/androidMain`: Android entry point, Android Keystore AES-256-GCM vault, Ed25519 generation.
- `composeApp/src/desktopMain`: JVM desktop entry point, passphrase-derived AES-256-GCM vault, Ed25519 generation.
- `.github/workflows/build.yml`: Android debug APK, Windows EXE, Linux DEB build matrix.

## Run / build

Requires JDK 17 and Android SDK to build Android. Use `./gradlew :composeApp:assembleDebug` for a **debug-signed** APK, or `./gradlew :composeApp:packageDistributionForCurrentOS` for a Windows/Linux desktop installer. The CI archives expire after 14 days. Windows/Linux users must choose a passphrase of at least 12 characters on first launch and enter it again on subsequent launches. **There is no recovery if it is lost.** Android stores its vault key in Android Keystore (hardware-backed only if the device provides that capability). Android backup is disabled. The app stores a public Ed25519 key, private seed, contact names/public keys, and unsent drafts in its encrypted vault. No data is sent over the network. Resetting your identity erases contacts and drafts. This is an early design; do not store sensitive data until it has been independently audited.

## Known limitations

Ed25519 is generated with Bouncy Castle, **not libsodium**. The fingerprint shown is the first 6 bytes of SHA-256; compare public keys/fingerprints through a trusted independent channel. Password-based desktop encryption uses PBKDF2-HMAC-SHA256 (310,000 iterations) and AES-GCM; it has not been independently reviewed. No authenticated key exchange, libsodium XChaCha20-Poly1305, SQLCipher, QR enrollment, peer discovery, Internet/mesh delivery, Tor, Nearby Connections, Multipeer Connectivity, WebRTC, media, or SOS mode is implemented. macOS/iOS builds are not configured. Never treat drafts as delivered messages. A production decentralized messenger requires a reviewed protocol, transport integration, reliable delivery, safe key management, and cross-platform security testing.
