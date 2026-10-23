package com.mahad.openbuddy.data.provider

import com.mahad.openbuddy.domain.model.AiProviderConfig
import com.mahad.openbuddy.domain.model.ChatMessage
import com.mahad.openbuddy.domain.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Support for the media-only provider types (fal.ai, Replicate, Pollinations). These are
 * not chat APIs: they exist so the agent's generate_video tool has several
 * independent backends. If one is added as the ACTIVE chat provider by
 * mistake, chat replies with a clear explanation instead of a cryptic error.
 */
class MediaOnlyProvider(
    override val config: AiProviderConfig,
    private val apiKey: String
) : AiProvider {

    override fun streamChat(messages: List<ChatMessage>, systemPrompt: String?): Flow<String> = flow {
        throw Exception(
            "${config.type.displayName} is a video-generation provider, not a chat provider. " +
                "Pick a chat provider (Gemini, Claude, Custom…) as the active provider — " +
                "video generation will still use this key automatically."
        )
    }

    override suspend fun testConnection(): Result<String> = when (config.type) {
        ProviderType.FAL -> MediaProviderProbe.falModels(apiKey)
            .map { "Connected to fal.ai — video generation ready (${it.size} video models available)." }
        ProviderType.REPLICATE -> MediaProviderProbe.replicateModels(apiKey)
            .map { "Connected to Replicate — video generation ready (${it.size} video models available)." }
        ProviderType.POLLINATIONS -> MediaProviderProbe.pollinationsModels(apiKey)
            .map { "Connected to Pollinations — video generation ready (${it.size} video models available). Note: video models use paid Pollen credits." }
        else -> Result.failure(Exception("Not a media provider."))
    }
}

/** Key validation + model lists for the media-only providers. */
object MediaProviderProbe {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** fal keys look like "<uuid>:<hex>". Used as a detection hint. */
    fun looksLikeFalKey(key: String): Boolean =
        key.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}:[0-9a-fA-F]{16,}$"))

    /**
     * Validates a fal key by asking the queue about a request id that does
     * not exist: an invalid key gets 401, a valid key gets 404 (not found).
     */
    suspend fun falModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://queue.fal.run/fal-ai/kling-video/v2.1/standard/text-to-video/requests/00000000-0000-0000-0000-000000000000/status")
                .header("Authorization", "Key $apiKey")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    401, 403 -> Result.failure(Exception("fal.ai rejected this key (HTTP ${response.code})."))
                    else -> Result.success(ProviderType.FAL.recommendedModels)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Pollinations keys are prefixed: "pk_" (app key) or "sk_" (secret). */
    fun looksLikePollinationsKey(key: String): Boolean =
        key.startsWith("pk_") || key.startsWith("sk_")

    /**
     * Validates a Pollinations key against the authenticated balance
     * endpoint: an invalid key gets 401, a valid key gets its Pollen
     * balance. The model list itself is public, so it cannot validate.
     */
    suspend fun pollinationsModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://gen.pollinations.ai/account/balance")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(ProviderType.POLLINATIONS.recommendedModels)
                } else {
                    Result.failure(Exception("Pollinations rejected this key (HTTP ${response.code}). Create a key at enter.pollinations.ai."))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Validates a Replicate token against the authenticated models endpoint. */
    suspend fun replicateModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.replicate.com/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(ProviderType.REPLICATE.recommendedModels)
                } else {
                    Result.failure(Exception("Replicate rejected this token (HTTP ${response.code})."))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
