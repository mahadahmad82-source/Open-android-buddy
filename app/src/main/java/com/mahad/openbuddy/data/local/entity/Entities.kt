package com.mahad.openbuddy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val providerId: String? = null,
    val modelName: String? = null
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val status: String = "COMPLETED",
    val errorMessage: String? = null
)

@Entity(tableName = "ai_providers")
data class AiProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val modelName: String,
    val baseUrl: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "code_drafts")
data class CodeDraftEntity(
    @PrimaryKey val id: String,
    val filename: String,
    val language: String,
    val code: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "query_history")
data class QueryHistoryEntity(
    @PrimaryKey val id: String,
    val query: String,
    val category: String,
    val timestamp: Long,
    val resultSummary: String,
    val executionTimeMs: Long = 0,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

@Entity(tableName = "voice_action_history")
data class VoiceActionHistoryEntity(
    @PrimaryKey val id: String,
    val rawCommand: String,
    val actionType: String,
    val target: String,
    val message: String?,
    val confidence: Float,
    val timestamp: Long,
    val status: String
)

// Phase 6: long-term agent memory — durable facts the assistant remembers
// about the user across conversations.
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val fact: String,
    val category: String = "general",
    val createdAt: Long = System.currentTimeMillis()
)

// Phase 4: user-defined skills — named instruction packs that steer the agent.
@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val createdAt: Long = System.currentTimeMillis()
)

// Phase 4: MCP (Model Context Protocol) servers the agent can call tools on.
@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val authToken: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

