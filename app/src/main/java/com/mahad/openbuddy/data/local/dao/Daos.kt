package com.mahad.openbuddy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mahad.openbuddy.data.local.entity.AiProviderEntity
import com.mahad.openbuddy.data.local.entity.AppSettingEntity
import com.mahad.openbuddy.data.local.entity.CodeDraftEntity
import com.mahad.openbuddy.data.local.entity.ConversationEntity
import com.mahad.openbuddy.data.local.entity.McpServerEntity
import com.mahad.openbuddy.data.local.entity.MemoryEntity
import com.mahad.openbuddy.data.local.entity.MessageEntity
import com.mahad.openbuddy.data.local.entity.QueryHistoryEntity
import com.mahad.openbuddy.data.local.entity.SkillEntity
import com.mahad.openbuddy.data.local.entity.VoiceActionHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun getAllConversationsList(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :newTitle, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameConversation(id: String, newTitle: String, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessageListForConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesList(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET content = :content, status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateMessageContentAndStatus(id: String, content: String, status: String, errorMessage: String?)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}

@Dao
interface AiProviderDao {
    @Query("SELECT * FROM ai_providers ORDER BY createdAt ASC")
    fun getAllProviders(): Flow<List<AiProviderEntity>>

    @Query("SELECT * FROM ai_providers ORDER BY createdAt ASC")
    suspend fun getAllProvidersList(): List<AiProviderEntity>

    @Query("SELECT * FROM ai_providers WHERE id = :id LIMIT 1")
    suspend fun getProviderById(id: String): AiProviderEntity?

    @Query("SELECT * FROM ai_providers WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProvider(): AiProviderEntity?

    @Query("SELECT COUNT(*) FROM ai_providers")
    suspend fun getProviderCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvider(provider: AiProviderEntity)

    @Update
    suspend fun updateProvider(provider: AiProviderEntity)

    @Query("DELETE FROM ai_providers WHERE id = :id")
    suspend fun deleteProviderById(id: String)

    @Query("UPDATE ai_providers SET isDefault = 0")
    suspend fun clearDefaultProvider()

    @Query("UPDATE ai_providers SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultProvider(id: String)

    @Transaction
    suspend fun setAsSingleDefault(id: String) {
        clearDefaultProvider()
        setDefaultProvider(id)
    }
}

@Dao
interface AppSettingDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    fun getSetting(key: String): Flow<String?>

    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSettingSync(key: String): String?

    @Query("SELECT * FROM app_settings")
    suspend fun getAllSettings(): List<AppSettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(entity: AppSettingEntity)
}

@Dao
interface CodeDraftDao {
    @Query("SELECT * FROM code_drafts ORDER BY createdAt DESC")
    fun getAllDrafts(): Flow<List<CodeDraftEntity>>

    @Query("SELECT * FROM code_drafts ORDER BY createdAt DESC")
    suspend fun getAllDraftsList(): List<CodeDraftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: CodeDraftEntity)

    @Query("DELETE FROM code_drafts WHERE id = :id")
    suspend fun deleteDraftById(id: String)

    @Query("DELETE FROM code_drafts")
    suspend fun deleteAllDrafts()
}

@Dao
interface QueryHistoryDao {
    @Query("SELECT * FROM query_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<QueryHistoryEntity>>

    @Query("SELECT * FROM query_history ORDER BY timestamp DESC")
    suspend fun getAllHistoryList(): List<QueryHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(item: QueryHistoryEntity)

    @Query("DELETE FROM query_history WHERE id = :id")
    suspend fun deleteHistoryById(id: String)

    @Query("DELETE FROM query_history")
    suspend fun clearHistory()
}

@Dao
interface VoiceActionHistoryDao {
    @Query("SELECT * FROM voice_action_history ORDER BY timestamp DESC")
    fun getAllVoiceHistory(): Flow<List<VoiceActionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceAction(action: VoiceActionHistoryEntity)

    @Query("DELETE FROM voice_action_history WHERE id = :id")
    suspend fun deleteVoiceActionById(id: String)

    @Query("DELETE FROM voice_action_history")
    suspend fun clearVoiceHistory()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    suspend fun getAllMemoriesList(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE fact LIKE '%' || :keyword || '%' OR category LIKE '%' || :keyword || '%' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun searchMemories(keyword: String, limit: Int = 10): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int = 20): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: String)

    @Query("DELETE FROM memories")
    suspend fun deleteAllMemories()
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY createdAt ASC")
    fun getAllSkills(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills ORDER BY createdAt ASC")
    suspend fun getAllSkillsList(): List<SkillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkill(skill: SkillEntity)

    @Query("DELETE FROM skills WHERE id = :id")
    suspend fun deleteSkillById(id: String)
}

@Dao
interface McpServerDao {
    @Query("SELECT * FROM mcp_servers ORDER BY createdAt ASC")
    fun getAllServers(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers ORDER BY createdAt ASC")
    suspend fun getAllServersList(): List<McpServerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: McpServerEntity)

    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun deleteServerById(id: String)
}

