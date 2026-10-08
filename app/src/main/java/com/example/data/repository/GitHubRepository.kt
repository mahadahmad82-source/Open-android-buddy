package com.example.data.repository

import android.util.Base64
import com.example.data.provider.AiProviderFactory
import com.example.data.remote.github.DiffItem
import com.example.data.remote.github.DiffLineType
import com.example.data.remote.github.GitHubApiService
import com.example.data.remote.github.GitHubBranch
import com.example.data.remote.github.GitHubContent
import com.example.data.remote.github.GitHubException
import com.example.data.remote.github.GitHubRepo
import com.example.data.remote.github.GitHubTreeItem
import com.example.data.remote.github.GitHubTreeNode
import com.example.data.remote.github.GitHubUser
import com.example.data.remote.github.ModifiedFile
import com.example.data.security.SecretStore
import com.example.domain.model.AiProviderConfig
import com.example.domain.model.ChatMessage
import com.example.domain.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

class GitHubRepository(
    private val secretStore: SecretStore,
    private val apiService: GitHubApiService = GitHubApiService(),
    private val providerFactory: AiProviderFactory
) {
    // Current authenticated user
    private val _currentUser = MutableStateFlow<GitHubUser?>(null)
    val currentUser: StateFlow<GitHubUser?> = _currentUser.asStateFlow()

    // Repositories list
    private val _repositories = MutableStateFlow<List<GitHubRepo>>(emptyList())
    val repositories: StateFlow<List<GitHubRepo>> = _repositories.asStateFlow()

    // Currently selected repository
    private val _selectedRepo = MutableStateFlow<GitHubRepo?>(null)
    val selectedRepo: StateFlow<GitHubRepo?> = _selectedRepo.asStateFlow()

    // Branches for current repo
    private val _branches = MutableStateFlow<List<GitHubBranch>>(emptyList())
    val branches: StateFlow<List<GitHubBranch>> = _branches.asStateFlow()

    // Selected branch
    private val _selectedBranch = MutableStateFlow<String>("main")
    val selectedBranch: StateFlow<String> = _selectedBranch.asStateFlow()

    // Hierarchical Tree
    private val _treeRoot = MutableStateFlow<GitHubTreeNode?>(null)
    val treeRoot: StateFlow<GitHubTreeNode?> = _treeRoot.asStateFlow()

    // Active open file
    private val _activeFile = MutableStateFlow<GitHubContent?>(null)
    val activeFile: StateFlow<GitHubContent?> = _activeFile.asStateFlow()

    // Active file text
    private val _activeFileOriginalContent = MutableStateFlow<String>("")
    val activeFileOriginalContent: StateFlow<String> = _activeFileOriginalContent.asStateFlow()

    private val _activeFileDraftContent = MutableStateFlow<String>("")
    val activeFileDraftContent: StateFlow<String> = _activeFileDraftContent.asStateFlow()

    // Multi-file modified staging map (Path -> ModifiedFile)
    private val _modifiedFiles = MutableStateFlow<Map<String, ModifiedFile>>(emptyMap())
    val modifiedFiles: StateFlow<Map<String, ModifiedFile>> = _modifiedFiles.asStateFlow()

    // Safety: Read-only mode active by default!
    private val _isReadOnlyMode = MutableStateFlow<Boolean>(true)
    val isReadOnlyMode: StateFlow<Boolean> = _isReadOnlyMode.asStateFlow()

    // General loading & error states
    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    fun setReadOnlyMode(readOnly: Boolean) {
        _isReadOnlyMode.value = readOnly
    }

    suspend fun validateAndFetchUser(token: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _errorMessage.value = null
        val result = apiService.getUser(token)
        _isLoading.value = false
        if (result.isSuccess) {
            _currentUser.value = result.getOrNull()
        } else {
            _errorMessage.value = result.exceptionOrNull()?.message
        }
        result
    }

    suspend fun loadRepositories(): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        val token = secretStore.getGitHubToken()
        if (token.isNullOrBlank()) {
            val err = GitHubException.InvalidToken("No GitHub token configured. Please add your PAT in Settings.")
            _errorMessage.value = err.message
            return@withContext Result.failure(err)
        }

        _isLoading.value = true
        _errorMessage.value = null
        val result = apiService.getRepositories(token)
        _isLoading.value = false

        if (result.isSuccess) {
            _repositories.value = result.getOrNull() ?: emptyList()
            // Also refresh user if null
            if (_currentUser.value == null) {
                val userRes = apiService.getUser(token)
                if (userRes.isSuccess) _currentUser.value = userRes.getOrNull()
            }
        } else {
            _errorMessage.value = result.exceptionOrNull()?.message
        }
        result
    }

    suspend fun selectRepository(repo: GitHubRepo) = withContext(Dispatchers.IO) {
        _selectedRepo.value = repo
        _selectedBranch.value = repo.defaultBranch
        _activeFile.value = null
        _activeFileOriginalContent.value = ""
        _activeFileDraftContent.value = ""
        _treeRoot.value = null

        val token = secretStore.getGitHubToken() ?: return@withContext
        _isLoading.value = true

        // Load branches
        val branchResult = apiService.getBranches(token, repo.owner.login, repo.name)
        if (branchResult.isSuccess) {
            _branches.value = branchResult.getOrNull() ?: emptyList()
        }

        // Load tree for default branch
        loadTree(repo.owner.login, repo.name, repo.defaultBranch)
        _isLoading.value = false
    }

    suspend fun switchBranch(branchName: String) = withContext(Dispatchers.IO) {
        val repo = _selectedRepo.value ?: return@withContext
        _selectedBranch.value = branchName
        _activeFile.value = null
        _activeFileOriginalContent.value = ""
        _activeFileDraftContent.value = ""
        loadTree(repo.owner.login, repo.name, branchName)
    }

    suspend fun createNewBranch(newBranchName: String, baseBranch: String): Result<Unit> = withContext(Dispatchers.IO) {
        val repo = _selectedRepo.value ?: return@withContext Result.failure(GitHubException.GenericError("No repository selected"))
        val token = secretStore.getGitHubToken() ?: return@withContext Result.failure(GitHubException.InvalidToken())

        val branchObj = _branches.value.firstOrNull { it.name == baseBranch }
        val baseSha = branchObj?.commitSha
        if (baseSha.isNullOrBlank()) {
            return@withContext Result.failure(GitHubException.GenericError("Base branch '$baseBranch' not found."))
        }

        _isLoading.value = true
        val result = apiService.createBranch(token, repo.owner.login, repo.name, newBranchName.trim(), baseSha)
        _isLoading.value = false

        if (result.isSuccess) {
            // Refresh branches
            val branchResult = apiService.getBranches(token, repo.owner.login, repo.name)
            if (branchResult.isSuccess) {
                _branches.value = branchResult.getOrNull() ?: emptyList()
            }
            _selectedBranch.value = newBranchName.trim()
            loadTree(repo.owner.login, repo.name, newBranchName.trim())
        } else {
            _errorMessage.value = result.exceptionOrNull()?.message
        }
        result
    }

    private suspend fun loadTree(owner: String, repo: String, branch: String) = withContext(Dispatchers.IO) {
        val token = secretStore.getGitHubToken() ?: return@withContext
        _isLoading.value = true
        val treeResult = apiService.getTree(token, owner, repo, branch, recursive = true)
        _isLoading.value = false

        if (treeResult.isSuccess) {
            val response = treeResult.getOrNull()
            if (response != null) {
                val root = buildHierarchicalTree(response.tree)
                _treeRoot.value = root
            }
        } else {
            _errorMessage.value = treeResult.exceptionOrNull()?.message
        }
    }

    private fun buildHierarchicalTree(items: List<GitHubTreeItem>): GitHubTreeNode {
        val root = GitHubTreeNode(
            name = "root",
            fullPath = "",
            isDirectory = true,
            sha = "",
            isExpanded = true
        )

        for (item in items) {
            val segments = item.path.split("/")
            var currentNode = root

            for (i in segments.indices) {
                val segment = segments[i]
                val isLast = i == segments.size - 1
                val subPath = segments.take(i + 1).joinToString("/")

                var child = currentNode.children.firstOrNull { it.name == segment }
                if (child == null) {
                    child = GitHubTreeNode(
                        name = segment,
                        fullPath = subPath,
                        isDirectory = if (isLast) item.isDirectory else true,
                        sha = if (isLast) item.sha else "",
                        size = if (isLast) item.size else 0L,
                        isExpanded = false
                    )
                    currentNode.children.add(child)
                }
                currentNode = child
            }
        }

        sortTree(root)
        return root
    }

    private fun sortTree(node: GitHubTreeNode) {
        node.children.sortWith(compareByDescending<GitHubTreeNode> { it.isDirectory }.thenBy { it.name.lowercase() })
        node.children.forEach { sortTree(it) }
    }

    suspend fun openFile(node: GitHubTreeNode): Result<String> = withContext(Dispatchers.IO) {
        val repo = _selectedRepo.value ?: return@withContext Result.failure(GitHubException.GenericError("No repository selected"))
        val token = secretStore.getGitHubToken() ?: return@withContext Result.failure(GitHubException.InvalidToken())
        val branch = _selectedBranch.value

        _isLoading.value = true
        _errorMessage.value = null
        val contentResult = apiService.getFileContent(token, repo.owner.login, repo.name, node.fullPath, branch)
        _isLoading.value = false

        if (contentResult.isSuccess) {
            val contentObj = contentResult.getOrNull()!!
            _activeFile.value = contentObj

            // Decode content
            val rawBase64 = contentObj.content?.replace("\n", "") ?: ""
            val decodedString = try {
                val bytes = Base64.decode(rawBase64, Base64.DEFAULT)
                String(bytes, StandardCharsets.UTF_8)
            } catch (e: Exception) {
                "[Binary file or unsupported encoding]"
            }

            _activeFileOriginalContent.value = decodedString

            // Check if there is an existing modified draft in memory
            val staged = _modifiedFiles.value[node.fullPath]
            _activeFileDraftContent.value = staged?.newContent ?: decodedString

            Result.success(decodedString)
        } else {
            val err = contentResult.exceptionOrNull()
            _errorMessage.value = err?.message
            Result.failure(err ?: GitHubException.GenericError("Failed to open file"))
        }
    }

    fun updateActiveDraftContent(newText: String) {
        _activeFileDraftContent.value = newText
        val file = _activeFile.value ?: return
        val original = _activeFileOriginalContent.value

        val currentMap = _modifiedFiles.value.toMutableMap()
        if (newText != original) {
            currentMap[file.path] = ModifiedFile(
                path = file.path,
                originalContent = original,
                newContent = newText,
                originalSha = file.sha,
                isNew = false
            )
        } else {
            currentMap.remove(file.path)
        }
        _modifiedFiles.value = currentMap
    }

    fun discardFileChanges(path: String) {
        val currentMap = _modifiedFiles.value.toMutableMap()
        currentMap.remove(path)
        _modifiedFiles.value = currentMap

        if (_activeFile.value?.path == path) {
            _activeFileDraftContent.value = _activeFileOriginalContent.value
        }
    }

    fun discardAllChanges() {
        _modifiedFiles.value = emptyMap()
        _activeFileDraftContent.value = _activeFileOriginalContent.value
    }

    // ==========================================
    // AI Assisted Editing & Diff computation
    // ==========================================

    suspend fun proposeAiCodeEdit(
        originalContent: String,
        filePath: String,
        instruction: String,
        providerConfig: AiProviderConfig,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val provider = providerFactory.createProviderWithCustomKey(providerConfig, apiKey)
            val extension = filePath.substringAfterLast('.', "")
            val language = when (extension.lowercase()) {
                "kt" -> "kotlin"
                "java" -> "java"
                "py" -> "python"
                "js" -> "javascript"
                "ts" -> "typescript"
                "xml" -> "xml"
                "json" -> "json"
                "html" -> "html"
                "css" -> "css"
                "md" -> "markdown"
                "yaml", "yml" -> "yaml"
                "sql" -> "sql"
                else -> "text"
            }

            // Chunk if file is very large (> 40k chars)
            val safeContent = if (originalContent.length > 50000) {
                originalContent.take(50000) + "\n\n// ... [Content truncated for AI prompt limit]"
            } else {
                originalContent
            }

            val systemPrompt = """
                You are Open Buddy, an expert code transformation engine.
                You are modifying the file '$filePath' ($language).
                The user will give an instruction.
                You MUST return ONLY the full updated code of the file.
                Do NOT include introductory or concluding conversational chat.
                Do NOT wrap in markdown unless standard code tags. Provide pure updated code.
            """.trimIndent()

            val userPrompt = """
                FILE PATH: $filePath
                LANGUAGE: $language

                CURRENT CODE:
                ```$language
                $safeContent
                ```

                INSTRUCTION:
                $instruction

                Provide the complete updated file code fulfilling the instruction:
            """.trimIndent()

            val messages = listOf(
                ChatMessage(role = MessageRole.USER, content = userPrompt)
            )

            val fullResponse = StringBuilder()
            provider.streamChat(messages, systemPrompt).collect { chunk ->
                fullResponse.append(chunk)
            }

            val rawOutput = fullResponse.toString().trim()
            val extractedCode = extractCodeFromAiResponse(rawOutput, language)
            Result.success(extractedCode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractCodeFromAiResponse(raw: String, language: String): String {
        val trimmed = raw.trim()
        val codeBlockRegex = Regex("```(?:[a-zA-Z0-9_-]+)?\\s*\\n?([\\s\\S]*?)```")
        val match = codeBlockRegex.find(trimmed)
        return if (match != null) {
            match.groupValues[1].trim()
        } else {
            trimmed
        }
    }

    suspend fun suggestCommitMessageWithAi(
        modifiedFiles: List<ModifiedFile>,
        providerConfig: AiProviderConfig,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val provider = providerFactory.createProviderWithCustomKey(providerConfig, apiKey)
            val fileSummaries = modifiedFiles.joinToString("\n") { file ->
                val linesOld = file.originalContent.lines().size
                val linesNew = file.newContent.lines().size
                "- ${file.path} (delta: ${linesNew - linesOld} lines)"
            }

            val systemPrompt = "You are a Git commit assistant. Generate a concise, standard Conventional Commit message (under 72 chars) summarizing the changes."
            val userPrompt = "Generate a single line conventional commit message for changes to these files:\n$fileSummaries"

            val messages = listOf(ChatMessage(role = MessageRole.USER, content = userPrompt))
            val fullResponse = StringBuilder()
            provider.streamChat(messages, systemPrompt).collect { chunk ->
                fullResponse.append(chunk)
            }
            val msg = fullResponse.toString().trim().lines().firstOrNull()?.trim('`', '"', ' ') ?: "chore: update files via Open Buddy"
            Result.success(msg)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun computeDiff(oldCode: String, newCode: String): List<DiffItem> {
        val oldLines = oldCode.lines()
        val newLines = newCode.lines()
        val result = mutableListOf<DiffItem>()

        // Standard LCS (Longest Common Subsequence) diff algorithm
        val m = oldLines.size
        val n = newLines.size
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                if (oldLines[i - 1] == newLines[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        var i = m
        var j = n
        val backtrack = mutableListOf<DiffItem>()

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines[i - 1] == newLines[j - 1]) {
                backtrack.add(DiffItem(i, j, oldLines[i - 1], DiffLineType.UNCHANGED))
                i--
                j--
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                backtrack.add(DiffItem(null, j, "+ " + newLines[j - 1], DiffLineType.ADDED))
                j--
            } else if (i > 0 && (j == 0 || dp[i][j - 1] < dp[i - 1][j])) {
                backtrack.add(DiffItem(i, null, "- " + oldLines[i - 1], DiffLineType.REMOVED))
                i--
            }
        }

        backtrack.reverse()
        return backtrack
    }

    // ==========================================
    // Commit and Push
    // ==========================================

    suspend fun commitAndPush(
        commitMessage: String,
        targetBranch: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val repo = _selectedRepo.value ?: return@withContext Result.failure(GitHubException.GenericError("No repository selected"))
        val token = secretStore.getGitHubToken() ?: return@withContext Result.failure(GitHubException.InvalidToken())
        val modifiedList = _modifiedFiles.value.values.toList()

        if (modifiedList.isEmpty()) {
            return@withContext Result.failure(GitHubException.GenericError("No modified files to commit."))
        }

        if (_isReadOnlyMode.value) {
            return@withContext Result.failure(GitHubException.GenericError("GitHub is in Read-Only mode. Please toggle Write Mode to push changes."))
        }

        _isLoading.value = true
        _errorMessage.value = null

        val pushResult = apiService.commitMultipleFiles(
            token = token,
            owner = repo.owner.login,
            repo = repo.name,
            branch = targetBranch,
            commitMessage = commitMessage,
            modifiedFiles = modifiedList
        )

        _isLoading.value = false

        if (pushResult.isSuccess) {
            // Clear staged modifications
            _modifiedFiles.value = emptyMap()
            // Reload tree for the branch
            loadTree(repo.owner.login, repo.name, targetBranch)
            // If active file was among committed files, update its original text and sha
            val active = _activeFile.value
            if (active != null) {
                val committed = modifiedList.firstOrNull { it.path == active.path }
                if (committed != null) {
                    _activeFileOriginalContent.value = committed.newContent
                    _activeFileDraftContent.value = committed.newContent
                }
            }
        } else {
            _errorMessage.value = pushResult.exceptionOrNull()?.message
        }

        pushResult
    }

    fun closeActiveFile() {
        _activeFile.value = null
        _activeFileOriginalContent.value = ""
        _activeFileDraftContent.value = ""
    }

    fun closeRepository() {
        _selectedRepo.value = null
        _selectedBranch.value = "main"
        _branches.value = emptyList()
        _treeRoot.value = null
        closeActiveFile()
    }
}
