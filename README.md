# Veil

**Veil** is a premium, privacy-minded cross-platform messenger prototype built with Kotlin Multiplatform and Compose Multiplatform. Its visual language is quiet and editorial: warm off-white surfaces, deep green accents, restrained typography, and adaptive navigation—no neon or cyberpunk styling.

> **Security and scope notice:** this repository is an early UI/identity prototype, not a finished secure messenger. It generates an Ed25519 keypair on-device using Bouncy Castle, but currently retains key material only in process memory. Contacts and messages are local demo data; there is no network transport, peer-to-peer mesh, calling, encrypted message protocol, encrypted database, OS keychain persistence, or secure SOS wipe. Do not use it for sensitive conversations. The visible expiry timer and lock screen are prototype interactions, not a security guarantee.

## Project structure

```text
.
├── .github/workflows/build.yml       # Android APK + Windows installer EXE matrix
├── composeApp/
│   ├── src/commonMain/                # Shared Compose UI and app state
│   ├── src/androidMain/               # Android activity and Ed25519 implementation
│   └── src/desktopMain/               # Desktop entry point and Ed25519 implementation
├── build.gradle.kts                   # Root Kotlin/Compose/Android plugin versions
├── settings.gradle.kts
└── gradle.properties
```

The current build targets Android and Windows desktop. The shared UI is structured for further KMP targets, but iOS and Linux packaging are not configured yet.

## Implemented prototype

- On-device Ed25519 public identity and short fingerprint, generated at launch.
- Adaptive desktop sidebar and mobile conversation navigation.
- Add a local contact by pasting a public key; copy your public identifier.
- Local-only message composition and in-memory expiry (one minute / one hour).
- A session-hiding prototype screen with a local-session reset action.
- No accounts, phone numbers, permissions, or network access in this prototype.

## Build locally

Use JDK 17 and Gradle 8.11.1 or open the project in Android Studio with Kotlin Multiplatform support.

```sh
gradle :composeApp:assembleDebug
gradle :composeApp:run                    # Desktop preview
# Windows only; WiX Toolset is required for the installer EXE:
gradle :composeApp:packageReleaseExe
```

The debug APK is written under `composeApp/build/outputs/apk/debug/`. The Windows installer is written under `composeApp/build/compose/binaries/main-release/exe/`.

## GitHub Actions

`.github/workflows/build.yml` runs a two-entry matrix on pushes to `main` and `arena/**`, or via **Actions → Build Veil APK and Windows EXE → Run workflow**. It uploads `veil-android-apk` and `veil-windows-exe` artifacts for 30 days. Build artifacts are available from the workflow run's **Artifacts** section; they are not public release downloads.
