package app.serein

import android.os.Bundle
import android.graphics.Color
import android.view.WindowManager
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
        // Block system screenshots, screen recording and recent-app previews on Android.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Color.rgb(17, 25, 22)
        window.navigationBarColor = Color.rgb(17, 25, 22)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0
        activeActivity = this
        setContent { SereinApp() }
    }

    override fun onDestroy() {
        if (activeActivity === this) activeActivity = null
        super.onDestroy()
    }
}
