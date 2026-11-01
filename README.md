# Serein

A **prototype**, not a secure messenger. Calm, light Compose Multiplatform UI with a sidebar on desktop and bottom navigation on Android. It generates an Ed25519 identity locally on launch and shows the public key and SHA-256 fingerprint. The private seed remains in process memory only and is lost on exit. There is no messaging or network transport yet.

## Layout

- `composeApp/src/commonMain`: shared Compose UI and identity contract.
- `composeApp/src/androidMain`: Android entry point and Ed25519 implementation.
- `composeApp/src/desktopMain`: JVM desktop entry point and Ed25519 implementation (Windows/Linux).
- `.github/workflows/build.yml`: Android debug APK, Windows EXE, Linux DEB CI matrix.

## Build

Requires JDK 17 and an Android SDK for Android builds. `./gradlew :composeApp:assembleDebug` builds a **debug-signed, non-release** APK; `./gradlew :composeApp:packageDistributionForCurrentOS` builds the native desktop package on Windows or Linux. CI artifacts expire after 14 days. macOS and iOS are not configured.

## Security boundaries / roadmap

Ed25519 is generated using Bouncy Castle, **not libsodium**. Fingerprint display is not contact verification. Key persistence, authenticated key exchange, ratcheting, XChaCha20-Poly1305, SQLCipher, QR enrollment, peer discovery, multihop routing, Tor, Nearby Connections, Multipeer Connectivity, WebRTC, media, and SOS are **not implemented**. These need protocol design, platform permissions, integration tests, and independent security review before any privacy claims or real-world use. Never publish an unverified public key as proof of identity; never use the prototype for sensitive communications.
