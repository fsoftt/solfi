package com.solfi.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Solfi",
        state = rememberWindowState(size = DpSize(430.dp, 860.dp)),
    ) {
        App()
    }
}
