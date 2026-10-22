package com.mahad.openbuddy.ui

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Results of file pickers launched by MainActivity via the classic
 * startActivityForResult flow.
 *
 * Why not the Compose ActivityResultLauncher? On this FragmentActivity host
 * the activity-result registry can hand out request codes above 0xFFFF,
 * which FragmentActivity rejects with "Can only use lower 16 bits for
 * requestCode" — the confirmed cause of both the Voice tab crash and the
 * media-picker crash. Classic launches use small fixed codes and are safe.
 */
object ActivityResults {
    /** Image/video picked in chat for attachment. */
    val pickedMedia = MutableSharedFlow<Uri>(extraBufferCapacity = 1)

    /** Backup file picked for restore. */
    val pickedBackupFile = MutableSharedFlow<Uri>(extraBufferCapacity = 1)

    /** Outcome of writing a backup to a user-created document. */
    val backupDocumentSaved = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
}

/** Backup payload waiting to be written into a document the user creates. */
object PendingFileWrite {
    var payload: String? = null
}
