package com.example.data.repository

import android.content.Context
import com.example.data.local.CodePilotDatabase
import com.example.data.local.entity.AiProviderEntity
import com.example.data.local.entity.AppSettingEntity
import com.example.data.local.entity.CodeDraftEntity
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.VoiceActionHistoryEntity
import com.example.data.provider.AiProviderFactory
import com.example.data.security.SecretStore
import com.example.data.security.SensitiveDataRedactor
import com.example.data.voice.VoiceIntentParser
import com.example.data.backup.LocalBackupManager
import com.example.domain.model.LastBackupInfo
import com.example.domain.model.LocalBackupData
import com.example.domain.model.RestoreMode
import com.example.domain.model.RestorePreviewSummary
import com.example.domain.model.BackupRestoreException
import com.example.domain.model.AiError
import com.example.domain.model.AiProviderConfig
import com.example.domain.model.ChatMessage
import com.example.domain.model.CodeDraft
import com.example.domain.model.Conversation
import com.example.domain.model.Message
import com.example.domain.model.MessageRole
import com.example.domain.model.MessageStatus
import com.example.domain.model.ParsedVoiceCommand
import com.example.domain.model.ProviderType
import com.example.domain.model.VoiceActionHistoryItem
import com.example.domain.model.VoiceActionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class CodePilotRepository(
    private val context: Context,
    private var database: CodePilotDatabase,
    private val secretStore: SecretStore,
    private val providerFactory: AiProviderFactory
) {
    companion object {
        const val KEY_DEFAULT_SYSTEM_PROMPT = "default_system_prompt"
        const val KEY_ACTIVE_CONVERSATION_ID = "active_conversation_id"
        const val KEY_ACTIVE_PROVIDER_ID = "active_provider_id"

        const val DEFAULT_SYSTEM_PROMPT =
            "You are Open Buddy, an expert software engineer and programming assistant. Provide clean, well-structured, production-ready code with concise explanations. Format code in markdown with explicit language tags."
    }

    private val conversationDao get() = database.conversationDao()
    private val messageDao get() = database.messageDao()
    private val providerDao get() = database.aiProviderDao()
    private val appSettingDao get() = database.appSettingDao()
    private val draftDao get() = database.codeDraftDao()
    private val voiceActionDao get() = database.voiceActionHistoryDao()

    private var activeStreamJob: Job? = null
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // Security & Privacy preferences state flows
    private val _biometricLockEnabled = MutableStateFlow(secretStore.isBiometricLockEnabled())
    val biometricLockEnabled: StateFlow<Boolean> = _biometricLockEnabled.asStateFlow()

    private val _autoLockTimeoutMinutes = MutableStateFlow(secretStore.getAutoLockTimeoutMinutes())
    val autoLockTimeoutMinutes: StateFlow<Int> = _autoLockTimeoutMinutes.asStateFlow()

    private val _redactionEnabled = MutableStateFlow(secretStore.isRedactionEnabled())
    val redactionEnabled: StateFlow<Boolean> = _redactionEnabled.asStateFlow()

    // Developer tokens state flows
    private val _hasGitHubToken = MutableStateFlow(secretStore.hasGitHubToken())
    val hasGitHubToken: StateFlow<Boolean> = _hasGitHubToken.asStateFlow()

    private val _hasSupabaseToken = MutableStateFlow(secretStore.hasSupabaseToken())
    val hasSupabaseToken: StateFlow<Boolean> = _hasSupabaseToken.asStateFlow()

    private val _supabaseUrl = MutableStateFlow(secretStore.getSupabaseUrl() ?: "")
    val supabaseUrl: StateFlow<String> = _supabaseUrl.asStateFlow()

    // Local Backup & Restore state flow
    private val _lastBackupInfo = MutableStateFlow(secretStore.getLastBackupInfo())
    val lastBackupInfo: StateFlow<LastBackupInfo?> = _lastBackupInfo.asStateFlow()

    val conversations: Flow<List<Conversation>> = conversationDao.getAllConversations().map { list ->
        list.map { entity ->
            Conversation(
                id = entity.id,
                title = entity.title,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                providerId = entity.providerId,
                modelName = entity.modelName
            )
        }
    }.flowOn(Dispatchers.IO)

    val providers: Flow<List<AiProviderConfig>> = providerDao.getAllProviders().map { list ->
        list.map { entity ->
            AiProviderConfig(
                id = entity.id,
                name = entity.name,
                type = ProviderType.fromString(entity.type),
                modelName = entity.modelName,
                baseUrl = entity.baseUrl,
                isDefault = entity.isDefault,
                hasApiKey = secretStore.hasApiKey(entity.id),
                createdAt = entity.createdAt
            )
        }
    }.flowOn(Dispatchers.IO)

    val systemPrompt: Flow<String> = appSettingDao.getSetting(KEY_DEFAULT_SYSTEM_PROMPT).map { saved ->
        saved ?: DEFAULT_SYSTEM_PROMPT
    }.flowOn(Dispatchers.IO)

    val activeConversationId: Flow<String?> = appSettingDao.getSetting(KEY_ACTIVE_CONVERSATION_ID).flowOn(Dispatchers.IO)

    val activeProviderId: Flow<String?> = appSettingDao.getSetting(KEY_ACTIVE_PROVIDER_ID).flowOn(Dispatchers.IO)

    val codeDrafts: Flow<List<CodeDraft>> = draftDao.getAllDrafts().map { list ->
        list.map { entity ->
            CodeDraft(
                id = entity.id,
                filename = entity.filename,
                language = entity.language,
                code = entity.code,
                createdAt = entity.createdAt
            )
        }
    }.flowOn(Dispatchers.IO)

    fun getMessagesForConversation(conversationId: String): Flow<List<Message>> {
        return messageDao.getMessagesForConversation(conversationId).map { list ->
            list.map { entity ->
                Message(
                    id = entity.id,
                    conversationId = entity.conversationId,
                    role = MessageRole.fromString(entity.role),
                    content = entity.content,
                    timestamp = entity.timestamp,
                    status = MessageStatus.fromString(entity.status),
                    errorMessage = entity.errorMessage
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun initializeDefaultDataIfNeeded() = withContext(Dispatchers.IO) {
        val count = providerDao.getProviderCount()
        if (count == 0) {
            val geminiId = UUID.randomUUID().toString()
            val gemini = AiProviderEntity(
                id = geminiId,
                name = "Google Gemini Flash",
                type = ProviderType.GEMINI.name,
                modelName = "gemini-2.5-flash",
                baseUrl = null,
                isDefault = true
            )
            val claude = AiProviderEntity(
                id = UUID.randomUUID().toString(),
                name = "Anthropic Claude 3.7 Sonnet",
                type = ProviderType.CLAUDE.name,
                modelName = "claude-3-7-sonnet-20250219",
                baseUrl = null,
                isDefault = false
            )
            val kimi = AiProviderEntity(
                id = UUID.randomUUID().toString(),
                name = "Moonshot Kimi",
                type = ProviderType.KIMI.name,
                modelName = "moonshot-v1-8k",
                baseUrl = "https://api.moonshot.cn/v1",
                isDefault = false
            )
            val custom = AiProviderEntity(
                id = UUID.randomUUID().toString(),
                name = "Custom OpenAI",
                type = ProviderType.CUSTOM_OPENAI.name,
                modelName = "gpt-4o",
                baseUrl = "https://api.openai.com/v1",
                isDefault = false
            )
            providerDao.insertProvider(gemini)
            providerDao.insertProvider(claude)
            providerDao.insertProvider(kimi)
            providerDao.insertProvider(custom)
            appSettingDao.setSetting(AppSettingEntity(KEY_ACTIVE_PROVIDER_ID, geminiId))
        } else {
            // Auto-migrate any deprecated model names stored in previous installations
            try {
                val existing = providerDao.getAllProvidersList()
                for (p in existing) {
                    if (p.type == ProviderType.GEMINI.name && (p.modelName.equals("gemini-pro", ignoreCase = true) || p.modelName.equals("gemini-1.0-pro", ignoreCase = true))) {
                        providerDao.updateProvider(p.copy(modelName = "gemini-2.5-flash"))
                    }
                }
            } catch (_: Exception) {}
        }

        val activeConv = appSettingDao.getSettingSync(KEY_ACTIVE_CONVERSATION_ID)
        if (activeConv == null) {
            val newId = createNewConversation("Welcome to Open Buddy")
            appSettingDao.setSetting(AppSettingEntity(KEY_ACTIVE_CONVERSATION_ID, newId))
            messageDao.insertMessage(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = newId,
                    role = MessageRole.ASSISTANT.name,
                    content = """
# Welcome to Open Buddy!

I am ready to assist you with Kotlin, Jetpack Compose, Python, algorithms, architecture, and code debugging.

### Security Highlights
- **Hardware-Backed Key Vault**: All API keys, GitHub tokens, and Supabase tokens are encrypted with AES-256-GCM via Android KeyStore.
- **SQLCipher Encrypted Database**: Local database encrypted at rest with a 256-bit KeyStore-protected passphrase.
- **Biometric App Lock**: Protect the app with biometric or device credentials and configurable auto-lock.
- **Data Redaction**: Sensitive personal data and tokens are automatically masked before leaving the device.

```kotlin
fun main() {
    println("Ready to code securely with Open Buddy!")
}
```
                    """.trimIndent(),
                    timestamp = System.currentTimeMillis(),
                    status = MessageStatus.COMPLETED.name,
                    errorMessage = null
                )
            )
        }
    }

    suspend fun createNewConversation(title: String = "New Chat"): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val activeProvider = getActiveProvider()
        val entity = ConversationEntity(
            id = id,
            title = title,
            createdAt = now,
            updatedAt = now,
            providerId = activeProvider?.id,
            modelName = activeProvider?.modelName
        )
        conversationDao.insertConversation(entity)
        appSettingDao.setSetting(AppSettingEntity(KEY_ACTIVE_CONVERSATION_ID, id))
        id
    }

    suspend fun renameConversation(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        conversationDao.renameConversation(id, newTitle.trim(), System.currentTimeMillis())
    }

    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        messageDao.deleteMessagesForConversation(id)
        conversationDao.deleteConversationById(id)
        val active = appSettingDao.getSettingSync(KEY_ACTIVE_CONVERSATION_ID)
        if (active == id) {
            val remaining = conversationDao.getAllConversations().firstOrNull()?.firstOrNull()
            if (remaining != null) {
                appSettingDao.setSetting(AppSettingEntity(KEY_ACTIVE_CONVERSATION_ID, remaining.id))
            } else {
                val newId = createNewConversation("New Chat")
                appSettingDao.setSetting(AppSettingEntity(KEY_ACTIVE_CONVERSATION_ID, newId))
            }
        }
    }

    suspend fun setActiveConversation(id: String) = withContext(Dispatchers.IO) {
        appSettingDao.setSetting(AppSettingEntity(KEY_ACTIVE_CONVERSATION_ID, id))
    }

    suspend fun setActiveProvider(id: String) = withContext(Dispatchers.IO) {
        appSettingDao.setSetting(AppSettingEntity(KEY_ACTIVE_PROVIDER_ID, id))
    }

    suspend fun getActiveProvider(): AiProviderConfig? = withContext(Dispatchers.IO) {
        val activeId = appSettingDao.getSettingSync(KEY_ACTIVE_PROVIDER_ID)
        val entity = if (activeId != null) {
            providerDao.getProviderById(activeId) ?: providerDao.getDefaultProvider()
        } else {
            providerDao.getDefaultProvider()
        }
        entity?.let {
            AiProviderConfig(
                id = it.id,
                name = it.name,
                type = ProviderType.fromString(it.type),
                modelName = it.modelName,
                baseUrl = it.baseUrl,
                isDefault = it.isDefault,
                hasApiKey = secretStore.hasApiKey(it.id),
                createdAt = it.createdAt
            )
        }
    }

    suspend fun saveProvider(
        id: String?,
        name: String,
        type: ProviderType,
        modelName: String,
        baseUrl: String?,
        apiKey: String?,
        setAsDefault: Boolean
    ): String = withContext(Dispatchers.IO) {
        val providerId = id ?: UUID.randomUUID().toString()
        val isFirst = providerDao.getProviderCount() == 0
        val markDefault = setAsDefault || isFirst

        if (markDefault) {
            providerDao.clearDefaultProvider()
        }

        val entity = AiProviderEntity(
            id = providerId,
            name = name.trim(),
            type = type.name,
            modelName = modelName.trim(),
            baseUrl = baseUrl?.trim()?.ifBlank { null },
            isDefault = markDefault,
            createdAt = System.currentTimeMillis()
        )
        providerDao.insertProvider(entity)

        if (!apiKey.isNullOrBlank()) {
            secretStore.storeApiKey(providerId, apiKey.trim())
        }

        if (markDefault) {
            appSettingDao.setSetting(AppSettingEntity(KEY_ACTIVE_PROVIDER_ID, providerId))
        }

        providerId
    }

    suspend fun deleteProvider(id: String) = withContext(Dispatchers.IO) {
        secretStore.deleteApiKey(id)
        providerDao.deleteProviderById(id)
        val active = appSettingDao.getSettingSync(KEY_ACTIVE_PROVIDER_ID)
        if (active == id) {
            val fallback = providerDao.getDefaultProvider() ?: providerDao.getAllProviders().firstOrNull()?.firstOrNull()
            if (fallback != null) {
                appSettingDao.setSetting(AppSettingEntity(KEY_ACTIVE_PROVIDER_ID, fallback.id))
            }
        }
    }

    suspend fun getProviderApiKey(providerId: String): String? = withContext(Dispatchers.IO) {
        secretStore.getApiKey(providerId)
    }

    suspend fun testProviderConnection(
        type: ProviderType,
        modelName: String,
        baseUrl: String?,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val tempConfig = AiProviderConfig(
            id = "test_temp_id",
            name = type.displayName,
            type = type,
            modelName = modelName,
            baseUrl = baseUrl,
            isDefault = false
        )
        val provider = providerFactory.createProviderWithCustomKey(tempConfig, apiKey)
        provider.testConnection()
    }

    suspend fun updateSystemPrompt(prompt: String) = withContext(Dispatchers.IO) {
        appSettingDao.setSetting(AppSettingEntity(KEY_DEFAULT_SYSTEM_PROMPT, prompt.trim()))
    }

    suspend fun resetSystemPrompt() = withContext(Dispatchers.IO) {
        appSettingDao.setSetting(AppSettingEntity(KEY_DEFAULT_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT))
    }

    fun stopGeneration() {
        activeStreamJob?.cancel()
        activeStreamJob = null
        _isGenerating.value = false
    }

    // ==========================================
    // Messaging with Redaction Support
    // ==========================================

    suspend fun sendMessage(
        conversationId: String,
        userText: String,
        scope: CoroutineScope
    ) = withContext(Dispatchers.IO) {
        if (userText.isBlank()) return@withContext

        // Auto-title conversation on first user query
        val existingMessages = messageDao.getMessageListForConversation(conversationId)
        val userMessagesCount = existingMessages.count { it.role == MessageRole.USER.name }
        if (userMessagesCount == 0) {
            val titleCandidate = userText.lines().firstOrNull()?.take(36) ?: "Chat"
            conversationDao.renameConversation(conversationId, titleCandidate, System.currentTimeMillis())
        }

        // Apply redaction if enabled before sending to AI provider
        val isRedacting = secretStore.isRedactionEnabled()
        val textForAi = if (isRedacting) {
            SensitiveDataRedactor.redact(userText).text
        } else {
            userText
        }

        // Insert user message in database
        val userMessageId = UUID.randomUUID().toString()
        val userMsgEntity = MessageEntity(
            id = userMessageId,
            conversationId = conversationId,
            role = MessageRole.USER.name,
            content = userText.trim(),
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.COMPLETED.name,
            errorMessage = null
        )
        messageDao.insertMessage(userMsgEntity)
        conversationDao.renameConversation(conversationId, userText.lines().firstOrNull()?.take(36) ?: "Chat", System.currentTimeMillis())

        // Prepare assistant message
        val assistantMessageId = UUID.randomUUID().toString()
        val assistantMsgEntity = MessageEntity(
            id = assistantMessageId,
            conversationId = conversationId,
            role = MessageRole.ASSISTANT.name,
            content = "",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.STREAMING.name,
            errorMessage = null
        )
        messageDao.insertMessage(assistantMsgEntity)

        executeStreamingResponse(
            conversationId = conversationId,
            assistantMessageId = assistantMessageId,
            lastUserTextForAi = textForAi,
            scope = scope
        )
    }

    suspend fun retryLastAssistantMessage(
        conversationId: String,
        assistantMessageId: String,
        scope: CoroutineScope
    ) = withContext(Dispatchers.IO) {
        messageDao.updateMessageContentAndStatus(
            id = assistantMessageId,
            content = "",
            status = MessageStatus.STREAMING.name,
            errorMessage = null
        )
        executeStreamingResponse(
            conversationId = conversationId,
            assistantMessageId = assistantMessageId,
            lastUserTextForAi = null,
            scope = scope
        )
    }

    private fun executeStreamingResponse(
        conversationId: String,
        assistantMessageId: String,
        lastUserTextForAi: String?,
        scope: CoroutineScope
    ) {
        stopGeneration()
        _isGenerating.value = true

        activeStreamJob = scope.launch(Dispatchers.IO) {
            val fullContent = StringBuilder()
            try {
                val providerConfig = getActiveProvider()
                    ?: throw AiError.Other("No AI Provider selected. Please configure a provider in Settings.")

                val apiKey = secretStore.getApiKey(providerConfig.id)
                if (apiKey.isNullOrBlank()) {
                    throw AiError.InvalidKey(providerConfig.name)
                }

                val aiProvider = providerFactory.createProvider(providerConfig)
                val sysPrompt = appSettingDao.getSettingSync(KEY_DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT

                val historyEntities = messageDao.getMessageListForConversation(conversationId)
                val isRedacting = secretStore.isRedactionEnabled()

                val chatMessages = historyEntities
                    .filter { it.id != assistantMessageId }
                    .map { entity ->
                        val content = if (entity.role == MessageRole.USER.name && isRedacting) {
                            SensitiveDataRedactor.redact(entity.content).text
                        } else {
                            entity.content
                        }
                        ChatMessage(
                            role = MessageRole.fromString(entity.role),
                            content = content
                        )
                    }

                var lastSaveTime = System.currentTimeMillis()
                aiProvider.streamChat(chatMessages, sysPrompt).collect { chunk ->
                    fullContent.append(chunk)
                    val now = System.currentTimeMillis()
                    if (now - lastSaveTime > 120) {
                        messageDao.updateMessageContentAndStatus(
                            id = assistantMessageId,
                            content = fullContent.toString(),
                            status = MessageStatus.STREAMING.name,
                            errorMessage = null
                        )
                        lastSaveTime = now
                    }
                }

                // Final success update
                messageDao.updateMessageContentAndStatus(
                    id = assistantMessageId,
                    content = fullContent.toString(),
                    status = MessageStatus.COMPLETED.name,
                    errorMessage = null
                )
            } catch (e: Exception) {
                if (e is CancellationException) {
                    messageDao.updateMessageContentAndStatus(
                        id = assistantMessageId,
                        content = fullContent.toString().ifBlank { "[Generation stopped]" },
                        status = MessageStatus.COMPLETED.name,
                        errorMessage = null
                    )
                } else {
                    val safeError = when (e) {
                        is AiError -> e.message
                        else -> e.message ?: "An unexpected error occurred while contacting the AI provider."
                    }
                    messageDao.updateMessageContentAndStatus(
                        id = assistantMessageId,
                        content = fullContent.toString(),
                        status = MessageStatus.ERROR.name,
                        errorMessage = safeError
                    )
                }
            } finally {
                _isGenerating.value = false
                activeStreamJob = null
            }
        }
    }

    suspend fun saveCodeDraft(filename: String, language: String, code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val id = UUID.randomUUID().toString()
            val entity = CodeDraftEntity(
                id = id,
                filename = filename.trim(),
                language = language.trim(),
                code = code,
                createdAt = System.currentTimeMillis()
            )
            draftDao.insertDraft(entity)

            val draftsDir = File(context.filesDir, "code_drafts").apply { mkdirs() }
            val draftFile = File(draftsDir, "${System.currentTimeMillis()}_${filename.trim()}")
            draftFile.writeText(code)

            Result.success("Saved code draft '${entity.filename}' to local drafts")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // GitHub and Supabase Token Management
    // ==========================================

    fun saveGitHubToken(token: String) {
        secretStore.storeGitHubToken(token.trim())
        _hasGitHubToken.value = secretStore.hasGitHubToken()
    }

    fun getGitHubToken(): String? {
        return secretStore.getGitHubToken()
    }

    fun deleteGitHubToken() {
        secretStore.deleteGitHubToken()
        _hasGitHubToken.value = false
    }

    fun saveSupabaseCredentials(url: String, token: String) {
        secretStore.storeSupabaseUrl(url.trim())
        secretStore.storeSupabaseToken(token.trim())
        _hasSupabaseToken.value = secretStore.hasSupabaseToken()
        _supabaseUrl.value = url.trim()
    }

    fun getSupabaseToken(): String? {
        return secretStore.getSupabaseToken()
    }

    fun getSupabaseUrl(): String? {
        return secretStore.getSupabaseUrl()
    }

    fun deleteSupabaseCredentials() {
        secretStore.deleteSupabaseUrl()
        secretStore.deleteSupabaseToken()
        _hasSupabaseToken.value = false
        _supabaseUrl.value = ""
    }

    // ==========================================
    // Security & Privacy Settings
    // ==========================================

    fun setBiometricLockEnabled(enabled: Boolean) {
        secretStore.setBiometricLockEnabled(enabled)
        _biometricLockEnabled.value = enabled
    }

    fun setAutoLockTimeoutMinutes(minutes: Int) {
        secretStore.setAutoLockTimeoutMinutes(minutes)
        _autoLockTimeoutMinutes.value = minutes
    }

    fun setRedactionEnabled(enabled: Boolean) {
        secretStore.setRedactionEnabled(enabled)
        _redactionEnabled.value = enabled
    }

    // ==========================================
    // Data Export (JSON without secrets)
    // ==========================================

    suspend fun exportDataJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        root.put("appName", "Open Buddy")
        root.put("version", "1.0")
        root.put("exportedAt", isoFormat.format(Date()))
        root.put("securityNotice", "Secrets, API keys, GitHub tokens, and Supabase tokens are excluded from this export.")

        // Settings (non-secret only)
        val settingsObj = JSONObject()
        settingsObj.put("systemPrompt", appSettingDao.getSettingSync(KEY_DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT)
        settingsObj.put("biometricLockEnabled", secretStore.isBiometricLockEnabled())
        settingsObj.put("autoLockTimeoutMinutes", secretStore.getAutoLockTimeoutMinutes())
        settingsObj.put("redactionEnabled", secretStore.isRedactionEnabled())
        root.put("settings", settingsObj)

        // Configured AI Providers (Excluding keys)
        val providersArray = JSONArray()
        val providerEntities = providerDao.getAllProviders().firstOrNull() ?: emptyList()
        for (p in providerEntities) {
            val pObj = JSONObject()
            pObj.put("id", p.id)
            pObj.put("name", p.name)
            pObj.put("type", p.type)
            pObj.put("modelName", p.modelName)
            pObj.put("baseUrl", p.baseUrl ?: JSONObject.NULL)
            pObj.put("isDefault", p.isDefault)
            pObj.put("hasEncryptedApiKey", secretStore.hasApiKey(p.id))
            providersArray.put(pObj)
        }
        root.put("providers", providersArray)

        // Integrations status (excluding tokens)
        val integrationsObj = JSONObject()
        integrationsObj.put("hasGitHubToken", secretStore.hasGitHubToken())
        integrationsObj.put("hasSupabaseToken", secretStore.hasSupabaseToken())
        integrationsObj.put("supabaseUrl", secretStore.getSupabaseUrl() ?: JSONObject.NULL)
        root.put("integrations", integrationsObj)

        // Conversations and Messages
        val convsArray = JSONArray()
        val convEntities = conversationDao.getAllConversations().firstOrNull() ?: emptyList()
        for (c in convEntities) {
            val cObj = JSONObject()
            cObj.put("id", c.id)
            cObj.put("title", c.title)
            cObj.put("createdAt", c.createdAt)
            cObj.put("updatedAt", c.updatedAt)
            cObj.put("modelName", c.modelName ?: JSONObject.NULL)

            val messagesArray = JSONArray()
            val messageEntities = messageDao.getMessageListForConversation(c.id)
            for (m in messageEntities) {
                val mObj = JSONObject()
                mObj.put("id", m.id)
                mObj.put("role", m.role)
                mObj.put("content", m.content)
                mObj.put("timestamp", m.timestamp)
                mObj.put("status", m.status)
                messagesArray.put(mObj)
            }
            cObj.put("messages", messagesArray)
            convsArray.put(cObj)
        }
        root.put("conversations", convsArray)

        // Code Drafts
        val draftsArray = JSONArray()
        val draftEntities = draftDao.getAllDrafts().firstOrNull() ?: emptyList()
        for (d in draftEntities) {
            val dObj = JSONObject()
            dObj.put("id", d.id)
            dObj.put("filename", d.filename)
            dObj.put("language", d.language)
            dObj.put("code", d.code)
            dObj.put("createdAt", d.createdAt)
            draftsArray.put(dObj)
        }
        root.put("codeDrafts", draftsArray)

        root.toString(2)
    }

    // ==========================================
    // Local Backup & Restore Operations
    // ==========================================

    suspend fun createEncryptedBackupPayload(passphrase: String): String = withContext(Dispatchers.IO) {
        val conversationsList = conversationDao.getAllConversationsList()
        val messagesList = messageDao.getAllMessagesList()
        val providersList = providerDao.getAllProvidersList()
        val queryHistoryList = database.queryHistoryDao().getAllHistoryList()
        val draftsList = draftDao.getAllDraftsList()
        val allSettings = appSettingDao.getAllSettings().associate { it.key to it.value }

        // Metadata only, NO secrets/keys/tokens!
        val integrationsMetadata = mapOf(
            "hasGitHubToken" to secretStore.hasGitHubToken().toString(),
            "hasSupabaseToken" to secretStore.hasSupabaseToken().toString(),
            "supabaseUrl" to (secretStore.getSupabaseUrl() ?: "")
        )

        val backupData = LocalBackupData(
            version = 1,
            appName = "Open Buddy",
            exportedAt = System.currentTimeMillis(),
            conversations = conversationsList,
            messages = messagesList,
            aiProviders = providersList,
            queryHistory = queryHistoryList,
            codeDrafts = draftsList,
            settings = allSettings,
            integrationsMetadata = integrationsMetadata
        )

        val plainJson = LocalBackupManager.serializeBackupData(backupData)
        LocalBackupManager.encryptBackup(plainJson, passphrase.toCharArray())
    }

    fun recordCompletedBackup(fileName: String, sizeBytes: Long) {
        val now = System.currentTimeMillis()
        secretStore.setLastBackupInfo(now, sizeBytes, fileName)
        _lastBackupInfo.value = LastBackupInfo(now, sizeBytes, fileName)
    }

    fun decryptAndInspectBackup(envelopeJson: String, passphrase: String): Result<RestorePreviewSummary> {
        return try {
            val summary = LocalBackupManager.decryptAndValidateBackup(envelopeJson, passphrase.toCharArray())
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(summary: RestorePreviewSummary, mode: RestoreMode): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            stopGeneration()
            val backup = summary.rawBackupData

            if (mode == RestoreMode.REPLACE) {
                // Delete existing data from Room
                messageDao.deleteAllMessages()
                conversationDao.deleteAllConversations()
                draftDao.deleteAllDrafts()
                database.queryHistoryDao().clearHistory()
            }

            // Restore/Merge Conversations
            for (conv in backup.conversations) {
                conversationDao.insertConversation(conv)
            }

            // Restore/Merge Messages
            for (msg in backup.messages) {
                messageDao.insertMessage(msg)
            }

            // Restore/Merge Query History
            for (q in backup.queryHistory) {
                database.queryHistoryDao().insertHistoryItem(q)
            }

            // Restore/Merge Code Drafts
            for (d in backup.codeDrafts) {
                draftDao.insertDraft(d)
                try {
                    val draftsDir = File(context.filesDir, "code_drafts").apply { mkdirs() }
                    val draftFile = File(draftsDir, "${d.createdAt}_${d.filename.trim()}")
                    if (!draftFile.exists()) {
                        draftFile.writeText(d.code)
                    }
                } catch (_: Exception) {}
            }

            // Restore/Merge AI Providers (Excluding keys)
            for (p in backup.aiProviders) {
                val existing = providerDao.getProviderById(p.id)
                if (existing == null) {
                    providerDao.insertProvider(p)
                } else if (mode == RestoreMode.REPLACE) {
                    providerDao.updateProvider(p)
                }
            }

            // Restore Settings
            for ((key, value) in backup.settings) {
                appSettingDao.setSetting(AppSettingEntity(key, value))
            }

            // Update active conversation pointer
            val activeConvId = appSettingDao.getSettingSync(KEY_ACTIVE_CONVERSATION_ID)
            if (activeConvId == null || conversationDao.getConversationById(activeConvId) == null) {
                val firstConv = backup.conversations.firstOrNull() ?: conversationDao.getAllConversationsList().firstOrNull()
                if (firstConv != null) {
                    appSettingDao.setSetting(AppSettingEntity(KEY_ACTIVE_CONVERSATION_ID, firstConv.id))
                } else {
                    createNewConversation("New Chat")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // Delete Everything (Wipes Database, Secrets, Settings)
    // ==========================================

    suspend fun deleteEverything(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            stopGeneration()

            // 1. Close and delete Room database file
            CodePilotDatabase.closeAndReset()
            context.deleteDatabase(CodePilotDatabase.DATABASE_NAME)

            // 2. Wipe all KeyStore encrypted secrets and preferences
            secretStore.wipeAllSecrets()

            // 3. Delete any local code draft files
            val draftsDir = File(context.filesDir, "code_drafts")
            if (draftsDir.exists()) {
                draftsDir.deleteRecursively()
            }

            // 4. Re-instantiate a clean encrypted database
            database = CodePilotDatabase.getInstance(context)

            // 5. Reinitialize fresh default provider setup and welcome chat
            initializeDefaultDataIfNeeded()

            // 6. Update local state flows
            _biometricLockEnabled.value = false
            _autoLockTimeoutMinutes.value = 5
            _redactionEnabled.value = true
            _hasGitHubToken.value = false
            _hasSupabaseToken.value = false
            _supabaseUrl.value = ""

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // Voice Actions & Intent History
    // ==========================================

    val voiceActionHistory: Flow<List<VoiceActionHistoryItem>> = voiceActionDao.getAllVoiceHistory().map { list ->
        list.map { entity ->
            VoiceActionHistoryItem(
                id = entity.id,
                rawCommand = entity.rawCommand,
                actionType = entity.actionType,
                target = entity.target,
                message = entity.message,
                confidence = entity.confidence,
                timestamp = entity.timestamp,
                status = entity.status
            )
        }
    }.flowOn(Dispatchers.IO)

    suspend fun recordVoiceActionHistory(
        rawCommand: String,
        actionType: String,
        target: String,
        message: String?,
        confidence: Float,
        status: String
    ) = withContext(Dispatchers.IO) {
        val entity = VoiceActionHistoryEntity(
            id = UUID.randomUUID().toString(),
            rawCommand = rawCommand,
            actionType = actionType,
            target = target,
            message = message,
            confidence = confidence,
            timestamp = System.currentTimeMillis(),
            status = status
        )
        voiceActionDao.insertVoiceAction(entity)
    }

    suspend fun clearVoiceActionHistory() = withContext(Dispatchers.IO) {
        voiceActionDao.clearVoiceHistory()
    }

    suspend fun deleteVoiceActionHistoryItem(id: String) = withContext(Dispatchers.IO) {
        voiceActionDao.deleteVoiceActionById(id)
    }

    suspend fun isVoiceActionEnabled(action: VoiceActionType): Boolean = withContext(Dispatchers.IO) {
        val key = "voice_action_enabled_${action.actionKey}"
        val value = appSettingDao.getSettingSync(key)
        value?.toBooleanStrictOrNull() ?: true // enabled by default
    }

    suspend fun setVoiceActionEnabled(action: VoiceActionType, enabled: Boolean) = withContext(Dispatchers.IO) {
        val key = "voice_action_enabled_${action.actionKey}"
        appSettingDao.setSetting(AppSettingEntity(key, enabled.toString()))
    }

    suspend fun getVoiceActionSettingsMap(): Map<VoiceActionType, Boolean> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<VoiceActionType, Boolean>()
        for (type in VoiceActionType.entries) {
            val key = "voice_action_enabled_${type.actionKey}"
            val setting = appSettingDao.getSettingSync(key)
            map[type] = setting?.toBooleanStrictOrNull() ?: true
        }
        map
    }

    suspend fun parseVoiceCommandWithAi(commandText: String): Result<ParsedVoiceCommand> = withContext(Dispatchers.IO) {
        val trimmed = commandText.trim()
        if (trimmed.isBlank()) return@withContext Result.failure(Exception("Empty voice command"))

        // 1. Try local heuristic / regex parser first
        val heuristic = VoiceIntentParser.parseHeuristic(trimmed)

        try {
            val provider = getActiveProvider()
            val apiKey = if (provider != null) secretStore.getApiKey(provider.id) else null

            // If AI provider or key is missing, return heuristic parse if available
            if (provider == null || apiKey.isNullOrBlank()) {
                if (heuristic != null) {
                    if (!isVoiceActionEnabled(heuristic.actionType)) {
                        return@withContext Result.failure(
                            Exception("Action '${heuristic.actionType.displayName}' is currently disabled in Settings. Enable it in Settings > Voice Actions to proceed.")
                        )
                    }
                    return@withContext Result.success(heuristic)
                }
                return@withContext Result.failure(AiError.Other("No AI Provider configured. Please add an API key in Settings or use supported voice commands."))
            }

            val aiProvider = providerFactory.createProvider(provider)
            val flow = aiProvider.streamChat(
                messages = listOf(ChatMessage(role = MessageRole.USER, content = trimmed)),
                systemPrompt = VoiceIntentParser.SYSTEM_PROMPT
            )

            val fullResponse = StringBuilder()
            flow.collect { chunk ->
                fullResponse.append(chunk)
            }

            val parseResult = VoiceIntentParser.parse(fullResponse.toString(), trimmed)
            if (parseResult.isFailure) {
                // Fallback to heuristic
                if (heuristic != null) {
                    if (!isVoiceActionEnabled(heuristic.actionType)) {
                        return@withContext Result.failure(
                            Exception("Action '${heuristic.actionType.displayName}' is currently disabled in Settings.")
                        )
                    }
                    return@withContext Result.success(heuristic)
                }
                return@withContext parseResult
            }

            val parsed = parseResult.getOrThrow()

            // Check per-action on/off toggle
            if (!isVoiceActionEnabled(parsed.actionType)) {
                return@withContext Result.failure(
                    Exception("Action '${parsed.actionType.displayName}' is currently disabled in Settings. Enable it in Settings > Voice Actions to proceed.")
                )
            }

            Result.success(parsed)
        } catch (e: Exception) {
            if (heuristic != null) {
                if (!isVoiceActionEnabled(heuristic.actionType)) {
                    return@withContext Result.failure(
                        Exception("Action '${heuristic.actionType.displayName}' is currently disabled in Settings.")
                    )
                }
                return@withContext Result.success(heuristic)
            }
            Result.failure(e)
        }
    }
}

