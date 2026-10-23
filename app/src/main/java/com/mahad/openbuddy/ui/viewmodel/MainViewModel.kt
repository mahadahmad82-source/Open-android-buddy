package com.mahad.openbuddy.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mahad.openbuddy.data.agent.AgentEngine
import com.mahad.openbuddy.data.agent.ToolApprovalRequest
import com.mahad.openbuddy.data.local.OpenBuddyDatabase
import com.mahad.openbuddy.data.local.entity.AppSettingEntity
import com.mahad.openbuddy.data.local.entity.McpServerEntity
import com.mahad.openbuddy.data.local.entity.MemoryEntity
import com.mahad.openbuddy.data.local.entity.SkillEntity
import com.mahad.openbuddy.data.provider.AiProviderFactory
import com.mahad.openbuddy.data.remote.github.DiffItem
import com.mahad.openbuddy.data.remote.github.GitHubBranch
import com.mahad.openbuddy.data.remote.github.GitHubContent
import com.mahad.openbuddy.data.remote.github.GitHubRepo
import com.mahad.openbuddy.data.remote.github.GitHubTreeNode
import com.mahad.openbuddy.data.remote.github.GitHubUser
import com.mahad.openbuddy.data.remote.github.ModifiedFile
import com.mahad.openbuddy.data.repository.OpenBuddyRepository
import com.mahad.openbuddy.data.repository.GitHubRepository
import com.mahad.openbuddy.data.repository.SupabaseRepository
import com.mahad.openbuddy.data.security.SecretStore
import com.mahad.openbuddy.data.voice.TtsManager
import com.mahad.openbuddy.data.voice.VoiceIntentExecutor
import com.mahad.openbuddy.data.voice.VoiceSpeechRecognizerHelper
import com.mahad.openbuddy.service.AgentForegroundService
import com.mahad.openbuddy.domain.model.AiError
import com.mahad.openbuddy.domain.model.AiProviderConfig
import com.mahad.openbuddy.domain.model.CodeDraft
import com.mahad.openbuddy.domain.model.Conversation
import com.mahad.openbuddy.domain.model.DatabaseTableInfo
import com.mahad.openbuddy.domain.model.LastBackupInfo
import com.mahad.openbuddy.domain.model.Message
import com.mahad.openbuddy.domain.model.MessageRole
import com.mahad.openbuddy.domain.model.ParsedVoiceCommand
import com.mahad.openbuddy.domain.model.ProviderType
import com.mahad.openbuddy.domain.model.QueryHistoryItem
import com.mahad.openbuddy.domain.model.QueryResult
import com.mahad.openbuddy.domain.model.RestoreMode
import com.mahad.openbuddy.domain.model.RestorePreviewSummary
import com.mahad.openbuddy.domain.model.VoiceActionHistoryItem
import com.mahad.openbuddy.domain.model.VoiceActionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val secretStore = SecretStore.getInstance(application)
    private val database = OpenBuddyDatabase.getInstance(application)
    private val providerFactory = AiProviderFactory(secretStore)
    private val repository = OpenBuddyRepository(application, database, secretStore, providerFactory)
    val gitHubRepository = GitHubRepository(secretStore = secretStore, providerFactory = providerFactory)
    val supabaseRepository = SupabaseRepository(
        secretStore = secretStore,
        queryHistoryDao = database.queryHistoryDao(),
        providerFactory = providerFactory
    )

    // Agent (Phase 2-7): TTS + agent engine + related state
    val ttsManager = TtsManager(application)
    val agentEngine = AgentEngine(
        context = application,
        database = database,
        secretStore = secretStore,
        providerFactory = providerFactory,
        openBuddyRepository = repository,
        supabaseRepository = supabaseRepository,
        ttsManager = ttsManager
    )

    private val _agentMode = MutableStateFlow(false)
    val agentMode: StateFlow<Boolean> = _agentMode.asStateFlow()

    private val _speakReplies = MutableStateFlow(false)
    val speakReplies: StateFlow<Boolean> = _speakReplies.asStateFlow()

    private val _activeSkillId = MutableStateFlow<String?>(null)
    val activeSkillId: StateFlow<String?> = _activeSkillId.asStateFlow()

    private val _handsFreeActive = MutableStateFlow(false)
    val handsFreeActive: StateFlow<Boolean> = _handsFreeActive.asStateFlow()

    val agentPendingApproval: StateFlow<ToolApprovalRequest?> = agentEngine.pendingApproval
    val agentStatusText: StateFlow<String?> = agentEngine.statusText

    val skills: StateFlow<List<SkillEntity>> = database.skillDao().getAllSkills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryEntity>> = database.memoryDao().getAllMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mcpServers: StateFlow<List<McpServerEntity>> = database.mcpServerDao().getAllServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var agentJob: Job? = null
    private var handsFreeJob: Job? = null

    // App Lock State: locked on start if biometric lock is enabled
    private val _isAppLocked = MutableStateFlow(secretStore.isBiometricLockEnabled())
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private var lastBackgroundTime: Long = 0L

    val isGenerating: StateFlow<Boolean> = combine(repository.isGenerating, agentEngine.isRunning) { chat, agent -> chat || agent }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val conversations: StateFlow<List<Conversation>> = repository.conversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeConversationId: StateFlow<String?> = repository.activeConversationId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val providers: StateFlow<List<AiProviderConfig>> = repository.providers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProvider: StateFlow<AiProviderConfig?> = repository.providers
        .combine(repository.activeProviderId) { providerList, activeId ->
            if (activeId != null) {
                providerList.firstOrNull { it.id == activeId }
                    ?: providerList.firstOrNull { it.isDefault }
                    ?: providerList.firstOrNull()
            } else {
                providerList.firstOrNull { it.isDefault } ?: providerList.firstOrNull()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val systemPrompt: StateFlow<String> = repository.systemPrompt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OpenBuddyRepository.DEFAULT_SYSTEM_PROMPT)

    val codeDrafts: StateFlow<List<CodeDraft>> = repository.codeDrafts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeMessages: StateFlow<List<Message>> = activeConversationId
        .flatMapLatest { convId ->
            if (convId != null) repository.getMessagesForConversation(convId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Security & Privacy preferences
    val biometricLockEnabled: StateFlow<Boolean> = repository.biometricLockEnabled
    val autoLockTimeoutMinutes: StateFlow<Int> = repository.autoLockTimeoutMinutes
    val redactionEnabled: StateFlow<Boolean> = repository.redactionEnabled

    // Developer Vault Tokens
    val hasGitHubToken: StateFlow<Boolean> = repository.hasGitHubToken
    val hasSupabaseToken: StateFlow<Boolean> = repository.hasSupabaseToken
    val supabaseUrl: StateFlow<String> = repository.supabaseUrl

    // Local Backup & Restore state
    val lastBackupInfo: StateFlow<LastBackupInfo?> = repository.lastBackupInfo

    private val _hasSupabaseCredentials = MutableStateFlow(supabaseRepository.hasCredentials())
    val hasSupabaseCredentials: StateFlow<Boolean> = _hasSupabaseCredentials.asStateFlow()

    private val _supabaseProjectRef = MutableStateFlow(supabaseRepository.getProjectRef())
    val supabaseProjectRef: StateFlow<String?> = _supabaseProjectRef.asStateFlow()

    // Supabase StateFlows
    val supabaseTables: StateFlow<List<DatabaseTableInfo>> = supabaseRepository.tables
    val isSupabaseSchemaLoading: StateFlow<Boolean> = supabaseRepository.isSchemaLoading
    val supabaseSchemaError: StateFlow<String?> = supabaseRepository.schemaError
    val isSupabaseQueryRunning: StateFlow<Boolean> = supabaseRepository.isQueryRunning
    val isSupabaseAiGenerating: StateFlow<Boolean> = supabaseRepository.isAiGenerating
    val supabaseLastQueryResult: StateFlow<QueryResult?> = supabaseRepository.lastQueryResult
    val isSupabaseWriteMode: StateFlow<Boolean> = supabaseRepository.isWriteMode
    val supabaseQueryHistory: StateFlow<List<QueryHistoryItem>> = supabaseRepository.queryHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // GitHub Integration StateFlows
    val gitHubUser: StateFlow<GitHubUser?> = gitHubRepository.currentUser
    val gitHubRepos: StateFlow<List<GitHubRepo>> = gitHubRepository.repositories
    val selectedGitHubRepo: StateFlow<GitHubRepo?> = gitHubRepository.selectedRepo
    val gitHubBranches: StateFlow<List<GitHubBranch>> = gitHubRepository.branches
    val selectedGitHubBranch: StateFlow<String> = gitHubRepository.selectedBranch
    val gitHubTreeRoot: StateFlow<GitHubTreeNode?> = gitHubRepository.treeRoot
    val activeGitHubFile: StateFlow<GitHubContent?> = gitHubRepository.activeFile
    val activeGitHubFileOriginal: StateFlow<String> = gitHubRepository.activeFileOriginalContent
    val activeGitHubFileDraft: StateFlow<String> = gitHubRepository.activeFileDraftContent
    val gitHubModifiedFiles: StateFlow<Map<String, ModifiedFile>> = gitHubRepository.modifiedFiles
    val gitHubPullRequests: StateFlow<List<com.mahad.openbuddy.data.remote.github.GitHubPullRequest>> = gitHubRepository.pullRequests
    val isGitHubReadOnly: StateFlow<Boolean> = gitHubRepository.isReadOnlyMode
    val isGitHubLoading: StateFlow<Boolean> = gitHubRepository.isLoading
    val gitHubErrorMessage: StateFlow<String?> = gitHubRepository.errorMessage

    // Voice Actions & Speech-to-Text State
    val speechRecognizerHelper = VoiceSpeechRecognizerHelper(application)
    val isVoiceListening: StateFlow<Boolean> = speechRecognizerHelper.isListening
    val voiceRmsLevel: StateFlow<Float> = speechRecognizerHelper.rmsLevel
    val voiceTranscript: StateFlow<String> = speechRecognizerHelper.transcript
    val voiceRecognizerError: StateFlow<String?> = speechRecognizerHelper.errorMessage

    private val _isChatVoiceListening = MutableStateFlow(false)
    val isChatVoiceListening: StateFlow<Boolean> = _isChatVoiceListening.asStateFlow()

    private val _chatVoiceTranscript = MutableStateFlow("")
    val chatVoiceTranscript: StateFlow<String> = _chatVoiceTranscript.asStateFlow()

    private val _isVoiceParsing = MutableStateFlow(false)
    val isVoiceParsing: StateFlow<Boolean> = _isVoiceParsing.asStateFlow()

    private val _voiceStatusMessage = MutableStateFlow<String?>(null)
    val voiceStatusMessage: StateFlow<String?> = _voiceStatusMessage.asStateFlow()

    private val _voiceErrorMessage = MutableStateFlow<String?>(null)
    val voiceErrorMessage: StateFlow<String?> = _voiceErrorMessage.asStateFlow()

    private val _pendingVoiceAction = MutableStateFlow<ParsedVoiceCommand?>(null)
    val pendingVoiceAction: StateFlow<ParsedVoiceCommand?> = _pendingVoiceAction.asStateFlow()

    val voiceActionHistory: StateFlow<List<VoiceActionHistoryItem>> = repository.voiceActionHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _voiceActionSettings = MutableStateFlow<Map<VoiceActionType, Boolean>>(
        VoiceActionType.entries.associateWith { true }
    )
    val voiceActionSettings: StateFlow<Map<VoiceActionType, Boolean>> = _voiceActionSettings.asStateFlow()

    init {
        speechRecognizerHelper.onFinalResult = { transcribedText ->
            processVoiceCommand(transcribedText)
        }

        viewModelScope.launch {
            speechRecognizerHelper.errorMessage.collect { err ->
                if (!err.isNullOrBlank()) {
                    _voiceErrorMessage.value = err
                }
            }
        }

        viewModelScope.launch {
            repository.initializeDefaultDataIfNeeded()
            loadAgentSettings()
            seedBuiltInSkillsIfNeeded()
            loadVoiceActionSettings()
            if (secretStore.hasGitHubToken()) {
                val token = secretStore.getGitHubToken() ?: ""
                gitHubRepository.validateAndFetchUser(token)
            }
        }
    }

    fun setVoiceError(msg: String?) {
        _voiceErrorMessage.value = msg
    }

    // ==========================================
    // App Lock Lifecycle Controls
    // ==========================================

    fun unlockApp() {
        _isAppLocked.value = false
    }

    fun lockApp() {
        if (biometricLockEnabled.value) {
            _isAppLocked.value = true
        }
    }

    fun onAppForegrounded() {
        if (biometricLockEnabled.value && lastBackgroundTime > 0L) {
            val elapsedMinutes = (System.currentTimeMillis() - lastBackgroundTime) / (1000 * 60)
            if (elapsedMinutes >= autoLockTimeoutMinutes.value) {
                _isAppLocked.value = true
            }
        }
    }

    fun onAppBackgrounded() {
        lastBackgroundTime = System.currentTimeMillis()
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        repository.setBiometricLockEnabled(enabled)
        if (!enabled) {
            _isAppLocked.value = false
        }
    }

    fun setAutoLockTimeoutMinutes(minutes: Int) {
        repository.setAutoLockTimeoutMinutes(minutes)
    }

    fun setRedactionEnabled(enabled: Boolean) {
        repository.setRedactionEnabled(enabled)
    }

    // ==========================================
    // Developer Vault Token Management
    // ==========================================

    fun saveGitHubToken(token: String) {
        repository.saveGitHubToken(token)
        viewModelScope.launch {
            gitHubRepository.validateAndFetchUser(token)
        }
    }

    suspend fun validateGitHubToken(token: String): Result<GitHubUser> {
        return gitHubRepository.validateAndFetchUser(token)
    }

    fun getGitHubToken(): String? {
        return repository.getGitHubToken()
    }

    fun deleteGitHubToken() {
        repository.deleteGitHubToken()
    }

    fun saveSupabaseCredentials(urlOrRef: String, token: String) {
        repository.saveSupabaseCredentials(urlOrRef, token)
        _hasSupabaseCredentials.value = supabaseRepository.hasCredentials()
        _supabaseProjectRef.value = supabaseRepository.getProjectRef()
        viewModelScope.launch {
            refreshSupabaseSchema()
        }
    }

    fun getSupabaseToken(): String? {
        return repository.getSupabaseToken()
    }

    fun getSupabaseUrl(): String? {
        return repository.getSupabaseUrl()
    }

    fun deleteSupabaseCredentials() {
        repository.deleteSupabaseCredentials()
        _hasSupabaseCredentials.value = false
        _supabaseProjectRef.value = null
    }

    // ==========================================
    // Supabase Database Tab Actions
    // ==========================================

    fun setSupabaseWriteMode(enabled: Boolean) {
        supabaseRepository.setWriteMode(enabled)
    }

    fun refreshSupabaseSchema() {
        viewModelScope.launch {
            supabaseRepository.refreshSchema()
        }
    }

    suspend fun previewSupabaseTable(tableName: String): Result<QueryResult> {
        return supabaseRepository.previewTable(tableName)
    }

    suspend fun executeSupabaseQuery(sql: String): Result<QueryResult> {
        return supabaseRepository.executeQuery(sql)
    }

    suspend fun generateSupabaseSql(prompt: String): Result<Pair<String, String>> {
        val provider = activeProvider.value ?: repository.getActiveProvider()
            ?: return Result.failure(AiError.Other("No AI Provider configured. Please add an API key in Settings."))
        val apiKey = secretStore.getApiKey(provider.id)
            ?: return Result.failure(AiError.InvalidKey(provider.name))
        return supabaseRepository.generateSqlFromPrompt(prompt, provider, apiKey)
    }

    suspend fun explainSupabaseQuery(sql: String): Result<String> {
        val provider = activeProvider.value ?: repository.getActiveProvider()
            ?: return Result.failure(AiError.Other("No AI Provider configured. Please add an API key in Settings."))
        val apiKey = secretStore.getApiKey(provider.id)
            ?: return Result.failure(AiError.InvalidKey(provider.name))
        return supabaseRepository.explainQuery(sql, provider, apiKey)
    }

    suspend fun optimizeSupabaseQuery(sql: String): Result<String> {
        val provider = activeProvider.value ?: repository.getActiveProvider()
            ?: return Result.failure(AiError.Other("No AI Provider configured. Please add an API key in Settings."))
        val apiKey = secretStore.getApiKey(provider.id)
            ?: return Result.failure(AiError.InvalidKey(provider.name))
        return supabaseRepository.optimizeQuery(sql, provider, apiKey)
    }

    fun clearSupabaseHistory() {
        viewModelScope.launch {
            supabaseRepository.clearHistory()
        }
    }

    suspend fun testSupabaseConnection(projectRef: String, token: String): Result<String> {
        return com.mahad.openbuddy.data.remote.supabase.SupabaseApiService().testConnection(projectRef, token)
    }

    // ==========================================
    // Local Backup & Restore
    // ==========================================

    suspend fun createEncryptedBackup(passphrase: String): Result<String> {
        return try {
            val encryptedJson = repository.createEncryptedBackupPayload(passphrase)
            Result.success(encryptedJson)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun recordCompletedBackup(fileName: String, sizeBytes: Long) {
        repository.recordCompletedBackup(fileName, sizeBytes)
    }

    fun decryptAndInspectBackup(envelopeJson: String, passphrase: String): Result<RestorePreviewSummary> {
        return repository.decryptAndInspectBackup(envelopeJson, passphrase)
    }

    suspend fun executeRestore(summary: RestorePreviewSummary, mode: RestoreMode): Result<Unit> {
        return repository.restoreBackup(summary, mode)
    }

    // ==========================================
    // Privacy & Data Management
    // ==========================================

    suspend fun exportDataJson(): String {
        return repository.exportDataJson()
    }

    suspend fun deleteEverything(): Result<Unit> {
        val result = repository.deleteEverything()
        if (result.isSuccess) {
            _isAppLocked.value = false
        }
        return result
    }

    // ==========================================
    // Chat & Provider Operations
    // ==========================================

    // Media attachment staged from the chat photo/video picker, sent with the
    // next message (vision for Gemini/Claude/OpenAI-style providers).
    private val _pendingAttachment = MutableStateFlow<com.mahad.openbuddy.data.media.StagedAttachment?>(null)
    val pendingAttachment: StateFlow<com.mahad.openbuddy.data.media.StagedAttachment?> = _pendingAttachment.asStateFlow()

    fun stageAttachment(uri: android.net.Uri) {
        viewModelScope.launch {
            val staged = withContext(Dispatchers.IO) {
                try {
                    val app = getApplication<Application>()
                    val resolver = app.contentResolver
                    val mime = resolver.getType(uri) ?: "image/jpeg"
                    if (!mime.startsWith("image/") && !mime.startsWith("video/")) return@withContext null
                    val ext = when {
                        mime.contains("png") -> "png"
                        mime.contains("webp") -> "webp"
                        mime.contains("gif") -> "gif"
                        mime.contains("quicktime") -> "mov"
                        mime.contains("mp4") -> "mp4"
                        mime.contains("webm") -> "webm"
                        else -> "jpg"
                    }
                    val dir = java.io.File(app.filesDir, "attachments").apply { mkdirs() }
                    val file = java.io.File(dir, "att-${System.currentTimeMillis()}.$ext")
                    val copied = resolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                        true
                    } ?: false
                    if (!copied) return@withContext null
                    // Inline vision payloads have practical size limits; keep the cap generous but real.
                    if (file.length() > 25L * 1024 * 1024) {
                        file.delete()
                        return@withContext null
                    }
                    com.mahad.openbuddy.data.media.StagedAttachment(
                        path = file.absolutePath,
                        mimeType = mime,
                        kind = if (mime.startsWith("video/")) "video" else "image",
                        displayName = file.name
                    )
                } catch (e: Exception) {
                    null
                }
            }
            if (staged != null) {
                _pendingAttachment.value = staged
            }
        }
    }

    fun clearPendingAttachment() {
        _pendingAttachment.value = null
    }

    // ------------------------------------------------------------------
    // Self-update: check GitHub releases, download in-app, hand to the
    // system installer (see data/update/AppUpdateManager).
    // ------------------------------------------------------------------

    private val _updateState = MutableStateFlow<com.mahad.openbuddy.data.update.UpdateUiState>(
        com.mahad.openbuddy.data.update.UpdateUiState.Idle
    )
    val updateState: StateFlow<com.mahad.openbuddy.data.update.UpdateUiState> = _updateState.asStateFlow()
    private var updateCheckedThisSession = false

    fun installedVersionName(): String {
        return try {
            val app = getApplication<Application>()
            @Suppress("DEPRECATION")
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    fun checkForUpdates(manual: Boolean = false) {
        if (!manual && updateCheckedThisSession) return
        updateCheckedThisSession = true
        if (_updateState.value is com.mahad.openbuddy.data.update.UpdateUiState.Downloading) return
        viewModelScope.launch {
            _updateState.value = com.mahad.openbuddy.data.update.UpdateUiState.Checking
            val result = com.mahad.openbuddy.data.update.AppUpdateManager.fetchLatestUpdate(installedVersionName())
            result.fold(
                onSuccess = { info ->
                    _updateState.value = when {
                        info != null -> com.mahad.openbuddy.data.update.UpdateUiState.Available(info)
                        manual -> com.mahad.openbuddy.data.update.UpdateUiState.UpToDate
                        else -> com.mahad.openbuddy.data.update.UpdateUiState.Idle
                    }
                },
                onFailure = { error ->
                    _updateState.value = if (manual) {
                        com.mahad.openbuddy.data.update.UpdateUiState.Failed(error.message ?: "Update check failed.")
                    } else {
                        com.mahad.openbuddy.data.update.UpdateUiState.Idle
                    }
                }
            )
        }
    }

    fun downloadUpdate() {
        val current = _updateState.value
        val info = (current as? com.mahad.openbuddy.data.update.UpdateUiState.Available)?.info ?: return
        viewModelScope.launch {
            _updateState.value = com.mahad.openbuddy.data.update.UpdateUiState.Downloading(info, 0)
            val result = com.mahad.openbuddy.data.update.AppUpdateManager.downloadApk(
                getApplication(),
                info
            ) { percent ->
                _updateState.value = com.mahad.openbuddy.data.update.UpdateUiState.Downloading(info, percent)
            }
            _updateState.value = result.fold(
                onSuccess = { file -> com.mahad.openbuddy.data.update.UpdateUiState.ReadyToInstall(info, file) },
                onFailure = { error ->
                    com.mahad.openbuddy.data.update.UpdateUiState.Failed(error.message ?: "Download failed.")
                }
            )
        }
    }

    fun dismissUpdate() {
        _updateState.value = com.mahad.openbuddy.data.update.UpdateUiState.Idle
    }

    fun sendMessage(text: String) {
        val convId = activeConversationId.value ?: return
        val attachment = _pendingAttachment.value
        val effectiveText = if (text.isBlank() && attachment != null) "Attached media" else text
        if (effectiveText.isBlank() && attachment == null) return
        _pendingAttachment.value = null
        if (_agentMode.value) {
            agentJob?.cancel()
            agentJob = viewModelScope.launch {
                val skill = _activeSkillId.value?.let { id -> skills.value.firstOrNull { it.id == id } }
                agentEngine.runTurn(convId, effectiveText, skill, _speakReplies.value, attachment)
            }
        } else {
            viewModelScope.launch {
                repository.sendMessage(convId, effectiveText, viewModelScope, attachment)
            }
        }
    }

    fun stopGeneration() {
        agentJob?.cancel()
        agentJob = null
        agentEngine.cancelRun()
        repository.stopGeneration()
        ttsManager.stop()
    }

    // ==========================================
    // Agent mode, skills, memory, MCP (Phases 2-7)
    // ==========================================

    private fun loadAgentSettings() {
        viewModelScope.launch {
            val dao = database.appSettingDao()
            _agentMode.value = dao.getSettingSync(KEY_AGENT_MODE) == "true"
            _speakReplies.value = dao.getSettingSync(KEY_SPEAK_REPLIES) == "true"
            _activeSkillId.value = dao.getSettingSync(KEY_ACTIVE_SKILL_ID)
        }
    }

    private fun seedBuiltInSkillsIfNeeded() {
        viewModelScope.launch {
            val dao = database.skillDao()
            if (dao.getAllSkillsList().isEmpty()) {
                BUILT_IN_SKILLS.forEach { dao.insertSkill(it) }
            }
        }
    }

    fun setAgentMode(enabled: Boolean) {
        _agentMode.value = enabled
        viewModelScope.launch {
            database.appSettingDao().setSetting(AppSettingEntity(KEY_AGENT_MODE, enabled.toString()))
        }
    }

    fun setSpeakReplies(enabled: Boolean) {
        _speakReplies.value = enabled
        if (!enabled) ttsManager.stop()
        viewModelScope.launch {
            database.appSettingDao().setSetting(AppSettingEntity(KEY_SPEAK_REPLIES, enabled.toString()))
        }
    }

    fun resolveToolApproval(approved: Boolean) {
        agentEngine.resolveApproval(approved)
    }

    fun setActiveSkill(skillId: String?) {
        _activeSkillId.value = skillId
        viewModelScope.launch {
            val dao = database.appSettingDao()
            if (skillId == null) {
                dao.setSetting(AppSettingEntity(KEY_ACTIVE_SKILL_ID, ""))
                _activeSkillId.value = null
            } else {
                dao.setSetting(AppSettingEntity(KEY_ACTIVE_SKILL_ID, skillId))
            }
        }
    }

    fun saveSkill(id: String?, name: String, description: String, instructions: String) {
        viewModelScope.launch {
            database.skillDao().insertSkill(
                SkillEntity(
                    id = id ?: UUID.randomUUID().toString(),
                    name = name.trim(),
                    description = description.trim(),
                    instructions = instructions.trim()
                )
            )
        }
    }

    fun deleteSkill(id: String) {
        viewModelScope.launch {
            database.skillDao().deleteSkillById(id)
            if (_activeSkillId.value == id) setActiveSkill(null)
        }
    }

    fun addMemory(fact: String, category: String = "general") {
        viewModelScope.launch {
            database.memoryDao().insertMemory(
                MemoryEntity(id = UUID.randomUUID().toString(), fact = fact.trim(), category = category)
            )
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            database.memoryDao().deleteMemoryById(id)
        }
    }

    fun addMcpServer(name: String, url: String, authToken: String?) {
        viewModelScope.launch {
            database.mcpServerDao().insertServer(
                McpServerEntity(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    url = url.trim(),
                    authToken = authToken?.trim()?.ifBlank { null }
                )
            )
        }
    }

    fun deleteMcpServer(id: String) {
        viewModelScope.launch {
            database.mcpServerDao().deleteServerById(id)
        }
    }

    // ==========================================
    // Hands-free voice conversation (Phase 5)
    // ==========================================

    fun startHandsFree() {
        if (_handsFreeActive.value) return
        _handsFreeActive.value = true
        AgentForegroundService.start(getApplication(), "Hands-free conversation is on")
        listenForHandsFree()
    }

    fun stopHandsFree() {
        _handsFreeActive.value = false
        handsFreeJob?.cancel()
        handsFreeJob = null
        speechRecognizerHelper.stopListening()
        ttsManager.stop()
        AgentForegroundService.stop(getApplication())
    }

    private fun listenForHandsFree() {
        if (!_handsFreeActive.value) return
        speechRecognizerHelper.onPartialResult = null
        speechRecognizerHelper.onFinalResult = { transcript ->
            if (_handsFreeActive.value) {
                if (transcript.isNotBlank()) {
                    onHandsFreeTranscript(transcript)
                } else {
                    listenForHandsFree()
                }
            }
        }
        speechRecognizerHelper.startListening(null)
    }

    private fun onHandsFreeTranscript(transcript: String) {
        sendMessage(transcript)
        handsFreeJob?.cancel()
        handsFreeJob = viewModelScope.launch {
            // Wait for the reply to finish generating…
            delay(350)
            if (isGenerating.value) isGenerating.first { !it }
            if (!_handsFreeActive.value) return@launch
            // …then speak it (hands-free always speaks), and listen again.
            val reply = activeMessages.value.lastOrNull { it.role == MessageRole.ASSISTANT }?.content
            if (!reply.isNullOrBlank() && ttsManager.isReady()) {
                ttsManager.onSpeakingFinished = {
                    if (_handsFreeActive.value) listenForHandsFree()
                }
                ttsManager.speak(reply)
                // Safety net: if TTS never reports completion, resume listening anyway.
                delay(60_000)
                if (_handsFreeActive.value && !isVoiceListening.value) listenForHandsFree()
            } else {
                delay(600)
                if (_handsFreeActive.value) listenForHandsFree()
            }
        }
    }

    fun retryLastAssistantMessage(messageId: String) {
        val convId = activeConversationId.value ?: return
        viewModelScope.launch {
            repository.retryLastAssistantMessage(convId, messageId, viewModelScope)
        }
    }

    fun selectConversation(id: String) {
        viewModelScope.launch {
            repository.setActiveConversation(id)
        }
    }

    fun createNewConversation(title: String = "New Chat") {
        viewModelScope.launch {
            val newId = repository.createNewConversation(title)
            repository.setActiveConversation(newId)
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameConversation(id, newTitle)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id)
        }
    }

    fun selectProvider(id: String) {
        viewModelScope.launch {
            repository.setActiveProvider(id)
        }
    }

    fun saveProvider(
        id: String?,
        name: String,
        type: ProviderType,
        model: String,
        baseUrl: String?,
        apiKey: String?,
        setAsDefault: Boolean
    ) {
        viewModelScope.launch {
            repository.saveProvider(id, name, type, model, baseUrl, apiKey, setAsDefault)
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            repository.deleteProvider(id)
        }
    }

    suspend fun getProviderApiKey(id: String): String? {
        return repository.getProviderApiKey(id)
    }

    suspend fun testConnection(
        type: ProviderType,
        model: String,
        baseUrl: String?,
        apiKey: String
    ): Result<String> {
        return repository.testProviderConnection(type, model, baseUrl, apiKey)
    }

    suspend fun fetchGeminiModels(apiKey: String): Result<List<String>> {
        return com.mahad.openbuddy.data.provider.GeminiProvider.fetchAvailableModels(apiKey)
    }

    /** Zero-config: live model list for ANY provider type, not just Gemini. */
    suspend fun fetchModels(type: ProviderType, baseUrl: String?, apiKey: String): Result<List<String>> {
        return com.mahad.openbuddy.data.provider.ModelCatalog.fetchModels(type, baseUrl, apiKey)
    }

    fun saveSystemPrompt(prompt: String) {
        viewModelScope.launch {
            repository.updateSystemPrompt(prompt)
        }
    }

    fun resetSystemPrompt() {
        viewModelScope.launch {
            repository.resetSystemPrompt()
        }
    }

    fun saveCodeDraft(filename: String, language: String, code: String) {
        viewModelScope.launch {
            repository.saveCodeDraft(filename, language, code)
        }
    }

    fun deleteCodeDraft(id: String) {
        viewModelScope.launch {
            database.codeDraftDao().deleteDraftById(id)
        }
    }

    // ==========================================
    // GitHub Tab Actions
    // ==========================================

    fun loadGitHubRepos() {
        viewModelScope.launch {
            gitHubRepository.loadRepositories()
        }
    }

    fun selectGitHubRepo(repo: GitHubRepo) {
        viewModelScope.launch {
            gitHubRepository.selectRepository(repo)
        }
    }

    fun switchGitHubBranch(branch: String) {
        viewModelScope.launch {
            gitHubRepository.switchBranch(branch)
        }
    }

    suspend fun createGitHubBranch(newBranch: String, baseBranch: String): Result<Unit> {
        return gitHubRepository.createNewBranch(newBranch, baseBranch)
    }

    // Phase 1: pull requests, delete file, new file

    fun loadGitHubPullRequests() {
        viewModelScope.launch {
            gitHubRepository.loadPullRequests()
        }
    }

    suspend fun createGitHubPullRequest(title: String, body: String, head: String, base: String): Result<com.mahad.openbuddy.data.remote.github.GitHubPullRequest> {
        return gitHubRepository.createPullRequest(title, body, head, base)
    }

    suspend fun mergeGitHubPullRequest(number: Int): Result<String> {
        return gitHubRepository.mergePullRequest(number)
    }

    suspend fun deleteGitHubFile(node: GitHubTreeNode, commitMessage: String): Result<Unit> {
        return gitHubRepository.deleteFile(node, commitMessage)
    }

    fun stageGitHubNewFile(path: String, initialContent: String): Result<Unit> {
        return gitHubRepository.stageNewFile(path, initialContent)
    }

    fun openGitHubFile(node: GitHubTreeNode) {
        viewModelScope.launch {
            gitHubRepository.openFile(node)
        }
    }

    fun updateGitHubDraftContent(text: String) {
        gitHubRepository.updateActiveDraftContent(text)
    }

    fun discardGitHubFileChanges(path: String) {
        gitHubRepository.discardFileChanges(path)
    }

    fun discardAllGitHubChanges() {
        gitHubRepository.discardAllChanges()
    }

    fun setGitHubReadOnly(readOnly: Boolean) {
        gitHubRepository.setReadOnlyMode(readOnly)
    }

    fun clearGitHubError() {
        gitHubRepository.clearError()
    }

    fun closeGitHubFile() {
        gitHubRepository.closeActiveFile()
    }

    fun closeGitHubRepo() {
        gitHubRepository.closeRepository()
    }

    suspend fun proposeAiFileEdit(filePath: String, originalContent: String, instruction: String): Result<String> {
        val provider = activeProvider.value ?: repository.getActiveProvider()
            ?: return Result.failure(AiError.Other("No AI Provider configured. Please add an API key in Settings."))
        val apiKey = secretStore.getApiKey(provider.id)
            ?: return Result.failure(AiError.InvalidKey(provider.name))
        return gitHubRepository.proposeAiCodeEdit(originalContent, filePath, instruction, provider, apiKey)
    }

    suspend fun suggestCommitMessage(modifiedFiles: List<ModifiedFile>): Result<String> {
        val provider = activeProvider.value ?: repository.getActiveProvider()
            ?: return Result.failure(AiError.Other("No AI Provider configured. Please add an API key in Settings."))
        val apiKey = secretStore.getApiKey(provider.id)
            ?: return Result.failure(AiError.InvalidKey(provider.name))
        return gitHubRepository.suggestCommitMessageWithAi(modifiedFiles, provider, apiKey)
    }

    fun computeDiff(oldCode: String, newCode: String): List<DiffItem> {
        return gitHubRepository.computeDiff(oldCode, newCode)
    }

    suspend fun commitAndPushGitHub(commitMessage: String, targetBranch: String): Result<String> {
        return gitHubRepository.commitAndPush(commitMessage, targetBranch)
    }

    // ==========================================
    // Voice Actions & Assistant Execution
    // ==========================================

    fun startChatVoiceListening(languagePreference: String? = null) {
        _isChatVoiceListening.value = true
        _chatVoiceTranscript.value = ""
        speechRecognizerHelper.onPartialResult = { partial ->
            if (_isChatVoiceListening.value) {
                _chatVoiceTranscript.value = partial
            }
        }
        speechRecognizerHelper.onFinalResult = { finalResult ->
            if (_isChatVoiceListening.value) {
                _chatVoiceTranscript.value = finalResult
                _isChatVoiceListening.value = false
            }
        }
        speechRecognizerHelper.startListening(languagePreference)
    }

    fun stopChatVoiceListening() {
        _isChatVoiceListening.value = false
        speechRecognizerHelper.stopListening()
    }

    fun clearChatVoiceTranscript() {
        _chatVoiceTranscript.value = ""
    }

    fun startVoiceListening(languagePreference: String? = null) {
        _isChatVoiceListening.value = false
        _voiceErrorMessage.value = null
        _voiceStatusMessage.value = "Listening... Speak your command (English, Roman Urdu, Urdu)"
        speechRecognizerHelper.onPartialResult = null
        speechRecognizerHelper.onFinalResult = { transcribedText ->
            processVoiceCommand(transcribedText)
        }
        speechRecognizerHelper.startListening(languagePreference)
    }

    fun stopVoiceListening() {
        _isChatVoiceListening.value = false
        speechRecognizerHelper.stopListening()
        _voiceStatusMessage.value = null
    }

    fun clearVoiceError() {
        _voiceErrorMessage.value = null
        speechRecognizerHelper.clearError()
    }

    fun clearVoiceStatus() {
        _voiceStatusMessage.value = null
    }

    fun cancelPendingVoiceAction() {
        val pending = _pendingVoiceAction.value
        if (pending != null) {
            viewModelScope.launch {
                repository.recordVoiceActionHistory(
                    rawCommand = pending.rawCommand,
                    actionType = pending.actionType.actionKey,
                    target = pending.target,
                    message = pending.message,
                    confidence = pending.confidence,
                    status = "CANCELLED"
                )
            }
        }
        _pendingVoiceAction.value = null
        _voiceStatusMessage.value = "Action cancelled."
    }

    fun processVoiceCommand(commandText: String) {
        if (commandText.isBlank()) return
        stopVoiceListening()
        _voiceErrorMessage.value = null
        _voiceStatusMessage.value = "Interpreting voice intent with AI..."
        _isVoiceParsing.value = true

        viewModelScope.launch {
            val result = repository.parseVoiceCommandWithAi(commandText)
            _isVoiceParsing.value = false
            if (result.isSuccess) {
                val parsed = result.getOrNull()
                _pendingVoiceAction.value = parsed
                _voiceStatusMessage.value = "Review and confirm action below"
            } else {
                val err = result.exceptionOrNull()?.message ?: "Could not parse voice command"
                _voiceErrorMessage.value = err
                _voiceStatusMessage.value = null
                // Record rejected in history
                repository.recordVoiceActionHistory(
                    rawCommand = commandText,
                    actionType = "unsupported",
                    target = "N/A",
                    message = err,
                    confidence = 0f,
                    status = "REJECTED"
                )
            }
        }
    }

    fun executeConfirmedVoiceAction(
        context: android.content.Context,
        command: ParsedVoiceCommand,
        manualRecipientOverride: String? = null
    ) {
        viewModelScope.launch {
            val execResult = VoiceIntentExecutor.execute(context, command, manualRecipientOverride)
            if (execResult.isSuccess) {
                val msg = execResult.getOrNull() ?: "Action launched successfully."
                _voiceStatusMessage.value = msg
                _pendingVoiceAction.value = null
                repository.recordVoiceActionHistory(
                    rawCommand = command.rawCommand,
                    actionType = command.actionType.actionKey,
                    target = manualRecipientOverride?.ifBlank { null } ?: command.target,
                    message = command.message,
                    confidence = command.confidence,
                    status = "EXECUTED"
                )
            } else {
                val err = execResult.exceptionOrNull()?.message ?: "Failed to execute action"
                _voiceErrorMessage.value = err
                repository.recordVoiceActionHistory(
                    rawCommand = command.rawCommand,
                    actionType = command.actionType.actionKey,
                    target = command.target,
                    message = err,
                    confidence = command.confidence,
                    status = "ERROR"
                )
            }
        }
    }

    fun clearVoiceHistory() {
        viewModelScope.launch {
            repository.clearVoiceActionHistory()
        }
    }

    fun deleteVoiceHistoryItem(id: String) {
        viewModelScope.launch {
            repository.deleteVoiceActionHistoryItem(id)
        }
    }

    fun toggleVoiceActionSetting(action: VoiceActionType, enabled: Boolean) {
        viewModelScope.launch {
            repository.setVoiceActionEnabled(action, enabled)
            loadVoiceActionSettings()
        }
    }

    private fun loadVoiceActionSettings() {
        viewModelScope.launch {
            val map = repository.getVoiceActionSettingsMap()
            _voiceActionSettings.value = map
        }
    }

    fun resolveContact(context: android.content.Context, contactName: String): String? {
        return VoiceIntentExecutor.resolveContactNumber(context, contactName)
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizerHelper.stopListening()
        ttsManager.shutdown()
    }

    companion object {
        private const val KEY_AGENT_MODE = "agent_mode"
        private const val KEY_SPEAK_REPLIES = "speak_replies"
        private const val KEY_ACTIVE_SKILL_ID = "active_skill_id"

        private val BUILT_IN_SKILLS = listOf(
            SkillEntity(
                id = "builtin-code-reviewer",
                name = "Code Reviewer",
                description = "Reviews code for bugs, security and style",
                instructions = "Act as a senior code reviewer. When the user shares or points to code, examine it for correctness bugs, security issues, performance problems and readability. Report findings as a prioritized list (critical first) with file/line references where possible, then suggest concrete fixes. Use the GitHub tools to read files when the user names a repository."
            ),
            SkillEntity(
                id = "builtin-sql-analyst",
                name = "SQL Analyst",
                description = "Writes and runs careful SQL on the connected database",
                instructions = "Act as a careful database analyst for the user's connected Supabase/Postgres database. Prefer read-only SELECT queries. Before any write or destructive statement, explain in plain words what it will change and how many rows it may affect; those statements need the user's approval in the app. Always show the SQL you ran and summarize the result rows in plain language."
            ),
            SkillEntity(
                id = "builtin-daily-planner",
                name = "Daily Planner",
                description = "Plans the day: reminders, timers, calendar events",
                instructions = "Act as the user's daily planning assistant. Help them organize their day using the device tools: create calendar events for appointments, set timers for focused work, and remember durable preferences with remember_fact. Confirm times using current_datetime when the request is relative (\"in 2 hours\", \"tomorrow morning\")."
            )
        )
    }
}

