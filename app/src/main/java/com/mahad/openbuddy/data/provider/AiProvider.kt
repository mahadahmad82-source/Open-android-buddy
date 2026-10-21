package com.mahad.openbuddy.data.provider

import com.mahad.openbuddy.domain.model.AiProviderConfig
import com.mahad.openbuddy.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Common abstraction for all AI chat model providers.
 * Returns a streaming Flow of text chunks.
 */
interface AiProvider {
    val config: AiProviderConfig

    fun streamChat(
        messages: List<ChatMessage>,
        systemPrompt: String? = null
    ): Flow<String>

    suspend fun testConnection(): Result<String>
}
