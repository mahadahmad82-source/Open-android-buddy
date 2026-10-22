package com.mahad.openbuddy.data.provider

import com.mahad.openbuddy.domain.model.AiError
import com.mahad.openbuddy.domain.model.AiProviderConfig
import com.mahad.openbuddy.domain.model.ChatMessage
import com.mahad.openbuddy.domain.model.MessageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class GeminiProvider(
    override val config: AiProviderConfig,
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : AiProvider {

    override fun streamChat(messages: List<ChatMessage>, systemPrompt: String?): Flow<String> = flow {
        if (apiKey.isBlank()) {
            throw AiError.InvalidKey(config.name)
        }

        val jsonBody = JSONObject()

        if (!systemPrompt.isNullOrBlank()) {
            val sysInstruction = JSONObject()
            val parts = JSONArray()
            parts.put(JSONObject().put("text", systemPrompt))
            sysInstruction.put("parts", parts)
            jsonBody.put("systemInstruction", sysInstruction)
        }

        val contents = JSONArray()
        for (msg in messages) {
            // System text goes in systemInstruction (above), never as a chat turn.
            if (msg.role == MessageRole.SYSTEM) continue
            val role = if (msg.role == MessageRole.USER) "user" else "model"
            val item = JSONObject()
            item.put("role", role)
            val parts = JSONArray()
            parts.put(JSONObject().put("text", msg.content))
            item.put("parts", parts)
            contents.put(item)
        }
        jsonBody.put("contents", contents)

        val rawModel = config.modelName.trim().removePrefix("models/")
        val model = when {
            rawModel.isBlank() -> "gemini-2.5-flash"
            rawModel.equals("gemini-pro", ignoreCase = true) ||
            rawModel.equals("gemini-1.0-pro", ignoreCase = true) ||
            rawModel.equals("gemini-1.0", ignoreCase = true) -> "gemini-2.5-flash"
            rawModel.equals("gemini-1.5-flash-latest", ignoreCase = true) -> "gemini-2.5-flash"
            rawModel.equals("gemini-1.5-pro-latest", ignoreCase = true) -> "gemini-2.5-flash"
            rawModel.equals("gemini-1.5-flash", ignoreCase = true) -> "gemini-2.5-flash"
            rawModel.equals("gemini-1.5-pro", ignoreCase = true) -> "gemini-2.5-flash"
            rawModel.equals("gemini-2.0-flash-exp", ignoreCase = true) -> "gemini-2.5-flash"
            else -> rawModel
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse"

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)
        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val code = response.code
                val errBody = try { response.body?.string() ?: "" } catch (_: Exception) { "" }
                response.close()
                when (code) {
                    400 -> {
                        if (errBody.contains("API_KEY", ignoreCase = true) || errBody.contains("key", ignoreCase = true)) {
                            throw AiError.InvalidKey(config.name)
                        }
                        throw AiError.ServerError(code, extractJsonErrorMessage(errBody))
                    }
                    401, 403 -> throw AiError.InvalidKey(config.name)
                    429 -> throw AiError.RateLimited(config.name)
                    else -> throw AiError.ServerError(code, extractJsonErrorMessage(errBody))
                }
            }

            val source = response.body?.source() ?: throw AiError.Other("No response body received")
            var hasEmitted = false

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data:")) {
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank() || data == "[DONE]") continue
                    try {
                        val parsed = JSONObject(data)
                        val candidates = parsed.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val firstCandidate = candidates.getJSONObject(0)
                            val content = firstCandidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                // Join ALL parts — taking only part 0 truncated multi-part replies.
                                val text = buildString {
                                    for (p in 0 until parts.length()) {
                                        append(parts.getJSONObject(p).optString("text"))
                                    }
                                }
                                if (text.isNotEmpty()) {
                                    emit(text)
                                    hasEmitted = true
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // skip non-json frames
                    }
                }
            }
            response.close()
            if (!hasEmitted) {
                throw AiError.Other("The model returned an empty response. It may have been blocked by safety filters — please rephrase your message and try again.")
            }
        } catch (e: Exception) {
            when (e) {
                is CancellationException -> {
                    call.cancel()
                    throw e
                }
                is AiError -> throw e
                is SocketTimeoutException -> throw AiError.Timeout()
                is UnknownHostException, is ConnectException -> throw AiError.NoInternet()
                is IOException -> throw AiError.NetworkError(e.message ?: "Connection disrupted")
                else -> throw AiError.Other(e.message ?: "Unknown error occurred")
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            var received = ""
            try {
                streamChat(listOf(ChatMessage(MessageRole.USER, "Hello")), "Respond in 1 word.")
                    .onEach { received += it }
                    .first { received.length > 5 }
            } catch (_: NoSuchElementException) {
                // Stream ended before the threshold — still a valid response.
            }
            if (received.isBlank()) {
                throw Exception("No response received from the provider.")
            }
            val elapsed = System.currentTimeMillis() - start
            Result.success("Connection successful! Latency: ${elapsed}ms")
        } catch (e: Exception) {
            val safeMessage = when (e) {
                is AiError -> e.message
                is SocketTimeoutException -> "Request timed out"
                is UnknownHostException, is ConnectException -> "No internet connection or server unreachable"
                else -> e.message ?: "Failed to connect"
            }
            Result.failure(Exception(safeMessage))
        }
    }

    private fun extractJsonErrorMessage(body: String): String {
        return try {
            val json = JSONObject(body)
            val error = json.optJSONObject("error")
            error?.optString("message") ?: body.take(200)
        } catch (_: Exception) {
            body.take(200)
        }
    }

    companion object {
        suspend fun fetchAvailableModels(apiKey: String, client: OkHttpClient = OkHttpClient()): Result<List<String>> = withContext(Dispatchers.IO) {
            try {
                val cleanKey = apiKey.trim()
                if (cleanKey.isBlank()) return@withContext Result.failure(Exception("API key cannot be blank"))
                val request = Request.Builder()
                    // Key goes in a header, never in the URL (URLs leak into logs/history).
                    .url("https://generativelanguage.googleapis.com/v1beta/models")
                    .addHeader("x-goog-api-key", cleanKey)
                    .header("User-Agent", "Open-Buddy-Android")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}: ${body.take(120)}"))
                    }
                    val json = JSONObject(body)
                    val modelsArray = json.optJSONArray("models") ?: JSONArray()
                    val result = mutableListOf<String>()
                    for (i in 0 until modelsArray.length()) {
                        val m = modelsArray.getJSONObject(i)
                        val name = m.getString("name").removePrefix("models/")
                        val supportedMethods = m.optJSONArray("supportedGenerationMethods")
                        val canGenerate = supportedMethods != null && (0 until supportedMethods.length()).any { idx ->
                            supportedMethods.getString(idx) == "generateContent"
                        }
                        if (canGenerate && !name.contains("embedding", ignoreCase = true) && !name.contains("aqa", ignoreCase = true)) {
                            result.add(name)
                        }
                    }
                    // Sort modern flash and pro models to the top
                    result.sortWith(compareByDescending<String> { it.contains("2.5") || it.contains("flash") }.thenBy { it })
                    Result.success(result)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
