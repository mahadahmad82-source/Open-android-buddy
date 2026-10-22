package com.mahad.openbuddy.ui

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Results of runtime-permission requests issued by MainActivity.
 *
 * Why not the Compose ActivityResultLauncher? With a FragmentActivity host,
 * launching the RequestPermission contract through the activity-result
 * registry can crash with "Can only use lower 16 bits for requestCode"
 * (IllegalArgumentException from FragmentActivity.checkForValidRequestCode) —
 * this was the Voice tab crash. Permissions are therefore requested the
 * classic way with a small fixed request code, and results are broadcast
 * here for whichever screen asked.
 */
object PermissionResults {
    val recordAudio = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
}
