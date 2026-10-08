package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CodePilotDatabase
import com.example.data.provider.AiProviderFactory
import com.example.data.remote.github.DiffItem
import com.example.data.remote.github.GitHubBranch
import com.example.data.remote.github.GitHubContent
import com.example.data.remote.github.GitHubRepo
import com.example.data.remote.github.GitHubTreeNode
import com.example.data.remote.github.GitHubUser
import com.example.data.remote.github.ModifiedFile
import com.example.data.repository.CodePilotRepository
import com.example.data.repository.GitHubRepository
import com.example.data.repository.SupabaseRepository
import com.example.data.security.SecretStore
import com.example.data.voice.VoiceIntentExecutor
import com.example.data.voice.VoiceSpeechRecognizerHelper
import com.example.domain.model.AiError
import com.example.domain.model.AiProviderConfig
import com.example.domain.model.CodeDraft
import com.example.domain.model.Conversation
import com.example.domain.model.DatabaseTableInfo
import com.example.domain.model.LastBackupInfo
import com.example.domain.model.Message
import com.example.domain.model.ParsedVoiceCommand
import com.example.domain.model.ProviderType
import com.example.domain.model.QueryHistoryItem
import com.example.domain.model.QueryResult
import com.example.domain.model.RestoreMode
import com.example.domain.model.RestorePreviewSummary
import com.example.domain.model.VoiceActionHistoryItem
import com.example.domain.model.VoiceActionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val secretStore = SecretStore.getInstance(application)
    private val database = CodePilotDatabase.getInstance(application)
    private val providerFactory = AiProviderFactory(secretStore)
    private val repository = CodePilotRepository(application, database, secretStore, providerFactory)
    val gitHubRepository = GitHubRepository(secretStore = secretStore, providerFactory = providerFactory)
    val supabaseRepository = SupabaseRepository(
        secretStore = secretStore,
        queryHistoryDao = database.queryHistoryDao(),
        providerFactory = providerFactory
    )

    // App Lock State: locked on start if biometric lock is enabled
    private val _isAppLocked = MutableStateFlow(secretStore.isBiometricLockEnabled())
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private var lastBackgroundTime: Long = 0L

    val isGenerating: StateFlow<Boolean> = repository.isGenerating

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CodePilotRepository.DEFAULT_SYSTEM_PROMPT)

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
        return com.example.data.remote.supabase.SupabaseApiService().testConnection(projectRef, token)
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

    fun sendMessage(text: String) {
        val convId = activeConversationId.value ?: return
        viewModelScope.launch {
            repository.sendMessage(convId, text, viewModelScope)
        }
    }

    fun stopGeneration() {
        repository.stopGeneration()
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
        return com.example.data.provider.GeminiProvider.fetchAvailableModels(apiKey)
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
    }
}

