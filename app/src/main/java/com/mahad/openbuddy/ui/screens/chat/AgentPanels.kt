package com.mahad.openbuddy.ui.screens.chat

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.mahad.openbuddy.data.agent.ToolApprovalRequest
import com.mahad.openbuddy.data.local.entity.McpServerEntity
import com.mahad.openbuddy.data.local.entity.MemoryEntity
import com.mahad.openbuddy.data.local.entity.SkillEntity
import com.mahad.openbuddy.ui.components.GlassCard
import com.mahad.openbuddy.ui.components.GlassTextField
import com.mahad.openbuddy.ui.components.GradientButton
import com.mahad.openbuddy.ui.components.PillChip
import com.mahad.openbuddy.ui.theme.GlassTheme
import java.io.File

// ============================================================================
// Media markers: agent results embed [[image:/path]] / [[video:/path]] lines
// in the assistant message; the chat renders them as real media below the text.
// ============================================================================

private val mediaMarkerRegex = Regex("\\[\\[(image|video):([^\\]]+)]]")

fun extractMediaMarkers(content: String): Pair<String, List<Pair<String, String>>> {
    val media = mediaMarkerRegex.findAll(content).map { it.groupValues[1] to it.groupValues[2] }.toList()
    val cleaned = mediaMarkerRegex.replace(content, "").trim()
    return cleaned to media
}

@Composable
fun GeneratedMediaView(kind: String, path: String) {
    val context = LocalContext.current
    val tokens = GlassTheme.tokens
    val file = remember(path) { File(path) }

    fun openExternally() {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, if (kind == "video") "video/*" else "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) { /* viewer missing — ignore */ }
    }

    if (kind == "image") {
        val bitmap = remember(path) {
            try {
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Generated image",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { openExternally() }
            )
        } else {
            Text(
                text = "Image saved: ${file.name}",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }
    } else {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = tokens.glassFillStrong,
            border = tokens.glassBorderStroke,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { openExternally() }
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = tokens.accentCyan, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Generated video", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = tokens.textPrimary)
                    Text(file.name, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                }
            }
        }
    }
}

// ============================================================================
// Agent toolbar (mode toggle, skill picker, speak toggle, hub entry)
// ============================================================================

@Composable
fun AgentToolbar(
    agentMode: Boolean,
    onToggleAgentMode: (Boolean) -> Unit,
    skills: List<SkillEntity>,
    activeSkillId: String?,
    onSelectSkill: (String?) -> Unit,
    speakReplies: Boolean,
    onToggleSpeakReplies: (Boolean) -> Unit,
    onOpenHub: () -> Unit
) {
    var skillMenuExpanded by remember { mutableStateOf(false) }
    val activeSkill = skills.firstOrNull { it.id == activeSkillId }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PillChip(
            text = if (agentMode) "Agent: ON" else "Agent: OFF",
            isSelected = agentMode,
            onClick = { onToggleAgentMode(!agentMode) },
            testTag = "agent_mode_chip"
        )
        if (agentMode) {
            Box {
                PillChip(
                    text = "Skill: ${activeSkill?.name ?: "None"}",
                    isSelected = activeSkill != null,
                    onClick = { skillMenuExpanded = true },
                    testTag = "agent_skill_chip"
                )
                DropdownMenu(expanded = skillMenuExpanded, onDismissRequest = { skillMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("None", fontSize = 13.sp) },
                        onClick = { onSelectSkill(null); skillMenuExpanded = false }
                    )
                    skills.forEach { skill ->
                        DropdownMenuItem(
                            text = { Text(skill.name, fontSize = 13.sp) },
                            onClick = { onSelectSkill(skill.id); skillMenuExpanded = false }
                        )
                    }
                }
            }
            PillChip(
                text = if (speakReplies) "Speak: ON" else "Speak: OFF",
                isSelected = speakReplies,
                onClick = { onToggleSpeakReplies(!speakReplies) },
                testTag = "agent_speak_chip"
            )
            PillChip(
                text = "Skills & Memory",
                isSelected = false,
                onClick = onOpenHub,
                testTag = "agent_hub_chip"
            )
        }
    }
}

// ============================================================================
// Tool approval card
// ============================================================================

@Composable
fun ToolApprovalCard(
    request: ToolApprovalRequest,
    onDecision: (Boolean) -> Unit
) {
    val tokens = GlassTheme.tokens
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        fillColor = Color(0xFFF59E0B).copy(alpha = 0.12f),
        borderStroke = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.55f))
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Approval needed — ${request.toolName}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = tokens.textPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = request.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onDecision(false) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reject")
                }
                GradientButton(
                    text = "Approve",
                    onClick = { onDecision(true) },
                    leadingIcon = Icons.Default.Check,
                    modifier = Modifier.weight(1f),
                    testTag = "approve_tool_button"
                )
            }
        }
    }
}

// ============================================================================
// Agent hub: skills, memory and MCP server management
// ============================================================================

@Composable
fun AgentHubDialog(
    skills: List<SkillEntity>,
    activeSkillId: String?,
    onSelectSkill: (String?) -> Unit,
    onSaveSkill: (String?, String, String, String) -> Unit,
    onDeleteSkill: (String) -> Unit,
    memories: List<MemoryEntity>,
    onAddMemory: (String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    mcpServers: List<McpServerEntity>,
    onAddMcpServer: (String, String, String?) -> Unit,
    onDeleteMcpServer: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = GlassTheme.tokens
    var tab by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            fillColor = tokens.backgroundEnd.copy(alpha = 0.97f)
        ) {
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Agent Hub",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = tokens.textPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = tokens.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillChip(text = "Skills", isSelected = tab == 0, onClick = { tab = 0 }, testTag = "hub_tab_skills")
                    PillChip(text = "Memory", isSelected = tab == 1, onClick = { tab = 1 }, testTag = "hub_tab_memory")
                    PillChip(text = "MCP Servers", isSelected = tab == 2, onClick = { tab = 2 }, testTag = "hub_tab_mcp")
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (tab) {
                    0 -> SkillsTab(
                        skills = skills,
                        activeSkillId = activeSkillId,
                        onSelectSkill = onSelectSkill,
                        onSaveSkill = onSaveSkill,
                        onDeleteSkill = onDeleteSkill
                    )
                    1 -> MemoryTab(
                        memories = memories,
                        onAddMemory = onAddMemory,
                        onDeleteMemory = onDeleteMemory
                    )
                    else -> McpTab(
                        servers = mcpServers,
                        onAddMcpServer = onAddMcpServer,
                        onDeleteMcpServer = onDeleteMcpServer
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillsTab(
    skills: List<SkillEntity>,
    activeSkillId: String?,
    onSelectSkill: (String?) -> Unit,
    onSaveSkill: (String?, String, String, String) -> Unit,
    onDeleteSkill: (String) -> Unit
) {
    val tokens = GlassTheme.tokens
    var editingId by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        if (showForm) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassTextField(value = name, onValueChange = { name = it }, placeholder = "Skill name", modifier = Modifier.fillMaxWidth())
                GlassTextField(value = description, onValueChange = { description = it }, placeholder = "Short description", modifier = Modifier.fillMaxWidth())
                GlassTextField(value = instructions, onValueChange = { instructions = it }, placeholder = "Instructions for the agent…", modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showForm = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    GradientButton(
                        text = "Save Skill",
                        onClick = {
                            onSaveSkill(editingId, name, description, instructions)
                            showForm = false
                        },
                        enabled = name.isNotBlank() && instructions.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(skills) { skill ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = tokens.glassFill,
                        border = tokens.glassBorderStroke,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = skill.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = tokens.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (skill.id == activeSkillId) {
                                    Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = tokens.accentCyan, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (skill.description.isNotBlank()) {
                                Text(skill.description, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(
                                    onClick = { onSelectSkill(if (skill.id == activeSkillId) null else skill.id) }
                                ) {
                                    Text(if (skill.id == activeSkillId) "Deactivate" else "Activate", fontSize = 12.sp)
                                }
                                IconButton(onClick = {
                                    editingId = skill.id
                                    name = skill.name
                                    description = skill.description
                                    instructions = skill.instructions
                                    showForm = true
                                }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit skill", tint = tokens.textSecondary, modifier = Modifier.size(17.dp))
                                }
                                IconButton(onClick = { onDeleteSkill(skill.id) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete skill", tint = tokens.textSecondary, modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            GradientButton(
                text = "Add Skill",
                onClick = {
                    editingId = null
                    name = ""
                    description = ""
                    instructions = ""
                    showForm = true
                },
                leadingIcon = Icons.Default.Add,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MemoryTab(
    memories: List<MemoryEntity>,
    onAddMemory: (String) -> Unit,
    onDeleteMemory: (String) -> Unit
) {
    val tokens = GlassTheme.tokens
    var newFact by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        Text(
            text = "Facts the agent remembers about you. It can also add facts itself with the remember_fact tool.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (memories.isEmpty()) {
            Text("No memories yet.", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(memories) { memory ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = tokens.glassFill,
                        border = tokens.glassBorderStroke,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(memory.fact, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                                Text(memory.category, style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                            }
                            IconButton(onClick = { onDeleteMemory(memory.id) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Forget this", tint = tokens.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassTextField(
                value = newFact,
                onValueChange = { newFact = it },
                placeholder = "New fact to remember…",
                modifier = Modifier.weight(1f)
            )
            GradientButton(
                text = "Add",
                onClick = {
                    if (newFact.isNotBlank()) {
                        onAddMemory(newFact.trim())
                        newFact = ""
                    }
                },
                enabled = newFact.isNotBlank()
            )
        }
    }
}

@Composable
private fun McpTab(
    servers: List<McpServerEntity>,
    onAddMcpServer: (String, String, String?) -> Unit,
    onDeleteMcpServer: (String) -> Unit
) {
    val tokens = GlassTheme.tokens
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        Text(
            text = "MCP (Model Context Protocol) servers expose extra tools to the agent over HTTP. Their tools appear automatically in agent mode.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (servers.isEmpty()) {
            Text("No MCP servers configured.", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(servers) { server ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = tokens.glassFill,
                        border = tokens.glassBorderStroke,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(server.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = tokens.textPrimary)
                                Text(server.url, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = tokens.textSecondary)
                            }
                            IconButton(onClick = { onDeleteMcpServer(server.id) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove server", tint = tokens.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassTextField(value = name, onValueChange = { name = it }, placeholder = "Server name (e.g. Filesystem)", modifier = Modifier.fillMaxWidth())
            GlassTextField(value = url, onValueChange = { url = it }, placeholder = "Server URL (https://…/mcp)", modifier = Modifier.fillMaxWidth())
            GlassTextField(value = token, onValueChange = { token = it }, placeholder = "Bearer token (optional)", modifier = Modifier.fillMaxWidth())
            GradientButton(
                text = "Add MCP Server",
                onClick = {
                    onAddMcpServer(name.trim(), url.trim(), token)
                    name = ""
                    url = ""
                    token = ""
                },
                enabled = name.isNotBlank() && url.startsWith("http"),
                leadingIcon = Icons.Default.Add,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
