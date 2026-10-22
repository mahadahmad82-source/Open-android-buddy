package com.mahad.openbuddy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.mahad.openbuddy.ui.screens.MainScreen
import com.mahad.openbuddy.ui.theme.OpenBuddyTheme
import com.mahad.openbuddy.ui.theme.GlassBackground
import com.mahad.openbuddy.ui.viewmodel.MainViewModel

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /**
     * Requests the microphone permission the classic way, with a small fixed
     * request code. The Compose ActivityResultLauncher route crashes on
     * FragmentActivity hosts ("Can only use lower 16 bits for requestCode")
     * for the RequestPermission contract — this was the Voice tab crash.
     * Screens observe the outcome via [com.mahad.openbuddy.ui.PermissionResults].
     */
    fun requestRecordAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            com.mahad.openbuddy.ui.PermissionResults.recordAudio.tryEmit(granted)
        }
    }

    companion object {
        const val REQUEST_RECORD_AUDIO = 0x0B01
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Save the stack trace of any crash so it can be shown on next launch.
        com.mahad.openbuddy.data.CrashReporter.install(this)
        enableEdgeToEdge()
        setContent {
            OpenBuddyTheme {
                // Screenshots intentionally allowed everywhere (owner's
                // decision 2026-10-23 — FLAG_SECURE fully removed).

                GlassBackground(modifier = Modifier.fillMaxSize()) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
    }
}
