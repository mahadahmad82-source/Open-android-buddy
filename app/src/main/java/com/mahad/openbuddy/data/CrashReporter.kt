package com.mahad.openbuddy.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Last-crash capture: an uncaught-exception handler that saves the stack
 * trace to a file before the process dies, so the app can show the exact
 * error on the next launch (and the user can copy/send it for debugging).
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                record(appContext, throwable)
            } catch (_: Exception) {
                // Never let the reporter itself break crash handling.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun record(context: Context, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val text = buildString {
            append("Open Buddy crash report — ").append(timestamp).append("\n\n")
            append(sw.toString())
        }
        try {
            File(context.applicationContext.filesDir, FILE_NAME).writeText(text)
        } catch (_: Exception) {
            // Storage full / unavailable: nothing more we can do.
        }
    }

    fun readLastCrash(context: Context): String? {
        return try {
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            if (file.exists()) file.readText().take(12_000).ifBlank { null } else null
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        try {
            File(context.applicationContext.filesDir, FILE_NAME).delete()
        } catch (_: Exception) {
            // Ignore.
        }
    }
}
