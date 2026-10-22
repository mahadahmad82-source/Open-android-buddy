package com.mahad.openbuddy.data.media

import android.content.Context
import android.util.Base64
import com.mahad.openbuddy.domain.model.AiProviderConfig
import com.mahad.openbuddy.domain.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Phase 7 — image & video generation using the user's own provider key.
 *
 * Images:
 *  - Gemini: generateContent with image output (gemini-2.5-flash-image family),
 *    or any model whose name contains "image".
 *  - OpenAI-compatible: POST {baseUrl}/images/generations (b64_json or url).
 *  - Claude: no image API exists — returns an explanatory failure.
 *
 * Video (Gemini only): Veo via predictLongRunning + operation polling,
 * saved as MP4. Requires a Gemini key with Veo access (AI Studio paid tier).
 */
class MediaGenerationService(private val context: Context) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun outputDir(): File {
        val dir = File(context.filesDir, "generated")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun saveBytes(bytes: ByteArray, extension: String, prefix: String): File {
        val file = File(outputDir(), "${prefix}_${System.currentTimeMillis()}.$extension")
        file.writeBytes(bytes)
        return file
    }

    suspend fun generateImage(config: AiProviderConfig, apiKey: String, prompt: String): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                when (config.type) {
                    ProviderType.GEMINI -> generateImageGemini(config, apiKey, prompt)
                    ProviderType.CLAUDE -> Result.failure(
                        Exception("Claude does not offer image generation. Switch the active provider to Gemini or an OpenAI-compatible provider to generate images.")
                    )
                    else -> generateImageOpenAiCompatible(config, apiKey, prompt)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun generateImageGemini(config: AiProviderConfig, apiKey: String, prompt: String): Result<File> {
        val model = config.modelName.takeIf { it.contains("image", ignoreCase = true) }
            ?: "gemini-2.5-flash-image"
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply { put("text", prompt) }))
            }))
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
            })
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return Result.failure(Exception("Gemini image error (${response.code}): ${errorMessage(body)}"))
            }
            val parts = JSONObject(body)
                .optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?: return Result.failure(Exception("Gemini returned no content parts."))
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                val data = inline?.optString("data", "")?.takeIf { it.isNotBlank() }
                if (data != null) {
                    val bytes = Base64.decode(data, Base64.DEFAULT)
                    return Result.success(saveBytes(bytes, "png", "image"))
                }
            }
            return Result.failure(Exception("Gemini's response contained no image data. The selected model may not support image output."))
        }
    }

    private fun generateImageOpenAiCompatible(config: AiProviderConfig, apiKey: String, prompt: String): Result<File> {
        val base = (config.baseUrl ?: "https://api.openai.com/v1").trimEnd('/')
        val model = config.modelName.ifBlank { "gpt-image-1" }
        val payload = JSONObject().apply {
            put("model", model)
            put("prompt", prompt)
            put("n", 1)
        }
        val request = Request.Builder()
            .url("$base/images/generations")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return Result.failure(Exception("Image API error (${response.code}): ${errorMessage(body)}"))
            }
            val first = JSONObject(body).optJSONArray("data")?.optJSONObject(0)
                ?: return Result.failure(Exception("Image API returned no data."))
            val b64 = first.optString("b64_json", "")
            if (b64.isNotBlank()) {
                return Result.success(saveBytes(Base64.decode(b64, Base64.DEFAULT), "png", "image"))
            }
            val url = first.optString("url", "")
            if (url.isNotBlank()) {
                val imgRequest = Request.Builder().url(url).get().build()
                client.newCall(imgRequest).execute().use { imgResponse ->
                    val bytes = imgResponse.body?.bytes()
                        ?: return Result.failure(Exception("Could not download the generated image."))
                    return Result.success(saveBytes(bytes, "png", "image"))
                }
            }
            return Result.failure(Exception("Image API response had neither b64_json nor url."))
        }
    }

    suspend fun generateVideo(config: AiProviderConfig, apiKey: String, prompt: String): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                if (config.type != ProviderType.GEMINI) {
                    return@withContext Result.failure(
                        Exception("Video generation is currently supported only with a Gemini provider (Veo). Switch the active provider to Gemini to generate videos.")
                    )
                }
                generateVideoVeo(apiKey, prompt)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun generateVideoVeo(apiKey: String, prompt: String): Result<File> {
        val model = "veo-3.1-fast-generate-preview"
        val payload = JSONObject().apply {
            put("instances", JSONArray().put(JSONObject().apply { put("prompt", prompt) }))
            put("parameters", JSONObject().apply {
                put("aspectRatio", "9:16")
                put("sampleCount", 1)
            })
        }
        val startRequest = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:predictLongRunning")
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        val operationName = client.newCall(startRequest).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return Result.failure(Exception("Veo start error (${response.code}): ${errorMessage(body)}"))
            }
            JSONObject(body).optString("name", "")
        }
        if (operationName.isBlank()) {
            return Result.failure(Exception("Veo did not return an operation name."))
        }

        // Poll the long-running operation (Veo usually takes 1-5 minutes).
        var videoUri: String? = null
        var polls = 0
        while (polls < 36 && videoUri == null) {
            polls++
            delay(10_000)
            val pollRequest = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/$operationName")
                .header("x-goog-api-key", apiKey)
                .get()
                .build()
            client.newCall(pollRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    if (json.has("error")) {
                        val err = json.getJSONObject("error")
                        throw Exception("Veo failed: ${err.optString("message", "unknown error")}")
                    }
                    if (json.optBoolean("done", false)) {
                        videoUri = json.optJSONObject("response")
                            ?.optJSONObject("generateVideoResponse")
                            ?.optJSONArray("generatedSamples")
                            ?.optJSONObject(0)
                            ?.optJSONObject("video")
                            ?.optString("uri", "")
                            ?.takeIf { it.isNotBlank() }
                    }
                }
            }
        }
        val uri = videoUri ?: return Result.failure(Exception("Veo did not finish in time or returned no video."))

        val downloadRequest = Request.Builder()
            .url(uri)
            .header("x-goog-api-key", apiKey)
            .get()
            .build()
        client.newCall(downloadRequest).execute().use { response ->
            val bytes = response.body?.bytes()
                ?: return Result.failure(Exception("Could not download the generated video."))
            return Result.success(saveBytes(bytes, "mp4", "video"))
        }
    }

    private fun errorMessage(body: String): String {
        return try {
            JSONObject(body).optJSONObject("error")?.optString("message")?.take(300)
                ?: body.take(300)
        } catch (_: Exception) {
            body.take(300)
        }
    }
}
