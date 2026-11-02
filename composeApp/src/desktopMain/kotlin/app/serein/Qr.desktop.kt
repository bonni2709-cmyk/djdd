package app.serein

import com.google.zxing.qrcode.encoder.Encoder
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.github.sarxos.webcam.Webcam
import java.util.concurrent.atomic.AtomicBoolean
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

actual fun encodeContactQr(publicKey: String): QrGrid {
    val matrix = Encoder.encode(CONTACT_SCHEME + publicKey, ErrorCorrectionLevel.M).matrix
    return QrGrid(matrix.width + 8, BooleanArray((matrix.width + 8) * (matrix.height + 8)) { index ->
        val x = index % (matrix.width + 8) - 4
        val y = index / (matrix.width + 8) - 4
        x in 0 until matrix.width && y in 0 until matrix.height && matrix.get(x, y).toInt() == 1
    })
}

private val scanning = AtomicBoolean(false)

actual fun scanContactQr(onResult: (String?) -> Unit) {
    if (!scanning.compareAndSet(false, true)) { onResult(null); return }
    Thread({
        var camera: Webcam? = null
        var found: String? = null
        try {
            camera = Webcam.getDefault()
            if (camera != null) {
                camera.open()
                val reader = MultiFormatReader()
                val until = System.currentTimeMillis() + 20_000
                while (System.currentTimeMillis() < until && found == null) {
                    val image = camera.image
                    if (image != null) {
                        val pixels = IntArray(image.width * image.height)
                        image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
                        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels)))
                        try { found = reader.decode(bitmap).text }
                        catch (_: com.google.zxing.ReaderException) { reader.reset() }
                    }
                    if (found == null) Thread.sleep(120)
                }
            }
        } catch (_: Exception) { /* Permission denied or no webcam: return null, never store frames. */ }
        finally {
            try { camera?.close() } catch (_: Exception) { }
            scanning.set(false)
            onResult(found)
        }
    }, "serein-qr-webcam").apply { isDaemon = true; start() }
}
