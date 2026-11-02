package app.veil.ui

/** The public identifier shown in the UI. Private key material never leaves the platform vault. */
data class LocalIdentity(val publicKey: String, val fingerprint: String)

/** Creates an Ed25519 keypair on this device. This MVP keeps it in process memory only. */
expect fun createLocalIdentity(): LocalIdentity

/** Current wall-clock time in milliseconds, implemented by each platform source set. */
expect fun currentTimeMillis(): Long
