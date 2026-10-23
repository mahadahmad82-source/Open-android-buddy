package com.mahad.openbuddy.data.provider

import com.mahad.openbuddy.data.security.SecretStore
import com.mahad.openbuddy.domain.model.AiProviderConfig
import com.mahad.openbuddy.domain.model.ProviderType

class AiProviderFactory(
    private val secretStore: SecretStore
) {
    fun createProvider(config: AiProviderConfig): AiProvider {
        val apiKey = secretStore.getApiKey(config.id) ?: ""
        return when (config.type) {
            ProviderType.GEMINI -> GeminiProvider(config, apiKey)
            ProviderType.CLAUDE -> ClaudeProvider(config, apiKey)
            ProviderType.FAL, ProviderType.REPLICATE, ProviderType.POLLINATIONS -> MediaOnlyProvider(config, apiKey)
            ProviderType.KIMI -> OpenAiCompatibleProvider(
                config = config.copy(
                    baseUrl = config.baseUrl ?: ProviderType.KIMI.defaultBaseUrl
                ),
                apiKey = apiKey
            )
            ProviderType.CUSTOM_OPENAI -> OpenAiCompatibleProvider(
                config = config,
                apiKey = apiKey
            )
            // AUTO_DETECT is resolved to a concrete provider before saving;
            // this branch is only a safety net and behaves like OpenAI-compatible.
            ProviderType.AUTO_DETECT -> OpenAiCompatibleProvider(
                config = config,
                apiKey = apiKey
            )
        }
    }

    fun createProviderWithCustomKey(config: AiProviderConfig, customKey: String): AiProvider {
        return when (config.type) {
            ProviderType.GEMINI -> GeminiProvider(config, customKey)
            ProviderType.CLAUDE -> ClaudeProvider(config, customKey)
            ProviderType.FAL, ProviderType.REPLICATE, ProviderType.POLLINATIONS -> MediaOnlyProvider(config, customKey)
            ProviderType.KIMI -> OpenAiCompatibleProvider(
                config = config.copy(
                    baseUrl = config.baseUrl ?: ProviderType.KIMI.defaultBaseUrl
                ),
                apiKey = customKey
            )
            ProviderType.CUSTOM_OPENAI -> OpenAiCompatibleProvider(
                config = config,
                apiKey = customKey
            )
            ProviderType.AUTO_DETECT -> OpenAiCompatibleProvider(
                config = config,
                apiKey = customKey
            )
        }
    }
}
