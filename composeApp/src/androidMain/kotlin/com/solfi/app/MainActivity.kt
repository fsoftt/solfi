package com.solfi.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.solfi.app.platform.AppContext
import com.solfi.app.platform.MicPermission

class MainActivity : ComponentActivity() {
    private var pendingMic: ((Boolean) -> Unit)? = null
    private val micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingMic?.invoke(granted)
        pendingMic = null
    }
    private val requestMic: ((Boolean) -> Unit) -> Unit = { cb ->
        pendingMic = cb
        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppContext.app = applicationContext
        MicPermission.requester = requestMic
        setContent { App() }
    }

    override fun onDestroy() {
        if (MicPermission.requester === requestMic) MicPermission.requester = null
        super.onDestroy()
    }
}
