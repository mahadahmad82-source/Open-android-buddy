package com.example.data.provider

import com.example.data.security.SecretStore
import com.example.domain.model.AiProviderConfig
import com.example.domain.model.ProviderType

class AiProviderFactory(
    private val secretStore: SecretStore
) {
    fun createProvider(config: AiProviderConfig): AiProvider {
        val apiKey = secretStore.getApiKey(config.id) ?: ""
        return when (config.type) {
            ProviderType.GEMINI -> GeminiProvider(config, apiKey)
            ProviderType.CLAUDE -> ClaudeProvider(config, apiKey)
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
        }
    }

    fun createProviderWithCustomKey(config: AiProviderConfig, customKey: String): AiProvider {
        return when (config.type) {
            ProviderType.GEMINI -> GeminiProvider(config, customKey)
            ProviderType.CLAUDE -> ClaudeProvider(config, customKey)
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
        }
    }
}
