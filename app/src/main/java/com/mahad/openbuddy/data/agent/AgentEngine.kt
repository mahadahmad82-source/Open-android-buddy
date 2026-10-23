package com.mahad.openbuddy.data.agent

import android.content.Context
import com.mahad.openbuddy.data.local.OpenBuddyDatabase
import com.mahad.openbuddy.data.local.entity.McpServerEntity
import com.mahad.openbuddy.data.local.entity.MessageEntity
import com.mahad.openbuddy.data.local.entity.SkillEntity
import com.mahad.openbuddy.data.mcp.McpClient
import com.mahad.openbuddy.data.media.MediaGenerationService
import com.mahad.openbuddy.data.provider.AiProviderFactory
import com.mahad.openbuddy.data.remote.github.GitHubApiService
import com.mahad.openbuddy.data.repository.OpenBuddyRepository
import com.mahad.openbuddy.data.repository.SupabaseRepository
import com.mahad.openbuddy.data.security.SecretStore
import com.mahad.openbuddy.data.voice.TtsManager
import com.mahad.openbuddy.domain.model.ChatMessage
import com.mahad.openbuddy.domain.model.MessageRole
import com.mahad.openbuddy.domain.model.MessageStatus
import com.mahad.openbuddy.service.AgentForegroundService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * Phase 2 — the agent loop.
 *
 * One agent turn:
 *  1. Persist the user message and an empty assistant placeholder.
 *  2. Build the system prompt: base prompt + tool protocol + tool catalog +
 *     relevant long-term memories + the active skill's instructions.
 *  3. Ask the model. If the reply contains tool calls, run them (asking the
 *     user first for world-changing tools), feed results back, and repeat —
 *     up to [MAX_STEPS] rounds.
 *  4. The first reply with no tool calls is the final answer; it is persisted
 *     (with media markers for generated images/videos) and optionally spoken.
 */
class AgentEngine(
    private val context: Context,
    private val database: OpenBuddyDatabase,
    private val secretStore: SecretStore,
    private val providerFactory: AiProviderFactory,
    private val openBuddyRepository: OpenBuddyRepository,
    private val supabaseRepository: SupabaseRepository,
    private val ttsManager: TtsManager
) {
    private val messageDao get() = database.messageDao()
    private val conversationDao get() = database.conversationDao()
    private val memoryDao get() = database.memoryDao()
    private val mcpServerDao get() = database.mcpServerDao()
    private val appSettingDao get() = database.appSettingDao()

    private val mcpClient = McpClient()
    private val gitHubApiService = GitHubApiService()
    private val mediaService = MediaGenerationService(context)

    private val _pendingApproval = MutableStateFlow<ToolApprovalRequest?>(null)
    val pendingApproval: StateFlow<ToolApprovalRequest?> = _pendingApproval.asStateFlow()

    private val _statusText = MutableStateFlow<String?>(null)
    val statusText: StateFlow<String?> = _statusText.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var approvalDeferred: CompletableDeferred<Boolean>? = null

    /** Called by the UI when the user taps Approve / Reject on the approval card. */
    fun resolveApproval(approved: Boolean) {
        approvalDeferred?.complete(approved)
        approvalDeferred = null
        _pendingApproval.value = null
    }

    fun cancelRun() {
        approvalDeferred?.complete(false)
        approvalDeferred = null
        _pendingApproval.value = null
    }

    private suspend fun awaitApproval(request: ToolApprovalRequest): Boolean {
        _pendingApproval.value = request
        _statusText.value = "Waiting for your approval: ${request.summary}"
        val deferred = CompletableDeferred<Boolean>()
        approvalDeferred = deferred
        return try {
            deferred.await()
        } catch (e: CancellationException) {
            false
        } finally {
            if (approvalDeferred === deferred) approvalDeferred = null
            _pendingApproval.value = null
        }
    }

    suspend fun runTurn(
        conversationId: String,
        userText: String,
        activeSkill: SkillEntity?,
        speakReplies: Boolean,
        attachment: com.mahad.openbuddy.data.media.StagedAttachment? = null
    ) = withContext(Dispatchers.IO) {
        if ((userText.isBlank() && attachment == null) || _isRunning.value) return@withContext
        _isRunning.value = true
        AgentForegroundService.start(context, "Agent is working on your request…")
        try {
            runTurnInternal(conversationId, userText, activeSkill, speakReplies, attachment)
        } finally {
            _isRunning.value = false
            _statusText.value = null
            _pendingApproval.value = null
            AgentForegroundService.stop(context)
        }
    }

    private suspend fun runTurnInternal(
        conversationId: String,
        userText: String,
        activeSkill: SkillEntity?,
        speakReplies: Boolean,
        attachment: com.mahad.openbuddy.data.media.StagedAttachment? = null
    ) {
        // 1. Persist user message (+ first-message auto-title, mirroring normal chat)
        val existing = messageDao.getMessageListForConversation(conversationId)
        if (existing.count { it.role == MessageRole.USER.name } == 0) {
            val title = userText.lines().firstOrNull()?.take(36) ?: "Chat"
            conversationDao.renameConversation(conversationId, title, System.currentTimeMillis())
        }
        messageDao.insertMessage(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = MessageRole.USER.name,
                content = userText.trim() + (attachment?.let {
                    com.mahad.openbuddy.data.media.AttachmentSupport.markerFor(it)
                } ?: ""),
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.COMPLETED.name
            )
        )
        val assistantId = UUID.randomUUID().toString()
        messageDao.insertMessage(
            MessageEntity(
                id = assistantId,
                conversationId = conversationId,
                role = MessageRole.ASSISTANT.name,
                content = "",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.STREAMING.name
            )
        )

        try {
            val providerConfig = openBuddyRepository.getActiveProvider()
                ?: throw Exception("No AI Provider selected. Please configure a provider in Settings.")
            val apiKey = secretStore.getApiKey(providerConfig.id)
                ?: throw Exception("No API key saved for ${providerConfig.name}.")
            val provider = providerFactory.createProviderWithCustomKey(providerConfig, apiKey)

            val tools = buildTools()
            val toolsByName = tools.associateBy { it.name }
            val systemPrompt = buildSystemPrompt(userText, activeSkill, tools)

            // Working history: recent conversation + this turn's tool exchanges.
            // The newest user message keeps its attachment (vision); markers are
            // stripped from the text the model sees.
            val historyEntities = messageDao.getMessageListForConversation(conversationId)
                .filter { it.id != assistantId }
            val lastUserEntityId = historyEntities.lastOrNull { it.role == MessageRole.USER.name }?.id
            val history = historyEntities
                .map { entity ->
                    val (cleanContent, att) = if (entity.role == MessageRole.USER.name) {
                        com.mahad.openbuddy.data.media.AttachmentSupport.extract(entity.content)
                    } else {
                        entity.content to null
                    }
                    ChatMessage(
                        role = MessageRole.fromString(entity.role),
                        content = cleanContent,
                        attachmentPath = if (entity.id == lastUserEntityId) att?.path else null,
                        attachmentMime = if (entity.id == lastUserEntityId) att?.mimeType else null
                    )
                }
                .let { capHistory(it) }
                .toMutableList()

            val stepLog = mutableListOf<String>()
            val mediaMarkers = mutableListOf<String>()
            var finalText = ""

            var step = 0
            while (step < MAX_STEPS) {
                step++
                _statusText.value = if (step == 1) "Thinking…" else "Thinking (step $step)…"
                val response = StringBuilder()
                provider.streamChat(history, systemPrompt).collect { chunk -> response.append(chunk) }
                val rawResponse = response.toString()
                val calls = ToolCallParser.parse(rawResponse)
                val visible = ToolCallParser.visibleText(rawResponse)

                if (calls.isEmpty()) {
                    finalText = visible.ifBlank { rawResponse.trim() }
                    break
                }

                // Show any prose the model wrote alongside its tool calls.
                if (visible.isNotBlank()) {
                    updateAssistant(assistantId, visible, MessageStatus.STREAMING)
                }
                history.add(ChatMessage(role = MessageRole.ASSISTANT, content = rawResponse))

                for (call in calls) {
                    val tool = toolsByName[call.name]
                    if (tool == null) {
                        history.add(ChatMessage(role = MessageRole.USER, content = "TOOL RESULT (${call.name}): Error — unknown tool '${call.name}'. Use only the tools listed in your instructions."))
                        stepLog.add("${call.name} — unknown tool")
                        continue
                    }

                    val needsApproval = tool.requiresApproval ||
                        (tool.name == "supabase_sql" && CoreTools.supabaseNeedsApproval(supabaseRepository, call.arguments))
                    if (needsApproval) {
                        val approved = awaitApproval(
                            ToolApprovalRequest(
                                id = UUID.randomUUID().toString(),
                                toolName = tool.name,
                                summary = tool.approvalSummary(call.arguments)
                            )
                        )
                        if (!approved) {
                            history.add(ChatMessage(role = MessageRole.USER, content = "TOOL RESULT (${call.name}): The user REJECTED this action. Do not retry it; continue without it or ask what they prefer."))
                            stepLog.add("${call.name} — rejected by user")
                            continue
                        }
                    }

                    _statusText.value = "Running ${tool.name}…"
                    val outcome = try {
                        tool.execute(call.arguments)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ToolOutcome("Error: ${e.message}")
                    }
                    outcome.mediaPath?.let { path ->
                        val kind = outcome.mediaKind ?: "image"
                        mediaMarkers.add("[[$kind:$path]]")
                    }
                    stepLog.add("${call.name} — done")
                    history.add(
                        ChatMessage(
                            role = MessageRole.USER,
                            content = "TOOL RESULT (${call.name}): ${outcome.text.take(MAX_TOOL_RESULT_CHARS)}"
                        )
                    )
                    _statusText.value = "Thinking…"
                }
            }

            if (finalText.isBlank()) {
                finalText = "I ran out of steps before finishing. Here is what happened:\n" +
                    stepLog.joinToString("\n") { "• $it" }
            }

            val footer = buildString {
                if (mediaMarkers.isNotEmpty()) {
                    append("\n\n")
                    append(mediaMarkers.joinToString("\n"))
                }
                if (stepLog.isNotEmpty()) {
                    append("\n\n—\nTools used: ")
                    append(stepLog.joinToString(", "))
                }
            }
            val fullText = finalText + footer
            updateAssistant(assistantId, fullText, MessageStatus.COMPLETED)

            if (speakReplies) {
                ttsManager.speak(finalText)
            }
        } catch (e: CancellationException) {
            updateAssistant(assistantId, "[Agent stopped]", MessageStatus.COMPLETED)
        } catch (e: Exception) {
            messageDao.updateMessageContentAndStatus(
                id = assistantId,
                content = "",
                status = MessageStatus.ERROR.name,
                errorMessage = e.message ?: "Agent run failed."
            )
        }
    }

    private suspend fun updateAssistant(id: String, content: String, status: MessageStatus) {
        messageDao.updateMessageContentAndStatus(id, content, status.name, null)
    }

    private fun capHistory(messages: List<ChatMessage>): List<ChatMessage> {
        val recent = messages.takeLast(30)
        var total = 0
        val kept = mutableListOf<ChatMessage>()
        for (message in recent.asReversed()) {
            total += message.content.length
            if (total > 50_000 && kept.isNotEmpty()) break
            kept.add(message)
        }
        return kept.asReversed()
    }

    private suspend fun buildTools(): List<AgentTool> {
        val core = CoreTools(
            secretStore = secretStore,
            gitHubApiService = gitHubApiService,
            supabaseRepository = supabaseRepository,
            memoryDao = memoryDao,
            mediaService = mediaService,
            mediaProviders = { forVideo ->
                // Resolve generation-capable providers across ALL saved
                // providers, not just the active chat provider, in preference
                // order (active first). Video backends: Gemini (Veo direct),
                // fal.ai (Kling/Luma/MiniMax), Replicate (Veo/Kling/
                // Seedance) and OpenAI-style gateways (a Custom/Kimi provider
                // such as OmniRoute serves /videos/generations — Veo via
                // Google Flow, Runway, …) — the tool fails over in order.
                // Images: Gemini-preferred, media-only types excluded.
                val all = openBuddyRepository.getAllProviderConfigs()
                val active = openBuddyRepository.getActiveProvider()
                val ordered = buildList {
                    if (active != null) add(active)
                    addAll(all.filter { it.id != active?.id })
                }
                val filtered = if (forVideo) {
                    ordered.filter {
                        it.type == com.mahad.openbuddy.domain.model.ProviderType.GEMINI ||
                            it.type == com.mahad.openbuddy.domain.model.ProviderType.FAL ||
                            it.type == com.mahad.openbuddy.domain.model.ProviderType.REPLICATE ||
                            it.type == com.mahad.openbuddy.domain.model.ProviderType.CUSTOM_OPENAI ||
                            it.type == com.mahad.openbuddy.domain.model.ProviderType.KIMI
                    }
                } else {
                    ordered
                        .filter {
                            it.type != com.mahad.openbuddy.domain.model.ProviderType.CLAUDE &&
                                it.type != com.mahad.openbuddy.domain.model.ProviderType.FAL &&
                                it.type != com.mahad.openbuddy.domain.model.ProviderType.REPLICATE
                        }
                        .sortedByDescending { it.type == com.mahad.openbuddy.domain.model.ProviderType.GEMINI }
                }
                val pairs = mutableListOf<Pair<com.mahad.openbuddy.domain.model.AiProviderConfig, String>>()
                for (config in filtered) {
                    val key = secretStore.getApiKey(config.id)
                    if (!key.isNullOrBlank()) pairs += config to key
                }
                pairs
            }
        )
        val tools = mutableListOf<AgentTool>()
        tools.addAll(core.all())
        tools.addAll(DeviceTools(context).all())

        // MCP tools: one adapter per remote tool, namespaced as mcp__<server>__<tool>.
        val servers = try {
            mcpServerDao.getAllServersList()
        } catch (_: Exception) {
            emptyList<McpServerEntity>()
        }
        for (server in servers) {
            try {
                val remoteTools = mcpClient.listTools(server).getOrNull() ?: continue
                for (remote in remoteTools) {
                    tools.add(McpAgentTool(server, remote, mcpClient))
                }
            } catch (_: Exception) {
                // An unreachable MCP server must never break the agent run.
            }
        }
        return tools
    }

    private suspend fun buildSystemPrompt(userText: String, activeSkill: SkillEntity?, tools: List<AgentTool>): String {
        val base = appSettingDao.getSettingSync(OpenBuddyRepository.KEY_DEFAULT_SYSTEM_PROMPT)
            ?: OpenBuddyRepository.DEFAULT_SYSTEM_PROMPT

        // Relevant memories: keyword hits for this request + the most recent ones.
        val memories = LinkedHashMap<String, String>()
        val keywords = userText.split(Regex("\\s+")).filter { it.length > 3 }.take(4)
        for (keyword in keywords) {
            try {
                memoryDao.searchMemories(keyword, 5).forEach { memories[it.id] = it.fact }
            } catch (_: Exception) { /* memory is best-effort */ }
        }
        try {
            memoryDao.getRecentMemories(12).forEach { memories.putIfAbsent(it.id, it.fact) }
        } catch (_: Exception) { /* ignore */ }

        val catalog = tools.joinToString("\n") { tool -> "- ${tool.name}: ${tool.description}" }

        return buildString {
            append(base)
            append("\n\n")
            append(AGENT_PROTOCOL)
            append("\n\nAVAILABLE TOOLS:\n")
            append(catalog)
            if (memories.isNotEmpty()) {
                append("\n\nWHAT YOU REMEMBER ABOUT THE USER (use it when relevant):\n")
                memories.values.take(15).forEach { append("- ").append(it).append("\n") }
            }
            if (activeSkill != null) {
                append("\n\nACTIVE SKILL — ").append(activeSkill.name).append(":\n")
                append(activeSkill.instructions)
            }
        }
    }

    companion object {
        private const val MAX_STEPS = 10
        private const val MAX_TOOL_RESULT_CHARS = 8000

        private val AGENT_PROTOCOL = """
            |You are Open Buddy in AGENT MODE. You can take real actions on the user's phone and accounts using tools.
            |
            |HOW TO CALL A TOOL:
            |Reply with a fenced block exactly like this (one block per call, you may send several blocks in one reply):
            |```tool
            |{"name": "TOOL_NAME", "arguments": {"arg": "value"}}
            |```
            |After each call you will receive a message starting with "TOOL RESULT (name):" containing the outcome.
            |
            |RULES:
            |- Only call tools that are listed below. Never invent tool names or arguments.
            |- You CAN create media: when the user asks for an image or a video, call generate_image / generate_video. Never claim you have no image or video tool, and never send the user to external websites for generation.
            |- generate_video tries every saved video backend in order (Gemini Veo, the user's OmniRoute gateway — Veo via Google Flow, fal.ai, Replicate). If it fails, report the tool's actual error text; never invent excuses like "a technical update is in progress".
            |- When the task is done, reply with a normal final answer and NO tool blocks.
            |- Keep final answers concise and in the user's language style.
            |- Tools marked as approval-gated will be shown to the user first; if a result says the user rejected an action, do not retry it.
            |- WhatsApp messages are NEVER sent automatically — the whatsapp_prefill tool only opens WhatsApp with text ready; the user presses Send.
            |- If a tool reports a missing permission or missing setup (token, connection), tell the user exactly what to enable instead of retrying blindly.
        """.trimMargin()
    }
}

/** Adapts one remote MCP tool into the agent's tool registry. */
private class McpAgentTool(
    private val server: McpServerEntity,
    private val remote: com.mahad.openbuddy.data.mcp.McpRemoteTool,
    private val client: McpClient
) : AgentTool {

    override val name: String = "mcp__${sanitize(server.name)}__${sanitize(remote.name)}"

    override val description: String = buildString {
        append("[MCP server '${server.name}'] ")
        append(remote.description.ifBlank { remote.name })
        remote.inputSchema?.let { schema ->
            append(" Input schema: ")
            append(schema.toString().take(400))
        }
    }

    override suspend fun execute(args: JSONObject): ToolOutcome {
        val result = client.callTool(server, remote.name, args)
        return result.fold(
            onSuccess = { ToolOutcome(it) },
            onFailure = { ToolOutcome("MCP error: ${it.message}") }
        )
    }

    private fun sanitize(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "server" }
}
