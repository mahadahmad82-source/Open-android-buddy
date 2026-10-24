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

    suspend fun generateVideo(
        config: AiProviderConfig,
        apiKey: String,
        prompt: String,
        modelOverride: String? = null
    ): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                when (config.type) {
                    ProviderType.GEMINI -> generateVideoVeo(apiKey, prompt)
                    ProviderType.FAL -> generateVideoFal(apiKey, modelOverride ?: config.modelName, prompt)
                    ProviderType.REPLICATE -> generateVideoReplicate(apiKey, modelOverride ?: config.modelName, prompt)
                    ProviderType.POLLINATIONS -> generateVideoPollinations(apiKey, modelOverride ?: config.modelName, prompt)
                    ProviderType.HF_SPACE -> generateVideoHfSpace(config, apiKey, prompt)
                    ProviderType.COMFYUI -> generateVideoComfyUi(config, prompt)
                    ProviderType.CUSTOM_OPENAI, ProviderType.KIMI ->
                        generateVideoOpenAiGateway(config, apiKey, prompt, modelOverride)
                    else -> Result.failure(
                        Exception("${config.name} cannot generate videos. Add a Gemini, fal.ai, Replicate, Pollinations, Hugging Face Space or ComfyUI provider for video generation.")
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * OpenAI-style video gateways (e.g. OmniRoute): POST {baseUrl}/videos/generations
     * with {model, prompt}. The gateway runs the job internally (Veo via
     * Google Flow/Vertex, Runway, fal, …) and answers OpenAI-like:
     * {"data": [{"url": "https://…", "format": "mp4"}]} — one blocking call.
     * A gateway's chat model ("auto") is NOT a video model, so when no
     * explicit video model is configured, the Google Flow Veo Fast model is
     * used — the route an Antigravity connection inside OmniRoute unlocks.
     */
    private fun generateVideoOpenAiGateway(
        config: AiProviderConfig,
        apiKey: String,
        prompt: String,
        modelOverride: String?
    ): Result<File> {
        val base = config.baseUrl?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("${config.name} has no base URL configured."))
        val configured = config.modelName.trim()
        val model = modelOverride?.takeIf { it.isNotBlank() }
            ?: configured.takeIf {
                it.isNotBlank() && it != "auto" &&
                    (it.contains("/") || it.contains("veo", true) || it.contains("video", true))
            }
            ?: "googleflow/veo-3.1-fast-generate"
        val payload = JSONObject().apply {
            put("model", model)
            put("prompt", prompt)
        }
        val gatewayClient = client.newBuilder()
            .callTimeout(12, TimeUnit.MINUTES)
            .readTimeout(12, TimeUnit.MINUTES)
            .build()
        val request = Request.Builder()
            .url("$base/videos/generations")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        val videoUrl = gatewayClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(bodyText).optJSONObject("error")?.optString("message")
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: bodyText.take(300)
                return Result.failure(
                    Exception("Video gateway (${config.name}, model $model) HTTP ${response.code}: $message")
                )
            }
            val json = JSONObject(bodyText)
            val data = json.optJSONArray("data")
            val first = if (data != null && data.length() > 0) data.optJSONObject(0) else null
            first?.optString("url")?.takeIf { it.isNotBlank() }
                ?: first?.optString("video_url")?.takeIf { it.isNotBlank() }
                ?: json.optString("url").takeIf { it.isNotBlank() }
                ?: return Result.failure(
                    Exception("Video gateway (${config.name}) response had no video URL: ${bodyText.take(300)}")
                )
        }
        // Download the finished video. Gateway-hosted URLs (same origin as
        // the gateway itself) can be auth-gated, so send the key for those.
        val downloadBuilder = Request.Builder().url(videoUrl).get()
        if (videoUrl.startsWith(base)) {
            downloadBuilder.header("Authorization", "Bearer $apiKey")
        }
        return client.newCall(downloadBuilder.build()).execute().use { dl ->
            if (!dl.isSuccessful) {
                Result.failure(Exception("Video gateway (${config.name}) video download failed: HTTP ${dl.code}"))
            } else {
                val bytes = dl.body?.bytes()
                if (bytes == null) Result.failure(Exception("Could not download the generated video."))
                else Result.success(saveBytes(bytes, "mp4", "video"))
            }
        }
    }

    /**
     * Pollinations (open-source, gen.pollinations.ai): one blocking GET
     * /video/{prompt} that returns the finished MP4 directly. Query params
     * pick the model, the length and the orientation; the key goes in the
     * Authorization header so it never appears in a URL. Every current
     * video model is paid-only there — generation spends the key's paid
     * Pollen balance (cheapest: Wan 2.2 Fast at 0.01 Pollen/sec, 480p;
     * Seedance 1 Pro Fast gives 720p). Generation can take minutes, so
     * this call uses a long timeout like the gateway backend.
     */
    private fun generateVideoPollinations(apiKey: String, modelName: String, prompt: String): Result<File> {
        val model = modelName.takeIf { it.contains("/") } ?: ProviderType.POLLINATIONS.defaultModel
        val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8").replace("+", "%20")
        val url = "https://gen.pollinations.ai/video/$encodedPrompt" +
            "?model=$model&duration=5&aspectRatio=9:16"
        val videoClient = client.newBuilder()
            .callTimeout(12, TimeUnit.MINUTES)
            .readTimeout(12, TimeUnit.MINUTES)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        return videoClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                return Result.failure(
                    Exception("Pollinations video error (${response.code}): ${errorMessage(body)}")
                )
            }
            val contentType = response.header("Content-Type") ?: ""
            val bytes = response.body?.bytes()
                ?: return Result.failure(Exception("Pollinations returned an empty response."))
            if (!contentType.contains("video", ignoreCase = true) && bytes.size < 10_000) {
                // A tiny non-video body is an error page/JSON, not a clip.
                return Result.failure(
                    Exception("Pollinations did not return a video: ${String(bytes).take(300)}")
                )
            }
            Result.success(saveBytes(bytes, "mp4", "video"))
        }
    }


    /**
     * Hugging Face Space (Gradio API): the user's own free ZeroGPU Space
     * running Wan 2.2. POST /gradio_api/call/generate starts the job, the
     * matching GET streams SSE events until process_completed, whose
     * payload carries the finished video file. The hf_ key goes in the
     * Authorization header so the generation spends the KEY OWNER's free
     * ZeroGPU quota (an anonymous call would have almost no quota).
     */
    private fun generateVideoHfSpace(config: AiProviderConfig, apiKey: String, prompt: String): Result<File> {
        val base = config.baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("${config.name} has no Base URL. Set it to your Space address (e.g. https://you-your-space.hf.space)."))
        val spaceClient = client.newBuilder()
            .callTimeout(15, TimeUnit.MINUTES)
            .readTimeout(15, TimeUnit.MINUTES)
            .build()
        val joinPayload = JSONObject().apply {
            put("data", JSONArray().put(prompt))
        }
        val joinRequest = Request.Builder()
            .url("$base/gradio_api/call/generate")
            .header("Content-Type", "application/json")
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
            .post(joinPayload.toString().toRequestBody(jsonMediaType))
            .build()
        val eventId = spaceClient.newCall(joinRequest).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return Result.failure(Exception("Hugging Face Space start error (${response.code}): ${errorMessage(body)}"))
            }
            JSONObject(body).optString("event_id", "")
        }
        if (eventId.isBlank()) {
            return Result.failure(Exception("Hugging Face Space did not return an event id."))
        }
        val streamRequest = Request.Builder()
            .url("$base/gradio_api/call/generate/$eventId")
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
            .get()
            .build()
        var videoUrl: String? = null
        var streamError: String? = null
        spaceClient.newCall(streamRequest).execute().use { response ->
            if (!response.isSuccessful) {
                return Result.failure(Exception("Hugging Face Space stream error (${response.code})."))
            }
            val source = response.body?.source()
                ?: return Result.failure(Exception("Hugging Face Space returned an empty stream."))
            var currentEvent = ""
            while (true) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.startsWith("event:") -> currentEvent = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        val dataText = line.removePrefix("data:").trim()
                        if (currentEvent == "error") {
                            streamError = dataText.take(300)
                        } else if (currentEvent == "complete" || currentEvent == "process_completed") {
                            videoUrl = extractGradioVideoUrl(dataText)
                        }
                    }
                }
                if (videoUrl != null || streamError != null) break
            }
        }
        if (streamError != null) {
            return Result.failure(Exception("Hugging Face Space generation failed: $streamError"))
        }
        val rawUrl = videoUrl
            ?: return Result.failure(Exception("Hugging Face Space finished without a video (the daily free ZeroGPU quota may be used up)."))
        val fullUrl = if (rawUrl.startsWith("http")) rawUrl else base + if (rawUrl.startsWith("/")) rawUrl else "/$rawUrl"
        val downloadBuilder = Request.Builder().url(fullUrl).get()
        if (apiKey.isNotBlank()) downloadBuilder.header("Authorization", "Bearer $apiKey")
        return client.newCall(downloadBuilder.build()).execute().use { dl ->
            if (!dl.isSuccessful) {
                Result.failure(Exception("Hugging Face Space video download failed: HTTP ${dl.code}"))
            } else {
                val bytes = dl.body?.bytes()
                if (bytes == null) Result.failure(Exception("Could not download the generated video."))
                else Result.success(saveBytes(bytes, "mp4", "video"))
            }
        }
    }

    /** Gradio results carry the video as a file object ({url/path}) or nested under "video". */
    private fun extractGradioVideoUrl(dataText: String): String? {
        return try {
            val first = JSONArray(dataText).optJSONObject(0) ?: return null
            first.optJSONObject("video")?.optString("url")?.takeIf { it.isNotBlank() }
                ?: first.optString("url").takeIf { it.isNotBlank() }
                ?: first.optString("path").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * ComfyUI server (the user's own, e.g. on a free Kaggle/Colab GPU):
     * POST /prompt with the Wan 2.2 workflow, poll /history until the run
     * completes, then download the clip from /view. ComfyUI has no API
     * key — the (per-session tunnel) Base URL is the whole credential.
     */
    private suspend fun generateVideoComfyUi(config: AiProviderConfig, prompt: String): Result<File> {
        val base = config.baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("${config.name} has no Base URL. Paste the current session's tunnel URL (it changes every session)."))
        val negative = "Bright tones, overexposed, static, blurred details, subtitles, style, works, paintings, images, static, overall gray, worst quality, low quality, JPEG compression residue, ugly, incomplete, extra fingers, poorly drawn hands, poorly drawn faces, deformed, disfigured, misshapen limbs, fused fingers, still picture, messy background"
        fun node(classType: String, inputs: JSONObject) = JSONObject().apply {
            put("class_type", classType)
            put("inputs", inputs)
        }
        fun ref(id: String, slot: Int = 0) = JSONArray().put(id).put(slot)
        val workflow = JSONObject().apply {
            put("1", node("UNETLoader", JSONObject().apply {
                put("unet_name", "wan2.2_ti2v_5B_fp16.safetensors")
                put("weight_dtype", "default")
            }))
            put("2", node("ModelSamplingSD3", JSONObject().apply {
                put("model", ref("1"))
                put("shift", 8.0)
            }))
            put("3", node("CLIPLoader", JSONObject().apply {
                put("clip_name", "umt5_xxl_fp8_e4m3fn_scaled.safetensors")
                put("type", "wan")
                put("device", "default")
            }))
            put("4", node("CLIPTextEncode", JSONObject().apply {
                put("text", prompt)
                put("clip", ref("3"))
            }))
            put("5", node("CLIPTextEncode", JSONObject().apply {
                put("text", negative)
                put("clip", ref("3"))
            }))
            put("6", node("VAELoader", JSONObject().apply {
                put("vae_name", "wan2.2_vae.safetensors")
            }))
            put("7", node("Wan22ImageToVideoLatent", JSONObject().apply {
                put("vae", ref("6"))
                put("width", 480)
                put("height", 832)
                put("length", 81)
                put("batch_size", 1)
            }))
            put("8", node("KSampler", JSONObject().apply {
                put("model", ref("2"))
                put("seed", (100_000..999_999_999).random())
                put("steps", 20)
                put("cfg", 5.0)
                put("sampler_name", "uni_pc")
                put("scheduler", "simple")
                put("positive", ref("4"))
                put("negative", ref("5"))
                put("latent_image", ref("7"))
                put("denoise", 1.0)
            }))
            put("9", node("VAEDecode", JSONObject().apply {
                put("samples", ref("8"))
                put("vae", ref("6"))
            }))
            put("10", node("CreateVideo", JSONObject().apply {
                put("images", ref("9"))
                put("fps", 16.0)
            }))
            put("11", node("SaveVideo", JSONObject().apply {
                put("video", ref("10"))
                put("filename_prefix", "openbuddy")
                put("format", "mp4")
                put("codec", "h264")
            }))
        }
        val submitPayload = JSONObject().apply {
            put("prompt", workflow)
            put("client_id", java.util.UUID.randomUUID().toString())
        }
        val submitRequest = Request.Builder()
            .url("$base/prompt")
            .header("Content-Type", "application/json")
            .post(submitPayload.toString().toRequestBody(jsonMediaType))
            .build()
        val promptId = client.newCall(submitRequest).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return Result.failure(Exception("ComfyUI submit error (${response.code}): ${errorMessage(body)}"))
            }
            JSONObject(body).optString("prompt_id", "")
        }
        if (promptId.isBlank()) {
            return Result.failure(Exception("ComfyUI did not return a prompt id (the workflow may have been rejected — see the notebook's ComfyUI log)."))
        }
        var videoRef: Triple<String, String, String>? = null
        var polls = 0
        while (polls < 120 && videoRef == null) {
            polls++
            delay(5_000)
            val historyRequest = Request.Builder().url("$base/history/$promptId").get().build()
            client.newCall(historyRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val entry = JSONObject(body).optJSONObject(promptId) ?: return@use
                    val status = entry.optJSONObject("status")
                    if (status?.optString("status_str") == "error") {
                        val messages = status.optJSONArray("messages")
                        var detail = "ComfyUI reported an execution error."
                        if (messages != null) {
                            for (i in 0 until messages.length()) {
                                val m = messages.optJSONArray(i)
                                if (m != null && m.optString(0) == "execution_error") {
                                    detail = "ComfyUI error: " + (m.optJSONObject(1)?.optString("exception_message") ?: detail)
                                    break
                                }
                            }
                        }
                        return Result.failure(Exception(detail.take(300)))
                    }
                    val outputs = entry.optJSONObject("outputs") ?: return@use
                    val keys = outputs.keys()
                    while (keys.hasNext()) {
                        val out = outputs.optJSONObject(keys.next()) ?: continue
                        val videos = out.optJSONArray("gifs") ?: out.optJSONArray("videos")
                        val first = videos?.optJSONObject(0)
                        if (first != null) {
                            videoRef = Triple(
                                first.optString("filename", ""),
                                first.optString("subfolder", ""),
                                first.optString("type", "output")
                            )
                            break
                        }
                    }
                }
            }
        }
        val (filename, subfolder, type) = videoRef
            ?: return Result.failure(Exception("ComfyUI did not finish the video in time (or the session stopped)."))
        val viewUrl = "$base/view?filename=" + java.net.URLEncoder.encode(filename, "UTF-8") +
            "&subfolder=" + java.net.URLEncoder.encode(subfolder, "UTF-8") +
            "&type=" + java.net.URLEncoder.encode(type, "UTF-8")
        return downloadVideo(viewUrl, null)
    }

    /**
     * fal.ai queue API: submit → poll status_url → fetch response_url.
     * The user's provider model field picks the fal model (Kling by default;
     * Luma, MiniMax and Veo models also work — aspect_ratio is only sent to
     * models known to accept it).
     */
    private suspend fun generateVideoFal(apiKey: String, modelName: String, prompt: String): Result<File> {
        val model = modelName.takeIf { it.startsWith("fal-ai/") }
            ?: ProviderType.FAL.defaultModel
        val payload = JSONObject().apply {
            put("prompt", prompt)
            if (model.contains("kling") || model.contains("luma") || model.contains("veo")) {
                put("aspect_ratio", "9:16")
            }
            if (model.contains("kling")) {
                put("duration", "5")
            }
        }
        val submitRequest = Request.Builder()
            .url("https://queue.fal.run/$model")
            .header("Authorization", "Key $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        val (statusUrl, responseUrl) = client.newCall(submitRequest).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return Result.failure(Exception("fal.ai submit error (${response.code}): ${errorMessage(body)}"))
            }
            val json = JSONObject(body)
            val s = json.optString("status_url", "")
            val r = json.optString("response_url", "")
            if (s.isBlank() || r.isBlank()) {
                return Result.failure(Exception("fal.ai did not return queue URLs."))
            }
            s to r
        }

        // Poll until COMPLETED (most clips finish within 1-5 minutes).
        var done = false
        var polls = 0
        while (polls < 72 && !done) {
            polls++
            delay(5_000)
            val statusRequest = Request.Builder()
                .url(statusUrl)
                .header("Authorization", "Key $apiKey")
                .get()
                .build()
            client.newCall(statusRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    when (JSONObject(body).optString("status", "")) {
                        "COMPLETED" -> done = true
                        "FAILED" -> return Result.failure(Exception("fal.ai reported the generation as FAILED."))
                    }
                }
            }
        }
        if (!done) return Result.failure(Exception("fal.ai did not finish the video in time."))

        val resultRequest = Request.Builder()
            .url(responseUrl)
            .header("Authorization", "Key $apiKey")
            .get()
            .build()
        val videoUrl = client.newCall(resultRequest).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return Result.failure(Exception("fal.ai result error (${response.code}): ${errorMessage(body)}"))
            }
            JSONObject(body).optJSONObject("video")?.optString("url", "")?.takeIf { it.isNotBlank() }
        } ?: return Result.failure(Exception("fal.ai result contained no video URL."))
        return downloadVideo(videoUrl, null)
    }

    /**
     * Replicate predictions API: create → poll → download output.
     * The user's provider model field picks the model (Veo 3 by default;
     * Kling, MiniMax and Seedance also work).
     */
    private suspend fun generateVideoReplicate(apiKey: String, modelName: String, prompt: String): Result<File> {
        val model = modelName.takeIf { it.contains("/") } ?: ProviderType.REPLICATE.defaultModel
        val input = JSONObject().apply {
            put("prompt", prompt)
            if (model.startsWith("google/veo")) {
                put("aspect_ratio", "9:16")
            }
        }
        val payload = JSONObject().apply { put("input", input) }
        val createRequest = Request.Builder()
            .url("https://api.replicate.com/v1/models/$model/predictions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        val predictionId = client.newCall(createRequest).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return Result.failure(Exception("Replicate create error (${response.code}): ${errorMessage(body)}"))
            }
            JSONObject(body).optString("id", "")
        }
        if (predictionId.isBlank()) {
            return Result.failure(Exception("Replicate did not return a prediction id."))
        }

        var outputUrl: String? = null
        var polls = 0
        while (polls < 72 && outputUrl == null) {
            polls++
            delay(5_000)
            val pollRequest = Request.Builder()
                .url("https://api.replicate.com/v1/predictions/$predictionId")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            client.newCall(pollRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    when (json.optString("status", "")) {
                        "succeeded" -> outputUrl = extractOutputUrl(json.opt("output"))
                        "failed", "canceled" -> return Result.failure(
                            Exception("Replicate generation ${json.optString("status")}: ${json.optString("error", "unknown error")}")
                        )
                    }
                }
            }
        }
        val url = outputUrl ?: return Result.failure(Exception("Replicate did not finish the video in time."))
        return downloadVideo(url, null)
    }

    /** Replicate outputs can be a URL string or an array of URLs. */
    private fun extractOutputUrl(output: Any?): String? {
        return when (output) {
            is String -> output.takeIf { it.startsWith("http") }
            is org.json.JSONArray -> {
                var found: String? = null
                for (i in 0 until output.length()) {
                    val item = output.optString(i, "")
                    if (item.startsWith("http")) {
                        found = item
                        break
                    }
                }
                found
            }
            else -> null
        }
    }

    private fun downloadVideo(url: String, apiKeyHeader: String?): Result<File> {
        val builder = Request.Builder().url(url).get()
        if (apiKeyHeader != null) builder.header("x-goog-api-key", apiKeyHeader)
        return client.newCall(builder.build()).execute().use { response ->
            val bytes = response.body?.bytes()
                ?: return Result.failure(Exception("Could not download the generated video."))
            Result.success(saveBytes(bytes, "mp4", "video"))
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
