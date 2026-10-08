package com.example.domain.model

enum class VoiceActionType(
    val actionKey: String,
    val displayName: String,
    val description: String,
    val samplePrompt: String
) {
    OPEN_APP(
        actionKey = "open_app",
        displayName = "Open App",
        description = "Find and launch any installed application",
        samplePrompt = "Open YouTube"
    ),
    DIAL(
        actionKey = "dial",
        displayName = "Phone Dialer",
        description = "Open phone dialer with number prefilled",
        samplePrompt = "Dial +1 (555) 234-5678"
    ),
    SMS_COMPOSE(
        actionKey = "sms_compose",
        displayName = "Compose SMS",
        description = "Open SMS messenger with recipient & message",
        samplePrompt = "Text John I'll be 10 minutes late"
    ),
    EMAIL_COMPOSE(
        actionKey = "email_compose",
        displayName = "Compose Email",
        description = "Open email app with recipient, subject & body",
        samplePrompt = "Email support@example.com about bug report"
    ),
    WHATSAPP_COMPOSE(
        actionKey = "whatsapp_compose",
        displayName = "WhatsApp Message",
        description = "Open WhatsApp chat with prefilled message",
        samplePrompt = "Send WhatsApp message to +1234567890 saying Hello"
    ),
    YOUTUBE_SEARCH(
        actionKey = "youtube_search",
        displayName = "YouTube Search",
        description = "Search YouTube app or web for videos",
        samplePrompt = "Search YouTube for Kotlin Jetpack Compose tutorial"
    ),
    OPEN_URL(
        actionKey = "open_url",
        displayName = "Open URL",
        description = "Open any web link in the browser",
        samplePrompt = "Open github.com"
    ),
    WEB_SEARCH(
        actionKey = "web_search",
        displayName = "Web Search",
        description = "Perform a web search via default search engine",
        samplePrompt = "Search the web for Android 15 features"
    ),
    SET_ALARM(
        actionKey = "set_alarm",
        displayName = "Set Alarm",
        description = "Set clock alarm with hour, minute and label",
        samplePrompt = "Set alarm for 7:30 AM for standup"
    ),
    SET_TIMER(
        actionKey = "set_timer",
        displayName = "Set Timer",
        description = "Set countdown timer for specified seconds",
        samplePrompt = "Set timer for 15 minutes"
    );

    companion object {
        val WHITELIST = entries.toSet()

        fun fromActionKey(key: String): VoiceActionType? {
            return entries.firstOrNull { it.actionKey.equals(key.trim(), ignoreCase = true) }
        }
    }
}

data class ParsedVoiceCommand(
    val rawCommand: String,
    val actionType: VoiceActionType,
    val params: Map<String, String>,
    val confidence: Float,
    val target: String,
    val message: String? = null,
    val requiresContactResolution: Boolean = false,
    val contactName: String? = null,
    val resolvedNumber: String? = null
)

data class VoiceActionHistoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val rawCommand: String,
    val actionType: String,
    val target: String,
    val message: String?,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String // "CONFIRMED", "CANCELLED", "REJECTED", "ERROR"
)
