package com.example.ui.screens.settings

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import com.example.ui.components.GradientSwitch
import com.example.ui.components.PillChip
import com.example.ui.theme.GlassTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.AiProviderConfig
import com.example.domain.model.BackupRestoreException
import com.example.domain.model.CodeDraft
import com.example.domain.model.LastBackupInfo
import com.example.domain.model.ProviderType
import com.example.domain.model.RestoreMode
import com.example.domain.model.RestorePreviewSummary
import com.example.domain.model.VoiceActionType
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDialog
import com.example.ui.components.GlassOutlinedButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.GradientButton
import com.example.ui.components.ShimmerListSkeleton
import com.example.ui.theme.CodeTextStyle
import com.example.ui.theme.SemanticError
import com.example.ui.theme.SemanticSuccess
import com.example.ui.theme.SemanticWarning
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    providers: List<AiProviderConfig>,
    currentSystemPrompt: String,
    codeDrafts: List<CodeDraft>,
    biometricLockEnabled: Boolean,
    autoLockTimeoutMinutes: Int,
    redactionEnabled: Boolean,
    hasGitHubToken: Boolean,
    hasSupabaseToken: Boolean,
    supabaseUrl: String,
    gitHubUsername: String? = null,
    voiceActionSettings: Map<VoiceActionType, Boolean> = emptyMap(),
    onToggleVoiceAction: ((VoiceActionType, Boolean) -> Unit)? = null,
    onValidateGitHubToken: (suspend (String) -> Result<String>)? = null,
    onTestSupabaseConnection: (suspend (projectRef: String, token: String) -> Result<String>)? = null,
    onSaveProvider: (id: String?, name: String, type: ProviderType, model: String, baseUrl: String?, apiKey: String?, setAsDefault: Boolean) -> Unit,
    onDeleteProvider: (id: String) -> Unit,
    onTestConnection: suspend (type: ProviderType, model: String, baseUrl: String?, apiKey: String) -> Result<String>,
    onGetProviderApiKey: suspend (id: String) -> String?,
    onSaveSystemPrompt: (String) -> Unit,
    onResetSystemPrompt: () -> Unit,
    onDeleteCodeDraft: (id: String) -> Unit,
    onSetBiometricLockEnabled: (Boolean) -> Unit,
    onSetAutoLockTimeoutMinutes: (Int) -> Unit,
    onSetRedactionEnabled: (Boolean) -> Unit,
    onSaveGitHubToken: (String) -> Unit,
    onDeleteGitHubToken: () -> Unit,
    onSaveSupabaseCredentials: (url: String, token: String) -> Unit,
    onDeleteSupabaseCredentials: () -> Unit,
    onExportData: suspend () -> String,
    onDeleteEverything: suspend () -> Result<Unit>,
    lastBackupInfo: LastBackupInfo? = null,
    onCreateBackup: (suspend (passphrase: String) -> Result<String>)? = null,
    onRecordCompletedBackup: ((fileName: String, sizeBytes: Long) -> Unit)? = null,
    onInspectBackup: ((envelopeJson: String, passphrase: String) -> Result<RestorePreviewSummary>)? = null,
    onExecuteRestore: (suspend (summary: RestorePreviewSummary, mode: RestoreMode) -> Result<Unit>)? = null,
    onFetchGeminiModels: (suspend (apiKey: String) -> Result<List<String>>)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Requirement 4: FLAG_SECURE protection for screens showing secrets
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("AI Providers", "Tokens Vault", "Backup & Restore", "Security", "Privacy", "System Prompt", "Voice Actions", "Drafts")
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog states
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<AiProviderConfig?>(null) }
    var deletingProvider by remember { mutableStateOf<AiProviderConfig?>(null) }

    var showGitHubDialog by remember { mutableStateOf(false) }
    var showSupabaseDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var exportedJsonData by remember { mutableStateOf<String?>(null) }

    // Local Backup & Restore states & SAF launchers
    var showCreateBackupDialog by remember { mutableStateOf(false) }
    var pendingEncryptedBackupPayload by remember { mutableStateOf<String?>(null) }
    var showDecryptBackupDialog by remember { mutableStateOf(false) }
    var showPasteBackupDialog by remember { mutableStateOf(false) }
    var directPasteBackupContent by remember { mutableStateOf("") }
    var pendingFileContentToDecrypt by remember { mutableStateOf<String?>(null) }
    var showRestoreOptionsDialog by remember { mutableStateOf(false) }
    var restoreSummaryPreview by remember { mutableStateOf<RestorePreviewSummary?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && pendingEncryptedBackupPayload != null) {
            scope.launch {
                try {
                    val payload = pendingEncryptedBackupPayload!!
                    val bytes = payload.toByteArray(Charsets.UTF_8)
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(bytes)
                        outputStream.flush()
                    }
                    val timeStampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val fileName = "codepilot_backup_$timeStampStr.cpbackup"
                    onRecordCompletedBackup?.invoke(fileName, bytes.size.toLong())
                    pendingEncryptedBackupPayload = null
                    val formatted = if (bytes.size < 1024) "${bytes.size} B" else String.format(Locale.US, "%.1f KB", bytes.size / 1024.0)
                    snackbarHostState.showSnackbar("Encrypted backup saved successfully ($formatted)")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Error saving backup file: ${e.message}")
                }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (!content.isNullOrBlank()) {
                        pendingFileContentToDecrypt = content
                        showDecryptBackupDialog = true
                    } else {
                        snackbarHostState.showSnackbar("Selected backup file is empty.")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Could not read backup file: ${e.message}")
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("settings_tab_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(tabs) { index, title ->
                    PillChip(
                        text = title,
                        isSelected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> {
                    AiProvidersTab(
                        providers = providers,
                        onAddClick = {
                            editingProvider = null
                            showEditorDialog = true
                        },
                        onEditClick = { provider ->
                            editingProvider = provider
                            showEditorDialog = true
                        },
                        onDeleteClick = { provider ->
                            deletingProvider = provider
                        }
                    )
                }
                1 -> {
                    TokensVaultTab(
                        hasGitHubToken = hasGitHubToken,
                        hasSupabaseToken = hasSupabaseToken,
                        supabaseUrl = supabaseUrl,
                        gitHubUsername = gitHubUsername,
                        onConfigureGitHub = { showGitHubDialog = true },
                        onDeleteGitHub = {
                            onDeleteGitHubToken()
                            scope.launch { snackbarHostState.showSnackbar("GitHub token deleted from Keystore") }
                        },
                        onConfigureSupabase = { showSupabaseDialog = true },
                        onDeleteSupabase = {
                            onDeleteSupabaseCredentials()
                            scope.launch { snackbarHostState.showSnackbar("Supabase credentials deleted from Keystore") }
                        }
                    )
                }
                2 -> {
                    BackupAndRestoreTab(
                        lastBackupInfo = lastBackupInfo,
                        onCreateBackupClick = { showCreateBackupDialog = true },
                        onRestoreBackupClick = {
                            openDocumentLauncher.launch(arrayOf("*/*"))
                        },
                        onPasteBackupClick = {
                            directPasteBackupContent = ""
                            showPasteBackupDialog = true
                        }
                    )
                }
                3 -> {
                    SecurityTab(
                        biometricLockEnabled = biometricLockEnabled,
                        autoLockTimeoutMinutes = autoLockTimeoutMinutes,
                        onToggleBiometricLock = onSetBiometricLockEnabled,
                        onSelectTimeout = onSetAutoLockTimeoutMinutes
                    )
                }
                4 -> {
                    PrivacyTab(
                        redactionEnabled = redactionEnabled,
                        onToggleRedaction = onSetRedactionEnabled,
                        onExportClick = {
                            scope.launch {
                                val json = onExportData()
                                exportedJsonData = json
                            }
                        },
                        onDeleteEverythingClick = {
                            showDeleteAllDialog = true
                        }
                    )
                }
                5 -> {
                    SystemPromptTab(
                        currentPrompt = currentSystemPrompt,
                        onSavePrompt = { prompt ->
                            onSaveSystemPrompt(prompt)
                            scope.launch {
                                snackbarHostState.showSnackbar("System prompt updated successfully")
                            }
                        },
                        onResetPrompt = {
                            onResetSystemPrompt()
                            scope.launch {
                                snackbarHostState.showSnackbar("System prompt reset to default")
                            }
                        }
                    )
                }
                6 -> {
                    VoiceActionsTab(
                        voiceSettings = voiceActionSettings,
                        onToggleAction = onToggleVoiceAction
                    )
                }
                7 -> {
                    SavedDraftsTab(
                        drafts = codeDrafts,
                        onDeleteDraft = onDeleteCodeDraft
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    // Provider Editor Dialog (Add / Edit)
    if (showEditorDialog) {
        ProviderEditorDialog(
            initialProvider = editingProvider,
            onDismiss = { showEditorDialog = false },
            onGetApiKey = onGetProviderApiKey,
            onTestConnection = onTestConnection,
            onFetchGeminiModels = onFetchGeminiModels,
            onSave = { id, name, type, model, baseUrl, apiKey, setAsDefault ->
                onSaveProvider(id, name, type, model, baseUrl, apiKey, setAsDefault)
                showEditorDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("Provider '$name' saved to KeyStore")
                }
            }
        )
    }

    // Delete Provider Confirmation Dialog
    if (deletingProvider != null) {
        val targetProvider = deletingProvider ?: return
        GlassDialog(
            onDismissRequest = { deletingProvider = null },
            title = "Delete AI Provider",
            icon = Icons.Default.Warning,
            iconTint = SemanticError,
            message = "Are you sure you want to delete '${targetProvider.name}'? Its configuration and encrypted API key will be permanently removed from this device.",
            confirmButtonText = "Delete Provider",
            isDestructive = true,
            onConfirm = {
                onDeleteProvider(targetProvider.id)
                deletingProvider = null
                scope.launch {
                    snackbarHostState.showSnackbar("Provider deleted")
                }
            },
            confirmTestTag = "confirm_delete_provider_button",
            dismissButtonText = "Cancel"
        )
    }

    // GitHub Token Dialog
    if (showGitHubDialog) {
        GitHubTokenDialog(
            currentUsername = gitHubUsername,
            onDismiss = { showGitHubDialog = false },
            onValidateToken = onValidateGitHubToken,
            onSave = { token ->
                onSaveGitHubToken(token)
                showGitHubDialog = false
                scope.launch { snackbarHostState.showSnackbar("GitHub token saved to KeyStore") }
            }
        )
    }

    // Supabase Credentials Dialog
    if (showSupabaseDialog) {
        SupabaseCredentialsDialog(
            currentUrl = supabaseUrl,
            onDismiss = { showSupabaseDialog = false },
            onTestConnection = onTestSupabaseConnection,
            onSave = { url, token ->
                onSaveSupabaseCredentials(url, token)
                showSupabaseDialog = false
                scope.launch { snackbarHostState.showSnackbar("Supabase credentials saved to KeyStore") }
            }
        )
    }

    // Export Data Result Dialog
    exportedJsonData?.let { json ->
        DataExportDialog(
            jsonData = json,
            onDismiss = { exportedJsonData = null }
        )
    }

    // Delete Everything Confirmation Dialog (Typed confirmation)
    if (showDeleteAllDialog) {
        DeleteEverythingDialog(
            onDismiss = { showDeleteAllDialog = false },
            onConfirmDelete = {
                scope.launch {
                    val result = onDeleteEverything()
                    showDeleteAllDialog = false
                    if (result.isSuccess) {
                        snackbarHostState.showSnackbar("All database records, Keystore keys, and settings wiped.")
                    } else {
                        snackbarHostState.showSnackbar("Error during wipe: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
        )
    }

    // Create Backup Passphrase Dialog
    if (showCreateBackupDialog) {
        CreateBackupPassphraseDialog(
            onDismiss = { showCreateBackupDialog = false },
            onCreateBackup = onCreateBackup,
            onProceedToSave = { encryptedPayload ->
                showCreateBackupDialog = false
                pendingEncryptedBackupPayload = encryptedPayload
                val timeStampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val suggestedFileName = "codepilot_backup_$timeStampStr.cpbackup"
                createDocumentLauncher.launch(suggestedFileName)
            }
        )
    }

    // Direct Paste Backup Dialog
    if (showPasteBackupDialog) {
        PasteBackupDialog(
            onDismiss = { showPasteBackupDialog = false },
            onProceedToDecrypt = { pastedText ->
                showPasteBackupDialog = false
                pendingFileContentToDecrypt = pastedText
                showDecryptBackupDialog = true
            }
        )
    }

    // Decrypt Backup Dialog
    if (showDecryptBackupDialog && pendingFileContentToDecrypt != null) {
        DecryptBackupDialog(
            fileContent = pendingFileContentToDecrypt!!,
            onDismiss = {
                showDecryptBackupDialog = false
                pendingFileContentToDecrypt = null
            },
            onInspectBackup = onInspectBackup,
            onDecrypted = { summary ->
                showDecryptBackupDialog = false
                pendingFileContentToDecrypt = null
                restoreSummaryPreview = summary
                showRestoreOptionsDialog = true
            }
        )
    }

    // Restore Options Dialog (Merge vs Replace)
    if (showRestoreOptionsDialog && restoreSummaryPreview != null) {
        RestoreOptionsDialog(
            summary = restoreSummaryPreview!!,
            onDismiss = {
                showRestoreOptionsDialog = false
                restoreSummaryPreview = null
            },
            onExecuteRestore = { summary, mode ->
                scope.launch {
                    val res = onExecuteRestore?.invoke(summary, mode)
                    showRestoreOptionsDialog = false
                    restoreSummaryPreview = null
                    if (res != null && res.isSuccess) {
                        snackbarHostState.showSnackbar("Backup restored successfully (${summary.conversationCount} conversations imported)")
                    } else {
                        snackbarHostState.showSnackbar("Restore error: ${res?.exceptionOrNull()?.message ?: "Unknown error"}")
                    }
                }
            }
        )
    }
}

// ==========================================
// Backup & Restore Tab (Local Encrypted Backups)
// ==========================================

@Composable
private fun BackupAndRestoreTab(
    lastBackupInfo: LastBackupInfo?,
    onCreateBackupClick: () -> Unit,
    onRestoreBackupClick: () -> Unit,
    onPasteBackupClick: () -> Unit = {}
) {
    val tokens = GlassTheme.tokens

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Local Backup & Restore",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = tokens.textPrimary
        )

        // Note: Cloud sync is not enabled yet
        GlassCard(
            modifier = Modifier.fillMaxWidth().testTag("cloud_sync_notice_card"),
            fillColor = tokens.accentViolet.copy(alpha = 0.12f),
            borderStroke = BorderStroke(1.dp, tokens.accentViolet.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(tokens.accentViolet.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = tokens.accentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cloud sync is not enabled yet",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = tokens.textPrimary
                    )
                    Text(
                        text = "Open Buddy operates fully local-first. Backups are encrypted with AES-256-GCM using your custom passphrase and saved directly to your device storage or chosen document provider.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                }
            }
        }

        // Last Backup Info Status Card
        GlassCard(
            modifier = Modifier.fillMaxWidth().testTag("last_backup_status_card")
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Backup Status",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = tokens.accentViolet
                )

                if (lastBackupInfo != null) {
                    val dateFormatted = remember(lastBackupInfo.timestamp) {
                        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
                        sdf.format(Date(lastBackupInfo.timestamp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Last backup: $dateFormatted",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = tokens.textPrimary
                            )
                            Text(
                                text = "Size: ${lastBackupInfo.formattedSize}  •  File: ${lastBackupInfo.fileName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = tokens.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "No backups created yet on this device",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = tokens.textPrimary
                            )
                            Text(
                                text = "Tap 'Create Backup File' below to generate your first encrypted backup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // Create Backup Action Card
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = tokens.accentViolet,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Create Encrypted Backup",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = tokens.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Serializes your conversations, messages, saved code drafts, SQL query history, AI provider configs, and preferences into an AES-256-GCM encrypted file protected by a custom passphrase. All API keys and secrets are strictly excluded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                GradientButton(
                    text = "Create Backup File",
                    onClick = onCreateBackupClick,
                    leadingIcon = Icons.Default.Download,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "create_backup_file_button"
                )
            }
        }

        // Restore Backup Action Card
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = tokens.accentCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Restore from Backup",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = tokens.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Select a previously created Open Buddy backup file (.cpbackup or .json) via the system file picker. After entering the passphrase, you can choose to Merge with existing data or Replace everything.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(tokens.glassFill)
                        .border(tokens.glassBorderStroke, RoundedCornerShape(50))
                        .clickable(onClick = onRestoreBackupClick)
                        .testTag("restore_from_backup_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = tokens.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Restore from Backup File",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.textPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(tokens.glassFill)
                        .border(tokens.glassBorderStroke, RoundedCornerShape(50))
                        .clickable(onClick = onPasteBackupClick)
                        .testTag("paste_backup_envelope_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = tokens.accentViolet,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Paste Backup Envelope Directly",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.accentViolet
                            )
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// Dialog: Paste Backup Text Directly
// ==========================================

@Composable
private fun PasteBackupDialog(
    onDismiss: () -> Unit,
    onProceedToDecrypt: (String) -> Unit
) {
    var pastedText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val tokens = GlassTheme.tokens

    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Paste Encrypted Backup",
        icon = Icons.Default.ContentCopy,
        iconTint = tokens.accentViolet,
        message = "Paste your previously exported Open Buddy backup envelope JSON below to decrypt and inspect its contents.",
        confirmButtonText = "Proceed to Decrypt",
        confirmButtonEnabled = pastedText.isNotBlank(),
        onConfirm = {
            val trimmed = pastedText.trim()
            if (trimmed.isBlank()) {
                errorMessage = "Please paste the backup envelope JSON."
                return@GlassDialog
            }
            onProceedToDecrypt(trimmed)
        },
        confirmTestTag = "confirm_paste_backup_button",
        dismissButtonText = "Cancel",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassTextField(
                    value = pastedText,
                    onValueChange = {
                        pastedText = it
                        errorMessage = null
                    },
                    placeholder = "Paste { \"magic\": \"CP_BACKUP_V1\", ... } here",
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paste_backup_content_input")
                )

                errorMessage?.let { err ->
                    Text(text = err, color = SemanticError, fontSize = 12.sp)
                }
            }
        }
    )
}

// ==========================================
// Dialog: Create Backup Passphrase Input
// ==========================================

@Composable
private fun CreateBackupPassphraseDialog(
    onDismiss: () -> Unit,
    onCreateBackup: (suspend (passphrase: String) -> Result<String>)?,
    onProceedToSave: (encryptedPayload: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var isPassphraseVisible by remember { mutableStateOf(false) }
    var isEncrypting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Encrypt Backup File",
        icon = Icons.Default.Lock,
        iconTint = GlassTheme.tokens.accentViolet,
        message = "Enter a strong passphrase to encrypt your backup with AES-256-GCM. Open Buddy does not store this passphrase anywhere on the device.",
        confirmButtonText = if (isEncrypting) "Encrypting..." else "Proceed to Save",
        confirmButtonEnabled = passphrase.isNotBlank() && confirmPassphrase.isNotBlank() && !isEncrypting,
        onConfirm = {
            if (passphrase.isBlank()) {
                errorMessage = "Passphrase cannot be blank."
                return@GlassDialog
            }
            if (passphrase != confirmPassphrase) {
                errorMessage = "Passphrases do not match. Please recheck."
                return@GlassDialog
            }

            isEncrypting = true
            errorMessage = null
            scope.launch {
                val result = onCreateBackup?.invoke(passphrase.trim())
                isEncrypting = false
                if (result != null && result.isSuccess) {
                    onProceedToSave(result.getOrThrow())
                } else {
                    errorMessage = result?.exceptionOrNull()?.message ?: "Encryption failed."
                }
            }
        },
        confirmTestTag = "confirm_create_backup_button",
        dismissButtonText = "Cancel",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Warning banner
                GlassCard(
                    modifier = Modifier.fillMaxWidth().testTag("backup_recovery_warning_banner"),
                    fillColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    borderStroke = BorderStroke(1.dp, SemanticError.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = SemanticError,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "If you forget this passphrase, the backup cannot be recovered.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = SemanticError
                        )
                    }
                }

                GlassTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        errorMessage = null
                    },
                    placeholder = "Enter backup passphrase",
                    visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPassphraseVisible = !isPassphraseVisible }) {
                            Icon(
                                imageVector = if (isPassphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPassphraseVisible) "Hide passphrase" else "Show passphrase",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_passphrase_input")
                )

                GlassTextField(
                    value = confirmPassphrase,
                    onValueChange = {
                        confirmPassphrase = it
                        errorMessage = null
                    },
                    placeholder = "Confirm backup passphrase",
                    visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_confirm_passphrase_input")
                )

                errorMessage?.let { err ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        fillColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                        borderStroke = BorderStroke(1.dp, SemanticError.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = SemanticError, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = err, color = SemanticError, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }
    )
}

// ==========================================
// Dialog: Decrypt Backup Passphrase Input
// ==========================================

@Composable
private fun DecryptBackupDialog(
    fileContent: String,
    onDismiss: () -> Unit,
    onInspectBackup: ((envelopeJson: String, passphrase: String) -> Result<RestorePreviewSummary>)?,
    onDecrypted: (RestorePreviewSummary) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var isPassphraseVisible by remember { mutableStateOf(false) }
    var isDecrypting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Decrypt Backup",
        icon = Icons.Default.Key,
        iconTint = GlassTheme.tokens.accentViolet,
        message = "Enter the passphrase you used when creating this backup file to decrypt and inspect its contents.",
        confirmButtonText = if (isDecrypting) "Decrypting..." else "Decrypt & Inspect",
        confirmButtonEnabled = passphrase.isNotBlank() && !isDecrypting,
        onConfirm = {
            if (passphrase.isBlank()) {
                errorMessage = "Passphrase cannot be blank."
                return@GlassDialog
            }
            isDecrypting = true
            errorMessage = null
            val res = onInspectBackup?.invoke(fileContent, passphrase.trim())
            isDecrypting = false
            if (res != null && res.isSuccess) {
                onDecrypted(res.getOrThrow())
            } else {
                errorMessage = when (val ex = res?.exceptionOrNull()) {
                    is BackupRestoreException.WrongPassphrase -> "Incorrect passphrase. Decryption failed."
                    is BackupRestoreException.CorruptedFile -> "The backup file is corrupted or not a valid Open Buddy backup."
                    is BackupRestoreException.IncompatibleVersion -> ex.message
                    else -> ex?.message ?: "Decryption failed."
                }
            }
        },
        confirmTestTag = "confirm_decrypt_backup_button",
        dismissButtonText = "Cancel",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        errorMessage = null
                    },
                    placeholder = "Enter backup passphrase",
                    visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPassphraseVisible = !isPassphraseVisible }) {
                            Icon(
                                imageVector = if (isPassphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPassphraseVisible) "Hide passphrase" else "Show passphrase",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("decrypt_passphrase_input")
                )

                errorMessage?.let { err ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().testTag("decrypt_error_banner"),
                        fillColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                        borderStroke = BorderStroke(1.dp, SemanticError.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = SemanticError, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = err, color = SemanticError, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    )
}

// ==========================================
// Dialog: Restore Options (Merge vs Replace)
// ==========================================

@Composable
private fun RestoreOptionsDialog(
    summary: RestorePreviewSummary,
    onDismiss: () -> Unit,
    onExecuteRestore: (RestorePreviewSummary, RestoreMode) -> Unit
) {
    var selectedMode by remember { mutableStateOf(RestoreMode.MERGE) }
    var isRestoring by remember { mutableStateOf(false) }

    val formattedDate = remember(summary.backupCreatedAt) {
        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        sdf.format(Date(summary.backupCreatedAt))
    }
    val tokens = GlassTheme.tokens

    GlassDialog(
        onDismissRequest = { if (!isRestoring) onDismiss() },
        title = "Backup Verified",
        icon = Icons.Default.CheckCircle,
        iconTint = Color(0xFF10B981),
        message = "Backup successfully decrypted! Review the contents below and choose how you would like to import the data.",
        confirmButtonText = if (isRestoring) "Restoring..." else if (selectedMode == RestoreMode.MERGE) "Merge and Restore" else "Replace and Restore",
        confirmButtonEnabled = !isRestoring,
        onConfirm = {
            isRestoring = true
            onExecuteRestore(summary, selectedMode)
        },
        confirmTestTag = "confirm_execute_restore_button",
        dismissButtonText = "Cancel",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Backup Stats Summary Card
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restore_preview_stats_card")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Backup Created: $formattedDate",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = tokens.accentViolet
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "• Conversations: ${summary.conversationCount}", style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                        Text(text = "• Messages: ${summary.messageCount}", style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                        Text(text = "• Saved Code Drafts: ${summary.draftCount}", style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                        Text(text = "• SQL Query History: ${summary.queryHistoryCount}", style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                        Text(text = "• AI Providers: ${summary.providerCount}", style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                    }
                }

                Text(
                    text = "Choose Restore Strategy:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = tokens.textPrimary
                )

                // Merge Option
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMode = RestoreMode.MERGE }
                        .testTag("restore_mode_merge_option"),
                    borderStroke = if (selectedMode == RestoreMode.MERGE) BorderStroke(1.dp, tokens.accentViolet) else tokens.glassBorderStroke
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == RestoreMode.MERGE,
                            onClick = { selectedMode = RestoreMode.MERGE }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Merge Data (Recommended)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = tokens.textPrimary
                            )
                            Text(
                                text = "Keep existing conversations on this device and add new records from the backup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                        }
                    }
                }

                // Replace Option (Destructive)
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMode = RestoreMode.REPLACE }
                        .testTag("restore_mode_replace_option"),
                    fillColor = if (selectedMode == RestoreMode.REPLACE) SemanticError.copy(alpha = 0.15f) else tokens.glassFill,
                    borderStroke = if (selectedMode == RestoreMode.REPLACE) BorderStroke(1.dp, SemanticError) else tokens.glassBorderStroke
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == RestoreMode.REPLACE,
                            onClick = { selectedMode = RestoreMode.REPLACE }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Replace Everything (Destructive)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (selectedMode == RestoreMode.REPLACE) SemanticError else tokens.textPrimary
                            )
                            Text(
                                text = "Wipe current conversations, messages, drafts, and query history, and replace completely with the backup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                        }
                    }
                }
            }
        }
    )
}

// ==========================================
// Security Tab (Biometric Lock & Encryption)
// ==========================================

@Composable
private fun SecurityTab(
    biometricLockEnabled: Boolean,
    autoLockTimeoutMinutes: Int,
    onToggleBiometricLock: (Boolean) -> Unit,
    onSelectTimeout: (Int) -> Unit
) {
    val tokens = GlassTheme.tokens

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "App & Key Security",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = tokens.textPrimary
        )

        // Biometric Lock Card
        GlassCard(
            modifier = Modifier.fillMaxWidth()
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
                        Surface(
                            shape = CircleShape,
                            color = tokens.accentViolet.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, tokens.accentViolet.copy(alpha = 0.4f)),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = tokens.accentViolet,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Biometric App Lock",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = tokens.textPrimary
                            )
                            Text(
                                text = "BiometricPrompt with device-credential fallback",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                        }
                    }

                    GradientSwitch(
                        checked = biometricLockEnabled,
                        onCheckedChange = onToggleBiometricLock,
                        modifier = Modifier.testTag("biometric_lock_toggle")
                    )
                }

                AnimatedVisibility(visible = biometricLockEnabled) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = "Auto-Lock Timeout",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.accentViolet
                        )
                        Text(
                            text = "Lock app on start and after device is in background for:",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val timeouts = listOf(1 to "1 minute", 5 to "5 minutes", 15 to "15 minutes")
                        timeouts.forEach { (mins, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectTimeout(mins) }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = autoLockTimeoutMinutes == mins,
                                    onClick = { onSelectTimeout(mins) },
                                    modifier = Modifier.testTag("timeout_radio_$mins")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = tokens.textPrimary,
                                    fontWeight = if (autoLockTimeoutMinutes == mins) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        // Encryption Overview Card
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Security Architecture",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = tokens.textPrimary
                )

                // Keystore Vault Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Android Keystore (AES-256-GCM)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.textPrimary
                        )
                        Text(
                            text = "Hardware-backed key encryption for all API keys, GitHub tokens, and Supabase credentials.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }

                // SQLCipher Database Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SQLCipher Room Encryption",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.textPrimary
                        )
                        Text(
                            text = "Full database encryption at rest using a 256-bit passphrase stored inside the Keystore.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }

                // Screen Capture Protection
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FLAG_SECURE Active",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.textPrimary
                        )
                        Text(
                            text = "Screenshots blocked and content hidden in recent-apps switcher when viewing secrets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }

                // Cloud Backup Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Backup Disabled (allowBackup=\"false\")",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.textPrimary
                        )
                        Text(
                            text = "Prevents keys or local databases from being synced to external Google cloud backups.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// Privacy Tab (Plain English Privacy, Google Play Data Safety, Redaction, Export, Wipe)
// ==========================================

@Composable
private fun PrivacyTab(
    redactionEnabled: Boolean,
    onToggleRedaction: (Boolean) -> Unit,
    onExportClick: () -> Unit,
    onDeleteEverythingClick: () -> Unit
) {
    val tokens = GlassTheme.tokens
    val clipboardManager = LocalClipboardManager.current
    var playSafetyCopied by remember { mutableStateOf(false) }

    val playSafetySummaryText = """
GOOGLE PLAY DATA SAFETY FORM - SUMMARY FOR CODEPILOT AI

1. Data Collection & Sharing Overview:
   - Does your app collect or share user data? NO data is collected by the developer or shared with third-party advertisers.
   - User Data leaves the device ONLY for user-initiated AI queries sent directly via HTTPS to the user-chosen AI service provider (e.g., Google Gemini, Anthropic Claude, OpenAI).

2. Data Types:
   - User Prompts & Messages: Ephemeral transmission directly to the AI provider configured by the user with their own API key. Stored locally on device in an encrypted database.
   - Personal Info / Secrets / Passwords: Stored exclusively on device in Android KeyStore (AES-256-GCM). NEVER transmitted to any analytics or developer servers.
   - Audio / Voice: Audio is processed on device using Android SpeechRecognizer. Audio recordings are NOT stored or uploaded to developer servers.

3. Security Practices:
   - Data encrypted in transit? YES (HTTPS TLS 1.3 only).
   - Data encrypted at rest? YES (Android KeyStore hardware encryption + SQLCipher 256-bit database).
   - Can users delete their data? YES (In-app 1-tap complete permanent wipe and data export).

4. Permissions Declared & Purpose:
   - android.permission.INTERNET: For direct HTTPS calls to user-configured AI APIs, GitHub, and Supabase.
   - android.permission.ACCESS_NETWORK_STATE: For offline detection and connection health.
   - android.permission.USE_BIOMETRIC: For local app lock authentication.
   - android.permission.RECORD_AUDIO: For speech-to-text voice automator (requested only on user mic tap).
   - Zero telephony permissions (No CALL_PHONE, No SEND_SMS).
   - Zero Accessibility service permissions.
   - Zero advertising, tracking, or telemetry libraries.
""".trimIndent()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Data Privacy & Governance",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = tokens.textPrimary
        )

        // 1. What Stays on Device Card
        GlassCard(
            modifier = Modifier.fillMaxWidth().testTag("privacy_stays_on_device_card"),
            fillColor = Color(0xFF10B981).copy(alpha = 0.08f),
            borderStroke = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "What Stays On Your Device (100% Local)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = tokens.textPrimary
                    )
                }

                Text(
                    text = "• Local Encrypted Database: All your conversations, messages, SQL queries, and saved code drafts are stored exclusively in an encrypted Room SQLite database on your device.\n" +
                           "• Hardware KeyStore Vault: Your API keys (Gemini, Claude, OpenAI, custom), GitHub tokens, and Supabase credentials are encrypted using hardware-backed Android KeyStore (AES-256-GCM). They never leave your device except to authorize direct requests to those specific services.\n" +
                           "• Biometrics: Biometric authentication (fingerprint / face unlock) is handled strictly by the Android OS Biometric subsystem.\n" +
                           "• Zero Tracking: No analytics, no ads, no crash beacons, and no tracking SDKs.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = tokens.textSecondary
                )
            }
        }

        // 2. What Leaves the Device Card
        GlassCard(
            modifier = Modifier.fillMaxWidth().testTag("privacy_leaves_device_card"),
            fillColor = tokens.accentViolet.copy(alpha = 0.08f),
            borderStroke = BorderStroke(1.dp, tokens.accentViolet.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(tokens.accentViolet.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = tokens.accentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "What Leaves Your Device",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = tokens.textPrimary
                    )
                }

                Text(
                    text = "• AI Prompts & Context: Only when you send a message, your prompt and active conversation context are transmitted directly over HTTPS to the AI service provider you configured (Google Gemini, Anthropic Claude, OpenAI, or your custom server).\n" +
                           "• Direct Client-to-API Calls: Open Buddy does not use any developer proxy server. All API requests travel directly between your phone and your configured provider.\n" +
                           "• GitHub & Supabase: Git repository operations and SQL queries travel directly to api.github.com and your Supabase project endpoint.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = tokens.textSecondary
                )
            }
        }

        // 3. What Backup & Restore Stores Card
        GlassCard(
            modifier = Modifier.fillMaxWidth().testTag("privacy_backup_info_card"),
            fillColor = tokens.accentCyan.copy(alpha = 0.08f),
            borderStroke = BorderStroke(1.dp, tokens.accentCyan.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(tokens.accentCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            tint = tokens.accentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "What \"Backup & Restore\" Stores",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = tokens.textPrimary
                    )
                }

                Text(
                    text = "• Local File Only: Backups are serialized into an AES-256-GCM encrypted file (.cpbackup or .json) protected by a passphrase you choose.\n" +
                           "• Zero Cloud Uploads: Nothing is uploaded to any cloud backup or remote server. The file is saved strictly to your local storage or document provider via Android Storage Access Framework.\n" +
                           "• Secrets Omitted: API keys, GitHub tokens, and Supabase credentials are automatically excluded from all backup files for maximum security.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = tokens.textSecondary
                )
            }
        }

        // 4. In-App Google Play Data Safety Summary (Copy Tool)
        GlassCard(
            modifier = Modifier.fillMaxWidth().testTag("google_play_data_safety_card")
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Google Play Data Safety Summary",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = tokens.textPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(tokens.accentViolet.copy(alpha = 0.2f))
                            .border(BorderStroke(1.dp, tokens.accentViolet.copy(alpha = 0.4f)), RoundedCornerShape(50))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(playSafetySummaryText))
                                playSafetyCopied = true
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("copy_play_data_safety_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (playSafetyCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy Data Safety Summary",
                                tint = if (playSafetyCopied) Color(0xFF10B981) else tokens.accentCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (playSafetyCopied) "Copied!" else "Copy Summary",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (playSafetyCopied) Color(0xFF10B981) else tokens.accentCyan
                            )
                        }
                    }
                }

                Text(
                    text = "Use this verified summary to complete your Google Play Console Data Safety questionnaire in seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0D1117),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = playSafetySummaryText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = Color(0xFFE6EDF3),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // 5. Redaction Card
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sensitive Data Redaction",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = tokens.textPrimary
                        )
                        Text(
                            text = "Automatically masks emails, phone numbers, API keys, and tokens before transmitting prompts to AI providers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }

                    GradientSwitch(
                        checked = redactionEnabled,
                        onCheckedChange = onToggleRedaction,
                        modifier = Modifier.testTag("redaction_toggle")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0D1117),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Example Transformation:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF8B949E)
                        )
                        Text(
                            text = "Input:  \"Debug key sk-123456... for test@example.com\"",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFE6EDF3)
                        )
                        Text(
                            text = "Output: \"Debug key [REDACTED_API_KEY] for [REDACTED_EMAIL]\"",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF7EE787)
                        )
                    }
                }
            }
        }

        // 6. Export Data Card
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Export My Data",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = tokens.textPrimary
                )
                Text(
                    text = "Export your chat history, conversations, code drafts, and non-sensitive settings into a clean JSON document. Zero secrets or keys are exported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                GradientButton(
                    text = "Export Data (JSON)",
                    onClick = onExportClick,
                    leadingIcon = Icons.Default.Download,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_data_button")
                )
            }
        }

        // 7. Delete Everything Card (Danger Zone)
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            fillColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
            borderStroke = BorderStroke(1.dp, SemanticError.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = SemanticError,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Permanent Wipe",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = SemanticError
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Permanently deletes the entire encrypted database, all conversations, messages, drafts, Android Keystore keys, GitHub tokens, Supabase credentials, and device settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                GradientButton(
                    text = "Delete Everything",
                    onClick = onDeleteEverythingClick,
                    leadingIcon = Icons.Default.Delete,
                    gradient = Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626))),
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "delete_everything_button"
                )
            }
        }
    }
}

// ==========================================
// Tokens Vault Tab (GitHub, Supabase)
// ==========================================

@Composable
private fun TokensVaultTab(
    hasGitHubToken: Boolean,
    hasSupabaseToken: Boolean,
    supabaseUrl: String,
    gitHubUsername: String? = null,
    onConfigureGitHub: () -> Unit,
    onDeleteGitHub: () -> Unit,
    onConfigureSupabase: () -> Unit,
    onDeleteSupabase: () -> Unit
) {
    val tokens = GlassTheme.tokens

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Developer Tokens Vault",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = tokens.textPrimary
        )
        Text(
            text = "Store your developer tokens encrypted at rest via AES-256-GCM in the Android Keystore.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary
        )

        // GitHub Token Card
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GitHub Personal Access Token",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = tokens.textPrimary
                        )
                        if (!gitHubUsername.isNullOrBlank()) {
                            Text(
                                text = "Authenticated as @$gitHubUsername",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = tokens.accentViolet
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (hasGitHubToken) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.size(8.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasGitHubToken) "Token Encrypted in Keystore" else "No token configured",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasGitHubToken) Color(0xFF10B981) else SemanticError
                            )
                        }
                    }

                    Row {
                        IconButton(onClick = onConfigureGitHub, modifier = Modifier.testTag("configure_github_token_button")) {
                            Icon(
                                imageVector = if (hasGitHubToken) Icons.Default.Edit else Icons.Default.Add,
                                contentDescription = "Configure GitHub Token",
                                tint = tokens.accentViolet
                            )
                        }
                        if (hasGitHubToken) {
                            IconButton(onClick = onDeleteGitHub, modifier = Modifier.testTag("delete_github_token_button")) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete GitHub Token",
                                    tint = SemanticError
                                )
                            }
                        }
                    }
                }
            }
        }

        // Supabase Token Card
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Supabase Project & Access Token",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = tokens.textPrimary
                        )
                        if (supabaseUrl.isNotBlank()) {
                            Text(
                                text = "Project: ${supabaseUrl.removePrefix("https://").removeSuffix(".supabase.co")}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = tokens.accentCyan
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (hasSupabaseToken) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.size(8.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasSupabaseToken) "Personal Access Token Encrypted in Keystore" else "No token configured",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasSupabaseToken) Color(0xFF10B981) else SemanticError
                            )
                        }
                    }

                    Row {
                        IconButton(onClick = onConfigureSupabase, modifier = Modifier.testTag("configure_supabase_button")) {
                            Icon(
                                imageVector = if (hasSupabaseToken) Icons.Default.Edit else Icons.Default.Add,
                                contentDescription = "Configure Supabase",
                                tint = tokens.accentCyan
                            )
                        }
                        if (hasSupabaseToken) {
                            IconButton(onClick = onDeleteSupabase, modifier = Modifier.testTag("delete_supabase_button")) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Supabase credentials",
                                    tint = SemanticError
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// Dialogs: Delete Everything with Typed Confirmation
// ==========================================

@Composable
private fun DeleteEverythingDialog(
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    var typedConfirmation by remember { mutableStateOf("") }
    val isConfirmed = typedConfirmation.trim().equals("DELETE", ignoreCase = true)
    val tokens = GlassTheme.tokens

    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Delete Everything",
        icon = Icons.Default.Warning,
        iconTint = SemanticError,
        message = "This action is permanent and cannot be undone. It will wipe all conversations, messages, SQLite data, and encrypted credentials in Android KeyStore.",
        confirmButtonText = "Permanently Delete Everything",
        confirmButtonEnabled = isConfirmed,
        onConfirm = {
            if (isConfirmed) onConfirmDelete()
        },
        confirmTestTag = "confirm_delete_everything_button",
        dismissButtonText = "Cancel",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    fillColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    borderStroke = BorderStroke(1.dp, SemanticError.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Permanent Data Destruction:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = SemanticError
                        )
                        Text(
                            text = "• All conversations, messages, and chat history\n" +
                                    "• All encrypted API keys, GitHub tokens, and Supabase credentials in Android KeyStore\n" +
                                    "• The SQLCipher encrypted Room database\n" +
                                    "• All local code drafts and settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textPrimary
                        )
                    }
                }

                Text(
                    text = "To confirm, please type DELETE below:",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = tokens.textPrimary
                )

                GlassTextField(
                    value = typedConfirmation,
                    onValueChange = { typedConfirmation = it },
                    placeholder = "DELETE",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_everything_confirmation_input")
                )
            }
        }
    )
}

// ==========================================
// Dialog: Data Export Result
// ==========================================

@Composable
private fun DataExportDialog(
    jsonData: String,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Data Export Ready",
        icon = Icons.Default.CheckCircle,
        iconTint = Color(0xFF10B981),
        message = "Your conversations, messages, and settings have been formatted as JSON. All secrets, keys, and tokens have been excluded.",
        confirmButtonText = if (copied) "Copied!" else "Copy JSON",
        onConfirm = {
            clipboardManager.setText(AnnotatedString(jsonData))
            copied = true
        },
        confirmTestTag = "copy_export_json_button",
        dismissButtonText = "Close",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    fillColor = Color(0xFF0D1117)
                ) {
                    Box(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = jsonData,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFFE6EDF3),
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }

                GradientButton(
                    text = "Share JSON File",
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, jsonData)
                            type = "application/json"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share Open Buddy Data Export")
                        context.startActivity(shareIntent)
                    },
                    leadingIcon = Icons.Default.Share,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_export_json_button")
                )
            }
        }
    )
}

// ==========================================
// Dialog: GitHub Token Input with Validation
// ==========================================

@Composable
private fun GitHubTokenDialog(
    currentUsername: String?,
    onDismiss: () -> Unit,
    onValidateToken: (suspend (String) -> Result<String>)?,
    onSave: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var tokenValue by remember { mutableStateOf("") }
    var isVisible by remember { mutableStateOf(false) }
    var isValidating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<Result<String>?>(null) }

    val tokens = GlassTheme.tokens

    GlassDialog(
        onDismissRequest = onDismiss,
        title = "GitHub Access Token",
        icon = Icons.Default.Key,
        iconTint = tokens.accentViolet,
        message = "Token will be encrypted with AES-256-GCM in the hardware-backed Android KeyStore.",
        confirmButtonText = "Save Token",
        confirmButtonEnabled = tokenValue.isNotBlank(),
        onConfirm = { onSave(tokenValue.trim()) },
        confirmTestTag = "save_github_token_button",
        dismissButtonText = "Cancel",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Recommendation Card
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    fillColor = tokens.accentViolet.copy(alpha = 0.12f),
                    borderStroke = BorderStroke(1.dp, tokens.accentViolet.copy(alpha = 0.3f))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "💡 Recommended: Fine-Grained PAT",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = tokens.accentViolet
                        )
                        Text(
                            text = "Use a GitHub Fine-Grained Personal Access Token with 'Repository permissions: Contents (Read and write)'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textPrimary
                        )
                    }
                }

                GlassTextField(
                    value = tokenValue,
                    onValueChange = {
                        tokenValue = it
                        validationResult = null
                    },
                    placeholder = "Personal Access Token (github_pat_...)",
                    visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isVisible = !isVisible }) {
                            Icon(
                                imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isVisible) "Hide token" else "Show token",
                                tint = tokens.textSecondary
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_token_input_field")
                )

                // Validate Button
                if (onValidateToken != null) {
                    GradientButton(
                        text = if (isValidating) "Validating..." else "Validate Token",
                        onClick = {
                            isValidating = true
                            validationResult = null
                            scope.launch {
                                val res = onValidateToken(tokenValue.trim())
                                validationResult = res
                                isValidating = false
                            }
                        },
                        enabled = tokenValue.isNotBlank() && !isValidating,
                        leadingIcon = Icons.Default.PlayArrow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("validate_github_token_button")
                    )
                }

                validationResult?.let { result ->
                    if (result.isSuccess) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            fillColor = Color(0xFF10B981).copy(alpha = 0.15f),
                            borderStroke = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Authenticated as @${result.getOrNull()}",
                                    color = Color(0xFF10B981),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            fillColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            borderStroke = BorderStroke(1.dp, SemanticError.copy(alpha = 0.5f))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = SemanticError,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = result.exceptionOrNull()?.message ?: "Validation failed. Please verify token.",
                                    color = SemanticError,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

// ==========================================
// Dialog: Token Input (Generic)
// ==========================================

@Composable
private fun TokenInputDialog(
    title: String,
    description: String,
    label: String,
    isSecret: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    var isVisible by remember { mutableStateOf(!isSecret) }
    val tokens = GlassTheme.tokens

    GlassDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = Icons.Default.Key,
        iconTint = tokens.accentViolet,
        message = description,
        confirmButtonText = "Save Token",
        confirmButtonEnabled = value.isNotBlank(),
        onConfirm = { onSave(value.trim()) },
        confirmTestTag = "save_token_button",
        dismissButtonText = "Cancel",
        content = {
            GlassTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = label,
                visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    if (isSecret) {
                        IconButton(onClick = { isVisible = !isVisible }) {
                            Icon(
                                imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isVisible) "Hide token" else "Show token",
                                tint = tokens.textSecondary
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("token_input_field")
            )
        }
    )
}

// ==========================================
// Dialog: Supabase Credentials Input
// ==========================================

@Composable
private fun SupabaseCredentialsDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onTestConnection: (suspend (projectRef: String, token: String) -> Result<String>)? = null,
    onSave: (urlOrRef: String, token: String) -> Unit
) {
    var refOrUrl by remember { mutableStateOf(currentUrl) }
    var token by remember { mutableStateOf("") }
    var isTokenVisible by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<String>?>(null) }
    val scope = rememberCoroutineScope()
    val tokens = GlassTheme.tokens

    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Configure Supabase Project",
        icon = Icons.Default.Storage,
        iconTint = tokens.accentCyan,
        message = "Connects securely to the Supabase Management API to explore schemas and execute queries without anon keys.",
        confirmButtonText = "Save Credentials",
        confirmButtonEnabled = refOrUrl.isNotBlank() && token.isNotBlank(),
        onConfirm = { onSave(refOrUrl.trim(), token.trim()) },
        confirmTestTag = "save_supabase_button",
        dismissButtonText = "Cancel",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassTextField(
                    value = refOrUrl,
                    onValueChange = {
                        refOrUrl = it
                        testResult = null
                    },
                    placeholder = "Project Ref (or URL, e.g. abcdefghijklmnopqrst)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supabase_url_input")
                )

                GlassTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        testResult = null
                    },
                    placeholder = "Personal Access Token (sbp_...)",
                    visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                            Icon(
                                imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isTokenVisible) "Hide token" else "Show token",
                                tint = tokens.textSecondary
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supabase_token_input")
                )

                Text(
                    text = "Generate a Personal Access Token in Supabase: Account > Access Tokens > Generate new token.",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textSecondary
                )

                // Test Connection Button
                if (onTestConnection != null) {
                    GradientButton(
                        text = if (isTesting) "Testing Connection..." else "Test Connection",
                        onClick = {
                            isTesting = true
                            testResult = null
                            scope.launch {
                                val res = onTestConnection(refOrUrl.trim(), token.trim())
                                testResult = res
                                isTesting = false
                            }
                        },
                        enabled = refOrUrl.isNotBlank() && token.isNotBlank() && !isTesting,
                        leadingIcon = Icons.Default.PlayArrow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_supabase_connection_button")
                    )
                }

                testResult?.let { result ->
                    if (result.isSuccess) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            fillColor = Color(0xFF10B981).copy(alpha = 0.15f),
                            borderStroke = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = result.getOrNull() ?: "Connected successfully",
                                    color = Color(0xFF10B981),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            fillColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            borderStroke = BorderStroke(1.dp, SemanticError.copy(alpha = 0.5f))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = SemanticError, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = result.exceptionOrNull()?.message ?: "Connection test failed",
                                    color = SemanticError,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

// ==========================================
// Existing AI Providers Tab & Components
// ==========================================

@Composable
private fun AiProvidersTab(
    providers: List<AiProviderConfig>,
    onAddClick: () -> Unit,
    onEditClick: (AiProviderConfig) -> Unit,
    onDeleteClick: (AiProviderConfig) -> Unit
) {
    val tokens = GlassTheme.tokens

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Configured Providers",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = tokens.textPrimary
                )
                Text(
                    text = "Bring your own API keys for Gemini, Claude, Kimi, or custom endpoints.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        GradientButton(
            text = "Add AI Provider",
            onClick = onAddClick,
            leadingIcon = Icons.Default.Add,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_provider_button")
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (providers.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Key,
                title = "No AI Providers Configured",
                message = "Add Gemini, Claude, or custom OpenAI endpoints to start generating code.",
                actionLabel = "Add First Provider",
                onActionClick = onAddClick,
                actionTestTag = "empty_add_first_provider_button",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(providers, key = { it.id }) { provider ->
                    ProviderCardItem(
                        provider = provider,
                        onEdit = { onEditClick(provider) },
                        onDelete = { onDeleteClick(provider) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderCardItem(
    provider: AiProviderConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = GlassTheme.tokens

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_item_${provider.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = tokens.textPrimary
                    )
                    if (provider.isDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = tokens.accentViolet.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, tokens.accentViolet.copy(alpha = 0.5f)),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "DEFAULT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = tokens.accentViolet,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Type: ${provider.type.displayName}  •  Model: ${provider.modelName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!provider.baseUrl.isNullOrBlank()) {
                    Text(
                        text = "Base URL: ${provider.baseUrl}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (provider.hasApiKey) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.size(8.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (provider.hasApiKey) "API Key Encrypted in Keystore" else "No API Key configured",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (provider.hasApiKey) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                    )
                }
            }

            IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_provider_${provider.id}")) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit provider",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_provider_${provider.id}")) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete provider",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderEditorDialog(
    initialProvider: AiProviderConfig?,
    onDismiss: () -> Unit,
    onGetApiKey: suspend (id: String) -> String?,
    onTestConnection: suspend (type: ProviderType, model: String, baseUrl: String?, apiKey: String) -> Result<String>,
    onFetchGeminiModels: (suspend (apiKey: String) -> Result<List<String>>)? = null,
    onSave: (id: String?, name: String, type: ProviderType, model: String, baseUrl: String?, apiKey: String?, setAsDefault: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val tokens = GlassTheme.tokens

    var name by remember { mutableStateOf(initialProvider?.name ?: "") }
    var selectedType by remember { mutableStateOf(initialProvider?.type ?: ProviderType.GEMINI) }
    var modelName by remember { mutableStateOf(initialProvider?.modelName ?: selectedType.defaultModel) }
    var baseUrl by remember { mutableStateOf(initialProvider?.baseUrl ?: (selectedType.defaultBaseUrl ?: "")) }
    var apiKey by remember { mutableStateOf("") }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var setAsDefault by remember { mutableStateOf(initialProvider?.isDefault ?: false) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<String>?>(null) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    var liveGeminiModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchModelsMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialProvider) {
        if (initialProvider != null) {
            val key = onGetApiKey(initialProvider.id)
            if (!key.isNullOrBlank()) {
                apiKey = key
            }
        }
    }

    // Auto-fetch models when provider is Gemini and API key is entered
    LaunchedEffect(selectedType, apiKey) {
        if (selectedType == ProviderType.GEMINI && apiKey.isNotBlank() && liveGeminiModels.isEmpty() && !isFetchingModels) {
            isFetchingModels = true
            val res = onFetchGeminiModels?.invoke(apiKey)
            isFetchingModels = false
            if (res != null && res.isSuccess) {
                liveGeminiModels = res.getOrThrow()
            }
        }
    }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = if (initialProvider == null) "Add AI Provider" else "Edit AI Provider",
        icon = Icons.Default.Key,
        iconTint = tokens.accentViolet,
        message = "Configure endpoint, model, and encrypted API credentials for your AI provider.",
        confirmButtonText = "Save Provider",
        onConfirm = {
            val finalName = name.ifBlank { selectedType.displayName }
            val finalModel = modelName.ifBlank { selectedType.defaultModel }
            onSave(
                initialProvider?.id,
                finalName,
                selectedType,
                finalModel,
                baseUrl.ifBlank { null },
                apiKey.ifBlank { null },
                setAsDefault
            )
        },
        confirmTestTag = "save_provider_button",
        dismissButtonText = "Cancel",
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Provider Type Selector Chips
                Text(
                    text = "Provider Type",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = tokens.textSecondary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ProviderType.entries.toTypedArray()) { type ->
                        PillChip(
                            text = type.displayName,
                            isSelected = selectedType == type,
                            onClick = {
                                selectedType = type
                                if (name.isBlank() || ProviderType.entries.any { it.displayName == name }) {
                                    name = type.displayName
                                }
                                modelName = type.defaultModel
                                baseUrl = type.defaultBaseUrl ?: ""
                                testResult = null
                            }
                        )
                    }
                }

                // Display Name Input
                GlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Display Name (e.g. Claude 3.5 Sonnet)",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("provider_name_input")
                )

                // Model Name Input
                GlassTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    placeholder = "Model Name (e.g. ${selectedType.defaultModel})",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("provider_model_input")
                )

                // Model Suggestions & Live Fetch Row
                if (selectedType == ProviderType.GEMINI) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (liveGeminiModels.isNotEmpty()) "Available Models (Live from Gemini API)" else "Recommended Models",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = tokens.textSecondary
                        )

                        TextButton(
                            onClick = {
                                if (apiKey.isNotBlank()) {
                                    scope.launch {
                                        isFetchingModels = true
                                        fetchModelsMessage = null
                                        val res = onFetchGeminiModels?.invoke(apiKey)
                                        isFetchingModels = false
                                        if (res != null && res.isSuccess) {
                                            liveGeminiModels = res.getOrThrow()
                                            fetchModelsMessage = "Fetched ${liveGeminiModels.size} live models"
                                        } else {
                                            fetchModelsMessage = "Failed: ${res?.exceptionOrNull()?.message}"
                                        }
                                    }
                                } else {
                                    fetchModelsMessage = "Please enter an API key first"
                                }
                            },
                            enabled = !isFetchingModels,
                            modifier = Modifier.height(28.dp)
                        ) {
                            if (isFetchingModels) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = tokens.accentCyan)
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = if (isFetchingModels) "Fetching..." else "Refresh Models",
                                fontSize = 11.sp,
                                color = tokens.accentCyan
                            )
                        }
                    }

                    if (fetchModelsMessage != null) {
                        Text(
                            text = fetchModelsMessage ?: "",
                            fontSize = 11.sp,
                            color = if (fetchModelsMessage?.startsWith("Failed") == true || fetchModelsMessage?.contains("key") == true) SemanticWarning else SemanticSuccess,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }

                val suggestions = when (selectedType) {
                    ProviderType.GEMINI -> {
                        if (liveGeminiModels.isNotEmpty()) {
                            liveGeminiModels
                        } else {
                            listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.5-flash-lite", "gemini-1.5-flash", "gemini-1.5-pro")
                        }
                    }
                    ProviderType.CLAUDE -> listOf("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022")
                    ProviderType.KIMI -> listOf("moonshot-v1-8k", "moonshot-v1-32k")
                    ProviderType.CUSTOM_OPENAI -> listOf("gpt-4o", "deepseek-chat")
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(suggestions) { sug ->
                        PillChip(
                            text = sug,
                            isSelected = modelName == sug,
                            onClick = { modelName = sug }
                        )
                    }
                }

                // Base URL Input (Custom OpenAI or Kimi)
                if (selectedType == ProviderType.CUSTOM_OPENAI || selectedType == ProviderType.KIMI) {
                    GlassTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        placeholder = "Base URL (e.g. ${selectedType.defaultBaseUrl ?: "https://api.openai.com/v1"})",
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("provider_base_url_input")
                    )
                }

                // API Key Input
                GlassTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        testResult = null
                    },
                    placeholder = "API Key (encrypted in Keystore)",
                    singleLine = true,
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(
                            onClick = { isApiKeyVisible = !isApiKeyVisible },
                            modifier = Modifier.testTag("toggle_api_key_visibility")
                        ) {
                            Icon(
                                imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isApiKeyVisible) "Hide API key" else "Show API key",
                                tint = tokens.textSecondary
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("provider_api_key_input")
                )

                // Set as Default Toggle
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Set as default provider",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.textPrimary
                        )
                        GradientSwitch(
                            checked = setAsDefault,
                            onCheckedChange = { setAsDefault = it },
                            modifier = Modifier.testTag("provider_default_switch")
                        )
                    }
                }

                // Test Connection Button
                GradientButton(
                    text = if (isTestingConnection) "Testing Connection..." else "Test Connection",
                    onClick = {
                        isTestingConnection = true
                        testResult = null
                        scope.launch {
                            val result = onTestConnection(
                                selectedType,
                                modelName.ifBlank { selectedType.defaultModel },
                                baseUrl.ifBlank { null },
                                apiKey
                            )
                            testResult = result
                            isTestingConnection = false
                        }
                    },
                    enabled = !isTestingConnection && apiKey.isNotBlank(),
                    leadingIcon = if (isTestingConnection) null else Icons.Default.PlayArrow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_connection_button")
                )

                testResult?.let { res ->
                    if (res.isSuccess) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            fillColor = Color(0xFF10B981).copy(alpha = 0.15f),
                            borderStroke = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = res.getOrNull() ?: "Connection verified successfully!",
                                    color = Color(0xFF10B981),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            fillColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            borderStroke = BorderStroke(1.dp, SemanticError.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = SemanticError,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = res.exceptionOrNull()?.message ?: "Connection test failed",
                                    color = SemanticError,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun SystemPromptTab(
    currentPrompt: String,
    onSavePrompt: (String) -> Unit,
    onResetPrompt: () -> Unit
) {
    var promptText by remember(currentPrompt) { mutableStateOf(currentPrompt) }
    val tokens = GlassTheme.tokens

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Assistant System Prompt",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = tokens.textPrimary
        )
        Text(
            text = "Customize the default instructions given to all AI models for code generation, formatting, and tone.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary
        )

        Text("Prompt Presets:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = tokens.accentViolet)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(
                "Senior Engineer" to "You are Open Buddy, a senior Android & software engineer. Provide idiomatic, clean code with concise explanations and markdown code blocks.",
                "Bug Reviewer" to "You are Open Buddy, an expert code reviewer and bug hunter. Analyze code thoroughly, point out edge cases, race conditions, memory leaks, and provide fixed code snippets.",
                "Concise Coder" to "You are Open Buddy. Answer with minimal words and maximum code. Provide only the necessary snippet with clear comments."
            )
            items(presets) { (label, presetPrompt) ->
                PillChip(
                    text = label,
                    isSelected = promptText == presetPrompt,
                    onClick = { promptText = presetPrompt }
                )
            }
        }

        GlassTextField(
            value = promptText,
            onValueChange = { promptText = it },
            placeholder = "Enter custom system instructions...",
            maxLines = 14,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("system_prompt_input_field")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            GlassOutlinedButton(
                text = "Reset to Default",
                onClick = onResetPrompt,
                leadingIcon = Icons.Default.Refresh,
                testTag = "reset_system_prompt_button"
            )

            GradientButton(
                text = "Save Prompt",
                onClick = { onSavePrompt(promptText) },
                leadingIcon = Icons.Default.Check,
                testTag = "save_system_prompt_button"
            )
        }
    }
}

@Composable
private fun SavedDraftsTab(
    drafts: List<CodeDraft>,
    onDeleteDraft: (id: String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copiedId by remember { mutableStateOf<String?>(null) }
    var deletingDraft by remember { mutableStateOf<CodeDraft?>(null) }

    val tokens = GlassTheme.tokens

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Saved Code Drafts",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = tokens.textPrimary
        )
        Text(
            text = "Locally stored code snippets saved from chat responses.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (drafts.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.FolderOpen,
                title = "No Saved Drafts",
                message = "Tap 'Save to file' on any chat code block to store local drafts.",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(drafts, key = { it.id }) { draft ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = tokens.accentViolet.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, tokens.accentViolet.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = draft.language.uppercase(),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tokens.accentViolet,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = draft.filename,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = tokens.textPrimary
                                    )
                                }

                                Row {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(draft.code))
                                            copiedId = draft.id
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (copiedId == draft.id) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = "Copy code",
                                            tint = if (copiedId == draft.id) Color(0xFF10B981) else tokens.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { deletingDraft = draft },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete draft",
                                            tint = SemanticError,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0D1117),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = draft.code.lines().take(5).joinToString("\n") + if (draft.code.lines().size > 5) "\n..." else "",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = Color(0xFFE6EDF3),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (deletingDraft != null) {
        val target = deletingDraft ?: return
        GlassDialog(
            onDismissRequest = { deletingDraft = null },
            title = "Delete Code Draft",
            icon = Icons.Default.Warning,
            iconTint = SemanticError,
            message = "Are you sure you want to delete '${target.filename}'? This draft will be permanently removed.",
            confirmButtonText = "Delete Draft",
            onConfirm = {
                onDeleteDraft(target.id)
                deletingDraft = null
            },
            confirmTestTag = "confirm_delete_draft_button",
            dismissButtonText = "Cancel"
        )
    }
}

@Composable
fun VoiceActionsTab(
    voiceSettings: Map<VoiceActionType, Boolean>,
    onToggleAction: ((VoiceActionType, Boolean) -> Unit)?
) {
    val tokens = GlassTheme.tokens

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(tokens.accentViolet.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = tokens.accentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Voice Action Toggles",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = tokens.textPrimary
                        )
                        Text(
                            text = "Configure which Android intent actions Open Buddy is permitted to parse and execute.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }
            }
        }

        items(VoiceActionType.entries) { actionType ->
            val isEnabled = voiceSettings[actionType] ?: true
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                testTag = "voice_setting_card_${actionType.actionKey}"
            ) {
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isEnabled) tokens.accentViolet.copy(alpha = 0.2f)
                                    else tokens.glassFill
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getVoiceActionSettingIcon(actionType),
                                contentDescription = null,
                                tint = if (isEnabled) tokens.accentCyan else tokens.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = actionType.displayName,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = tokens.textPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(tokens.glassFill)
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = actionType.actionKey,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                        color = tokens.accentViolet
                                    )
                                }
                            }
                            Text(
                                text = actionType.description,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = tokens.textSecondary
                            )
                            Text(
                                text = "e.g. \"${actionType.samplePrompt}\"",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = tokens.accentCyan.copy(alpha = 0.8f)
                            )
                        }
                    }

                    GradientSwitch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            onToggleAction?.invoke(actionType, checked)
                        },
                        modifier = Modifier.testTag("toggle_voice_action_${actionType.actionKey}")
                    )
                }
            }
        }
    }
}

private fun getVoiceActionSettingIcon(type: VoiceActionType): ImageVector {
    return when (type) {
        VoiceActionType.OPEN_APP -> Icons.Default.Apps
        VoiceActionType.DIAL -> Icons.Default.Call
        VoiceActionType.SMS_COMPOSE -> Icons.Default.Sms
        VoiceActionType.EMAIL_COMPOSE -> Icons.Default.Email
        VoiceActionType.WHATSAPP_COMPOSE -> Icons.Default.Chat
        VoiceActionType.YOUTUBE_SEARCH -> Icons.Default.PlayCircle
        VoiceActionType.OPEN_URL -> Icons.Default.Language
        VoiceActionType.WEB_SEARCH -> Icons.Default.Search
        VoiceActionType.SET_ALARM -> Icons.Default.Alarm
        VoiceActionType.SET_TIMER -> Icons.Default.Timer
    }
}

