package com.mahad.openbuddy.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mahad.openbuddy.domain.model.Message
import com.mahad.openbuddy.domain.model.MessageRole
import com.mahad.openbuddy.domain.model.MessageStatus
import com.mahad.openbuddy.ui.components.CodeBlockView
import com.mahad.openbuddy.ui.components.ErrorBanner
import com.mahad.openbuddy.ui.components.GlassSnackbar
import com.mahad.openbuddy.ui.components.MarkdownText
import com.mahad.openbuddy.ui.theme.GlassTheme
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    messages: List<Message>,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onRetryMessage: (messageId: String) -> Unit,
    onSaveCodeDraft: (filename: String, language: String, code: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    isVoiceListening: Boolean = false,
    voiceTranscript: String = "",
    onStartVoiceListening: (languagePreference: String?) -> Unit = {},
    onStopVoiceListening: () -> Unit = {},
    // Agent mode (Phases 2-7)
    agentMode: Boolean = false,
    onToggleAgentMode: (Boolean) -> Unit = {},
    agentStatusText: String? = null,
    pendingApproval: com.mahad.openbuddy.data.agent.ToolApprovalRequest? = null,
    onApprovalDecision: (Boolean) -> Unit = {},
    skills: List<com.mahad.openbuddy.data.local.entity.SkillEntity> = emptyList(),
    activeSkillId: String? = null,
    onSelectSkill: (String?) -> Unit = {},
    speakReplies: Boolean = false,
    onToggleSpeakReplies: (Boolean) -> Unit = {},
    memories: List<com.mahad.openbuddy.data.local.entity.MemoryEntity> = emptyList(),
    onAddMemory: (String) -> Unit = {},
    onDeleteMemory: (String) -> Unit = {},
    onSaveSkill: (String?, String, String, String) -> Unit = { _, _, _, _ -> },
    onDeleteSkill: (String) -> Unit = {},
    mcpServers: List<com.mahad.openbuddy.data.local.entity.McpServerEntity> = emptyList(),
    onAddMcpServer: (String, String, String?) -> Unit = { _, _, _ -> },
    onDeleteMcpServer: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = GlassTheme.tokens
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    var inputText by remember { mutableStateOf("") }
    var voiceLanguage by remember { mutableStateOf("en-US") } // "en-US" or "ur-PK"
    var showAgentHub by remember { mutableStateOf(false) }

    // Sync live voice transcript into input text field
    LaunchedEffect(voiceTranscript) {
        if (voiceTranscript.isNotBlank() && isVoiceListening) {
            inputText = voiceTranscript
        }
    }

    // Permission launcher for RECORD_AUDIO requested on mic tap
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onStartVoiceListening(voiceLanguage)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission is required to use voice input.")
            }
        }
    }

    // Auto-scroll to bottom on new message or when generating
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            // Messages List or Empty State
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(28.dp)
                    ) {
                        // Glowing gradient orb
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .shadow(20.dp, CircleShape, spotColor = tokens.accentViolet.copy(alpha = 0.4f))
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            tokens.accentViolet.copy(alpha = 0.35f),
                                            tokens.accentBlue.copy(alpha = 0.15f),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .border(tokens.glassBorderStroke, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = tokens.accentCyan,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Open Buddy",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            ),
                            color = tokens.textPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Ask architectural questions, synthesize Kotlin routines, review code, or speak your prompts in English or Roman Urdu.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            ),
                            color = tokens.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(messages, key = { _, msg -> msg.id }) { index, message ->
                        val isLastAssistant = message.role == MessageRole.ASSISTANT &&
                                (index == messages.lastIndex || index == messages.lastIndex - 1)

                        MessageBubbleItem(
                            message = message,
                            isLastAssistant = isLastAssistant,
                            isGenerating = isGenerating,
                            onCopyText = { text ->
                                clipboardManager.setText(AnnotatedString(text))
                                scope.launch {
                                    snackbarHostState.showSnackbar("Copied to clipboard")
                                }
                            },
                            onRetry = { onRetryMessage(message.id) },
                            onSaveCodeDraft = { filename, lang, code ->
                                onSaveCodeDraft(filename, lang, code)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Saved draft '$filename'")
                                }
                            },
                            onNavigateToSettings = onNavigateToSettings
                        )
                    }
                }
            }

            // Tool approval card (agent mode) — Approve / Reject gates world-changing tools
            if (pendingApproval != null) {
                ToolApprovalCard(
                    request = pendingApproval,
                    onDecision = onApprovalDecision
                )
            }

            // Live agent status line
            if (agentMode && agentStatusText != null) {
                Text(
                    text = agentStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            // Agent toolbar: mode toggle, skill picker, speak toggle, hub
            AgentToolbar(
                agentMode = agentMode,
                onToggleAgentMode = onToggleAgentMode,
                skills = skills,
                activeSkillId = activeSkillId,
                onSelectSkill = onSelectSkill,
                speakReplies = speakReplies,
                onToggleSpeakReplies = onToggleSpeakReplies,
                onOpenHub = { showAgentHub = true }
            )

            // Floating Glass Input Bar with Mic Button
            FloatingGlassInputBar(
                inputText = inputText,
                isGenerating = isGenerating,
                isVoiceListening = isVoiceListening,
                selectedVoiceLanguage = voiceLanguage,
                onToggleVoiceLanguage = {
                    voiceLanguage = if (voiceLanguage == "en-US") "ur-PK" else "en-US"
                },
                onTextChanged = { inputText = it },
                onSend = {
                    if (isVoiceListening) {
                        onStopVoiceListening()
                    }
                    if (inputText.isNotBlank()) {
                        val textToSend = inputText.trim()
                        inputText = ""
                        onSendMessage(textToSend)
                    }
                },
                onStop = onStopGeneration,
                onMicClick = {
                    if (isVoiceListening) {
                        onStopVoiceListening()
                    } else {
                        val hasAudio = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasAudio) {
                            onStartVoiceListening(voiceLanguage)
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            )
        }

        // Custom Glass Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp)
        ) { snackbarData ->
            GlassSnackbar(snackbarData = snackbarData)
        }

        // Agent hub: skills, memory, MCP servers
        if (showAgentHub) {
            AgentHubDialog(
                skills = skills,
                activeSkillId = activeSkillId,
                onSelectSkill = onSelectSkill,
                onSaveSkill = onSaveSkill,
                onDeleteSkill = onDeleteSkill,
                memories = memories,
                onAddMemory = onAddMemory,
                onDeleteMemory = onDeleteMemory,
                mcpServers = mcpServers,
                onAddMcpServer = onAddMcpServer,
                onDeleteMcpServer = onDeleteMcpServer,
                onDismiss = { showAgentHub = false }
            )
        }
    }
}

@Composable
private fun MessageBubbleItem(
    message: Message,
    isLastAssistant: Boolean,
    isGenerating: Boolean,
    onCopyText: (String) -> Unit,
    onRetry: () -> Unit,
    onSaveCodeDraft: (filename: String, language: String, code: String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val tokens = GlassTheme.tokens
    val isUser = message.role == MessageRole.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // User Bubble: Primary Gradient with rounded corners (18dp, 18dp, 4dp, 18dp)
            val userBubbleShape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .shadow(8.dp, userBubbleShape, spotColor = tokens.accentViolet.copy(alpha = 0.35f))
                    .clip(userBubbleShape)
                    .background(tokens.primaryGradient)
                    .testTag("user_message_bubble")
                    .padding(14.dp)
            ) {
                Text(
                    text = message.content,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    lineHeight = 22.sp
                )
            }
        } else {
            // Assistant Bubble: Frosted Glass with gradient stroke (18dp, 18dp, 18dp, 4dp)
            val assistantBubbleShape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.97f)
                    .shadow(6.dp, assistantBubbleShape, spotColor = Color.Black.copy(alpha = 0.25f))
                    .clip(assistantBubbleShape)
                    .background(tokens.glassFillStrong)
                    .border(tokens.glassBorderStroke, assistantBubbleShape)
                    .testTag("assistant_message_bubble")
                    .padding(14.dp)
            ) {
                Column {
                    // Header with Icon & Name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(tokens.accentViolet.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = tokens.accentCyan,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open Buddy",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp
                            ),
                            color = tokens.accentViolet
                        )

                        if (message.status == MessageStatus.STREAMING) {
                            Spacer(modifier = Modifier.width(8.dp))
                            AnimatedTypingDots()
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Markdown content (+ generated media rendered from markers)
                    if (message.content.isNotEmpty()) {
                        val (cleanedContent, mediaItems) = remember(message.content) {
                            extractMediaMarkers(message.content)
                        }
                        if (cleanedContent.isNotEmpty()) {
                            MarkdownText(
                                markdown = cleanedContent,
                                textColor = tokens.textPrimary,
                                onSaveCodeToFile = onSaveCodeDraft
                            )
                        }
                        mediaItems.forEach { (kind, path) ->
                            Spacer(modifier = Modifier.height(10.dp))
                            GeneratedMediaView(kind = kind, path = path)
                        }
                    } else if (message.status == MessageStatus.STREAMING) {
                        // Subtle shimmer placeholder while streaming initial tokens
                        StreamingShimmerLine()
                    }

                    // Error presentation
                    if (message.status == MessageStatus.ERROR && !message.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ErrorBanner(
                            errorMessage = message.errorMessage,
                            onNavigateToSettings = onNavigateToSettings,
                            onRetry = onRetry
                        )
                    }

                    // Action buttons at bottom of assistant message
                    if (message.content.isNotEmpty() && message.status != MessageStatus.ERROR) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Copy message button
                            IconButton(
                                onClick = { onCopyText(message.content) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("copy_message_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy message",
                                    tint = tokens.textSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Retry button (available if not currently generating)
                            if (isLastAssistant && !isGenerating) {
                                IconButton(
                                    onClick = onRetry,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("retry_message_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry response",
                                        tint = tokens.textSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedTypingDots() {
    val tokens = GlassTheme.tokens
    val transition = rememberInfiniteTransition(label = "dots_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dots_alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.alpha(alpha)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(tokens.accentViolet)
        )
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(tokens.accentBlue)
        )
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(tokens.accentCyan)
        )
    }
}

@Composable
fun StreamingShimmerLine() {
    val tokens = GlassTheme.tokens
    val transition = rememberInfiniteTransition(label = "streaming_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "streaming_shimmer_trans"
    )

    val shimmerColors = listOf(
        tokens.textSecondary.copy(alpha = 0.1f),
        tokens.accentCyan.copy(alpha = 0.35f),
        tokens.textSecondary.copy(alpha = 0.1f)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 150f, 0f),
        end = Offset(translateAnim, 0f)
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Thinking...",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary
        )
    }
}

// Floating Glass Input Bar
@Composable
private fun FloatingGlassInputBar(
    inputText: String,
    isGenerating: Boolean,
    isVoiceListening: Boolean = false,
    selectedVoiceLanguage: String = "en-US",
    onToggleVoiceLanguage: () -> Unit = {},
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMicClick: () -> Unit = {}
) {
    val tokens = GlassTheme.tokens
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(26.dp)

    // Pulse animation for voice listening
    val infiniteTransition = rememberInfiniteTransition(label = "chat_mic_pulse")
    val micPulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isVoiceListening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isVoiceListening) 600 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chat_mic_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .navigationBarsPadding()
    ) {
        // Listening Banner if voice is active
        AnimatedVisibility(
            visible = isVoiceListening,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tokens.accentViolet.copy(alpha = 0.2f))
                    .border(BorderStroke(1.dp, tokens.accentCyan.copy(alpha = 0.4f)), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(tokens.accentCyan)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedVoiceLanguage == "ur-PK") "Listening (Urdu / اردو)... Speak now" else "Listening (English)... Speak now",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        ),
                        color = tokens.accentCyan
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, shape, spotColor = Color.Black.copy(alpha = 0.3f))
                .clip(shape)
                .background(tokens.glassFillStrong)
                .border(tokens.glassBorderStroke, shape)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voice Microphone Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .scale(if (isVoiceListening) micPulseScale else 1f)
                        .clip(CircleShape)
                        .background(
                            if (isVoiceListening) Brush.linearGradient(listOf(tokens.accentCyan, tokens.accentViolet))
                            else SolidColor(tokens.glassFill)
                        )
                        .border(
                            if (isVoiceListening) BorderStroke(1.5.dp, Color.White.copy(alpha = 0.9f))
                            else tokens.glassBorderStroke,
                            CircleShape
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onMicClick()
                        }
                        .testTag("chat_voice_mic_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVoiceListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = if (isVoiceListening) "Stop voice input" else "Start voice input",
                        tint = if (isVoiceListening) Color.White else tokens.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Voice Language Switch Badge (EN / UR)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedVoiceLanguage == "ur-PK") tokens.accentViolet.copy(alpha = 0.3f) else tokens.glassFill)
                        .border(
                            if (selectedVoiceLanguage == "ur-PK") BorderStroke(1.dp, tokens.accentViolet) else tokens.glassBorderStroke,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onToggleVoiceLanguage() }
                        .padding(horizontal = 6.dp, vertical = 5.dp)
                        .testTag("chat_voice_language_chip"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedVoiceLanguage == "ur-PK") "UR" else "EN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedVoiceLanguage == "ur-PK") tokens.accentCyan else tokens.textSecondary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Input TextField
                BasicTextField(
                    value = inputText,
                    onValueChange = onTextChanged,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .testTag("chat_input_field"),
                    textStyle = TextStyle(
                        color = tokens.textPrimary,
                        fontSize = 14.5.sp,
                        lineHeight = 20.sp
                    ),
                    cursorBrush = SolidColor(tokens.accentViolet),
                    maxLines = 5,
                    decorationBox = { innerTextField ->
                        if (inputText.isEmpty()) {
                            Text(
                                text = if (isVoiceListening) "Listening... speak now" else "Ask Open Buddy or tap mic...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                                color = if (isVoiceListening) tokens.accentCyan else tokens.textSecondary.copy(alpha = 0.65f)
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Send or Stop button
                if (isGenerating) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(tokens.error.copy(alpha = 0.25f))
                            .border(BorderStroke(1.dp, tokens.error.copy(alpha = 0.6f)), CircleShape)
                            .clickable(onClick = onStop)
                            .testTag("stop_generation_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop generation",
                            tint = tokens.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    val canSend = inputText.isNotBlank()
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed && canSend) 0.94f else 1f,
                        animationSpec = tween(100),
                        label = "send_scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .scale(scale)
                            .shadow(
                                elevation = if (canSend) 8.dp else 0.dp,
                                shape = CircleShape,
                                spotColor = tokens.accentViolet.copy(alpha = 0.5f)
                            )
                            .clip(CircleShape)
                            .background(
                                if (canSend) tokens.primaryGradient
                                else SolidColor(tokens.glassFill)
                            )
                            .border(
                                if (canSend) BorderStroke(1.dp, tokens.accentCyan.copy(alpha = 0.4f))
                                else tokens.glassBorderStroke,
                                CircleShape
                            )
                            .clickable(
                                enabled = canSend,
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSend()
                            }
                            .testTag("send_message_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = if (canSend) Color.White else tokens.textSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
