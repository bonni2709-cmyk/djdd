package app.serein

/** Text encoded in the QR is public data only; never place a seed/private key inside. */
const val CONTACT_SCHEME = "serein:v1:"

fun parseContactQr(value: String): String? {
    if (!value.startsWith(CONTACT_SCHEME)) return null
    val key = value.removePrefix(CONTACT_SCHEME)
    return try { if (decodeBase64Url(key).size == 32) key else null }
    catch (_: Exception) { null }
}

data class QrGrid(val size: Int, val cells: BooleanArray) {
    fun dark(x: Int, y: Int) = cells[y * size + x]
}

expect fun encodeContactQr(publicKey: String): QrGrid

expect fun scanContactQr(onResult: (String?) -> Unit)
