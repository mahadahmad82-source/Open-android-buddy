package com.mahad.openbuddy.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahad.openbuddy.domain.model.DatabaseTableInfo
import com.mahad.openbuddy.domain.model.QueryHistoryItem
import com.mahad.openbuddy.domain.model.QueryResult
import com.mahad.openbuddy.ui.components.ConversationsDrawerContent
import com.mahad.openbuddy.ui.components.ModelSelector
import com.mahad.openbuddy.ui.screens.chat.ChatScreen
import com.mahad.openbuddy.ui.screens.settings.SettingsScreen
import com.mahad.openbuddy.ui.screens.tabs.DatabaseTab
import com.mahad.openbuddy.ui.screens.tabs.GitHubTab
import com.mahad.openbuddy.ui.screens.tabs.VoiceTab
import com.mahad.openbuddy.ui.theme.GlassTheme
import com.mahad.openbuddy.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

enum class ScreenTab(val label: String, val icon: ImageVector) {
    CHAT("Chat", Icons.Default.ChatBubbleOutline),
    GITHUB("GitHub", Icons.Default.Code),
    DATABASE("Database", Icons.Default.Storage),
    VOICE("Voice", Icons.Default.Mic),
    SETTINGS("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val tokens = GlassTheme.tokens
    val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()

    // If app is locked by Biometric App Lock, present the secure Lock Screen overlay
    if (isAppLocked) {
        LockScreen(
            onUnlockSuccess = { viewModel.unlockApp() },
            modifier = modifier
        )
        return
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(ScreenTab.CHAT) }

    // If the previous run crashed, show the saved crash report once so the
    // user can copy/send it — this is how tab crashes get diagnosed exactly.
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var crashReport by remember { mutableStateOf(com.mahad.openbuddy.data.CrashReporter.readLastCrash(appContext)) }
    if (crashReport != null) {
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                com.mahad.openbuddy.data.CrashReporter.clear(appContext)
                crashReport = null
            },
            title = { Text("Last crash report") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = crashReport ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(crashReport ?: ""))
                }) { Text("Copy report") }
            },
            dismissButton = {
                TextButton(onClick = {
                    com.mahad.openbuddy.data.CrashReporter.clear(appContext)
                    crashReport = null
                }) { Text("Dismiss") }
            }
        )
    }

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()
    val systemPrompt by viewModel.systemPrompt.collectAsStateWithLifecycle()
    val codeDrafts by viewModel.codeDrafts.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val isChatVoiceListening by viewModel.isChatVoiceListening.collectAsStateWithLifecycle()
    val chatVoiceTranscript by viewModel.chatVoiceTranscript.collectAsStateWithLifecycle()

    // Security & Privacy states
    val biometricLockEnabled by viewModel.biometricLockEnabled.collectAsStateWithLifecycle()
    val autoLockTimeoutMinutes by viewModel.autoLockTimeoutMinutes.collectAsStateWithLifecycle()
    val redactionEnabled by viewModel.redactionEnabled.collectAsStateWithLifecycle()
    val hasGitHubToken by viewModel.hasGitHubToken.collectAsStateWithLifecycle()
    val gitHubUser by viewModel.gitHubUser.collectAsStateWithLifecycle()
    val hasSupabaseToken by viewModel.hasSupabaseToken.collectAsStateWithLifecycle()
    val supabaseUrl by viewModel.supabaseUrl.collectAsStateWithLifecycle()
    val voiceActionSettings by viewModel.voiceActionSettings.collectAsStateWithLifecycle()
    val lastBackupInfo by viewModel.lastBackupInfo.collectAsStateWithLifecycle()

    // Agent (Phases 2-7)
    val agentMode by viewModel.agentMode.collectAsStateWithLifecycle()
    val speakReplies by viewModel.speakReplies.collectAsStateWithLifecycle()
    val agentStatusText by viewModel.agentStatusText.collectAsStateWithLifecycle()
    val pendingToolApproval by viewModel.agentPendingApproval.collectAsStateWithLifecycle()
    val agentSkills by viewModel.skills.collectAsStateWithLifecycle()
    val activeSkillId by viewModel.activeSkillId.collectAsStateWithLifecycle()
    val agentMemories by viewModel.memories.collectAsStateWithLifecycle()
    val agentMcpServers by viewModel.mcpServers.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()

    // Media picked via MainActivity's classic picker flow lands here.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.mahad.openbuddy.ui.ActivityResults.pickedMedia.collect { uri ->
            viewModel.stageAttachment(uri)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = tokens.backgroundEnd.copy(alpha = 0.96f)
            ) {
                ConversationsDrawerContent(
                    conversations = conversations,
                    activeConversationId = activeConversationId,
                    onSelectConversation = { convId ->
                        viewModel.selectConversation(convId)
                        scope.launch { drawerState.close() }
                        currentScreen = ScreenTab.CHAT
                    },
                    onNewConversation = {
                        viewModel.createNewConversation("New Chat")
                        scope.launch { drawerState.close() }
                        currentScreen = ScreenTab.CHAT
                    },
                    onRenameConversation = { id, newTitle ->
                        viewModel.renameConversation(id, newTitle)
                    },
                    onDeleteConversation = { id ->
                        viewModel.deleteConversation(id)
                    }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // Transparent Top Bar
                TopAppBar(
                    title = {
                        if (currentScreen == ScreenTab.CHAT) {
                            ModelSelector(
                                activeProvider = activeProvider,
                                providers = providers,
                                onSelectProvider = { providerId ->
                                    viewModel.selectProvider(providerId)
                                },
                                onOpenSettings = {
                                    currentScreen = ScreenTab.SETTINGS
                                }
                            )
                        } else {
                            Text(
                                text = currentScreen.label,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = tokens.textPrimary
                            )
                        }
                    },
                    navigationIcon = {
                        if (currentScreen == ScreenTab.CHAT) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(tokens.glassFill)
                                    .border(tokens.glassBorderStroke, CircleShape)
                                    .clickable { scope.launch { drawerState.open() } }
                                    .testTag("open_conversations_drawer"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Conversations menu",
                                    tint = tokens.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(tokens.glassFill)
                                    .border(tokens.glassBorderStroke, CircleShape)
                                    .clickable { currentScreen = ScreenTab.CHAT }
                                    .testTag("back_to_chat_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Chat",
                                    tint = tokens.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        if (currentScreen == ScreenTab.CHAT) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(tokens.glassFill)
                                    .border(tokens.glassBorderStroke, CircleShape)
                                    .clickable { currentScreen = ScreenTab.SETTINGS }
                                    .testTag("open_settings_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = tokens.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                // Floating Pill-Shaped Glass Bottom Bar with Margins from screen edges
                FloatingGlassBottomBar(
                    selectedTab = currentScreen,
                    onSelectTab = { currentScreen = it }
                )
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            // If on another tab, back press navigates to Chat tab
            if (currentScreen != ScreenTab.CHAT) {
                BackHandler {
                    currentScreen = ScreenTab.CHAT
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Direction-aware smooth transition: 250ms fade + directional slide
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (fadeIn(tween(250, easing = FastOutSlowInEasing)) +
                                slideInHorizontally(tween(250, easing = FastOutSlowInEasing)) { it / 6 * direction }) togetherWith
                                (fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                                        slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { -it / 6 * direction })
                    },
                    label = "screen_transition"
                ) { screen ->
                    when (screen) {
                        ScreenTab.CHAT -> {
                            ChatScreen(
                                messages = messages,
                                isGenerating = isGenerating,
                                isVoiceListening = isChatVoiceListening,
                                voiceTranscript = chatVoiceTranscript,
                                onStartVoiceListening = { lang -> viewModel.startChatVoiceListening(lang) },
                                onStopVoiceListening = { viewModel.stopChatVoiceListening() },
                                onSendMessage = { text -> viewModel.sendMessage(text) },
                                onStopGeneration = { viewModel.stopGeneration() },
                                onRetryMessage = { msgId -> viewModel.retryLastAssistantMessage(msgId) },
                                onSaveCodeDraft = { filename, lang, code ->
                                    viewModel.saveCodeDraft(filename, lang, code)
                                },
                                onNavigateToSettings = { currentScreen = ScreenTab.SETTINGS },
                                agentMode = agentMode,
                                onToggleAgentMode = { viewModel.setAgentMode(it) },
                                agentStatusText = agentStatusText,
                                pendingApproval = pendingToolApproval,
                                onApprovalDecision = { viewModel.resolveToolApproval(it) },
                                skills = agentSkills,
                                activeSkillId = activeSkillId,
                                onSelectSkill = { viewModel.setActiveSkill(it) },
                                speakReplies = speakReplies,
                                onToggleSpeakReplies = { viewModel.setSpeakReplies(it) },
                                memories = agentMemories,
                                onAddMemory = { viewModel.addMemory(it) },
                                onDeleteMemory = { viewModel.deleteMemory(it) },
                                onSaveSkill = { id, name, desc, instr -> viewModel.saveSkill(id, name, desc, instr) },
                                onDeleteSkill = { viewModel.deleteSkill(it) },
                                mcpServers = agentMcpServers,
                                onAddMcpServer = { name, url, token -> viewModel.addMcpServer(name, url, token) },
                                onDeleteMcpServer = { viewModel.deleteMcpServer(it) },
                                pendingAttachment = pendingAttachment,
                                onClearAttachment = { viewModel.clearPendingAttachment() }
                            )
                        }
                        ScreenTab.GITHUB -> {
                            GitHubTab(
                                viewModel = viewModel,
                                onNavigateToSettings = { currentScreen = ScreenTab.SETTINGS }
                            )
                        }
                        ScreenTab.DATABASE -> {
                            DatabaseTab(
                                viewModel = viewModel,
                                onNavigateToSettings = { currentScreen = ScreenTab.SETTINGS }
                            )
                        }
                        ScreenTab.VOICE -> {
                            VoiceTab(
                                viewModel = viewModel
                            )
                        }
                        ScreenTab.SETTINGS -> {
                            SettingsScreen(
                                providers = providers,
                                currentSystemPrompt = systemPrompt,
                                codeDrafts = codeDrafts,
                                biometricLockEnabled = biometricLockEnabled,
                                autoLockTimeoutMinutes = autoLockTimeoutMinutes,
                                redactionEnabled = redactionEnabled,
                                hasGitHubToken = hasGitHubToken,
                                hasSupabaseToken = hasSupabaseToken,
                                supabaseUrl = supabaseUrl,
                                gitHubUsername = gitHubUser?.login,
                                voiceActionSettings = voiceActionSettings,
                                onToggleVoiceAction = { action, enabled ->
                                    viewModel.toggleVoiceActionSetting(action, enabled)
                                },
                                onValidateGitHubToken = { token ->
                                    val res = viewModel.validateGitHubToken(token)
                                    if (res.isSuccess) {
                                        Result.success(res.getOrNull()?.login ?: "Verified")
                                    } else {
                                        Result.failure(res.exceptionOrNull() ?: Exception("Token validation failed"))
                                    }
                                },
                                onTestSupabaseConnection = { ref, token ->
                                    viewModel.testSupabaseConnection(ref, token)
                                },
                                onSaveProvider = { id, name, type, model, baseUrl, apiKey, setAsDefault ->
                                    viewModel.saveProvider(id, name, type, model, baseUrl, apiKey, setAsDefault)
                                },
                                onDeleteProvider = { id -> viewModel.deleteProvider(id) },
                                onTestConnection = { type, model, baseUrl, apiKey ->
                                    viewModel.testConnection(type, model, baseUrl, apiKey)
                                },
                                onGetProviderApiKey = { id -> viewModel.getProviderApiKey(id) },
                                onSaveSystemPrompt = { prompt -> viewModel.saveSystemPrompt(prompt) },
                                onResetSystemPrompt = { viewModel.resetSystemPrompt() },
                                onDeleteCodeDraft = { id -> viewModel.deleteCodeDraft(id) },
                                onSetBiometricLockEnabled = { enabled -> viewModel.setBiometricLockEnabled(enabled) },
                                onSetAutoLockTimeoutMinutes = { minutes -> viewModel.setAutoLockTimeoutMinutes(minutes) },
                                onSetRedactionEnabled = { enabled -> viewModel.setRedactionEnabled(enabled) },
                                onSaveGitHubToken = { token -> viewModel.saveGitHubToken(token) },
                                onDeleteGitHubToken = { viewModel.deleteGitHubToken() },
                                onSaveSupabaseCredentials = { url, token -> viewModel.saveSupabaseCredentials(url, token) },
                                onDeleteSupabaseCredentials = { viewModel.deleteSupabaseCredentials() },
                                onExportData = { viewModel.exportDataJson() },
                                onDeleteEverything = { viewModel.deleteEverything() },
                                lastBackupInfo = lastBackupInfo,
                                onCreateBackup = { passphrase -> viewModel.createEncryptedBackup(passphrase) },
                                onRecordCompletedBackup = { fileName, sizeBytes -> viewModel.recordCompletedBackup(fileName, sizeBytes) },
                                onInspectBackup = { envelopeJson, passphrase -> viewModel.decryptAndInspectBackup(envelopeJson, passphrase) },
                                onExecuteRestore = { summary, mode -> viewModel.executeRestore(summary, mode) },
                                onFetchGeminiModels = { apiKey -> viewModel.fetchGeminiModels(apiKey) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// Floating Pill-Shaped Glass Bottom Bar
// ============================================================================

@Composable
fun FloatingGlassBottomBar(
    selectedTab: ScreenTab,
    onSelectTab: (ScreenTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = GlassTheme.tokens
    val haptic = LocalHapticFeedback.current
    val pillShape = RoundedCornerShape(50)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .shadow(
                elevation = 16.dp,
                shape = pillShape,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.35f)
            )
            .clip(pillShape)
            .background(tokens.glassFillStrong)
            .border(tokens.glassBorderStroke, pillShape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScreenTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.95f else 1f,
                    animationSpec = tween(80),
                    label = "tab_scale"
                )

                val itemShape = RoundedCornerShape(50)

                Box(
                    modifier = Modifier
                        .scale(scale)
                        .clip(itemShape)
                        .then(
                            if (isSelected) {
                                Modifier
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = itemShape,
                                        spotColor = tokens.accentViolet.copy(alpha = 0.6f)
                                    )
                                    .background(tokens.primaryGradient)
                                    .border(BorderStroke(1.dp, tokens.accentCyan.copy(alpha = 0.4f)), itemShape)
                            } else {
                                Modifier.background(Color.Transparent)
                            }
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelectTab(tab)
                        }
                        .testTag("nav_tab_${tab.name.lowercase()}")
                        .padding(horizontal = if (isSelected) 14.dp else 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (isSelected) Color.White else tokens.textSecondary,
                            modifier = Modifier.size(19.dp)
                        )

                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
