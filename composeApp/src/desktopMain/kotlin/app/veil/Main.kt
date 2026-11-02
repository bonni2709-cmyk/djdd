package app.veil

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.veil.ui.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Veil — Private Messenger") {
        App()
    }
}
