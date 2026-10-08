package com.example.data.voice

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import com.example.domain.model.ParsedVoiceCommand
import com.example.domain.model.VoiceActionType
import java.net.URLEncoder

object VoiceIntentExecutor {

    /**
     * Resolves a contact name to a phone number if READ_CONTACTS permission is granted.
     * Returns null if contact not found or permission not granted.
     */
    fun resolveContactNumber(context: Context, contactName: String): String? {
        if (contactName.isBlank()) return null
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%${contactName.trim()}%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numberIdx != -1) {
                        return cursor.getString(numberIdx)
                    }
                }
            }
        } catch (_: Exception) {
            // Graceful fallback
        }
        return null
    }

    /**
     * Finds a launch intent for an installed application matching [appName].
     */
    fun findAppLaunchIntent(context: Context, appName: String): Intent? {
        val pm = context.packageManager
        val query = appName.trim().lowercase()

        // 1. Direct package match for common systems
        val commonPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "settings" to "com.android.settings",
            "calculator" to "com.google.android.calculator",
            "camera" to "com.google.android.GoogleCamera",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "photos" to "com.google.android.apps.photos",
            "gallery" to "com.google.android.apps.photos",
            "clock" to "com.google.android.deskclock",
            "calendar" to "com.google.android.calendar",
            "gmail" to "com.google.android.gm",
            "email" to "com.google.android.gm",
            "whatsapp" to "com.whatsapp",
            "spotify" to "com.spotify.music",
            "play store" to "com.android.vending",
            "files" to "com.google.android.apps.nbu.files"
        )

        val directPkg = commonPackages[query]
        if (directPkg != null) {
            val intent = pm.getLaunchIntentForPackage(directPkg)
            if (intent != null) return intent
        }

        // 2. Search installed launchable activities
        try {
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)

            // Exact match
            for (info in resolveInfos) {
                val label = info.loadLabel(pm).toString().lowercase()
                if (label == query) {
                    val launch = pm.getLaunchIntentForPackage(info.activityInfo.packageName)
                    if (launch != null) return launch
                }
            }

            // Substring match
            for (info in resolveInfos) {
                val label = info.loadLabel(pm).toString().lowercase()
                if (label.contains(query) || query.contains(label)) {
                    val launch = pm.getLaunchIntentForPackage(info.activityInfo.packageName)
                    if (launch != null) return launch
                }
            }
        } catch (_: Exception) {
            // Fallback
        }

        return null
    }

    /**
     * Executes the parsed voice action by opening the appropriate target Android app with prefilled data.
     * Always relies on standard intents without privileged permissions.
     */
    fun execute(
        context: Context,
        command: ParsedVoiceCommand,
        manualRecipientOverride: String? = null
    ): Result<String> {
        return try {
            when (command.actionType) {
                VoiceActionType.OPEN_APP -> executeOpenApp(context, command)
                VoiceActionType.DIAL -> executeDial(context, command, manualRecipientOverride)
                VoiceActionType.SMS_COMPOSE -> executeSms(context, command, manualRecipientOverride)
                VoiceActionType.EMAIL_COMPOSE -> executeEmail(context, command)
                VoiceActionType.WHATSAPP_COMPOSE -> executeWhatsApp(context, command, manualRecipientOverride)
                VoiceActionType.YOUTUBE_SEARCH -> executeYouTubeSearch(context, command)
                VoiceActionType.OPEN_URL -> executeOpenUrl(context, command)
                VoiceActionType.WEB_SEARCH -> executeWebSearch(context, command)
                VoiceActionType.SET_ALARM -> executeSetAlarm(context, command)
                VoiceActionType.SET_TIMER -> executeSetTimer(context, command)
            }
        } catch (e: ActivityNotFoundException) {
            Result.failure(Exception("No compatible app found to handle this action."))
        } catch (e: Exception) {
            Result.failure(Exception("Could not launch action: ${e.message}"))
        }
    }

    private fun executeOpenApp(context: Context, command: ParsedVoiceCommand): Result<String> {
        val appName = command.target
        val launchIntent = findAppLaunchIntent(context, appName)
            ?: return Result.failure(Exception("Could not find installed application matching '$appName'."))

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return Result.success("Opening $appName...")
    }

    private fun executeDial(
        context: Context,
        command: ParsedVoiceCommand,
        manualOverride: String?
    ): Result<String> {
        val number = manualOverride?.ifBlank { null }
            ?: command.resolvedNumber
            ?: (if (command.requiresContactResolution && command.contactName != null) {
                resolveContactNumber(context, command.contactName)
            } else null)
            ?: command.params["phoneNumber"]
            ?: command.params["number"]

        if (number.isNullOrBlank()) {
            return Result.failure(Exception("Please provide a valid phone number or grant Contacts permission to call ${command.target}."))
        }

        val cleanNumber = number.replace(Regex("[^0-9+*#]"), "")
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(dialIntent)
        return Result.success("Opening dialer for $cleanNumber...")
    }

    private fun executeSms(
        context: Context,
        command: ParsedVoiceCommand,
        manualOverride: String?
    ): Result<String> {
        val number = manualOverride?.ifBlank { null }
            ?: command.resolvedNumber
            ?: (if (command.requiresContactResolution && command.contactName != null) {
                resolveContactNumber(context, command.contactName)
            } else null)
            ?: command.params["phoneNumber"]
            ?: command.params["number"]
            ?: ""

        val message = command.message ?: command.params["message"] ?: command.params["body"] ?: ""
        val cleanNumber = number.replace(Regex("[^0-9+*#]"), "")
        val uri = if (cleanNumber.isNotBlank()) Uri.parse("smsto:$cleanNumber") else Uri.parse("smsto:")

        val smsIntent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(smsIntent)
        return Result.success("Opening messaging with prefilled SMS...")
    }

    private fun executeEmail(context: Context, command: ParsedVoiceCommand): Result<String> {
        val email = command.params["email"] ?: command.params["to"] ?: ""
        val subject = command.params["subject"] ?: ""
        val body = command.params["body"] ?: command.params["message"] ?: ""

        val uri = if (email.isNotBlank()) Uri.parse("mailto:$email") else Uri.parse("mailto:")
        val emailIntent = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(emailIntent)
        return Result.success("Opening email client...")
    }

    private fun executeWhatsApp(
        context: Context,
        command: ParsedVoiceCommand,
        manualOverride: String?
    ): Result<String> {
        val number = manualOverride?.ifBlank { null }
            ?: command.resolvedNumber
            ?: (if (command.requiresContactResolution && command.contactName != null) {
                resolveContactNumber(context, command.contactName)
            } else null)
            ?: command.params["phoneNumber"]
            ?: command.params["number"]
            ?: ""

        val message = command.message ?: command.params["message"] ?: ""
        val cleanNumber = number.replace(Regex("[^0-9+]"), "")
        val encodedMsg = try { URLEncoder.encode(message, "UTF-8") } catch (_: Exception) { "" }

        val url = if (cleanNumber.isNotBlank()) {
            "https://wa.me/$cleanNumber?text=$encodedMsg"
        } else {
            "https://wa.me/?text=$encodedMsg"
        }

        val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(waIntent)
        return Result.success("Opening WhatsApp with prefilled message...")
    }

    private fun executeYouTubeSearch(context: Context, command: ParsedVoiceCommand): Result<String> {
        val query = command.target.ifBlank { command.params["query"] ?: "" }
        val encodedQuery = try { URLEncoder.encode(query, "UTF-8") } catch (_: Exception) { query }

        val appUri = Uri.parse("vnd.youtube:results?q=$encodedQuery")
        val appIntent = Intent(Intent.ACTION_VIEW, appUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(appIntent)
            return Result.success("Searching YouTube for '$query'...")
        } catch (_: Exception) {
            // Fallback to Web URL
            val webUri = Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")
            val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            return Result.success("Searching YouTube on web for '$query'...")
        }
    }

    private fun executeOpenUrl(context: Context, command: ParsedVoiceCommand): Result<String> {
        var url = command.target.ifBlank { command.params["url"] ?: "https://google.com" }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return Result.success("Opening $url...")
    }

    private fun executeWebSearch(context: Context, command: ParsedVoiceCommand): Result<String> {
        val query = command.target.ifBlank { command.params["query"] ?: "" }
        try {
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(searchIntent)
            return Result.success("Searching the web for '$query'...")
        } catch (_: Exception) {
            val encoded = try { URLEncoder.encode(query, "UTF-8") } catch (_: Exception) { query }
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            return Result.success("Searching Google for '$query'...")
        }
    }

    private fun executeSetAlarm(context: Context, command: ParsedVoiceCommand): Result<String> {
        val hour = command.params["hour"]?.toIntOrNull() ?: 7
        val minutes = command.params["minutes"]?.toIntOrNull() ?: 0
        val message = command.message ?: command.params["message"] ?: "Alarm"

        val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minutes)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(alarmIntent)
        return Result.success("Setting alarm for $hour:${minutes.toString().padStart(2, '0')}...")
    }

    private fun executeSetTimer(context: Context, command: ParsedVoiceCommand): Result<String> {
        val seconds = command.params["seconds"]?.toIntOrNull()
            ?: command.params["length"]?.toIntOrNull()
            ?: 300
        val message = command.message ?: command.params["message"] ?: "Timer"

        val timerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(timerIntent)
        return Result.success("Setting timer for $seconds seconds...")
    }
}
