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

class OpenAiCompatibleProvider(
    override val config: AiProviderConfig,
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : AiProvider {

    override fun streamChat(messages: List<ChatMessage>, systemPrompt: String?): Flow<String> = flow {
        val rawBaseUrl = config.baseUrl?.trim()?.ifBlank { null }
            ?: config.type.defaultBaseUrl
            ?: "https://api.openai.com/v1"

        val endpoint = if (rawBaseUrl.endsWith("/chat/completions")) {
            rawBaseUrl
        } else {
            "${rawBaseUrl.trimEnd('/')}/chat/completions"
        }

        val jsonBody = JSONObject()
        val model = config.modelName.ifBlank { config.type.defaultModel }
        jsonBody.put("model", model)
        jsonBody.put("stream", true)

        val messagesArray = JSONArray()
        if (!systemPrompt.isNullOrBlank()) {
            val sysObj = JSONObject()
            sysObj.put("role", "system")
            sysObj.put("content", systemPrompt)
            messagesArray.put(sysObj)
        }

        for (msg in messages) {
            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            val item = JSONObject()
            item.put("role", role)
            item.put("content", msg.content)
            messagesArray.put(item)
        }
        jsonBody.put("messages", messagesArray)

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        val request = requestBuilder.build()
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
                        val choices = parsed.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val firstChoice = choices.getJSONObject(0)
                            val delta = firstChoice.optJSONObject("delta")
                            val content = delta?.optString("content")
                            if (!content.isNullOrEmpty()) {
                                emit(content)
                            }
                        }
                    } catch (_: Exception) {
                        // ignore non-json frames
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
            val flow = streamChat(listOf(ChatMessage(MessageRole.USER, "Hi")), "Respond with 1 word.")
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
