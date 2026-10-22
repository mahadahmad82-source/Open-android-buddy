package com.mahad.openbuddy.data.agent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Calendar

/**
 * Phase 3 — on-device tools. Actions that change the outside world
 * (phone calls, SMS) always require the user's approval in chat first.
 * WhatsApp follows the standing anti-ban rule: the tool only OPENS WhatsApp
 * with the text prefilled — the user always presses Send themselves.
 */
class DeviceTools(private val context: Context) {

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun startActivity(intent: Intent): ToolOutcome {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ToolOutcome("Opened successfully.")
        } catch (e: Exception) {
            ToolOutcome("Could not open: ${e.message}")
        }
    }

    fun all(): List<AgentTool> = listOf(

        object : AgentTool {
            override val name = "make_phone_call"
            override val description = "Place a real phone call. Arguments: {\"number\": \"+92...\"}. Always asks the user to approve first."
            override val requiresApproval = true
            override fun approvalSummary(args: JSONObject) = "Call ${args.optString("number")}"
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val number = args.optString("number").trim()
                if (number.isBlank()) return ToolOutcome("No phone number provided.")
                if (!hasPermission(Manifest.permission.CALL_PHONE)) {
                    return ToolOutcome("CALL_PHONE permission is not granted. Tell the user to grant the Phone permission for Open Buddy in Android Settings, then try again.")
                }
                @Suppress("DEPRECATION")
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}"))
                val result = startActivity(intent)
                return if (result.text.startsWith("Opened")) ToolOutcome("Calling $number now.") else result
            }
        },

        object : AgentTool {
            override val name = "send_sms"
            override val description = "Send an SMS text message. Arguments: {\"number\": \"+92...\", \"message\": \"...\"}. Always asks the user to approve first."
            override val requiresApproval = true
            override fun approvalSummary(args: JSONObject) =
                "Send SMS to ${args.optString("number")}: \"${args.optString("message").take(80)}\""
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val number = args.optString("number").trim()
                val message = args.optString("message")
                if (number.isBlank() || message.isBlank()) return ToolOutcome("Number and message are both required.")
                if (!hasPermission(Manifest.permission.SEND_SMS)) {
                    return ToolOutcome("SEND_SMS permission is not granted. Tell the user to grant the SMS permission for Open Buddy in Android Settings, then try again.")
                }
                return try {
                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }
                    val parts = smsManager.divideMessage(message)
                    smsManager.sendMultipartTextMessage(number, null, parts, null, null)
                    ToolOutcome("SMS sent to $number.")
                } catch (e: Exception) {
                    ToolOutcome("SMS failed: ${e.message}")
                }
            }
        },

        object : AgentTool {
            override val name = "whatsapp_prefill"
            override val description = "Open WhatsApp chat with a number and a prefilled message. The user presses Send themselves — nothing is ever auto-sent. Arguments: {\"number\": \"923001234567\" (country code, no +), \"message\": \"...\"}."
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val number = args.optString("number").filter { it.isDigit() }
                val message = args.optString("message")
                if (number.isBlank()) return ToolOutcome("A WhatsApp number (with country code) is required.")
                val url = "https://wa.me/$number?text=${URLEncoder.encode(message, "UTF-8")}"
                val result = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                return if (result.text.startsWith("Opened")) {
                    ToolOutcome("WhatsApp opened with the message prefilled. The user will review and press Send.")
                } else result
            }
        },

        object : AgentTool {
            override val name = "open_app"
            override val description = "Open an installed app by its visible name. Arguments: {\"app_name\": \"WhatsApp\"}."
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val query = args.optString("app_name").trim()
                if (query.isBlank()) return ToolOutcome("No app name provided.")
                val pm = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = pm.queryIntentActivities(mainIntent, 0)
                val match = apps.firstOrNull { it.loadLabel(pm).toString().equals(query, ignoreCase = true) }
                    ?: apps.firstOrNull { it.loadLabel(pm).toString().contains(query, ignoreCase = true) }
                    ?: return ToolOutcome("No installed app found matching \"$query\".")
                val launchIntent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
                    ?: return ToolOutcome("Found ${match.loadLabel(pm)} but it has no launchable screen.")
                startActivity(launchIntent)
                return ToolOutcome("Opened ${match.loadLabel(pm)}.")
            }
        },

        object : AgentTool {
            override val name = "set_timer"
            override val description = "Set a countdown timer in the Clock app. Arguments: {\"seconds\": 600, \"label\": \"Tea\"}."
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val seconds = args.optInt("seconds", 0)
                if (seconds <= 0) return ToolOutcome("Timer length (seconds) must be positive.")
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, args.optString("label", "Open Buddy timer"))
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                }
                val result = startActivity(intent)
                return if (result.text.startsWith("Opened")) ToolOutcome("Timer set for $seconds seconds.") else result
            }
        },

        object : AgentTool {
            override val name = "create_calendar_event"
            override val description = "Create a calendar event (opens the Calendar editor prefilled; the user saves it). Arguments: {\"title\": \"...\", \"start_in_minutes\": 60, \"duration_minutes\": 30, \"location\": \"\", \"description\": \"\"}."
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val title = args.optString("title").trim()
                if (title.isBlank()) return ToolOutcome("An event title is required.")
                val start = Calendar.getInstance().apply {
                    add(Calendar.MINUTE, args.optInt("start_in_minutes", 60))
                }
                val end = (start.clone() as Calendar).apply {
                    add(Calendar.MINUTE, args.optInt("duration_minutes", 30).coerceAtLeast(5))
                }
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, title)
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.timeInMillis)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.timeInMillis)
                    putExtra(CalendarContract.Events.EVENT_LOCATION, args.optString("location", ""))
                    putExtra(CalendarContract.Events.DESCRIPTION, args.optString("description", ""))
                }
                val result = startActivity(intent)
                return if (result.text.startsWith("Opened")) ToolOutcome("Calendar editor opened for \"$title\". The user will review and save it.") else result
            }
        },

        object : AgentTool {
            override val name = "toggle_flashlight"
            override val description = "Turn the phone torch on or off. Arguments: {\"on\": true}."
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val on = args.optBoolean("on", true)
                return try {
                    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                        cameraManager.getCameraCharacteristics(id)
                            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    } ?: return ToolOutcome("This device has no flashlight.")
                    cameraManager.setTorchMode(cameraId, on)
                    ToolOutcome("Flashlight turned ${if (on) "on" else "off"}.")
                } catch (e: Exception) {
                    ToolOutcome("Flashlight failed: ${e.message}")
                }
            }
        },

        object : AgentTool {
            override val name = "device_status"
            override val description = "Report battery level/charging, free storage, device model and Android version. No arguments."
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val percent = if (level >= 0) (level * 100 / scale.coerceAtLeast(1)) else -1
                val statFs = StatFs(context.filesDir.absolutePath)
                val freeGb = statFs.availableBytes / (1024.0 * 1024.0 * 1024.0)
                val totalGb = statFs.totalBytes / (1024.0 * 1024.0 * 1024.0)
                return ToolOutcome(
                    "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}). " +
                        "Battery: $percent%${if (charging) " (charging)" else ""}. " +
                        "Storage: ${String.format("%.1f", freeGb)} GB free of ${String.format("%.1f", totalGb)} GB."
                )
            }
        },

        object : AgentTool {
            override val name = "search_contacts"
            override val description = "Search the phone contacts by name and return matching phone numbers. Arguments: {\"query\": \"Ahmed\"}."
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val query = args.optString("query").trim()
                if (query.isBlank()) return ToolOutcome("A contact name query is required.")
                if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
                    return ToolOutcome("READ_CONTACTS permission is not granted. Tell the user to grant the Contacts permission for Open Buddy in Android Settings, then try again.")
                }
                val results = mutableListOf<String>()
                val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                context.contentResolver.query(
                    uri, projection,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$query%"), null
                )?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (cursor.moveToNext() && results.size < 10) {
                        results.add("${cursor.getString(nameIdx)}: ${cursor.getString(numIdx)}")
                    }
                }
                return if (results.isEmpty()) ToolOutcome("No contacts found matching \"$query\".")
                else ToolOutcome("Contacts matching \"$query\":\n" + results.joinToString("\n"))
            }
        },

        object : AgentTool {
            override val name = "get_location"
            override val description = "Get the device's last known location (latitude/longitude). No arguments."
            override suspend fun execute(args: JSONObject): ToolOutcome {
                if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
                    !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                ) {
                    return ToolOutcome("Location permission is not granted. Tell the user to grant the Location permission for Open Buddy in Android Settings, then try again.")
                }
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                val best = providers.mapNotNull { provider ->
                    try {
                        @Suppress("MissingPermission")
                        locationManager.getLastKnownLocation(provider)
                    } catch (_: Exception) {
                        null
                    }
                }.maxByOrNull { it.time }
                    ?: return ToolOutcome("No location is available yet (GPS may be off or no fix has been recorded).")
                return ToolOutcome(
                    "Latitude: ${best.latitude}, Longitude: ${best.longitude} (accuracy ~${best.accuracy.toInt()} m, recorded ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(best.time))})."
                )
            }
        }
    )
}
