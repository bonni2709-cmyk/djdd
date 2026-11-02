package app.serein

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : ComponentActivity() {
    private var scanCallback: ((String?) -> Unit)? = null
    private val scanner = registerForActivityResult(ScanContract()) { result ->
        scanCallback?.invoke(result.contents)
        scanCallback = null
    }

    fun scanQr(callback: (String?) -> Unit) {
        scanCallback = callback
        scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Scan a Serein public key").setBeepEnabled(false))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContext = applicationContext
        activeActivity = this
        setContent { SereinApp() }
    }

    override fun onDestroy() {
        if (activeActivity === this) activeActivity = null
        super.onDestroy()
    }
}
