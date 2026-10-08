package com.example.data.voice

import com.example.domain.model.ParsedVoiceCommand
import com.example.domain.model.VoiceActionType
import org.json.JSONObject
import java.util.regex.Pattern

object VoiceIntentParser {

    val SYSTEM_PROMPT = """
        You are Open Buddy Voice Action Parser.
        Your task is to parse a voice or text command from a user (in English, Roman Urdu, or Urdu) into a single JSON object.
        You MUST respond with RAW JSON ONLY. No markdown ticks, no commentary, no additional text.

        JSON Schema:
        {
          "action": "ACTION_NAME",
          "params": {
             // action-specific parameters
          },
          "confidence": 0.0 to 1.0
        }

        SUPPORTED ACTIONS (Strict Whitelist):
        1. "open_app":
           - Description: User wants to open/launch an application.
           - English: "Open YouTube", "Launch Chrome", "Open Calculator"
           - Roman Urdu / Urdu: "YouTube kholo", "Chrome chalao", "Calculator open karo", "Camera open karo"
           - params: {"appName": "app name"}

        2. "dial":
           - Description: User wants to call or dial a phone number or contact.
           - English: "Call Alice", "Dial 555-1234", "Call +923001234567"
           - Roman Urdu / Urdu: "Alice ko call karo", "Dial karo 555-1234", "Ammi ko call lagao", "Number milao 03001234567"
           - params: {"phoneNumber": "phone number", "name": "contact name if given"}

        3. "sms_compose":
           - Description: User wants to send or compose an SMS text message.
           - English: "Text Bob I will be there in 5 minutes", "SMS Alice happy birthday"
           - Roman Urdu / Urdu: "Bob ko SMS karo mein 5 minute mein aa raha hun", "Alex ko text bhejo meeting shuru ho gayi"
           - params: {"phoneNumber": "phone number if given", "name": "contact name if given", "message": "message text"}

        4. "email_compose":
           - Description: User wants to send or compose an email.
           - English: "Email team@example.com about Project Update saying We are on schedule"
           - Roman Urdu / Urdu: "team@dev.org ko email bhejo subject report message done"
           - params: {"email": "email address if given", "name": "recipient name if given", "subject": "subject", "body": "email body"}

        5. "whatsapp_compose":
           - Description: User wants to send a WhatsApp message.
           - English: "WhatsApp Sarah Happy Birthday!", "Send WhatsApp message to Mom I reached safely"
           - Roman Urdu / Urdu: "Mom ko WhatsApp karo mein pohanch gaya hun", "Ali ko WhatsApp message bhejo kahan ho"
           - params: {"phoneNumber": "phone number if given", "name": "contact name if given", "message": "message text"}

        6. "youtube_search":
           - Description: User wants to search or play something on YouTube.
           - English: "Search YouTube for Android Jetpack Compose animation", "Play song on YouTube"
           - Roman Urdu / Urdu: "YouTube par Kotlin tutorials search karo", "YouTube pe gaana chalao", "YouTube search Android"
           - params: {"query": "search terms"}

        7. "open_url":
           - Description: User wants to open a specific website URL.
           - English: "Open github.com", "Go to google.com"
           - Roman Urdu / Urdu: "github.com kholo", "Website open karo android.com"
           - params: {"url": "https://..."}

        8. "web_search":
           - Description: User wants to search Google or the web.
           - English: "Search the web for Kotlin 2.0 release notes", "Google weather forecast"
           - Roman Urdu / Urdu: "Google pe search karo Lahore weather", "Web pe search karo Kotlin coroutines"
           - params: {"query": "search query"}

        9. "set_alarm":
           - Description: User wants to set an alarm in Clock.
           - English: "Set alarm for 7:30 AM for Standup", "Set alarm at 8 PM"
           - Roman Urdu / Urdu: "Subah 7:30 baje ka alarm lagao", "Sham 8 baje alarm set karo", "7 baje ka alarm lagao"
           - params: {"hour": integer 0-23, "minutes": integer 0-59, "message": "label/message"}

        10. "set_timer":
           - Description: User wants to start a countdown timer in Clock.
           - English: "Set timer for 10 minutes", "Set a 45 second timer for workout"
           - Roman Urdu / Urdu: "10 minute ka timer lagao", "5 minute timer set karo", "30 second ka timer"
           - params: {"seconds": integer total seconds, "message": "label"}

        CRITICAL RULES:
        - If the command does not match any of the 10 supported actions above, reject it by returning:
          {"action": "unsupported", "params": {"reason": "Explanation"}, "confidence": 0.0}
        - Return ONLY pure JSON.
    """.trimIndent()

    /**
     * Parses the raw AI response string into a validated [ParsedVoiceCommand].
     */
    fun parse(rawResponse: String, originalCommand: String): Result<ParsedVoiceCommand> {
        return try {
            val cleanJson = extractJson(rawResponse)
            val json = JSONObject(cleanJson)

            val actionKey = json.optString("action", "").trim()
            val actionType = VoiceActionType.fromActionKey(actionKey)
                ?: return Result.failure(
                    IllegalArgumentException("Action '$actionKey' is not supported. Supported actions: Open App, Dial, SMS, Email, WhatsApp, YouTube, Open URL, Web Search, Set Alarm, Set Timer.")
                )

            val confidence = json.optDouble("confidence", 0.85).toFloat().coerceIn(0.0f, 1.0f)
            val paramsObj = json.optJSONObject("params") ?: JSONObject()
            val paramsMap = mutableMapOf<String, String>()
            val keys = paramsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                paramsMap[k] = paramsObj.optString(k, "")
            }

            // Derive target and message from action-specific parameters
            val (target, message, requiresContactResolution, contactName, resolvedNumber) = deriveIntentDetails(
                actionType,
                paramsMap
            )

            val parsed = ParsedVoiceCommand(
                rawCommand = originalCommand,
                actionType = actionType,
                params = paramsMap,
                confidence = confidence,
                target = target,
                message = message,
                requiresContactResolution = requiresContactResolution,
                contactName = contactName,
                resolvedNumber = resolvedNumber
            )

            Result.success(parsed)
        } catch (e: Exception) {
            // Fallback to local heuristic parser if AI output is malformed
            val heuristic = parseHeuristic(originalCommand)
            if (heuristic != null) {
                Result.success(heuristic)
            } else {
                Result.failure(Exception("Failed to parse voice command intent: ${e.message}"))
            }
        }
    }

    /**
     * Instant local regex/heuristic parser for English, Roman Urdu, and Urdu commands.
     * Works offline without requiring an active AI API key or network request.
     */
    fun parseHeuristic(command: String): ParsedVoiceCommand? {
        val text = command.trim().lowercase()
        if (text.isBlank()) return null

        // 1. YouTube Search
        if (text.contains("youtube") && (text.contains("search") || text.contains("play") || text.contains("chalao") || text.contains("pe search") || text.contains("par search") || text.contains("dhoondo"))) {
            var query = text.replace(Regex("(?i)(search youtube for|search on youtube|youtube pe search karo|youtube par search karo|youtube search|play on youtube|youtube par chalao|youtube pe|youtube par|youtube)"), "").trim()
            query = query.removePrefix("for").removePrefix("pe").removePrefix("par").trim()
            return ParsedVoiceCommand(
                rawCommand = command,
                actionType = VoiceActionType.YOUTUBE_SEARCH,
                params = mapOf("query" to query.ifBlank { "YouTube" }),
                confidence = 0.95f,
                target = query.ifBlank { "YouTube Trending" },
                message = null
            )
        }

        // 2. Open URL
        if (text.contains(".com") || text.contains(".org") || text.contains(".io") || text.contains(".net") || text.startsWith("http")) {
            val words = text.split(" ")
            val urlWord = words.firstOrNull { it.contains(".") || it.startsWith("http") } ?: text
            val cleanUrl = if (urlWord.startsWith("http://") || urlWord.startsWith("https://")) urlWord else "https://$urlWord"
            return ParsedVoiceCommand(
                rawCommand = command,
                actionType = VoiceActionType.OPEN_URL,
                params = mapOf("url" to cleanUrl),
                confidence = 0.96f,
                target = cleanUrl,
                message = null
            )
        }

        // 3. WhatsApp Message (English / Roman Urdu)
        if (text.contains("whatsapp") || text.contains("watsp") || text.contains("watsapp")) {
            // e.g. "WhatsApp Mom I reached" or "Mom ko WhatsApp karo main pohanch gaya"
            var target = ""
            var msg = ""
            val urduMatch = Regex("(?i)(.+?)\\s+ko\\s+whatsapp\\s*(?:karo|message|karein|bhejo)?\\s*(.*)").find(text)
            if (urduMatch != null) {
                target = urduMatch.groupValues[1].replace(Regex("(?i)(send|bhejo)"), "").trim()
                msg = urduMatch.groupValues[2].trim()
            } else {
                val engMatch = Regex("(?i)(?:whatsapp|send whatsapp to|whatsapp to)\\s+([a-zA-Z0-9+]+)\\s*(.*)").find(text)
                if (engMatch != null) {
                    target = engMatch.groupValues[1].trim()
                    msg = engMatch.groupValues[2].trim()
                } else {
                    target = "WhatsApp Recipient"
                    msg = text.replace(Regex("(?i)(whatsapp|karo|bhejo)"), "").trim()
                }
            }
            val isNumber = target.matches(Regex("[0-9+]+"))
            return ParsedVoiceCommand(
                rawCommand = command,
                actionType = VoiceActionType.WHATSAPP_COMPOSE,
                params = mapOf(
                    (if (isNumber) "phoneNumber" else "name") to target,
                    "message" to msg
                ),
                confidence = 0.94f,
                target = target,
                message = msg.ifBlank { null },
                requiresContactResolution = !isNumber,
                contactName = if (!isNumber) target else null,
                resolvedNumber = if (isNumber) target else null
            )
        }

        // 4. Timer (English / Roman Urdu)
        if (text.contains("timer") || text.contains("taimar")) {
            var seconds = 300 // default 5m
            val minMatch = Regex("([0-9]+)\\s*(?:min|minute|mint|m\\b)").find(text)
            val secMatch = Regex("([0-9]+)\\s*(?:sec|second|s\\b)").find(text)
            if (minMatch != null) {
                seconds = (minMatch.groupValues[1].toIntOrNull() ?: 5) * 60
            } else if (secMatch != null) {
                seconds = secMatch.groupValues[1].toIntOrNull() ?: 30
            }
            val display = if (seconds >= 60) "${seconds / 60} minutes" else "$seconds seconds"
            return ParsedVoiceCommand(
                rawCommand = command,
                actionType = VoiceActionType.SET_TIMER,
                params = mapOf("seconds" to seconds.toString(), "message" to "Timer"),
                confidence = 0.98f,
                target = display,
                message = "Timer for $display"
            )
        }

        // 5. Alarm (English / Roman Urdu)
        if (text.contains("alarm") || text.contains("alam") || text.contains("baje ka") || text.contains("baje alarm")) {
            var hour = 7
            var min = 0
            val timeMatch = Regex("([0-9]{1,2})(?::([0-9]{2}))?\\s*(am|pm|baje|subah|sham|raat)?").find(text)
            if (timeMatch != null) {
                val parsedH = timeMatch.groupValues[1].toIntOrNull() ?: 7
                min = timeMatch.groupValues[2].toIntOrNull() ?: 0
                val modifier = timeMatch.groupValues[3]
                hour = if (modifier.equals("pm", true) || modifier.equals("sham", true) || modifier.equals("raat", true)) {
                    if (parsedH < 12) parsedH + 12 else parsedH
                } else {
                    parsedH
                }
            }
            val amPm = if (hour >= 12) "PM" else "AM"
            val displayH = if (hour % 12 == 0) 12 else hour % 12
            val display = "$displayH:${min.toString().padStart(2, '0')} $amPm"
            return ParsedVoiceCommand(
                rawCommand = command,
                actionType = VoiceActionType.SET_ALARM,
                params = mapOf("hour" to hour.toString(), "minutes" to min.toString(), "message" to "Alarm"),
                confidence = 0.96f,
                target = display,
                message = "Alarm set for $display"
            )
        }

        // 6. Dial / Call (English / Roman Urdu)
        if (text.startsWith("call") || text.startsWith("dial") || text.contains("call karo") || text.contains("call milao") || text.contains("ko call") || text.contains("number dial")) {
            var target = text.replace(Regex("(?i)(call karo|call milao|call|dial karo|dial|ko call karo|ko call lagao|ko call)"), "").trim()
            val isNum = target.matches(Regex(".*[0-9+].*"))
            val cleanTarget = if (isNum) target.replace(Regex("[^0-9+]"), "") else target
            return ParsedVoiceCommand(
                rawCommand = command,
                actionType = VoiceActionType.DIAL,
                params = mapOf((if (isNum) "phoneNumber" else "name") to cleanTarget),
                confidence = 0.94f,
                target = cleanTarget.ifBlank { "Dialer" },
                message = null,
                requiresContactResolution = !isNum,
                contactName = if (!isNum) cleanTarget else null,
                resolvedNumber = if (isNum) cleanTarget else null
            )
        }

        // 7. SMS / Text Message
        if (text.contains("sms") || text.contains("text") || text.contains("message bhejo") || text.contains("msg karo")) {
            var target = ""
            var msg = ""
            val urduMatch = Regex("(?i)(.+?)\\s+ko\\s+(?:sms|text|message|msg)\\s*(?:karo|bhejo)?\\s*(.*)").find(text)
            if (urduMatch != null) {
                target = urduMatch.groupValues[1].trim()
                msg = urduMatch.groupValues[2].trim()
            } else {
                val engMatch = Regex("(?i)(?:text|sms|message)\\s+([a-zA-Z0-9+]+)\\s*(.*)").find(text)
                if (engMatch != null) {
                    target = engMatch.groupValues[1].trim()
                    msg = engMatch.groupValues[2].trim()
                } else {
                    target = "Recipient"
                    msg = text
                }
            }
            val isNum = target.matches(Regex("[0-9+]+"))
            return ParsedVoiceCommand(
                rawCommand = command,
                actionType = VoiceActionType.SMS_COMPOSE,
                params = mapOf(
                    (if (isNum) "phoneNumber" else "name") to target,
                    "message" to msg
                ),
                confidence = 0.92f,
                target = target,
                message = msg.ifBlank { null },
                requiresContactResolution = !isNum,
                contactName = if (!isNum) target else null,
                resolvedNumber = if (isNum) target else null
            )
        }

        // 8. Open App (English / Roman Urdu)
        if (text.startsWith("open") || text.startsWith("launch") || text.endsWith("kholo") || text.endsWith("chalao") || text.contains("open karo") || text.contains("chala do")) {
            val app = text.replace(Regex("(?i)(open karo|chala do|kholo|chalao|open app|open|launch)"), "").trim()
            if (app.isNotBlank()) {
                return ParsedVoiceCommand(
                    rawCommand = command,
                    actionType = VoiceActionType.OPEN_APP,
                    params = mapOf("appName" to app),
                    confidence = 0.95f,
                    target = app.replaceFirstChar { it.uppercase() },
                    message = "Open application: $app"
                )
            }
        }

        // 9. Web Search (Google / Web)
        if (text.contains("search") || text.contains("google") || text.contains("dhoondo") || text.contains("pata karo")) {
            val query = text.replace(Regex("(?i)(google pe search karo|google search|search web for|search Google for|search the web for|search for|search|google pe|google par|google)"), "").trim()
            return ParsedVoiceCommand(
                rawCommand = command,
                actionType = VoiceActionType.WEB_SEARCH,
                params = mapOf("query" to query.ifBlank { "Google Search" }),
                confidence = 0.93f,
                target = query.ifBlank { "Google Search" },
                message = null
            )
        }

        return null
    }

    private fun deriveIntentDetails(
        actionType: VoiceActionType,
        params: Map<String, String>
    ): IntentDetails {
        var target = ""
        var message: String? = null
        var requiresContact = false
        var contactName: String? = null
        var resolvedNumber: String? = null

        when (actionType) {
            VoiceActionType.OPEN_APP -> {
                target = params["appName"] ?: params["app_name"] ?: params["label"] ?: "App"
            }
            VoiceActionType.DIAL -> {
                val phone = params["phoneNumber"] ?: params["phone_number"] ?: params["number"]
                val name = params["name"] ?: params["contact"]
                if (!phone.isNullOrBlank()) {
                    target = phone
                    resolvedNumber = phone
                } else if (!name.isNullOrBlank()) {
                    target = name
                    contactName = name
                    requiresContact = true
                } else {
                    target = "Unknown Number"
                }
            }
            VoiceActionType.SMS_COMPOSE -> {
                val phone = params["phoneNumber"] ?: params["phone_number"] ?: params["number"]
                val name = params["name"] ?: params["contact"]
                message = params["message"] ?: params["body"] ?: params["text"] ?: ""
                if (!phone.isNullOrBlank()) {
                    target = phone
                    resolvedNumber = phone
                } else if (!name.isNullOrBlank()) {
                    target = name
                    contactName = name
                    requiresContact = true
                } else {
                    target = "Select Recipient"
                }
            }
            VoiceActionType.EMAIL_COMPOSE -> {
                val email = params["email"] ?: params["to"] ?: params["recipient"] ?: ""
                val subject = params["subject"] ?: ""
                val body = params["body"] ?: params["message"] ?: ""
                target = if (email.isNotBlank()) email else (params["name"] ?: "Compose Email")
                message = buildString {
                    if (subject.isNotBlank()) append("Subject: $subject\n")
                    if (body.isNotBlank()) append(body)
                }.trim().ifEmpty { null }
            }
            VoiceActionType.WHATSAPP_COMPOSE -> {
                val phone = params["phoneNumber"] ?: params["phone_number"] ?: params["number"]
                val name = params["name"] ?: params["contact"]
                message = params["message"] ?: params["text"] ?: ""
                if (!phone.isNullOrBlank()) {
                    target = phone
                    resolvedNumber = phone
                } else if (!name.isNullOrBlank()) {
                    target = name
                    contactName = name
                    requiresContact = true
                } else {
                    target = "WhatsApp Contact"
                }
            }
            VoiceActionType.YOUTUBE_SEARCH -> {
                val query = params["query"] ?: params["search"] ?: ""
                target = query.ifBlank { "Trending Videos" }
            }
            VoiceActionType.OPEN_URL -> {
                val rawUrl = params["url"] ?: params["link"] ?: ""
                target = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                    rawUrl
                } else {
                    "https://$rawUrl"
                }
            }
            VoiceActionType.WEB_SEARCH -> {
                val query = params["query"] ?: params["search"] ?: ""
                target = query.ifBlank { "Google Search" }
            }
            VoiceActionType.SET_ALARM -> {
                val hour = params["hour"]?.toIntOrNull() ?: 7
                val minutes = params["minutes"]?.toIntOrNull() ?: 0
                val msg = params["message"] ?: params["label"] ?: "Alarm"
                val amPm = if (hour >= 12) "PM" else "AM"
                val displayHour = if (hour % 12 == 0) 12 else hour % 12
                val displayMinutes = minutes.toString().padStart(2, '0')
                target = "$displayHour:$displayMinutes $amPm"
                message = msg
            }
            VoiceActionType.SET_TIMER -> {
                val seconds = params["seconds"]?.toIntOrNull() ?: params["length"]?.toIntOrNull() ?: 300
                val msg = params["message"] ?: params["label"] ?: "Timer"
                val mins = seconds / 60
                val remSec = seconds % 60
                target = if (mins > 0 && remSec > 0) "$mins min $remSec sec"
                else if (mins > 0) "$mins minutes"
                else "$seconds seconds"
                message = msg
            }
        }

        return IntentDetails(target, message, requiresContact, contactName, resolvedNumber)
    }

    private data class IntentDetails(
        val target: String,
        val message: String?,
        val requiresContact: Boolean,
        val contactName: String?,
        val resolvedNumber: String?
    )

    private fun extractJson(text: String): String {
        var clean = text.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json")
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```")
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```")
        }
        clean = clean.trim()
        val startIdx = clean.indexOf('{')
        val endIdx = clean.lastIndexOf('}')
        if (startIdx != -1 && endIdx != -1 && endIdx >= startIdx) {
            clean = clean.substring(startIdx, endIdx + 1)
        }
        return clean
    }
}

