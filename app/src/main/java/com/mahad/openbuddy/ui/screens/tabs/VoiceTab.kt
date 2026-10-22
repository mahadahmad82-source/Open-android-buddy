package com.mahad.openbuddy.ui.screens.tabs

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahad.openbuddy.domain.model.ParsedVoiceCommand
import com.mahad.openbuddy.domain.model.VoiceActionHistoryItem
import com.mahad.openbuddy.domain.model.VoiceActionType
import com.mahad.openbuddy.ui.components.GlassCard
import com.mahad.openbuddy.ui.components.GlassTextField
import com.mahad.openbuddy.ui.components.GradientButton
import com.mahad.openbuddy.ui.components.PillChip
import com.mahad.openbuddy.ui.theme.GlassTheme
import com.mahad.openbuddy.ui.theme.SemanticError
import com.mahad.openbuddy.ui.theme.SemanticSuccess
import com.mahad.openbuddy.ui.theme.SemanticWarning
import com.mahad.openbuddy.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VoiceTab(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tokens = GlassTheme.tokens
    val keyboardController = LocalSoftwareKeyboardController.current

    val isListening by viewModel.isVoiceListening.collectAsStateWithLifecycle()
    val rmsLevel by viewModel.voiceRmsLevel.collectAsStateWithLifecycle()
    val transcript by viewModel.voiceTranscript.collectAsStateWithLifecycle()
    val isParsing by viewModel.isVoiceParsing.collectAsStateWithLifecycle()
    val statusMessage by viewModel.voiceStatusMessage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.voiceErrorMessage.collectAsStateWithLifecycle()
    val pendingAction by viewModel.pendingVoiceAction.collectAsStateWithLifecycle()
    val voiceHistory by viewModel.voiceActionHistory.collectAsStateWithLifecycle()
    val voiceSettings by viewModel.voiceActionSettings.collectAsStateWithLifecycle()
    val handsFreeActive by viewModel.handsFreeActive.collectAsStateWithLifecycle()

    var typedCommand by remember { mutableStateOf("") }
    var manualRecipientInput by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var showVoiceInstructions by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("en-US") } // "en-US" or "ur-PK"

    // Microphone permission goes through MainActivity: the Compose
    // RequestPermission launcher crashes on this FragmentActivity host
    // ("Can only use lower 16 bits for requestCode") — that was the Voice
    // tab crash. pendingPermissionAction remembers WHY we asked.
    var pendingPermissionAction by remember { mutableStateOf<String?>(null) }
    val latestSelectedLanguage = androidx.compose.runtime.rememberUpdatedState(selectedLanguage)
    LaunchedEffect(Unit) {
        com.mahad.openbuddy.ui.PermissionResults.recordAudio.collect { isGranted ->
            val action = pendingPermissionAction ?: return@collect
            pendingPermissionAction = null
            if (isGranted) {
                viewModel.clearVoiceError()
                if (action == "handsfree") {
                    viewModel.startHandsFree()
                } else {
                    viewModel.startVoiceListening(latestSelectedLanguage.value)
                }
            } else {
                viewModel.setVoiceError(
                    if (action == "handsfree") {
                        "Microphone permission is required for hands-free conversation."
                    } else {
                        "Microphone permission was not granted. Please allow microphone access to use voice dictation, or type your command below."
                    }
                )
            }
        }
    }

    // Pulse animations for listening orb
    val infiniteTransition = rememberInfiniteTransition(label = "voice_orb_pulse")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.38f + (rmsLevel * 0.25f) else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 700 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale_1"
    )
    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.65f + (rmsLevel * 0.4f) else 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 1000 else 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale_2"
    )

    // Preset voice commands
    val presets = remember {
        listOf(
            PresetCommand("Open YouTube", VoiceActionType.OPEN_APP, Icons.Default.Apps),
            PresetCommand("Dial +1 (555) 019-2834", VoiceActionType.DIAL, Icons.Default.Call),
            PresetCommand("Text Alex I will be 10 minutes late", VoiceActionType.SMS_COMPOSE, Icons.Default.Sms),
            PresetCommand("Email team@dev.org about Release status", VoiceActionType.EMAIL_COMPOSE, Icons.Default.Email),
            PresetCommand("WhatsApp Mom I have arrived safely", VoiceActionType.WHATSAPP_COMPOSE, Icons.Default.Chat),
            PresetCommand("Search YouTube for Android Compose Animations", VoiceActionType.YOUTUBE_SEARCH, Icons.Default.PlayCircle),
            PresetCommand("Open github.com", VoiceActionType.OPEN_URL, Icons.Default.Language),
            PresetCommand("Search web for Kotlin Coroutines Flow best practices", VoiceActionType.WEB_SEARCH, Icons.Default.Search),
            PresetCommand("Set alarm for 7:30 AM for Standup", VoiceActionType.SET_ALARM, Icons.Default.Alarm),
            PresetCommand("Set timer for 10 minutes", VoiceActionType.SET_TIMER, Icons.Default.Timer)
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Status & Safe Mode Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(tokens.accentViolet.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = tokens.accentViolet,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "VOICE AUTOMATOR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = tokens.textPrimary
                        )
                        Text(
                            text = "Safe Mode • User Confirms",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = tokens.textSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isListening) tokens.accentViolet.copy(alpha = 0.25f)
                            else if (isParsing) tokens.accentCyan.copy(alpha = 0.2f)
                            else tokens.glassFill
                        )
                        .border(
                            if (isListening) BorderStroke(1.dp, tokens.accentViolet)
                            else if (isParsing) BorderStroke(1.dp, tokens.accentCyan)
                            else tokens.glassBorderStroke,
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isListening) SemanticSuccess
                                    else if (isParsing) tokens.accentCyan
                                    else tokens.textSecondary
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isListening) "LISTENING"
                            else if (isParsing) "AI PARSING"
                            else "SAFE MODE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isListening) tokens.accentCyan
                            else if (isParsing) tokens.accentCyan
                            else tokens.textSecondary
                        )
                    }
                }
            }
        }

        // Language Selector Row (English / Urdu)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recognition Language:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = tokens.textSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillChip(
                        text = "English",
                        isSelected = selectedLanguage == "en-US",
                        onClick = { selectedLanguage = "en-US" }
                    )
                    PillChip(
                        text = "Urdu (اردو)",
                        isSelected = selectedLanguage == "ur-PK",
                        onClick = { selectedLanguage = "ur-PK" }
                    )
                }
            }
        }

        // Hands-free conversation (Phase 5): speak -> agent answers aloud -> listen again
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hands-Free Conversation",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = tokens.textPrimary
                        )
                        Text(
                            text = if (handsFreeActive) {
                                "Listening… speak naturally. Replies are read aloud. Works with Agent mode tools too."
                            } else {
                                "Talk back and forth with Open Buddy — no typing. It listens, answers out loud, then listens again."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    PillChip(
                        text = if (handsFreeActive) "Stop" else "Start",
                        isSelected = handsFreeActive,
                        onClick = {
                            if (handsFreeActive) {
                                viewModel.stopHandsFree()
                            } else {
                                val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (hasAudio) {
                                    viewModel.startHandsFree()
                                } else {
                                    pendingPermissionAction = "handsfree"
                                    (context as? com.mahad.openbuddy.MainActivity)?.requestRecordAudioPermission()
                                }
                            }
                        },
                        testTag = "hands_free_toggle_chip"
                    )
                }
            }
        }

        // Voice Instructions & Supported Commands Guide (Collapsible & Safe)
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_instructions_section_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    showVoiceInstructions = !showVoiceInstructions
                                } catch (e: Exception) {
                                    viewModel.setVoiceError("Could not toggle instructions: ${e.message}")
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(tokens.accentViolet.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = tokens.accentViolet,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Voice Instructions & Commands",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = tokens.textPrimary
                                )
                                Text(
                                    text = if (showVoiceInstructions) "Tap to hide guide" else "Tap to see supported English & Urdu commands",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = tokens.textSecondary
                                )
                            }
                        }

                        Text(
                            text = if (showVoiceInstructions) "Hide ▲" else "Show ▼",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.accentCyan
                        )
                    }

                    if (showVoiceInstructions) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tokens.accentViolet.copy(alpha = 0.12f))
                                    .border(BorderStroke(1.dp, tokens.accentViolet.copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "Speak in English or Roman Urdu / Urdu. The AI understands your speech, parses your intent, and presents a confirmation card before launching. No action runs without your confirmation.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = tokens.textPrimary
                                )
                            }

                            val commandCategories = listOf(
                                "App Launcher" to listOf("Open YouTube" to "YouTube kholo", "Open Chrome" to "Chrome browser kholo"),
                                "Phone Dialer" to listOf("Call +1 555 0199" to "Alex ko call karo", "Dial 12345" to "Number dial karo"),
                                "WhatsApp" to listOf("WhatsApp Mom I reached" to "Mom ko WhatsApp karo", "Send WhatsApp to Alex" to "Alex ko message bhejo"),
                                "SMS & Email" to listOf("Text Alex I will be late" to "Message bhejo", "Email team about report" to "Email draft karo"),
                                "Alarm & Timer" to listOf("Set alarm for 7:30 AM" to "Subah 7 baje ka alarm lagao", "Set timer for 10 minutes" to "5 minute ka timer lagao"),
                                "Web & YouTube Search" to listOf("Search YouTube for Kotlin" to "YouTube pe search karo", "Search web for Android" to "Google pe search karo")
                            )

                            commandCategories.forEach { (category, examples) ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = category.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = tokens.accentCyan
                                    )
                                    examples.forEach { (eng, urdu) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(tokens.glassFill)
                                                .clickable {
                                                    viewModel.processVoiceCommand(eng)
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(text = "• \"$eng\"", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = tokens.textPrimary)
                                                Text(text = "  \"$urdu\"", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = tokens.textSecondary)
                                            }
                                            Text(text = "Try", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = tokens.accentViolet)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dedicated Microphone Permission Banner Card (shown whenever permission is not granted)
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voice_permission_prompt_card"),
                    fillColor = tokens.accentViolet.copy(alpha = 0.16f),
                    borderStroke = BorderStroke(1.dp, tokens.accentViolet.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = tokens.accentCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Microphone Access Required",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = tokens.textPrimary
                                )
                                Text(
                                    text = "Allow microphone access to speak commands in English or Urdu.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                    color = tokens.textSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(tokens.accentViolet)
                                .clickable {
                                    pendingPermissionAction = "listen"
                                    (context as? com.mahad.openbuddy.MainActivity)?.requestRecordAudioPermission()
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                                .testTag("request_mic_permission_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Grant Access", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Central Animated Listening Orb
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier.size(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer Pulsing Ring 2
                    Box(
                        modifier = Modifier
                            .size((140 * pulseScale2).dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        (if (isListening) tokens.accentCyan else tokens.accentViolet).copy(
                                            alpha = if (isListening) 0.25f else 0.08f
                                        ),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Outer Pulsing Ring 1
                    Box(
                        modifier = Modifier
                            .size((120 * pulseScale1).dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        (if (isListening) tokens.accentCyan else tokens.accentViolet).copy(
                                            alpha = if (isListening) 0.40f else 0.15f
                                        ),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Main Interactive Orb
                    Box(
                        modifier = Modifier
                            .size(106.dp)
                            .shadow(
                                elevation = if (isListening) 28.dp else 14.dp,
                                shape = CircleShape,
                                spotColor = if (isListening) tokens.accentCyan.copy(alpha = 0.7f)
                                else tokens.accentViolet.copy(alpha = 0.5f)
                            )
                            .clip(CircleShape)
                            .background(
                                if (isListening) {
                                    Brush.linearGradient(listOf(tokens.accentCyan, tokens.accentBlue))
                                } else {
                                    tokens.primaryGradient
                                }
                            )
                            .border(
                                BorderStroke(
                                    2.dp,
                                    if (isListening) Color.White.copy(alpha = 0.9f)
                                    else Color.White.copy(alpha = 0.35f)
                                ),
                                CircleShape
                            )
                            .clickable {
                                if (isListening) {
                                    viewModel.stopVoiceListening()
                                } else {
                                    val hasAudio = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasAudio) {
                                        viewModel.startVoiceListening(selectedLanguage)
                                    } else {
                                        pendingPermissionAction = "listen"
                                        (context as? com.mahad.openbuddy.MainActivity)?.requestRecordAudioPermission()
                                    }
                                }
                            }
                            .testTag("voice_orb_mic_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isParsing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(42.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = if (isListening) "Stop listening" else "Start voice recognition",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dynamic Audio Waveform Equalizer
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(28.dp)
                ) {
                    val baseBars = listOf(14f, 22f, 10f, 26f, 18f, 12f, 24f, 10f, 28f, 16f)
                    baseBars.forEachIndexed { idx, baseH ->
                        val dynamicFactor = if (isListening) {
                            (1f + (rmsLevel * (0.8f + (idx % 3) * 0.4f))).coerceIn(1f, 2.2f)
                        } else 0.35f

                        val barHeight = (baseH * dynamicFactor).dp.coerceIn(4.dp, 28.dp)

                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isListening) tokens.accentCyan
                                    else tokens.textSecondary.copy(alpha = 0.25f)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Transcript & Instructions
                Text(
                    text = when {
                        isParsing -> "Parsing intent with AI..."
                        transcript.isNotBlank() -> transcript
                        isListening -> "Listening... Speak your command now"
                        else -> "Tap the orb to speak, or type a command below."
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.5.sp,
                        fontWeight = if (isListening || transcript.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (transcript.isNotBlank()) tokens.textPrimary else tokens.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }

        // 3. Error Banner (if any)
        if (!errorMessage.isNullOrBlank()) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    fillColor = SemanticError.copy(alpha = 0.12f),
                    borderStroke = BorderStroke(1.dp, SemanticError.copy(alpha = 0.5f))
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
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = SemanticError,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = SemanticError
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearVoiceError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = tokens.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Status Notification
        if (!statusMessage.isNullOrBlank() && errorMessage.isNullOrBlank() && pendingAction == null) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    fillColor = tokens.accentCyan.copy(alpha = 0.1f),
                    borderStroke = BorderStroke(1.dp, tokens.accentCyan.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statusMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.accentCyan
                        )
                        IconButton(
                            onClick = { viewModel.clearVoiceStatus() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss status",
                                tint = tokens.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. MANDATORY Parsed Intent Confirmation Card
        if (pendingAction != null) {
            item {
                val action = pendingAction!!
                val confidencePct = (action.confidence * 100).toInt()
                val confidenceColor = when {
                    action.confidence >= 0.85f -> SemanticSuccess
                    action.confidence >= 0.60f -> SemanticWarning
                    else -> SemanticError
                }

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = tokens.accentViolet.copy(alpha = 0.4f)),
                    fillColor = tokens.glassFillStrong,
                    borderStroke = BorderStroke(1.5.dp, tokens.accentViolet.copy(alpha = 0.8f))
                ) {
                    // Header with Action Icon & Confidence Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(tokens.primaryGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getActionIcon(action.actionType),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = action.actionType.displayName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    color = tokens.textPrimary
                                )
                                Text(
                                    text = "Command: \"${action.rawCommand}\"",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                    color = tokens.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Confidence Meter Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(confidenceColor.copy(alpha = 0.15f))
                                .border(BorderStroke(1.dp, confidenceColor.copy(alpha = 0.4f)), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$confidencePct% Confident",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = confidenceColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Intent Target Details Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(tokens.glassFill)
                            .border(tokens.glassBorderStroke, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Target: ",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = tokens.textSecondary
                                )
                                Text(
                                    text = action.target,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = tokens.accentCyan
                                )
                            }

                            if (!action.message.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        text = "Details: ",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = tokens.textSecondary
                                    )
                                    Text(
                                        text = action.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = tokens.textPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Manual Recipient Fallback Input if number is needed
                    if (action.requiresContactResolution || (action.actionType in listOf(VoiceActionType.DIAL, VoiceActionType.SMS_COMPOSE, VoiceActionType.WHATSAPP_COMPOSE) && action.resolvedNumber.isNullOrBlank())) {
                        Spacer(modifier = Modifier.height(10.dp))
                        GlassTextField(
                            value = manualRecipientInput,
                            onValueChange = { manualRecipientInput = it },
                            placeholder = "Enter or paste phone number (e.g. +1 555 0199)...",
                            leadingIcon = Icons.Default.Call,
                            singleLine = true,
                            testTag = "voice_manual_recipient_input"
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Confirm and Cancel Action Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(tokens.glassFill)
                                .border(tokens.glassBorderStroke, RoundedCornerShape(50))
                                .clickable { viewModel.cancelPendingVoiceAction() }
                                .padding(horizontal = 16.dp, vertical = 11.dp)
                                .testTag("voice_cancel_action_button")
                        ) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = tokens.textSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        GradientButton(
                            text = "Launch Action",
                            onClick = {
                                viewModel.executeConfirmedVoiceAction(
                                    context = context,
                                    command = action,
                                    manualRecipientOverride = manualRecipientInput.ifBlank { null }
                                )
                                manualRecipientInput = ""
                            },
                            leadingIcon = Icons.Default.PlayArrow,
                            testTag = "voice_confirm_action_button"
                        )
                    }
                }
            }
        }

        // 6. Typed Command Input Field
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "TYPE OR DICTATE COMMAND",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassTextField(
                        value = typedCommand,
                        onValueChange = { typedCommand = it },
                        placeholder = "e.g., \"Open YouTube\", \"Call 555-0199\", \"Set timer 5m\"...",
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (typedCommand.isNotBlank()) {
                                viewModel.processVoiceCommand(typedCommand)
                                typedCommand = ""
                                keyboardController?.hide()
                            }
                        }),
                        testTag = "voice_typed_command_input"
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (typedCommand.isNotBlank()) tokens.primaryGradient
                                else Brush.linearGradient(listOf(tokens.glassFill, tokens.glassFill))
                            )
                            .border(tokens.glassBorderStroke, CircleShape)
                            .clickable(enabled = typedCommand.isNotBlank()) {
                                viewModel.processVoiceCommand(typedCommand)
                                typedCommand = ""
                                keyboardController?.hide()
                            }
                            .testTag("voice_send_typed_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send command",
                            tint = if (typedCommand.isNotBlank()) Color.White else tokens.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 7. Suggested Voice Commands Presets
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SUGGESTED COMMANDS (TAP TO TEST)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presets) { preset ->
                        val isEnabled = voiceSettings[preset.type] ?: true
                        GlassCard(
                            onClick = {
                                viewModel.processVoiceCommand(preset.command)
                            },
                            modifier = Modifier.width(260.dp),
                            fillColor = if (isEnabled) tokens.glassFill else tokens.glassFill.copy(alpha = 0.5f),
                            testTag = "preset_chip_${preset.type.actionKey}"
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
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(tokens.accentViolet.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = preset.icon,
                                            contentDescription = null,
                                            tint = tokens.accentCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = preset.type.displayName,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = tokens.textSecondary
                                        )
                                        Text(
                                            text = preset.command,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = tokens.textPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Run",
                                    tint = tokens.accentViolet,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 8. Action History List
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT VOICE ACTIONS (${voiceHistory.size})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = tokens.textSecondary
                    )

                    if (voiceHistory.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { viewModel.clearVoiceHistory() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("voice_clear_history_button")
                        ) {
                            Text(
                                text = "Clear All",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textSecondary
                            )
                        }
                    }
                }
            }
        }

        if (voiceHistory.isEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = tokens.textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No voice actions recorded yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }
            }
        } else {
            items(voiceHistory.take(15), key = { it.id }) { item ->
                val actionType = VoiceActionType.fromActionKey(item.actionType)
                val icon = if (actionType != null) getActionIcon(actionType) else Icons.Default.GraphicEq
                val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.timestamp))

                val statusColor = when (item.status) {
                    "EXECUTED" -> SemanticSuccess
                    "CANCELLED" -> tokens.textSecondary
                    "REJECTED" -> SemanticWarning
                    else -> SemanticError
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "voice_history_item_${item.id}"
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
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(tokens.accentViolet.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = tokens.accentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = actionType?.displayName ?: item.actionType.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = tokens.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• $dateStr",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = tokens.textSecondary
                                    )
                                }
                                Text(
                                    text = "\"${item.rawCommand}\"",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                    color = tokens.accentCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!item.message.isNullOrBlank()) {
                                    Text(
                                        text = item.message,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = tokens.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(statusColor.copy(alpha = 0.12f))
                                    .border(BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)), RoundedCornerShape(50))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = item.status,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = statusColor
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = { viewModel.processVoiceCommand(item.rawCommand) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Re-run command",
                                    tint = tokens.accentViolet,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private data class PresetCommand(
    val command: String,
    val type: VoiceActionType,
    val icon: ImageVector
)

private fun getActionIcon(type: VoiceActionType): ImageVector {
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
