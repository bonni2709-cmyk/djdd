package app.serein

import com.google.zxing.qrcode.encoder.Encoder
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

actual fun encodeContactQr(publicKey: String): QrGrid {
    val matrix = Encoder.encode(CONTACT_SCHEME + publicKey, ErrorCorrectionLevel.M).matrix
    return QrGrid(matrix.width + 8, BooleanArray((matrix.width + 8) * (matrix.height + 8)) { index ->
        val x = index % (matrix.width + 8) - 4
        val y = index / (matrix.width + 8) - 4
        x in 0 until matrix.width && y in 0 until matrix.height && matrix.get(x, y).toInt() == 1
    })
}

actual fun scanContactQr(onResult: (String?) -> Unit) { onResult(null) }
