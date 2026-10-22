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

    // ------------------------------------------------------------------
    // Classic activity-result flows (see ui/ActivityResults.kt for why the
    // Compose registry launchers are unsafe on this FragmentActivity host).
    // ------------------------------------------------------------------

    /** System photo picker (API 33+) or document picker for chat media. */
    fun launchMediaPicker() {
        val intent = if (android.os.Build.VERSION.SDK_INT >= 33) {
            android.content.Intent(android.provider.MediaStore.ACTION_PICK_IMAGES).apply {
                putExtra(android.provider.MediaStore.EXTRA_PICK_IMAGES_MAX, 1)
            }
        } else {
            android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(android.content.Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            }
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_PICK_MEDIA)
    }

    /** Document picker for choosing a backup file to restore. */
    fun launchBackupFilePicker() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_PICK_BACKUP)
    }

    /** System "create document" saver for a backup (payload in PendingFileWrite). */
    fun launchCreateBackupDocument(fileName: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(android.content.Intent.EXTRA_TITLE, fileName)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_CREATE_BACKUP)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            if (requestCode == REQUEST_CREATE_BACKUP) {
                com.mahad.openbuddy.ui.PendingFileWrite.payload = null
            }
            return
        }
        when (requestCode) {
            REQUEST_PICK_MEDIA -> data?.data?.let {
                com.mahad.openbuddy.ui.ActivityResults.pickedMedia.tryEmit(it)
            }
            REQUEST_PICK_BACKUP -> data?.data?.let {
                com.mahad.openbuddy.ui.ActivityResults.pickedBackupFile.tryEmit(it)
            }
            REQUEST_CREATE_BACKUP -> {
                val uri = data?.data
                val payload = com.mahad.openbuddy.ui.PendingFileWrite.payload
                var ok = false
                if (uri != null && payload != null) {
                    try {
                        contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(payload.toByteArray(Charsets.UTF_8))
                            out.flush()
                        }
                        ok = true
                    } catch (e: Exception) {
                        ok = false
                    }
                }
                com.mahad.openbuddy.ui.PendingFileWrite.payload = null
                com.mahad.openbuddy.ui.ActivityResults.backupDocumentSaved.tryEmit(ok)
            }
        }
    }

    companion object {
        const val REQUEST_RECORD_AUDIO = 0x0B01
        const val REQUEST_PICK_MEDIA = 0x0B02
        const val REQUEST_PICK_BACKUP = 0x0B03
        const val REQUEST_CREATE_BACKUP = 0x0B04
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
