package com.mahad.openbuddy.data.mcp

import com.mahad.openbuddy.data.local.entity.McpServerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class McpRemoteTool(
    val name: String,
    val description: String,
    val inputSchema: JSONObject?
)

/**
 * Phase 4 — minimal MCP (Model Context Protocol) client over Streamable HTTP.
 *
 * Speaks JSON-RPC 2.0: initialize -> notifications/initialized -> tools/list /
 * tools/call. Handles both plain JSON responses and SSE-framed responses
 * (the two shapes MCP HTTP servers use). The session id returned by the
 * initialize call is echoed back on later requests, as the spec requires.
 */
class McpClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val nextId = AtomicInteger(1)
    private val sessionIds = mutableMapOf<String, String>()

    private data class RpcResponse(val json: JSONObject, val sessionId: String?)

    private suspend fun post(server: McpServerEntity, payload: JSONObject, isNotification: Boolean): Result<RpcResponse?> =
        withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder()
                    .url(server.url)
                    .header("Accept", "application/json, text/event-stream")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Open-Buddy-Android")
                server.authToken?.takeIf { it.isNotBlank() }?.let {
                    builder.header("Authorization", "Bearer $it")
                }
                sessionIds[server.id]?.let { builder.header("Mcp-Session-Id", it) }
                builder.post(payload.toString().toRequestBody(jsonMediaType))

                client.newCall(builder.build()).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    val newSession = response.header("Mcp-Session-Id")
                    if (isNotification) {
                        return@use Result.success(RpcResponse(JSONObject(), newSession))
                    }
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("MCP server HTTP ${response.code}: ${body.take(300)}"))
                    }
                    val parsed = parseRpcBody(body)
                        ?: return@use Result.failure(Exception("MCP server returned an unreadable response."))
                    if (parsed.has("error")) {
                        val err = parsed.getJSONObject("error")
                        return@use Result.failure(Exception("MCP error: ${err.optString("message", "unknown")}"))
                    }
                    Result.success(RpcResponse(parsed, newSession))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** MCP HTTP servers answer with plain JSON or with an SSE stream of `data:` lines. */
    private fun parseRpcBody(body: String): JSONObject? {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            return try { JSONObject(trimmed) } catch (_: Exception) { null }
        }
        // SSE: take the last data payload that parses as a JSON-RPC message.
        var lastJson: JSONObject? = null
        for (line in trimmed.lineSequence()) {
            val data = line.trim().removePrefix("data:").trim()
            if (data.startsWith("{")) {
                try {
                    val obj = JSONObject(data)
                    if (obj.has("result") || obj.has("error")) lastJson = obj
                } catch (_: Exception) { /* keep scanning */ }
            }
        }
        return lastJson
    }

    private suspend fun ensureSession(server: McpServerEntity): Result<Unit> {
        if (sessionIds.containsKey(server.id)) return Result.success(Unit)
        val initPayload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", nextId.getAndIncrement())
            put("method", "initialize")
            put("params", JSONObject().apply {
                put("protocolVersion", "2024-11-05")
                put("capabilities", JSONObject())
                put("clientInfo", JSONObject().apply {
                    put("name", "Open Buddy")
                    put("version", "1.0")
                })
            })
        }
        val initResult = post(server, initPayload, isNotification = false)
        val rpc = initResult.getOrNull()
            ?: return Result.failure(initResult.exceptionOrNull() ?: Exception("MCP initialize failed"))
        rpc.sessionId?.let { sessionIds[server.id] = it }

        val initializedNote = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", "notifications/initialized")
        }
        post(server, initializedNote, isNotification = true)
        return Result.success(Unit)
    }

    suspend fun listTools(server: McpServerEntity): Result<List<McpRemoteTool>> {
        val session = ensureSession(server)
        if (session.isFailure) return Result.failure(session.exceptionOrNull() ?: Exception("MCP session failed"))
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", nextId.getAndIncrement())
            put("method", "tools/list")
            put("params", JSONObject())
        }
        val result = post(server, payload, isNotification = false)
        val rpc = result.getOrNull()
            ?: return Result.failure(result.exceptionOrNull() ?: Exception("tools/list failed"))
        val toolsArray = rpc.json.optJSONObject("result")?.optJSONArray("tools") ?: JSONArray()
        val tools = mutableListOf<McpRemoteTool>()
        for (i in 0 until toolsArray.length()) {
            val obj = toolsArray.optJSONObject(i) ?: continue
            tools.add(
                McpRemoteTool(
                    name = obj.optString("name", ""),
                    description = obj.optString("description", ""),
                    inputSchema = obj.optJSONObject("inputSchema")
                )
            )
        }
        return Result.success(tools.filter { it.name.isNotBlank() })
    }

    suspend fun callTool(server: McpServerEntity, toolName: String, arguments: JSONObject): Result<String> {
        val session = ensureSession(server)
        if (session.isFailure) return Result.failure(session.exceptionOrNull() ?: Exception("MCP session failed"))
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", nextId.getAndIncrement())
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", toolName)
                put("arguments", arguments)
            })
        }
        val result = post(server, payload, isNotification = false)
        val rpc = result.getOrNull()
            ?: return Result.failure(result.exceptionOrNull() ?: Exception("tools/call failed"))
        val resultObj = rpc.json.optJSONObject("result")
            ?: return Result.failure(Exception("MCP tool returned no result."))
        if (resultObj.optBoolean("isError", false)) {
            return Result.failure(Exception("MCP tool reported an error."))
        }
        val contentArray = resultObj.optJSONArray("content") ?: JSONArray()
        val texts = mutableListOf<String>()
        for (i in 0 until contentArray.length()) {
            val item = contentArray.optJSONObject(i) ?: continue
            when (item.optString("type")) {
                "text" -> texts.add(item.optString("text", ""))
                "image" -> texts.add("[image content returned by the tool]")
                "resource" -> texts.add(item.optJSONObject("resource")?.optString("text", "[resource]") ?: "[resource]")
                else -> texts.add(item.toString().take(500))
            }
        }
        return Result.success(texts.joinToString("\n").ifBlank { "(tool returned no text content)" }.take(8000))
    }

    fun forgetSession(serverId: String) {
        sessionIds.remove(serverId)
    }
}
