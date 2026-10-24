package com.mahad.openbuddy.domain.model

enum class ProviderType(
    val displayName: String,
    val defaultModel: String,
    val defaultBaseUrl: String?,
    val recommendedModels: List<String> = emptyList()
) {
    AUTO_DETECT(
        displayName = "Auto-detect (any API key)",
        defaultModel = "",
        defaultBaseUrl = null,
        recommendedModels = emptyList()
    ),
    GEMINI(
        displayName = "Google Gemini",
        defaultModel = "gemini-2.5-flash",
        defaultBaseUrl = null,
        recommendedModels = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.5-flash-lite"
        )
    ),
    CLAUDE(
        displayName = "Anthropic Claude",
        defaultModel = "claude-sonnet-4-5",
        defaultBaseUrl = null,
        recommendedModels = listOf(
            "claude-sonnet-4-5",
            "claude-opus-4-1",
            "claude-haiku-4-5"
        )
    ),
    KIMI(
        displayName = "Moonshot / Kimi",
        defaultModel = "moonshot-v1-8k",
        defaultBaseUrl = "https://api.moonshot.cn/v1",
        recommendedModels = listOf(
            "moonshot-v1-8k",
            "moonshot-v1-32k",
            "moonshot-v1-128k"
        )
    ),
    CUSTOM_OPENAI(
        displayName = "Custom OpenAI-Compatible",
        defaultModel = "gpt-4o",
        defaultBaseUrl = "https://api.openai.com/v1",
        recommendedModels = listOf(
            "gpt-4o",
            "gpt-4o-mini",
            "o3-mini",
            "deepseek-chat",
            "deepseek-reasoner",
            "qwen-plus"
        )
    ),
    // Media-only providers: not chat APIs — they exist so the agent's
    // generate_video tool has several independent backends (failover when
    // one platform is down, out of quota, or rejects a prompt).
    FAL(
        displayName = "fal.ai — Video Generation (Kling, Luma, MiniMax, Veo)",
        defaultModel = "fal-ai/kling-video/v2.1/standard/text-to-video",
        defaultBaseUrl = "https://queue.fal.run",
        recommendedModels = listOf(
            "fal-ai/kling-video/v2.1/standard/text-to-video",
            "fal-ai/kling-video/v2.1/master/text-to-video",
            "fal-ai/luma-dream-machine",
            "fal-ai/minimax/video-01-live/text-to-video",
            "fal-ai/veo3/fast"
        )
    ),
    REPLICATE(
        displayName = "Replicate — Video Generation (Veo, Kling, Seedance)",
        defaultModel = "google/veo-3",
        defaultBaseUrl = "https://api.replicate.com/v1",
        recommendedModels = listOf(
            "google/veo-3",
            "kwaivgi/kling-v2.1",
            "minimax/video-01",
            "bytedance/seedance-1-lite"
        )
    ),
    POLLINATIONS(
        displayName = "Pollinations — Video Generation (Wan, Veo, Seedance, Kling)",
        defaultModel = "alibaba/wan-2.2-fast",
        defaultBaseUrl = "https://gen.pollinations.ai",
        recommendedModels = listOf(
            "alibaba/wan-2.2-fast",
            "bytedance/seedance-1-pro-fast",
            "alibaba/wan-2.7",
            "google/veo-3.1-fast",
            "kwaivgi/kling-v3.0-std",
            "bytedance/seedance-2.0-mini"
        )
    ),
    HF_SPACE(
        displayName = "Hugging Face Space — Video Generation (Wan 2.2, Free ZeroGPU)",
        defaultModel = "wan2.2-ti2v-5b",
        defaultBaseUrl = null,
        recommendedModels = listOf("wan2.2-ti2v-5b")
    ),
    COMFYUI(
        displayName = "ComfyUI Server — Video Generation (Wan 2.2, Free GPU)",
        defaultModel = "wan2.2-ti2v-5b",
        defaultBaseUrl = null,
        recommendedModels = listOf("wan2.2-ti2v-5b")
    );

    companion object {
        fun fromString(value: String): ProviderType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: GEMINI
        }
    }
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM;

    companion object {
        fun fromString(value: String): MessageRole {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: USER
        }
    }
}

enum class MessageStatus {
    SENT,
    STREAMING,
    COMPLETED,
    ERROR;

    companion object {
        fun fromString(value: String): MessageStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: COMPLETED
        }
    }
}

data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageRole = role,
    val codeSnippet: String? = null,
    val codeLanguage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    /** Optional media the user attached to this message (sent to vision-capable providers). */
    val attachmentPath: String? = null,
    val attachmentMime: String? = null
)

data class GitFile(
    val path: String,
    val isDirectory: Boolean = false,
    val size: String = "",
    val language: String? = null
)

data class GitCommit(
    val hash: String,
    val message: String,
    val author: String,
    val timestamp: String,
    val changedFilesCount: Int = 1
)

data class DatabaseColumnInfo(
    val name: String,
    val dataType: String,
    val isNullable: Boolean = true,
    val isPrimaryKey: Boolean = false,
    val defaultValue: String? = null,
    val foreignKeyTable: String? = null,
    val foreignKeyColumn: String? = null
)

data class DatabaseTableInfo(
    val tableName: String,
    val rowCount: Int = 0,
    val columns: List<DatabaseColumnInfo> = emptyList()
) {
    val columnNames: List<String> get() = columns.map { it.name }
}

data class QueryResult(
    val columns: List<String>,
    val rows: List<List<String>>,
    val rowCount: Int,
    val executionTimeMs: Long,
    val error: String? = null,
    val isReadOnly: Boolean = true,
    val executedSql: String = ""
)

data class QueryHistoryItem(
    val id: String,
    val query: String,
    val category: String,
    val timestamp: Long,
    val resultSummary: String,
    val executionTimeMs: Long = 0,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

enum class StatementSafetyType(val label: String, val isDestructive: Boolean, val isWrite: Boolean) {
    READ_ONLY("Read-Only", false, false),
    MUTATING_WRITE("Write / Mutating", false, true),
    DESTRUCTIVE("Destructive Action", true, true)
}

data class StatementClassification(
    val type: StatementSafetyType,
    val targetTable: String?,
    val hasMultipleStatements: Boolean,
    val statementCount: Int,
    val explanation: String
)

data class AiProviderConfig(
    val id: String,
    val name: String,
    val type: ProviderType,
    val modelName: String,
    val baseUrl: String? = null,
    val isDefault: Boolean = false,
    val hasApiKey: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val providerId: String? = null,
    val modelName: String? = null
)

data class Message(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val status: MessageStatus = MessageStatus.COMPLETED,
    val errorMessage: String? = null
)

data class CodeDraft(
    val id: String,
    val filename: String,
    val language: String,
    val code: String,
    val createdAt: Long = System.currentTimeMillis()
)

sealed class AiError(override val message: String, val isKeyRelated: Boolean = false) : Exception(message) {
    class InvalidKey(providerName: String) :
        AiError("Invalid API key or unauthorized for $providerName (401/403). Please verify your API key in Settings.", isKeyRelated = true)

    class RateLimited(providerName: String) :
        AiError("Rate limit exceeded for $providerName (429). Please wait a few moments or switch providers.", isKeyRelated = false)

    class NoInternet :
        AiError("No internet connection. Please check your network and try again.", isKeyRelated = false)

    class Timeout :
        AiError("Request timed out. The provider took too long to respond. Please try again.", isKeyRelated = false)

    class ServerError(code: Int, detail: String) :
        AiError("Server error ($code): $detail", isKeyRelated = false)

    class NetworkError(detail: String) :
        AiError("Network error: $detail", isKeyRelated = false)

    class Other(detail: String) :
        AiError(detail, isKeyRelated = false)
}
