package app.serein

/** Experimental direct-LAN protocol. Not a reviewed messaging protocol; peer IP is exposed. */
expect class LanTransport(identity: Identity, knownKeys: () -> Set<String>, onMessage: (String, String) -> Boolean) {
    fun start(): String?
    fun stop()
    fun send(contact: Contact, text: String, onResult: (String?) -> Unit)
}

const val LAN_PORT = 45717
