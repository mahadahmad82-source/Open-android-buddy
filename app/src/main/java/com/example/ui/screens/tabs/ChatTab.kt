package com.example.ui.screens.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ChatMessage
import com.example.domain.model.Conversation
import com.example.domain.model.MessageRole
import com.example.ui.components.CodeBlockView
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassCard
import com.example.ui.components.PillChip
import com.example.ui.theme.GlassTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatTab(
    conversations: List<Conversation>,
    selectedConversationId: String?,
    messages: List<ChatMessage>,
    isGeneratingResponse: Boolean,
    onSelectConversation: (String) -> Unit,
    onCreateNewConversation: () -> Unit,
    onDeleteConversation: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = GlassTheme.tokens
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size, isGeneratingResponse) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Sessions Horizontal Selector Bar
        ConversationSelectorBar(
            conversations = conversations,
            selectedId = selectedConversationId,
            onSelect = onSelectConversation,
            onNew = onCreateNewConversation,
            onDelete = onDeleteConversation
        )

        // Messages List or Empty State
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && !isGeneratingResponse) {
                EmptyStateView(
                    icon = Icons.Default.ChatBubbleOutline,
                    title = "Open Buddy Offline Assistant",
                    message = "No messages yet in this session. Ask for code snippets, database queries, Kotlin patterns, or architecture advice.",
                    actionButtonText = "⚡ Generate Room Entity Sample",
                    onActionClick = {
                        onSendMessage("Generate a Room database entity in Kotlin")
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        ChatMessageItem(message = message)
                    }

                    if (isGeneratingResponse) {
                        item {
                            AssistantThinkingItem()
                        }
                    }
                }
            }
        }

        // Quick Suggestion Chips
        QuickSuggestionChips(
            onSuggestionClick = { suggestion ->
                inputText = suggestion
            }
        )

        // Chat Input Bar
        ChatInputBar(
            inputText = inputText,
            isGenerating = isGeneratingResponse,
            onTextChanged = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    val prompt = inputText.trim()
                    inputText = ""
                    onSendMessage(prompt)
                }
            }
        )
    }
}

@Composable
private fun ConversationSelectorBar(
    conversations: List<Conversation>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit
) {
    val tokens = GlassTheme.tokens

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.glassFill)
            .border(tokens.glassBorderStroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(conversations, key = { it.id }) { conv ->
                    val isSelected = conv.id == selectedId
                    PillChip(
                        text = conv.title.take(18) + if (conv.title.length > 18) "…" else "",
                        isSelected = isSelected,
                        onClick = { onSelect(conv.id) },
                        testTag = "conversation_chip_${conv.id}"
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // New Conversation Button
            IconButton(
                onClick = onNew,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tokens.primaryGradient)
                    .testTag("new_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Session",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatMessageItem(message: ChatMessage) {
    val tokens = GlassTheme.tokens
    val isUser = message.role == MessageRole.USER
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (isUser) "user_message_row" else "assistant_message_row"),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(tokens.accentViolet.copy(alpha = 0.22f))
                    .border(1.dp, tokens.accentViolet.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = tokens.accentCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.width(if (message.codeSnippet != null) 320.dp else 280.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            val shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            )

            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(
                        if (isUser) tokens.primaryGradient
                        else SolidColor(tokens.glassFillStrong)
                    )
                    .border(
                        if (isUser) BorderStroke(0.dp, Color.Transparent)
                        else tokens.glassBorderStroke,
                        shape
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) Color.White else tokens.textPrimary
                    )

                    if (message.codeSnippet != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        CodeBlockView(
                            code = message.codeSnippet,
                            language = message.codeLanguage ?: "kotlin"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = tokens.textSecondary.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun AssistantThinkingItem() {
    val tokens = GlassTheme.tokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(tokens.accentViolet.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = tokens.accentCyan
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Analyzing syntax...",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary
        )
    }
}

@Composable
private fun QuickSuggestionChips(
    onSuggestionClick: (String) -> Unit
) {
    val suggestions = listOf(
        "⚡ Room Entity",
        "🔍 SQLite Query",
        "🛠️ Coroutines Flow",
        "🛡️ BYOK Security"
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(suggestions) { text ->
            PillChip(
                text = text,
                isSelected = false,
                onClick = { onSuggestionClick(text.substringAfter(" ")) },
                testTag = "suggestion_chip_$text"
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    isGenerating: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    val tokens = GlassTheme.tokens
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(shape)
            .background(tokens.glassFillStrong)
            .border(tokens.glassBorderStroke, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = onTextChanged,
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                textStyle = TextStyle(
                    color = tokens.textPrimary,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(tokens.accentViolet),
                maxLines = 4,
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text(
                            text = "Ask code assistant...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary.copy(alpha = 0.6f)
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Send Button with gradient
            val canSend = inputText.isNotBlank() && !isGenerating
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) tokens.primaryGradient
                        else SolidColor(tokens.glassFill)
                    )
                    .clickable(enabled = canSend, onClick = onSend)
                    .testTag("chat_send_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) Color.White else tokens.textSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
