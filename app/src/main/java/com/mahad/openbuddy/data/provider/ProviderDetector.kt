package com.mahad.openbuddy.data.provider

import com.mahad.openbuddy.domain.model.ProviderType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** The result of identifying which provider an unknown API key belongs to. */
data class DetectedProvider(
    val displayName: String,
    val type: ProviderType,
    val baseUrl: String?,
    val models: List<String>,
    val defaultModel: String
)

/**
 * Universal API-key detection.
 *
 * The user pastes ANY provider's key — Mistral, Groq, OpenRouter, DeepSeek,
 * xAI, Together, Fireworks, Cerebras, Moonshot, OpenAI, Claude, Gemini — and
 * this detector probes each known provider's model-list endpoint with the key
 * until one accepts it. Key-prefix hints (AIza…, sk-ant-…, gsk_…, xai-…) are
 * tried first so detection is fast; the remaining candidates follow in
 * popularity order. A candidate only "wins" when it returns a real model
 * list, so a wrong guess is impossible: the provider itself confirms the key.
 */
object ProviderDetector {

    private data class Candidate(
        val displayName: String,
        val type: ProviderType,
        val baseUrl: String?,
        val keyHints: List<String> = emptyList()
    )

    /** Short timeouts keep a full sweep snappy; real endpoints answer in <1s. */
    private val probeClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private val candidates = listOf(
        Candidate("Google Gemini", ProviderType.GEMINI, null, listOf("AIza")),
        Candidate("Anthropic Claude", ProviderType.CLAUDE, null, listOf("sk-ant-")),
        Candidate("Mistral AI", ProviderType.CUSTOM_OPENAI, "https://api.mistral.ai/v1"),
        Candidate("Groq", ProviderType.CUSTOM_OPENAI, "https://api.groq.com/openai/v1", listOf("gsk_")),
        Candidate("OpenRouter", ProviderType.CUSTOM_OPENAI, "https://openrouter.ai/api/v1", listOf("sk-or-")),
        Candidate("DeepSeek", ProviderType.CUSTOM_OPENAI, "https://api.deepseek.com/v1"),
        Candidate("xAI Grok", ProviderType.CUSTOM_OPENAI, "https://api.x.ai/v1", listOf("xai-")),
        Candidate("OpenAI", ProviderType.CUSTOM_OPENAI, "https://api.openai.com/v1", listOf("sk-proj-", "sk-")),
        Candidate("Together AI", ProviderType.CUSTOM_OPENAI, "https://api.together.xyz/v1"),
        Candidate("Moonshot / Kimi", ProviderType.KIMI, "https://api.moonshot.cn/v1"),
        Candidate("Fireworks AI", ProviderType.CUSTOM_OPENAI, "https://api.fireworks.ai/inference/v1"),
        Candidate("Cerebras", ProviderType.CUSTOM_OPENAI, "https://api.cerebras.ai/v1"),
        Candidate("SiliconFlow", ProviderType.CUSTOM_OPENAI, "https://api.siliconflow.cn/v1"),
        Candidate("Hugging Face", ProviderType.CUSTOM_OPENAI, "https://router.huggingface.co/v1", listOf("hf_")),
        Candidate("Replicate (Video)", ProviderType.REPLICATE, "https://api.replicate.com/v1", listOf("r8_"))
    )

    suspend fun detect(apiKey: String): Result<DetectedProvider> {
        val key = apiKey.trim()
        if (key.isBlank()) {
            return Result.failure(Exception("API key is empty."))
        }

        // fal.ai keys have a distinctive "<uuid>:<hex>" shape — check them
        // first so another provider's endpoint never gets a false positive.
        if (MediaProviderProbe.looksLikeFalKey(key)) {
            val falModels = MediaProviderProbe.falModels(key).getOrNull()
            if (!falModels.isNullOrEmpty()) {
                return Result.success(
                    DetectedProvider(
                        displayName = "fal.ai (Video)",
                        type = ProviderType.FAL,
                        baseUrl = ProviderType.FAL.defaultBaseUrl,
                        models = falModels,
                        defaultModel = falModels.first()
                    )
                )
            }
        }

        val hinted = candidates.filter { candidate -> candidate.keyHints.any { key.startsWith(it) } }
        val ordered = hinted + candidates.filterNot { it in hinted }

        var lastError: Exception? = null
        for (candidate in ordered) {
            val modelsResult = fetchCandidateModels(candidate, key)
            val models = modelsResult.getOrNull()
            if (!models.isNullOrEmpty()) {
                val defaultModel = ModelCatalog.pickDefaultModel(candidate.type, models)
                    ?: models.first()
                return Result.success(
                    DetectedProvider(
                        displayName = candidate.displayName,
                        type = candidate.type,
                        baseUrl = candidate.baseUrl,
                        models = models,
                        defaultModel = defaultModel
                    )
                )
            }
            modelsResult.exceptionOrNull()?.let { lastError = it as? Exception ?: Exception(it.message) }
        }

        return Result.failure(
            Exception(
                "Could not detect which provider this API key belongs to. " +
                    "The key may be invalid, or the provider is not supported yet. " +
                    "You can still pick the platform manually and set its Base URL." +
                    (lastError?.message?.let { " (Last error: $it)" } ?: "")
            )
        )
    }

    private suspend fun fetchCandidateModels(candidate: Candidate, apiKey: String): Result<List<String>> {
        return when (candidate.type) {
            ProviderType.GEMINI -> GeminiProvider.fetchAvailableModels(apiKey)
            ProviderType.CLAUDE -> ClaudeProvider.fetchAvailableModels(apiKey)
            ProviderType.REPLICATE -> MediaProviderProbe.replicateModels(apiKey)
            ProviderType.FAL -> MediaProviderProbe.falModels(apiKey)
            else -> OpenAiCompatibleProvider.fetchAvailableModels(
                baseUrl = candidate.baseUrl ?: return Result.failure(Exception("No base URL for ${candidate.displayName}")),
                apiKey = apiKey,
                client = probeClient
            )
        }
    }
}
