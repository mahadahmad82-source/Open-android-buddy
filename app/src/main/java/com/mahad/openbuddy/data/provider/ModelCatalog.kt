package com.mahad.openbuddy.data.provider

import com.mahad.openbuddy.domain.model.ProviderType

/**
 * Zero-config model discovery.
 *
 * The user should only ever paste an API key: this catalog fetches the provider's
 * live model list and picks a sensible default chat model, so nobody has to know
 * or type a model name. Picking a different model stays an optional, advanced choice.
 */
object ModelCatalog {

    /** Model ids that are never useful as a chat default. */
    private val EXCLUDED_SUBSTRINGS = listOf(
        "embedding", "embed", "whisper", "tts", "dall-e", "dalle", "image",
        "moderation", "rerank", "audio", "transcribe", "realtime", "aqa",
        "babbage", "davinci", "curie", "ada-001", "search"
    )

    /** Preferred chat models per provider type, most preferred first (substring match). */
    private fun preferencesFor(type: ProviderType): List<String> = when (type) {
        ProviderType.GEMINI -> listOf(
            "gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash", "gemini-flash", "gemini-pro"
        )
        ProviderType.CLAUDE -> listOf(
            "claude-sonnet-4-5", "claude-sonnet-4", "claude-opus-4", "sonnet", "opus", "haiku"
        )
        ProviderType.KIMI -> listOf(
            "kimi-k2", "moonshot-v1-128k", "moonshot-v1-32k", "moonshot-v1-8k", "moonshot"
        )
        ProviderType.CUSTOM_OPENAI, ProviderType.AUTO_DETECT -> listOf(
            "gpt-4o", "claude-sonnet", "gemini-2.5-flash", "deepseek-chat",
            "mistral-large", "mistral-medium", "llama-3.3-70b", "grok-4", "grok-3",
            "gpt-4.1", "gpt-5", "deepseek-reasoner", "codestral",
            "qwen-plus", "mistral", "llama", "qwen", "grok", "gemini"
        )
    }

    suspend fun fetchModels(
        type: ProviderType,
        baseUrl: String?,
        apiKey: String
    ): Result<List<String>> {
        return when (type) {
            ProviderType.AUTO_DETECT -> ProviderDetector.detect(apiKey).map { it.models }
            ProviderType.GEMINI -> GeminiProvider.fetchAvailableModels(apiKey)
            ProviderType.CLAUDE -> ClaudeProvider.fetchAvailableModels(apiKey)
            ProviderType.KIMI -> OpenAiCompatibleProvider.fetchAvailableModels(
                baseUrl = baseUrl ?: ProviderType.KIMI.defaultBaseUrl ?: "https://api.moonshot.cn/v1",
                apiKey = apiKey
            )
            ProviderType.CUSTOM_OPENAI -> OpenAiCompatibleProvider.fetchAvailableModels(
                baseUrl = baseUrl ?: ProviderType.CUSTOM_OPENAI.defaultBaseUrl ?: "https://api.openai.com/v1",
                apiKey = apiKey
            )
        }
    }

    /**
     * Picks the best default chat model from a live model list.
     * Returns null only when the list is empty.
     */
    fun pickDefaultModel(type: ProviderType, models: List<String>): String? {
        if (models.isEmpty()) return null
        val chatModels = models.filterNot { model ->
            EXCLUDED_SUBSTRINGS.any { model.contains(it, ignoreCase = true) }
        }
        val pool = if (chatModels.isNotEmpty()) chatModels else models
        for (preference in preferencesFor(type)) {
            val match = pool.firstOrNull { it.contains(preference, ignoreCase = true) }
            if (match != null) return match
        }
        return pool.firstOrNull()
    }
}
