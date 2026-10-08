package com.example.data.provider

import com.example.domain.model.AiError
import com.example.domain.model.AiProviderConfig
import com.example.domain.model.ChatMessage
import com.example.domain.model.MessageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

class ClaudeProvider(
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
        val model = config.modelName.ifBlank { "claude-3-5-sonnet-20241022" }
        jsonBody.put("model", model)
        jsonBody.put("max_tokens", 4096)
        jsonBody.put("stream", true)

        if (!systemPrompt.isNullOrBlank()) {
            jsonBody.put("system", systemPrompt)
        }

        val messagesArray = JSONArray()
        for (msg in messages) {
            if (msg.role == MessageRole.SYSTEM) continue // Claude takes system prompt at top-level
            val item = JSONObject()
            val role = if (msg.role == MessageRole.USER) "user" else "assistant"
            item.put("role", role)
            item.put("content", msg.content)
            messagesArray.put(item)
        }
        jsonBody.put("messages", messagesArray)

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
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
                    401, 403 -> throw AiError.InvalidKey(config.name)
                    429 -> throw AiError.RateLimited(config.name)
                    else -> throw AiError.ServerError(code, extractJsonErrorMessage(errBody))
                }
            }

            val source = response.body?.source() ?: throw AiError.Other("No response body received")

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data:")) {
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank() || data == "[DONE]") continue
                    try {
                        val parsed = JSONObject(data)
                        val type = parsed.optString("type")
                        if (type == "content_block_delta") {
                            val delta = parsed.optJSONObject("delta")
                            if (delta != null && delta.optString("type") == "text_delta") {
                                val text = delta.optString("text")
                                if (text.isNotEmpty()) {
                                    emit(text)
                                }
                            }
                        } else if (type == "error") {
                            val err = parsed.optJSONObject("error")
                            val msg = err?.optString("message") ?: "Claude streaming error"
                            throw AiError.ServerError(500, msg)
                        }
                    } catch (e: Exception) {
                        if (e is AiError) throw e
                    }
                }
            }
            response.close()
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
            val flow = streamChat(listOf(ChatMessage(MessageRole.USER, "Hi")), "Answer with 1 word.")
            var received = ""
            flow.collect { chunk ->
                received += chunk
                if (received.length > 5) return@collect
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
}
