package com.mahad.openbuddy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahad.openbuddy.domain.model.Conversation
import com.mahad.openbuddy.ui.theme.GlassTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationsDrawerContent(
    conversations: List<Conversation>,
    activeConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onRenameConversation: (id: String, newTitle: String) -> Unit,
    onDeleteConversation: (id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = GlassTheme.tokens
    var renamingConversation by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deletingConversation by remember { mutableStateOf<Conversation?>(null) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(tokens.backgroundEnd.copy(alpha = 0.95f))
            .border(
                1.dp,
                Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            )
            .statusBarsPadding()
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Conversations",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = tokens.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // New Chat Action with primary gradient
            GradientButton(
                text = "New Chat",
                onClick = onNewConversation,
                leadingIcon = Icons.Default.Add,
                modifier = Modifier.fillMaxWidth(),
                testTag = "new_chat_button"
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(8.dp))

            // Conversations List
            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No conversations yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(conversations, key = { it.id }) { item ->
                        ConversationItemRow(
                            conversation = item,
                            isActive = item.id == activeConversationId,
                            onSelect = { onSelectConversation(item.id) },
                            onRenameClick = {
                                renamingConversation = item
                                renameText = item.title
                            },
                            onDeleteClick = {
                                deletingConversation = item
                            }
                        )
                    }
                }
            }
        }
    }

    // Rename Dialog (Glass style)
    if (renamingConversation != null) {
        GlassDialog(
            onDismissRequest = { renamingConversation = null },
            title = "Rename Conversation",
            icon = Icons.Default.Edit,
            iconTint = tokens.accentViolet,
            message = "Enter a new title for this conversation:",
            confirmButtonText = "Save",
            confirmButtonEnabled = renameText.isNotBlank(),
            onConfirm = {
                renamingConversation?.let { conv ->
                    if (renameText.isNotBlank()) {
                        onRenameConversation(conv.id, renameText.trim())
                    }
                }
                renamingConversation = null
            },
            confirmTestTag = "confirm_rename_button",
            dismissButtonText = "Cancel",
            content = {
                GlassTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    placeholder = "Conversation title...",
                    singleLine = true,
                    testTag = "rename_conversation_input"
                )
            }
        )
    }

    // Delete Confirmation Dialog (Safety Rule)
    if (deletingConversation != null) {
        GlassDialog(
            onDismissRequest = { deletingConversation = null },
            title = "Delete Conversation",
            icon = Icons.Default.Delete,
            iconTint = tokens.error,
            message = "Are you sure you want to delete '${deletingConversation?.title}'? All chat messages and generated code in this session will be permanently removed.",
            confirmButtonText = "Delete",
            isDestructive = true,
            onConfirm = {
                deletingConversation?.let { onDeleteConversation(it.id) }
                deletingConversation = null
            },
            confirmTestTag = "confirm_delete_button",
            dismissButtonText = "Cancel"
        )
    }
}

@Composable
private fun ConversationItemRow(
    conversation: Conversation,
    isActive: Boolean,
    onSelect: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val tokens = GlassTheme.tokens
    var menuExpanded by remember { mutableStateOf(false) }

    val formattedDate = remember(conversation.updatedAt) {
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        sdf.format(Date(conversation.updatedAt))
    }

    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isActive) tokens.accentViolet.copy(alpha = 0.22f)
                else Color.Transparent
            )
            .border(
                if (isActive) BorderStroke(1.dp, tokens.accentViolet.copy(alpha = 0.45f))
                else BorderStroke(0.dp, Color.Transparent),
                shape
            )
            .clickable(onClick = onSelect)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = if (isActive) tokens.accentCyan else tokens.textSecondary,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isActive) tokens.textPrimary else tokens.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = tokens.textSecondary.copy(alpha = 0.7f)
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Conversation options",
                        tint = tokens.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier
                        .background(tokens.backgroundEnd.copy(alpha = 0.96f))
                        .border(tokens.glassBorderStroke, RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = tokens.textPrimary) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = tokens.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRenameClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = tokens.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = tokens.error,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}
