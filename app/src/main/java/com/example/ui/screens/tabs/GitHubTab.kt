package com.example.ui.screens.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.github.DiffItem
import com.example.data.remote.github.DiffLineType
import com.example.data.remote.github.GitHubBranch
import com.example.data.remote.github.GitHubContent
import com.example.data.remote.github.GitHubRepo
import com.example.data.remote.github.GitHubTreeNode
import com.example.data.remote.github.GitHubUser
import com.example.data.remote.github.ModifiedFile
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDialog
import com.example.ui.components.GlassTextField
import com.example.ui.components.GradientButton
import com.example.ui.components.PillChip
import com.example.ui.components.ShimmerListSkeleton
import com.example.ui.components.highlightSyntax
import com.example.ui.theme.CodeBackground
import com.example.ui.theme.CodeTextStyle
import com.example.ui.theme.GlassTheme
import com.example.ui.theme.SemanticError
import com.example.ui.theme.SemanticSuccess
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubTab(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = GlassTheme.tokens
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val hasToken by viewModel.hasGitHubToken.collectAsStateWithLifecycle()
    val user by viewModel.gitHubUser.collectAsStateWithLifecycle()
    val repos by viewModel.gitHubRepos.collectAsStateWithLifecycle()
    val selectedRepo by viewModel.selectedGitHubRepo.collectAsStateWithLifecycle()
    val branches by viewModel.gitHubBranches.collectAsStateWithLifecycle()
    val selectedBranch by viewModel.selectedGitHubBranch.collectAsStateWithLifecycle()
    val treeRoot by viewModel.gitHubTreeRoot.collectAsStateWithLifecycle()
    val activeFile by viewModel.activeGitHubFile.collectAsStateWithLifecycle()
    val originalContent by viewModel.activeGitHubFileOriginal.collectAsStateWithLifecycle()
    val draftContent by viewModel.activeGitHubFileDraft.collectAsStateWithLifecycle()
    val modifiedFiles by viewModel.gitHubModifiedFiles.collectAsStateWithLifecycle()
    val isReadOnly by viewModel.isGitHubReadOnly.collectAsStateWithLifecycle()
    val isLoading by viewModel.isGitHubLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.gitHubErrorMessage.collectAsStateWithLifecycle()

    // Dialog & Sheet States
    var showCommitSheet by remember { mutableStateOf(false) }
    var showPushConfirmationDialog by remember { mutableStateOf(false) }
    var showNewBranchDialog by remember { mutableStateOf(false) }
    var showAiDiffModal by remember { mutableStateOf(false) }
    var aiProposedCode by remember { mutableStateOf("") }
    var aiDiffItems by remember { mutableStateOf<List<DiffItem>>(emptyList()) }
    var isAiGeneratingProposal by remember { mutableStateOf(false) }

    // Commit state
    var commitMessageInput by remember { mutableStateOf("") }
    var isSuggestingCommitMsg by remember { mutableStateOf(false) }
    var commitTargetBranch by remember { mutableStateOf(selectedBranch) }

    // Search & Filter state for repos
    var repoSearchQuery by remember { mutableStateOf("") }
    var selectedRepoFilter by remember { mutableIntStateOf(0) } // 0: All, 1: Public, 2: Private

    // Font size zoom state for editor
    var editorFontSize by remember { mutableFloatStateOf(13f) }

    // Trigger repo load on open if token exists and repos empty
    LaunchedEffect(hasToken) {
        if (hasToken && repos.isEmpty()) {
            viewModel.loadGitHubRepos()
        }
    }

    LaunchedEffect(selectedBranch) {
        commitTargetBranch = selectedBranch
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Workspace Top Glass Header
            GitHubWorkspaceHeader(
                selectedRepo = selectedRepo,
                selectedBranch = selectedBranch,
                branches = branches,
                isReadOnly = isReadOnly,
                modifiedCount = modifiedFiles.size,
                user = user,
                onBackToRepos = { viewModel.closeGitHubRepo() },
                onSelectBranch = { viewModel.switchGitHubBranch(it) },
                onCreateBranchClick = { showNewBranchDialog = true },
                onToggleReadOnly = { viewModel.setGitHubReadOnly(it) },
                onOpenCommitReview = { showCommitSheet = true },
                onRefresh = {
                    if (selectedRepo != null) {
                        viewModel.switchGitHubBranch(selectedBranch)
                    } else {
                        viewModel.loadGitHubRepos()
                    }
                }
            )

            // Error Banner if any
            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearGitHubError() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss error", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Main Content Area
            if (!hasToken) {
                // No token configured state
                NoTokenConfiguredView(onNavigateToSettings = onNavigateToSettings)
            } else if (selectedRepo == null) {
                // Repositories List View
                RepoListView(
                    repos = repos,
                    searchQuery = repoSearchQuery,
                    onSearchQueryChange = { repoSearchQuery = it },
                    filterIndex = selectedRepoFilter,
                    onFilterIndexChange = { selectedRepoFilter = it },
                    isLoading = isLoading,
                    onSelectRepo = { repo -> viewModel.selectGitHubRepo(repo) },
                    onRefresh = { viewModel.loadGitHubRepos() }
                )
            } else if (activeFile == null) {
                // Repository Explorer & Tree View
                RepoExplorerView(
                    repo = selectedRepo!!,
                    treeRoot = treeRoot,
                    selectedBranch = selectedBranch,
                    isLoading = isLoading,
                    modifiedFiles = modifiedFiles,
                    onOpenFile = { node -> viewModel.openGitHubFile(node) }
                )
            } else {
                // File Viewer & Editor View
                FileEditorView(
                    file = activeFile!!,
                    originalContent = originalContent,
                    draftContent = draftContent,
                    isReadOnly = isReadOnly,
                    fontSize = editorFontSize,
                    isLoading = isLoading,
                    isAiGenerating = isAiGeneratingProposal,
                    onFontSizeChange = { editorFontSize = it.coerceIn(9f, 24f) },
                    onContentChange = { newText -> viewModel.updateGitHubDraftContent(newText) },
                    onCloseFile = { viewModel.closeGitHubFile() },
                    onDiscardChanges = { viewModel.discardGitHubFileChanges(activeFile!!.path) },
                    onOpenCommitReview = { showCommitSheet = true },
                    onAskAi = { instruction ->
                        isAiGeneratingProposal = true
                        scope.launch {
                            val res = viewModel.proposeAiFileEdit(activeFile!!.path, draftContent, instruction)
                            isAiGeneratingProposal = false
                            if (res.isSuccess) {
                                aiProposedCode = res.getOrNull() ?: ""
                                aiDiffItems = viewModel.computeDiff(draftContent, aiProposedCode)
                                showAiDiffModal = true
                            } else {
                                snackbarHostState.showSnackbar("AI Edit failed: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    }
                )
            }
        }

        // Loading Overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = tokens.glassFillStrong,
                    border = tokens.glassBorderStroke
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = tokens.accentCyan,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Syncing with GitHub...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.textPrimary
                        )
                    }
                }
            }
        }

        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    // AI Diff Modal with Accept / Reject (Strictly never applies changes silently)
    if (showAiDiffModal) {
        AiDiffProposalModal(
            filePath = activeFile?.path ?: "File",
            diffItems = aiDiffItems,
            onAccept = {
                viewModel.updateGitHubDraftContent(aiProposedCode)
                showAiDiffModal = false
                scope.launch {
                    snackbarHostState.showSnackbar("AI changes applied to draft")
                }
            },
            onAcceptAndCommit = {
                viewModel.updateGitHubDraftContent(aiProposedCode)
                showAiDiffModal = false
                showCommitSheet = true
            },
            onReject = {
                showAiDiffModal = false
            }
        )
    }

    // Create New Branch Dialog
    if (showNewBranchDialog) {
        CreateBranchDialog(
            baseBranch = selectedBranch,
            onDismiss = { showNewBranchDialog = false },
            onCreateBranch = { newBranchName ->
                showNewBranchDialog = false
                scope.launch {
                    val result = viewModel.createGitHubBranch(newBranchName, selectedBranch)
                    if (result.isSuccess) {
                        snackbarHostState.showSnackbar("Created branch '$newBranchName'")
                    } else {
                        snackbarHostState.showSnackbar("Failed to create branch: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
        )
    }

    // Commit & Push Review Sheet
    if (showCommitSheet) {
        CommitReviewSheet(
            selectedRepo = selectedRepo,
            selectedBranch = commitTargetBranch,
            branches = branches,
            modifiedFiles = modifiedFiles.values.toList(),
            commitMessage = commitMessageInput,
            isSuggestingAi = isSuggestingCommitMsg,
            isReadOnly = isReadOnly,
            onBranchChange = { commitTargetBranch = it },
            onCreateBranch = { showNewBranchDialog = true },
            onCommitMessageChange = { commitMessageInput = it },
            onSuggestAiMessage = {
                isSuggestingCommitMsg = true
                scope.launch {
                    val res = viewModel.suggestCommitMessage(modifiedFiles.values.toList())
                    isSuggestingCommitMsg = false
                    if (res.isSuccess) {
                        commitMessageInput = res.getOrNull() ?: ""
                    }
                }
            },
            onDiscardFile = { path -> viewModel.discardGitHubFileChanges(path) },
            onDiscardAll = { viewModel.discardAllGitHubChanges() },
            onDismiss = { showCommitSheet = false },
            onProceedToPush = {
                showCommitSheet = false
                showPushConfirmationDialog = true
            }
        )
    }

    // Explicit Push Confirmation Dialog (Safety Guard)
    if (showPushConfirmationDialog && selectedRepo != null) {
        PushConfirmationDialog(
            repo = selectedRepo!!,
            branch = commitTargetBranch,
            commitMessage = commitMessageInput,
            modifiedFiles = modifiedFiles.values.toList(),
            onDismiss = { showPushConfirmationDialog = false },
            onConfirmPush = {
                showPushConfirmationDialog = false
                scope.launch {
                    if (isReadOnly) {
                        viewModel.setGitHubReadOnly(false)
                    }
                    val result = viewModel.commitAndPushGitHub(commitMessageInput, commitTargetBranch)
                    if (result.isSuccess) {
                        commitMessageInput = ""
                        snackbarHostState.showSnackbar("Successfully pushed commit to GitHub (${result.getOrNull()?.take(7)})")
                    } else {
                        snackbarHostState.showSnackbar("Push failed: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
        )
    }
}

// ============================================================================
// Top Workspace Header Bar
// ============================================================================

@Composable
private fun GitHubWorkspaceHeader(
    selectedRepo: GitHubRepo?,
    selectedBranch: String,
    branches: List<GitHubBranch>,
    isReadOnly: Boolean,
    modifiedCount: Int,
    user: GitHubUser?,
    onBackToRepos: () -> Unit,
    onSelectBranch: (String) -> Unit,
    onCreateBranchClick: () -> Unit,
    onToggleReadOnly: (Boolean) -> Unit,
    onOpenCommitReview: () -> Unit,
    onRefresh: () -> Unit
) {
    val tokens = GlassTheme.tokens
    var branchMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = tokens.glassFill,
        shape = RoundedCornerShape(16.dp),
        border = tokens.glassBorderStroke,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (selectedRepo != null) {
                        IconButton(
                            onClick = onBackToRepos,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("github_back_to_repos_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Repositories",
                                tint = tokens.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Column {
                        Text(
                            text = selectedRepo?.name ?: "GitHub Repositories",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            ),
                            color = tokens.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (selectedRepo != null) {
                                "${selectedRepo.owner.login} • ${if (selectedRepo.isPrivate) "Private" else "Public"}"
                            } else {
                                user?.let { "@${it.login} (${it.publicRepos} public repos)" } ?: "Personal Access Token required"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = tokens.textSecondary,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Branch Selector Pill
                    if (selectedRepo != null) {
                        Box {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = tokens.glassFillStrong,
                                border = BorderStroke(1.dp, tokens.accentCyan.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .clickable { branchMenuExpanded = true }
                                    .testTag("branch_selector_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AltRoute,
                                        contentDescription = "Branch",
                                        tint = tokens.accentCyan,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = selectedBranch,
                                        style = CodeTextStyle.copy(fontSize = 11.5.sp),
                                        color = tokens.textPrimary,
                                        maxLines = 1
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = branchMenuExpanded,
                                onDismissRequest = { branchMenuExpanded = false }
                            ) {
                                branches.forEach { branch ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (branch.name == selectedBranch) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = tokens.accentCyan,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                }
                                                Text(branch.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                            }
                                        },
                                        onClick = {
                                            onSelectBranch(branch.name)
                                            branchMenuExpanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = tokens.accentViolet, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Create New Branch...", color = tokens.accentViolet, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    },
                                    onClick = {
                                        branchMenuExpanded = false
                                        onCreateBranchClick()
                                    }
                                )
                            }
                        }
                    }

                    // Refresh Button
                    IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = tokens.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Safety & Staging Toolbar Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Read-Only vs Write Toggle (Safe by default)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isReadOnly) tokens.glassFillStrong else tokens.accentViolet.copy(alpha = 0.15f),
                    border = if (isReadOnly) tokens.glassBorderStroke else BorderStroke(1.dp, tokens.accentViolet.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { onToggleReadOnly(!isReadOnly) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isReadOnly) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (isReadOnly) "Read-Only Mode Active" else "Write Mode Active",
                            tint = if (isReadOnly) tokens.textSecondary else tokens.accentViolet,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isReadOnly) "Read-Only" else "Write Enabled",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = if (isReadOnly) tokens.textSecondary else tokens.accentViolet
                        )
                    }
                }

                // Modified Files / Commit Action Button
                if (modifiedCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = tokens.accentCyan.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, tokens.accentCyan.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clickable { onOpenCommitReview() }
                            .testTag("review_commit_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = tokens.accentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$modifiedCount modified • Commit",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = tokens.accentCyan
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// No Token Configured View
// ============================================================================

@Composable
private fun NoTokenConfiguredView(onNavigateToSettings: () -> Unit) {
    val tokens = GlassTheme.tokens

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(tokens.accentViolet.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = tokens.accentViolet,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "GitHub Personal Access Token Required",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = tokens.textPrimary
                )

                Text(
                    text = "To browse repositories, edit files, and push commits, please configure a Personal Access Token in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = tokens.glassFillStrong,
                    border = BorderStroke(1.dp, tokens.accentCyan.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "💡 Recommended: Fine-Grained PAT",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = tokens.accentCyan
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Limit access to selected repositories with 'Repository permissions: Contents (Read and write)'. Tokens are encrypted with AES-256-GCM via Android KeyStore.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = tokens.textSecondary
                        )
                    }
                }

                GradientButton(
                    text = "Configure Token in Settings",
                    onClick = onNavigateToSettings,
                    leadingIcon = Icons.Default.Settings,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "configure_github_settings_button"
                )
            }
        }
    }
}

// ============================================================================
// Repository List View with Search
// ============================================================================

@Composable
private fun RepoListView(
    repos: List<GitHubRepo>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filterIndex: Int,
    onFilterIndexChange: (Int) -> Unit,
    isLoading: Boolean,
    onSelectRepo: (GitHubRepo) -> Unit,
    onRefresh: () -> Unit
) {
    val tokens = GlassTheme.tokens

    val filteredRepos = remember(repos, searchQuery, filterIndex) {
        repos.filter { repo ->
            val matchesQuery = searchQuery.isBlank() ||
                    repo.name.contains(searchQuery, ignoreCase = true) ||
                    (repo.description?.contains(searchQuery, ignoreCase = true) == true) ||
                    (repo.language?.contains(searchQuery, ignoreCase = true) == true)

            val matchesFilter = when (filterIndex) {
                1 -> !repo.isPrivate
                2 -> repo.isPrivate
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Search Bar with GlassTextField
        GlassTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = "Search repositories by name, language...",
            leadingIcon = Icons.Default.Search,
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .testTag("github_repo_search_input")
        )

        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PillChip(
                text = "All (${repos.size})",
                isSelected = filterIndex == 0,
                onClick = { onFilterIndexChange(0) },
                testTag = "filter_repos_all"
            )
            PillChip(
                text = "Public",
                isSelected = filterIndex == 1,
                onClick = { onFilterIndexChange(1) },
                testTag = "filter_repos_public"
            )
            PillChip(
                text = "Private",
                isSelected = filterIndex == 2,
                onClick = { onFilterIndexChange(2) },
                testTag = "filter_repos_private"
            )
        }

        if (isLoading && repos.isEmpty()) {
            ShimmerListSkeleton(
                itemCount = 4,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )
        } else if (filteredRepos.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Code,
                title = if (searchQuery.isNotBlank()) "No Matching Repositories" else "No Repositories Found",
                message = if (searchQuery.isNotBlank()) "No repositories matched \"$searchQuery\". Try adjusting filters." else "No repositories available on this account.",
                actionButtonText = "Refresh Repos",
                onActionClick = onRefresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredRepos, key = { it.id }) { repo ->
                    RepoCardItem(repo = repo, onClick = { onSelectRepo(repo) })
                }
            }
        }
    }
}

@Composable
private fun RepoCardItem(
    repo: GitHubRepo,
    onClick: () -> Unit
) {
    val tokens = GlassTheme.tokens

    GlassCard(
        onClick = onClick,
        testTag = "repo_card_${repo.name}"
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(tokens.accentBlue.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (repo.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                            contentDescription = null,
                            tint = tokens.accentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = repo.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            ),
                            color = tokens.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = repo.fullName,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = tokens.textSecondary
                        )
                    }
                }

                // Privacy Pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (repo.isPrivate) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (repo.isPrivate) Color(0xFFEF4444).copy(alpha = 0.4f) else Color(0xFF10B981).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = if (repo.isPrivate) "Private" else "Public",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (repo.isPrivate) Color(0xFFEF4444) else Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (!repo.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = tokens.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata row (Language, Stars, Forks)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!repo.language.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = tokens.accentViolet,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = repo.language,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                            color = tokens.textPrimary
                        )
                    }
                }

                if (repo.stargazersCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFEAB308), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = repo.stargazersCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                            color = tokens.textSecondary
                        )
                    }
                }

                Text(
                    text = "branch: ${repo.defaultBranch}",
                    style = CodeTextStyle.copy(fontSize = 11.sp),
                    color = tokens.textSecondary
                )
            }
        }
    }
}

// ============================================================================
// Repository Explorer & Tree View
// ============================================================================

@Composable
private fun RepoExplorerView(
    repo: GitHubRepo,
    treeRoot: GitHubTreeNode?,
    selectedBranch: String,
    isLoading: Boolean,
    modifiedFiles: Map<String, ModifiedFile>,
    onOpenFile: (GitHubTreeNode) -> Unit
) {
    val tokens = GlassTheme.tokens

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Breadcrumb & Status Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${repo.name} / $selectedBranch",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = tokens.accentViolet
            )

            if (modifiedFiles.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = tokens.accentCyan.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "${modifiedFiles.size} unsaved changes",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = tokens.accentCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (isLoading && (treeRoot == null || treeRoot.children.isEmpty())) {
            ShimmerListSkeleton(
                itemCount = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )
        } else if (treeRoot == null || treeRoot.children.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.FolderOpen,
                title = "Directory Empty",
                message = "No files or folders found in branch '$selectedBranch'.",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(treeRoot.children) { childNode ->
                    TreeNodeItem(
                        node = childNode,
                        depth = 0,
                        modifiedFiles = modifiedFiles,
                        onOpenFile = onOpenFile
                    )
                }
            }
        }
    }
}

@Composable
private fun TreeNodeItem(
    node: GitHubTreeNode,
    depth: Int,
    modifiedFiles: Map<String, ModifiedFile>,
    onOpenFile: (GitHubTreeNode) -> Unit
) {
    val tokens = GlassTheme.tokens
    var isExpanded by remember { mutableStateOf(node.isExpanded) }
    val isModified = modifiedFiles.containsKey(node.fullPath)

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isModified) tokens.accentCyan.copy(alpha = 0.12f) else tokens.glassFill,
            border = BorderStroke(
                1.dp,
                if (isModified) tokens.accentCyan.copy(alpha = 0.4f) else Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 14).dp, top = 2.dp, bottom = 2.dp)
                .clickable {
                    if (node.isDirectory) {
                        isExpanded = !isExpanded
                        node.isExpanded = isExpanded
                    } else {
                        onOpenFile(node)
                    }
                }
                .testTag("tree_node_${node.name}")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (node.isDirectory) {
                        if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder
                    } else {
                        Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = if (node.isDirectory) Color(0xFFFBBF24) else tokens.accentCyan,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = node.name,
                    style = CodeTextStyle.copy(
                        fontSize = 12.5.sp,
                        fontWeight = if (node.isDirectory) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isModified) tokens.accentCyan else tokens.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isModified) {
                    Surface(
                        shape = CircleShape,
                        color = tokens.accentCyan,
                        modifier = Modifier.size(7.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(6.dp))
                }

                if (!node.isDirectory && node.size > 0) {
                    Text(
                        text = formatFileSize(node.size),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = tokens.textSecondary
                    )
                }
            }
        }

        if (node.isDirectory && isExpanded) {
            node.children.forEach { subNode ->
                TreeNodeItem(
                    node = subNode,
                    depth = depth + 1,
                    modifiedFiles = modifiedFiles,
                    onOpenFile = onOpenFile
                )
            }
        }
    }
}

// ============================================================================
// File Viewer & Code Editor View
// ============================================================================

@Composable
private fun FileEditorView(
    file: GitHubContent,
    originalContent: String,
    draftContent: String,
    isReadOnly: Boolean,
    fontSize: Float,
    isLoading: Boolean,
    isAiGenerating: Boolean,
    onFontSizeChange: (Float) -> Unit,
    onContentChange: (String) -> Unit,
    onCloseFile: () -> Unit,
    onDiscardChanges: () -> Unit,
    onOpenCommitReview: () -> Unit = {},
    onAskAi: (String) -> Unit
) {
    val tokens = GlassTheme.tokens
    val isModified = originalContent != draftContent
    var aiInstruction by remember { mutableStateOf("") }
    var showAiBar by remember { mutableStateOf(false) }

    val extension = file.path.substringAfterLast('.', "")
    val lines = remember(draftContent) { draftContent.lines() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // File Title & Actions Bar
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = tokens.glassFill,
            border = tokens.glassBorderStroke,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onCloseFile, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close file", modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = file.name,
                            style = CodeTextStyle.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            color = tokens.textPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = file.path,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            color = tokens.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Zoom Out
                    IconButton(
                        onClick = { onFontSizeChange(fontSize - 1f) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Font size down", modifier = Modifier.size(16.dp), tint = tokens.textSecondary)
                    }

                    Text(
                        text = "${fontSize.toInt()}sp",
                        style = CodeTextStyle.copy(fontSize = 11.sp),
                        color = tokens.textSecondary
                    )

                    // Zoom In
                    IconButton(
                        onClick = { onFontSizeChange(fontSize + 1f) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Font size up", modifier = Modifier.size(16.dp), tint = tokens.textSecondary)
                    }

                    // Ask AI Toggle Button
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (showAiBar) tokens.accentViolet else tokens.accentViolet.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, tokens.accentViolet.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clickable { showAiBar = !showAiBar }
                            .padding(start = 4.dp)
                            .testTag("toggle_ask_ai_bar")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = if (showAiBar) Color.White else tokens.accentViolet, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ask AI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (showAiBar) Color.White else tokens.accentViolet)
                        }
                    }
                }
            }
        }

        // Unsaved Changes Bar
        if (isModified) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = tokens.accentCyan.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, tokens.accentCyan.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Unsaved changes in this file (Staged)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = tokens.accentCyan
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onDiscardChanges,
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Discard", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = tokens.accentViolet,
                            modifier = Modifier
                                .clickable { onOpenCommitReview() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("file_editor_review_commit_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Review & Commit", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Code Editor Box with Line Numbers
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = CodeBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 6.dp)
        ) {
            val horizontalScrollState = rememberScrollState()

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Line Numbers Gutter
                Column(
                    modifier = Modifier
                        .background(Color(0xFF0F1420))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    for (i in 1..lines.size) {
                        Text(
                            text = i.toString(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.5f).sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Code text field (Editable or Viewable)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScrollState)
                        .padding(10.dp)
                ) {
                    if (isReadOnly) {
                        val highlighted = remember(draftContent, extension) {
                            highlightSyntax(draftContent, extension)
                        }
                        Text(
                            text = highlighted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.5f).sp,
                            color = Color(0xFFF1F5F9)
                        )
                    } else {
                        BasicTextField(
                            value = draftContent,
                            onValueChange = onContentChange,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.5f).sp,
                                color = Color(0xFFF1F5F9)
                            ),
                            cursorBrush = SolidColor(tokens.accentCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("file_code_editor_textfield")
                        )
                    }
                }
            }
        }

        // "Ask AI" in-file assistance bar
        AnimatedVisibility(visible = showAiBar) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = tokens.glassFillStrong,
                border = BorderStroke(1.dp, tokens.accentViolet.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = tokens.accentViolet, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ask AI to edit this file", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = tokens.accentViolet)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick suggestion chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        val suggestions = listOf(
                            "Refactor to Coroutines",
                            "Add error handling",
                            "Fix null safety & bugs",
                            "Add unit tests",
                            "Optimize performance"
                        )
                        items(suggestions) { sug ->
                            AssistChip(
                                onClick = { aiInstruction = sug },
                                label = { Text(sug, fontSize = 10.5.sp) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassTextField(
                            value = aiInstruction,
                            onValueChange = { aiInstruction = it },
                            placeholder = "e.g. Refactor method to use StateFlow...",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ask_ai_instruction_input")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        GradientButton(
                            text = if (isAiGenerating) "Refactoring..." else "Propose",
                            onClick = {
                                if (aiInstruction.isNotBlank()) {
                                    onAskAi(aiInstruction)
                                }
                            },
                            enabled = aiInstruction.isNotBlank() && !isAiGenerating,
                            leadingIcon = if (isAiGenerating) null else Icons.AutoMirrored.Filled.Send,
                            testTag = "submit_ask_ai_button"
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// AI Diff Proposal Modal (Accept / Reject)
// ============================================================================

@Composable
private fun AiDiffProposalModal(
    filePath: String,
    diffItems: List<DiffItem>,
    onAccept: () -> Unit,
    onAcceptAndCommit: () -> Unit = onAccept,
    onReject: () -> Unit
) {
    val tokens = GlassTheme.tokens

    Dialog(onDismissRequest = onReject) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            fillColor = tokens.backgroundEnd.copy(alpha = 0.96f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Difference, contentDescription = null, tint = tokens.accentViolet, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("AI Proposed Diff", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = tokens.textPrimary)
                            Text(filePath, style = CodeTextStyle.copy(fontSize = 11.sp), color = tokens.textSecondary)
                        }
                    }

                    IconButton(onClick = onReject, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = tokens.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Review the proposed line changes. Green = Added, Red = Removed. Changes will never be applied silently.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Diff Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CodeBackground,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(diffItems) { item ->
                            val (bgColor, textColor) = when (item.type) {
                                DiffLineType.ADDED -> SemanticSuccess.copy(alpha = 0.16f) to SemanticSuccess
                                DiffLineType.REMOVED -> SemanticError.copy(alpha = 0.16f) to SemanticError
                                DiffLineType.HEADER -> tokens.accentCyan.copy(alpha = 0.12f) to tokens.accentCyan
                                DiffLineType.UNCHANGED -> Color.Transparent to Color(0xFFCBD5E1)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bgColor)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = (item.newLineNumber ?: item.oldLineNumber)?.toString()?.padStart(4) ?: "    ",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.width(36.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = item.text,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = textColor
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Accept / Commit / Reject Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(tokens.glassFill)
                            .border(tokens.glassBorderStroke, RoundedCornerShape(50))
                            .clickable(onClick = onReject)
                            .testTag("reject_ai_diff_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reject", color = tokens.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(tokens.glassFillStrong)
                            .border(BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)), RoundedCornerShape(50))
                            .clickable(onClick = onAccept)
                            .testTag("accept_ai_diff_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Accept", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    GradientButton(
                        text = "Accept & Commit",
                        onClick = onAcceptAndCommit,
                        leadingIcon = Icons.Default.CloudUpload,
                        gradient = Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))),
                        modifier = Modifier.weight(1.6f),
                        testTag = "accept_and_commit_ai_diff_button"
                    )
                }
            }
        }
    }
}

// ============================================================================
// Create New Branch Dialog
// ============================================================================

@Composable
private fun CreateBranchDialog(
    baseBranch: String,
    onDismiss: () -> Unit,
    onCreateBranch: (String) -> Unit
) {
    var branchName by remember { mutableStateOf("") }
    val tokens = GlassTheme.tokens

    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Create New Branch",
        icon = Icons.Default.AltRoute,
        iconTint = tokens.accentViolet,
        message = "Creating new branch based on: $baseBranch",
        confirmButtonText = "Create Branch",
        confirmButtonEnabled = branchName.isNotBlank(),
        onConfirm = { onCreateBranch(branchName.trim()) },
        confirmTestTag = "confirm_create_branch_button",
        dismissButtonText = "Cancel",
        content = {
            GlassTextField(
                value = branchName,
                onValueChange = { branchName = it },
                placeholder = "e.g. feature/ai-refactor",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new_branch_name_input")
            )
        }
    )
}

// ============================================================================
// Commit Review Sheet
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommitReviewSheet(
    selectedRepo: GitHubRepo?,
    selectedBranch: String,
    branches: List<GitHubBranch>,
    modifiedFiles: List<ModifiedFile>,
    commitMessage: String,
    isSuggestingAi: Boolean,
    isReadOnly: Boolean,
    onBranchChange: (String) -> Unit,
    onCreateBranch: () -> Unit,
    onCommitMessageChange: (String) -> Unit,
    onSuggestAiMessage: () -> Unit,
    onDiscardFile: (String) -> Unit,
    onDiscardAll: () -> Unit,
    onDismiss: () -> Unit,
    onProceedToPush: () -> Unit
) {
    val tokens = GlassTheme.tokens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var branchDropdownExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.backgroundEnd.copy(alpha = 0.96f),
        contentColor = tokens.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 44.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Review & Commit Changes",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                ),
                color = tokens.textPrimary
            )

            // Target Branch Selection
            Text(
                text = "Target Branch:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = tokens.textSecondary
            )
            Box {
                GlassCard(
                    onClick = { branchDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AltRoute, contentDescription = null, tint = tokens.accentCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(selectedBranch, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = tokens.textPrimary)
                        }
                        Icon(Icons.Default.Edit, contentDescription = "Change branch", tint = tokens.textSecondary, modifier = Modifier.size(16.dp))
                    }
                }

                DropdownMenu(
                    expanded = branchDropdownExpanded,
                    onDismissRequest = { branchDropdownExpanded = false },
                    modifier = Modifier
                        .background(tokens.backgroundEnd.copy(alpha = 0.96f))
                        .border(tokens.glassBorderStroke, RoundedCornerShape(12.dp))
                ) {
                    branches.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b.name, fontFamily = FontFamily.Monospace, color = tokens.textPrimary) },
                            onClick = {
                                onBranchChange(b.name)
                                branchDropdownExpanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("+ Create New Branch...", color = tokens.accentViolet, fontWeight = FontWeight.Bold) },
                        onClick = {
                            branchDropdownExpanded = false
                            onCreateBranch()
                        }
                    )
                }
            }

            // Commit Message Field with AI Suggestion
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Commit Message:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = tokens.textSecondary
                )

                TextButton(
                    onClick = onSuggestAiMessage,
                    enabled = !isSuggestingAi && modifiedFiles.isNotEmpty(),
                    modifier = Modifier.testTag("ai_suggest_commit_message_button")
                ) {
                    if (isSuggestingAi) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = tokens.accentCyan, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Generating...", fontSize = 11.sp, color = tokens.accentCyan)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = tokens.accentViolet, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Suggest with AI", fontSize = 11.sp, color = tokens.accentViolet)
                    }
                }
            }

            GlassTextField(
                value = commitMessage,
                onValueChange = onCommitMessageChange,
                placeholder = "e.g. feat: refactor core architecture to Jetpack Compose",
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("commit_message_input_field")
            )

            // Modified Files Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Changed Files (${modifiedFiles.size})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = tokens.textSecondary
                )
                if (modifiedFiles.isNotEmpty()) {
                    TextButton(onClick = onDiscardAll) {
                        Text("Discard All", color = SemanticError, fontSize = 11.5.sp)
                    }
                }
            }

            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    modifiedFiles.forEach { file ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = tokens.accentCyan, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(file.path, style = CodeTextStyle.copy(fontSize = 12.sp), color = tokens.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onDiscardFile(file.path) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Discard file changes", modifier = Modifier.size(14.dp), tint = SemanticError)
                            }
                        }
                    }
                }
            }

            if (isReadOnly) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    fillColor = SemanticError.copy(alpha = 0.15f),
                    borderStroke = BorderStroke(1.dp, SemanticError.copy(alpha = 0.5f))
                ) {
                    Row(modifier = Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = SemanticError, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GitHub is currently in Read-Only mode. Please toggle 'Write Enabled' in the top bar before pushing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SemanticError
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(tokens.glassFill)
                        .border(tokens.glassBorderStroke, RoundedCornerShape(50))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = tokens.textSecondary, fontWeight = FontWeight.Medium)
                }

                GradientButton(
                    text = "Push Changes",
                    onClick = onProceedToPush,
                    enabled = commitMessage.isNotBlank() && modifiedFiles.isNotEmpty() && !isReadOnly,
                    leadingIcon = Icons.Default.CloudUpload,
                    modifier = Modifier.weight(1f),
                    testTag = "proceed_to_push_button"
                )
            }
        }
    }
}

// ============================================================================
// Push Confirmation Dialog (Safety Guard)
// ============================================================================

@Composable
private fun PushConfirmationDialog(
    repo: GitHubRepo,
    branch: String,
    commitMessage: String,
    modifiedFiles: List<ModifiedFile>,
    onDismiss: () -> Unit,
    onConfirmPush: () -> Unit
) {
    val tokens = GlassTheme.tokens

    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Confirm Push to Remote",
        icon = Icons.Default.Security,
        iconTint = tokens.accentCyan,
        message = "You are about to commit and push changes directly to the remote repository:",
        confirmButtonText = "Confirm & Push",
        onConfirm = onConfirmPush,
        confirmTestTag = "confirm_push_dialog_button",
        dismissButtonText = "Cancel",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    fillColor = tokens.glassFillStrong
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("• Repository: ${repo.fullName}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = tokens.textPrimary)
                        Text("• Branch: $branch", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = tokens.accentCyan)
                        Text("• Commit: \"$commitMessage\"", fontSize = 12.sp, color = tokens.textPrimary)
                        Text("• Modified files (${modifiedFiles.size}):", fontSize = 12.sp, color = tokens.textSecondary)
                        modifiedFiles.forEach { f ->
                            Text("    - ${f.path}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = tokens.accentViolet)
                        }
                    }
                }

                Text(
                    text = "This action will update the remote git branch and cannot be undone locally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
        }
    )
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${String.format("%.1f", bytes / (1024.0 * 1024.0))} MB"
    }
}
