package com.example.ui.screens.tabs

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.supabase.SqlSafetyClassifier
import com.example.data.remote.supabase.SqlSyntaxHighlighter
import com.example.domain.model.AiProviderConfig
import com.example.domain.model.DatabaseTableInfo
import com.example.domain.model.QueryHistoryItem
import com.example.domain.model.QueryResult
import com.example.domain.model.StatementClassification
import com.example.domain.model.StatementSafetyType
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDialog
import com.example.ui.components.GlassTextField
import com.example.ui.components.GradientButton
import com.example.ui.components.GradientSwitch
import com.example.ui.components.PillChip
import com.example.ui.components.ShimmerListSkeleton
import com.example.ui.screens.tabs.database.DatabaseResultChartView
import com.example.ui.theme.CodeTextStyle
import com.example.ui.theme.GlassTheme
import com.example.ui.theme.SemanticError
import com.example.ui.theme.SemanticSuccess
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DatabaseTab(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = GlassTheme.tokens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val tables by viewModel.supabaseTables.collectAsStateWithLifecycle()
    val isSchemaLoading by viewModel.isSupabaseSchemaLoading.collectAsStateWithLifecycle()
    val schemaError by viewModel.supabaseSchemaError.collectAsStateWithLifecycle()
    val isQueryRunning by viewModel.isSupabaseQueryRunning.collectAsStateWithLifecycle()
    val isAiGenerating by viewModel.isSupabaseAiGenerating.collectAsStateWithLifecycle()
    val lastQueryResult by viewModel.supabaseLastQueryResult.collectAsStateWithLifecycle()
    val queryHistory by viewModel.supabaseQueryHistory.collectAsStateWithLifecycle()
    val isWriteMode by viewModel.isSupabaseWriteMode.collectAsStateWithLifecycle()
    val hasCredentials by viewModel.hasSupabaseCredentials.collectAsStateWithLifecycle()
    val projectRef by viewModel.supabaseProjectRef.collectAsStateWithLifecycle()
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()

    var selectedSubTabIndex by remember { mutableIntStateOf(0) }
    var sqlQueryInput by remember { mutableStateOf("SELECT * FROM information_schema.tables WHERE table_schema = 'public' LIMIT 20;") }
    var plainEnglishPrompt by remember { mutableStateOf("") }

    // AI Explanation & Optimization dialog states
    var aiDialogTitle by remember { mutableStateOf<String?>(null) }
    var aiDialogContent by remember { mutableStateOf<String?>(null) }
    var aiGeneratedSql by remember { mutableStateOf<String?>(null) }
    var aiGeneratedExplanation by remember { mutableStateOf<String?>(null) }

    // Safety Confirmation Dialogs
    var pendingExecutionSql by remember { mutableStateOf<String?>(null) }
    var pendingClassification by remember { mutableStateOf<StatementClassification?>(null) }
    var showDestructiveDialog by remember { mutableStateOf(false) }
    var showMutatingDialog by remember { mutableStateOf(false) }
    var showMultipleStatementsDialog by remember { mutableStateOf(false) }
    var showReadOnlyBlockedDialog by remember { mutableStateOf(false) }

    // Cell detail dialog
    var selectedCellDetail by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Results view mode (0 = Table, 1 = Chart)
    var resultsViewMode by remember { mutableIntStateOf(0) }

    // Auto-load schema when connected and list is empty
    LaunchedEffect(hasCredentials) {
        if (hasCredentials && tables.isEmpty()) {
            viewModel.refreshSupabaseSchema()
        }
    }

    // Function to handle execute with safety validation
    fun attemptExecuteQuery(sqlToRun: String) {
        val trimmed = sqlToRun.trim()
        if (trimmed.isBlank()) return

        val classification = SqlSafetyClassifier.classify(trimmed)

        if (classification.hasMultipleStatements) {
            pendingClassification = classification
            showMultipleStatementsDialog = true
            return
        }

        if (!isWriteMode && classification.type != StatementSafetyType.READ_ONLY) {
            pendingClassification = classification
            showReadOnlyBlockedDialog = true
            return
        }

        when (classification.type) {
            StatementSafetyType.READ_ONLY -> {
                // Safe to run read-only directly
                scope.launch {
                    viewModel.executeSupabaseQuery(trimmed)
                    selectedSubTabIndex = 2 // Switch to Results tab
                }
            }
            StatementSafetyType.MUTATING_WRITE -> {
                pendingExecutionSql = trimmed
                pendingClassification = classification
                showMutatingDialog = true
            }
            StatementSafetyType.DESTRUCTIVE -> {
                pendingExecutionSql = trimmed
                pendingClassification = classification
                showDestructiveDialog = true
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Top Header: Connection status badge + Write Mode Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Supabase Project Ref Connection Badge
            Surface(
                shape = RoundedCornerShape(50),
                color = if (hasCredentials) tokens.accentCyan.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, if (hasCredentials) tokens.accentCyan.copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier
                    .clickable { onNavigateToSettings() }
                    .testTag("supabase_connection_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (hasCredentials) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.size(7.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasCredentials) "Supabase: ${projectRef?.take(14) ?: "Connected"}…" else "Supabase Not Configured",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (hasCredentials) tokens.accentCyan else MaterialTheme.colorScheme.error
                    )
                }
            }

            // Write Mode Toggle with Safety Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isWriteMode) "Write Mode" else "Read-Only",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isWriteMode) Color(0xFFF59E0B) else Color(0xFF10B981)
                    )
                )
                GradientSwitch(
                    checked = isWriteMode,
                    onCheckedChange = { viewModel.setSupabaseWriteMode(it) },
                    modifier = Modifier.testTag("database_write_mode_switch")
                )
            }
        }

        // Sub-Navigation Tabs: Schema, SQL Console, Results, History
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PillChip(
                text = "Schema (${tables.size})",
                isSelected = selectedSubTabIndex == 0,
                onClick = { selectedSubTabIndex = 0 },
                icon = Icons.Default.TableChart,
                testTag = "db_subtab_schema"
            )
            PillChip(
                text = "SQL Console",
                isSelected = selectedSubTabIndex == 1,
                onClick = { selectedSubTabIndex = 1 },
                icon = Icons.Default.Storage,
                testTag = "db_subtab_console"
            )
            PillChip(
                text = "Results",
                isSelected = selectedSubTabIndex == 2,
                onClick = { selectedSubTabIndex = 2 },
                icon = Icons.Default.BarChart,
                testTag = "db_subtab_results"
            )
            PillChip(
                text = "History",
                isSelected = selectedSubTabIndex == 3,
                onClick = { selectedSubTabIndex = 3 },
                icon = Icons.Default.History,
                testTag = "db_subtab_history"
            )
        }

        // Main Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedSubTabIndex) {
                0 -> {
                    // Schema Explorer
                    SupabaseSchemaExplorerView(
                        tables = tables,
                        isLoading = isSchemaLoading,
                        error = schemaError,
                        hasCredentials = hasCredentials,
                        onRefresh = { viewModel.refreshSupabaseSchema() },
                        onNavigateToSettings = onNavigateToSettings,
                        onPreviewTable = { tableName ->
                            scope.launch {
                                viewModel.previewSupabaseTable(tableName)
                                selectedSubTabIndex = 2 // Switch to results
                            }
                        },
                        onQueryTable = { tableName ->
                            sqlQueryInput = "SELECT * FROM \"$tableName\" LIMIT 50;"
                            selectedSubTabIndex = 1 // Switch to console
                        }
                    )
                }
                1 -> {
                    // SQL Console with AI Generator, Editor, Explain & Optimize
                    SupabaseSqlConsoleView(
                        queryInput = sqlQueryInput,
                        promptInput = plainEnglishPrompt,
                        isQueryRunning = isQueryRunning,
                        isAiGenerating = isAiGenerating,
                        activeProvider = activeProvider,
                        tables = tables,
                        onQueryInputChanged = { sqlQueryInput = it },
                        onPromptInputChanged = { plainEnglishPrompt = it },
                        onRunQuery = { attemptExecuteQuery(sqlQueryInput) },
                        onGenerateSqlFromPrompt = {
                            scope.launch {
                                val res = viewModel.generateSupabaseSql(plainEnglishPrompt)
                                if (res.isSuccess) {
                                    val (generatedSql, explanation) = res.getOrThrow()
                                    aiGeneratedSql = generatedSql
                                    aiGeneratedExplanation = explanation
                                    sqlQueryInput = generatedSql
                                } else {
                                    aiDialogTitle = "AI Generation Error"
                                    aiDialogContent = res.exceptionOrNull()?.message ?: "Failed to generate SQL"
                                }
                            }
                        },
                        onExplainQuery = {
                            scope.launch {
                                val res = viewModel.explainSupabaseQuery(sqlQueryInput)
                                if (res.isSuccess) {
                                    aiDialogTitle = "Query Explanation"
                                    aiDialogContent = res.getOrThrow()
                                } else {
                                    aiDialogTitle = "Explanation Error"
                                    aiDialogContent = res.exceptionOrNull()?.message ?: "Failed to explain query"
                                }
                            }
                        },
                        onOptimizeQuery = {
                            scope.launch {
                                val res = viewModel.optimizeSupabaseQuery(sqlQueryInput)
                                if (res.isSuccess) {
                                    aiDialogTitle = "Query Optimization Suggestions"
                                    aiDialogContent = res.getOrThrow()
                                } else {
                                    aiDialogTitle = "Optimization Error"
                                    aiDialogContent = res.exceptionOrNull()?.message ?: "Failed to optimize query"
                                }
                            }
                        }
                    )
                }
                2 -> {
                    // Query Results with Table, Chart & Export
                    SupabaseResultsView(
                        queryResult = lastQueryResult,
                        isQueryRunning = isQueryRunning,
                        viewMode = resultsViewMode,
                        onToggleViewMode = { resultsViewMode = it },
                        onSelectCell = { col, value -> selectedCellDetail = Pair(col, value) },
                        onExportCsv = { res ->
                            val csv = exportToCsv(res)
                            shareOrCopyData(context, csv, "text/csv", "query_results.csv")
                        },
                        onExportJson = { res ->
                            val json = exportToJson(res)
                            shareOrCopyData(context, json, "application/json", "query_results.json")
                        },
                        onNavigateToConsole = { selectedSubTabIndex = 1 }
                    )
                }
                3 -> {
                    // Local Query History
                    SupabaseHistoryView(
                        history = queryHistory,
                        onClearHistory = { viewModel.clearSupabaseHistory() },
                        onReRun = { query ->
                            sqlQueryInput = query
                            selectedSubTabIndex = 1
                            attemptExecuteQuery(query)
                        },
                        onLoadIntoEditor = { query ->
                            sqlQueryInput = query
                            selectedSubTabIndex = 1
                        }
                    )
                }
            }
        }
    }

    // ==========================================
    // AI Explanation / Optimization Dialog
    // ==========================================
    if (aiDialogTitle != null && aiDialogContent != null) {
        GlassDialog(
            onDismissRequest = {
                aiDialogTitle = null
                aiDialogContent = null
            },
            title = aiDialogTitle ?: "AI Analysis",
            icon = Icons.Default.Lightbulb,
            iconTint = tokens.accentViolet,
            confirmButtonText = "Close",
            onConfirm = {
                aiDialogTitle = null
                aiDialogContent = null
            },
            confirmTestTag = "dismiss_ai_dialog_button",
            content = {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    fillColor = tokens.codeBackground
                ) {
                    Text(
                        text = aiDialogContent ?: "",
                        style = CodeTextStyle.copy(fontSize = 12.5.sp, color = tokens.textPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    // ==========================================
    // Safety Dialog: Mutating Write Confirmation
    // ==========================================
    if (showMutatingDialog && pendingExecutionSql != null) {
        val sqlToExecute = pendingExecutionSql ?: ""
        val classification = pendingClassification

        GlassDialog(
            onDismissRequest = {
                showMutatingDialog = false
                pendingExecutionSql = null
            },
            title = "Confirm Write Operation",
            icon = Icons.Default.Tune,
            iconTint = Color(0xFFF59E0B),
            message = classification?.explanation ?: "This statement will modify data in your database.",
            confirmButtonText = "Execute Write",
            onConfirm = {
                showMutatingDialog = false
                val s = pendingExecutionSql ?: return@GlassDialog
                pendingExecutionSql = null
                scope.launch {
                    viewModel.executeSupabaseQuery(s)
                    selectedSubTabIndex = 2
                }
            },
            confirmTestTag = "confirm_write_button",
            dismissButtonText = "Cancel",
            content = {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    fillColor = Color(0xFF0D1117)
                ) {
                    Text(
                        text = sqlToExecute,
                        style = CodeTextStyle.copy(fontSize = 11.5.sp, color = Color(0xFF7EE787)),
                        modifier = Modifier.padding(2.dp)
                    )
                }
            }
        )
    }

    // ==========================================
    // Safety Dialog: Destructive Action (Typed Confirmation)
    // ==========================================
    if (showDestructiveDialog && pendingExecutionSql != null) {
        val sqlToExecute = pendingExecutionSql ?: ""
        val classification = pendingClassification
        val targetTable = classification?.targetTable ?: "table"
        var typedTableName by remember { mutableStateOf("") }
        val isConfirmed = typedTableName.trim().equals(targetTable, ignoreCase = true) || (targetTable == "table" && typedTableName.trim().equals("CONFIRM", ignoreCase = true))

        GlassDialog(
            onDismissRequest = {
                showDestructiveDialog = false
                pendingExecutionSql = null
            },
            title = "Destructive Database Action",
            icon = Icons.Default.Warning,
            iconTint = SemanticError,
            message = classification?.explanation ?: "This statement will permanently alter or delete database structures/records.",
            confirmButtonText = "Execute Destructive Action",
            confirmButtonEnabled = isConfirmed,
            onConfirm = {
                if (isConfirmed) {
                    showDestructiveDialog = false
                    val s = pendingExecutionSql ?: return@GlassDialog
                    pendingExecutionSql = null
                    scope.launch {
                        viewModel.executeSupabaseQuery(s)
                        selectedSubTabIndex = 2
                    }
                }
            },
            confirmTestTag = "confirm_destructive_button",
            dismissButtonText = "Cancel",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        fillColor = Color(0xFF0D1117),
                        borderStroke = BorderStroke(1.dp, SemanticError.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = sqlToExecute,
                            style = CodeTextStyle.copy(fontSize = 11.5.sp, color = Color(0xFFFF7B72)),
                            modifier = Modifier.padding(2.dp)
                        )
                    }

                    Text(
                        text = "To confirm execution, type the target name '$targetTable' below:",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = tokens.textPrimary
                    )

                    GlassTextField(
                        value = typedTableName,
                        onValueChange = { typedTableName = it },
                        placeholder = targetTable,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("destructive_confirmation_input")
                    )
                }
            }
        )
    }

    // ==========================================
    // Safety Dialog: Multiple Statements Rejected
    // ==========================================
    if (showMultipleStatementsDialog) {
        GlassDialog(
            onDismissRequest = { showMultipleStatementsDialog = false },
            title = "Multiple Statements Rejected",
            icon = Icons.Default.Warning,
            iconTint = SemanticError,
            message = "For security and transaction safety, executing multiple semicolon-separated SQL statements in a single run is strictly disallowed.\n\nPlease execute one SQL statement at a time.",
            confirmButtonText = "Understood",
            onConfirm = { showMultipleStatementsDialog = false },
            confirmTestTag = "dismiss_multiple_statements_dialog"
        )
    }

    // ==========================================
    // Safety Dialog: Read-Only Mode Violation
    // ==========================================
    if (showReadOnlyBlockedDialog) {
        GlassDialog(
            onDismissRequest = { showReadOnlyBlockedDialog = false },
            title = "Read-Only Mode Active",
            icon = Icons.Default.Warning,
            iconTint = Color(0xFFF59E0B),
            message = "The database console is currently in Read-Only mode. Modifying statements (INSERT, UPDATE, DELETE, CREATE, DROP, ALTER) are blocked.\n\nTo run write operations, toggle 'Write Mode' at the top of the Database tab.",
            confirmButtonText = "Enable Write Mode",
            onConfirm = {
                viewModel.setSupabaseWriteMode(true)
                showReadOnlyBlockedDialog = false
            },
            confirmTestTag = "enable_write_mode_dialog_button",
            dismissButtonText = "Cancel"
        )
    }

    // ==========================================
    // Cell Detail Dialog
    // ==========================================
    selectedCellDetail?.let { (colName, cellVal) ->
        val clipboardManager = LocalClipboardManager.current
        GlassDialog(
            onDismissRequest = { selectedCellDetail = null },
            title = colName,
            icon = Icons.Default.TableChart,
            iconTint = tokens.accentCyan,
            confirmButtonText = "Copy Value",
            onConfirm = {
                clipboardManager.setText(AnnotatedString(cellVal))
                selectedCellDetail = null
            },
            dismissButtonText = "Close",
            content = {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    fillColor = tokens.codeBackground
                ) {
                    Text(
                        text = cellVal,
                        style = CodeTextStyle.copy(fontSize = 13.sp, color = tokens.textPrimary)
                    )
                }
            }
        )
    }
}

// ============================================================================
// 1. Schema Explorer View
// ============================================================================

@Composable
private fun SupabaseSchemaExplorerView(
    tables: List<DatabaseTableInfo>,
    isLoading: Boolean,
    error: String?,
    hasCredentials: Boolean,
    onRefresh: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onPreviewTable: (String) -> Unit,
    onQueryTable: (String) -> Unit
) {
    val tokens = GlassTheme.tokens
    var searchQuery by remember { mutableStateOf("") }

    val filteredTables = remember(tables, searchQuery) {
        if (searchQuery.isBlank()) tables
        else tables.filter { table ->
            table.tableName.contains(searchQuery, ignoreCase = true) ||
                    table.columns.any { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    if (!hasCredentials) {
        EmptyStateView(
            icon = Icons.Default.Key,
            title = "Supabase Not Connected",
            message = "Configure your Supabase Project Ref and Personal Access Token in Settings > Tokens Vault to explore your schema.",
            actionButtonText = "Open Settings",
            onActionClick = onNavigateToSettings,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Search & Refresh Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search tables & columns...",
                leadingIcon = Icons.Default.Search,
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                } else null,
                modifier = Modifier
                    .weight(1f)
                    .testTag("schema_search_input")
            )

            IconButton(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier.testTag("refresh_schema_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Schema",
                        tint = tokens.accentCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (error != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        if (isLoading && tables.isEmpty()) {
            ShimmerListSkeleton(
                itemCount = 5,
                modifier = Modifier.fillMaxSize()
            )
        } else if (filteredTables.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.TableChart,
                title = if (searchQuery.isNotEmpty()) "No matching tables" else "No tables found",
                message = if (searchQuery.isNotEmpty()) "Try a different search keyword" else "The public schema in your Supabase database has no tables yet.",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredTables, key = { it.tableName }) { table ->
                    SchemaTableCard(
                        table = table,
                        onPreview = { onPreviewTable(table.tableName) },
                        onQuery = { onQueryTable(table.tableName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SchemaTableCard(
    table: DatabaseTableInfo,
    onPreview: () -> Unit,
    onQuery: () -> Unit
) {
    val tokens = GlassTheme.tokens
    var isExpanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("schema_table_card_${table.tableName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(tokens.accentViolet.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = null,
                        tint = tokens.accentViolet,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = table.tableName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.textPrimary
                    )
                    Text(
                        text = "${table.columns.size} columns",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textSecondary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PillChip(
                    text = "Preview 50",
                    isSelected = false,
                    onClick = onPreview,
                    icon = Icons.Default.Visibility,
                    testTag = "preview_table_${table.tableName}"
                )
                PillChip(
                    text = "Query",
                    isSelected = false,
                    onClick = onQuery,
                    icon = Icons.Default.PlayArrow,
                    testTag = "query_table_${table.tableName}"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Columns List Preview or Expanded View
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val displayColumns = if (isExpanded) table.columns else table.columns.take(4)
            displayColumns.forEach { col ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = col.name,
                            style = CodeTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = tokens.textPrimary
                        )

                        if (col.isPrimaryKey) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "PK",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        if (col.foreignKeyTable != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = tokens.accentCyan.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, tokens.accentCyan.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "FK ➔ ${col.foreignKeyTable}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.accentCyan,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = col.dataType + if (!col.isNullable) " (NOT NULL)" else "",
                        style = CodeTextStyle.copy(fontSize = 11.sp),
                        color = tokens.textSecondary
                    )
                }
            }

            if (table.columns.size > 4 && !isExpanded) {
                Text(
                    text = "+ ${table.columns.size - 4} more columns (tap card to expand)",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.accentViolet,
                    modifier = Modifier
                        .clickable { isExpanded = true }
                        .padding(top = 2.dp)
                )
            }
        }
    }
}

// ============================================================================
// 2. SQL Console View with AI Prompt & Syntax Highlighting Editor
// ============================================================================

@Composable
private fun SupabaseSqlConsoleView(
    queryInput: String,
    promptInput: String,
    isQueryRunning: Boolean,
    isAiGenerating: Boolean,
    activeProvider: AiProviderConfig?,
    tables: List<DatabaseTableInfo>,
    onQueryInputChanged: (String) -> Unit,
    onPromptInputChanged: (String) -> Unit,
    onRunQuery: () -> Unit,
    onGenerateSqlFromPrompt: () -> Unit,
    onExplainQuery: () -> Unit,
    onOptimizeQuery: () -> Unit
) {
    val tokens = GlassTheme.tokens
    var showAiPromptBox by remember { mutableStateOf(false) }

    val quickSnippets = remember(tables) {
        val list = mutableListOf<Pair<String, String>>()
        if (tables.isNotEmpty()) {
            val firstTable = tables.first().tableName
            list.add("SELECT $firstTable" to "SELECT * FROM \"$firstTable\" LIMIT 20;")
            list.add("Count $firstTable" to "SELECT count(*) FROM \"$firstTable\";")
        }
        list.add("List Tables" to "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';")
        list.add("DB Version" to "SELECT version();")
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // AI Assistant Bar ("Ask in plain English")
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAiPromptBox = !showAiPromptBox },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = tokens.accentViolet,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ask AI in Plain English",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = tokens.textPrimary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = tokens.accentViolet.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, tokens.accentViolet.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = activeProvider?.name ?: "AI Model",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.accentViolet,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = showAiPromptBox) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Describe what query you want to generate. AI only receives table and column names—never row data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )

                        GlassTextField(
                            value = promptInput,
                            onValueChange = onPromptInputChanged,
                            placeholder = "e.g. Find all users created in the last 7 days with active status",
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_sql_prompt_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            GradientButton(
                                text = if (isAiGenerating) "Generating SQL..." else "Generate SQL",
                                onClick = onGenerateSqlFromPrompt,
                                enabled = promptInput.isNotBlank() && !isAiGenerating,
                                leadingIcon = Icons.Default.AutoAwesome,
                                testTag = "generate_sql_button"
                            )
                        }
                    }
                }
            }
        }

        // Quick Snippets Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(quickSnippets) { (label, sql) ->
                PillChip(
                    text = label,
                    isSelected = false,
                    onClick = { onQueryInputChanged(sql) },
                    testTag = "quick_snippet_$label"
                )
            }
        }

        // SQL Editor with Syntax Highlighting
        val shape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(shape)
                .background(tokens.codeBackground)
                .border(tokens.glassBorderStroke, shape)
                .padding(12.dp)
        ) {
            BasicTextField(
                value = queryInput,
                onValueChange = onQueryInputChanged,
                visualTransformation = SqlSyntaxHighlighter.visualTransformation,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = tokens.textPrimary,
                    lineHeight = 18.sp
                ),
                cursorBrush = SolidColor(tokens.accentCyan),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("sql_editor_input"),
                decorationBox = { innerTextField ->
                    if (queryInput.isEmpty()) {
                        Text(
                            text = "Enter SQL query (e.g. SELECT * FROM users LIMIT 25;)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = tokens.textSecondary.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action Toolbar: Explain, Optimize, Clear, and Run Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onExplainQuery,
                    enabled = queryInput.isNotBlank() && !isAiGenerating,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("explain_query_button")
                ) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Explain", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onOptimizeQuery,
                    enabled = queryInput.isNotBlank() && !isAiGenerating,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("optimize_query_button")
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Optimize", fontSize = 12.sp)
                }
            }

            GradientButton(
                text = if (isQueryRunning) "Running..." else "Run",
                onClick = onRunQuery,
                enabled = queryInput.isNotBlank() && !isQueryRunning,
                leadingIcon = Icons.Default.PlayArrow,
                testTag = "execute_sql_button"
            )
        }
    }
}

// ============================================================================
// 3. Results View with Table, Chart & Export
// ============================================================================

@Composable
private fun SupabaseResultsView(
    queryResult: QueryResult?,
    isQueryRunning: Boolean,
    viewMode: Int,
    onToggleViewMode: (Int) -> Unit,
    onSelectCell: (String, String) -> Unit,
    onExportCsv: (QueryResult) -> Unit,
    onExportJson: (QueryResult) -> Unit,
    onNavigateToConsole: () -> Unit
) {
    val tokens = GlassTheme.tokens

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        if (isQueryRunning) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = tokens.accentCyan)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Executing query against Supabase...", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                }
            }
            return
        }

        if (queryResult == null) {
            EmptyStateView(
                icon = Icons.Default.Storage,
                title = "No Query Results Yet",
                message = "Run a query in the SQL Console or tap 'Preview 50' on a table in the Schema tab to view data here.",
                actionButtonText = "Open SQL Console",
                onActionClick = onNavigateToConsole,
                modifier = Modifier.fillMaxSize()
            )
            return
        }

        // Stats Banner & View Mode Toggles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stats indicator
            if (queryResult.error == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${queryResult.rowCount} rows • ${queryResult.executionTimeMs}ms",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = SemanticSuccess
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (queryResult.isReadOnly) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (queryResult.isReadOnly) "READ-ONLY" else "MUTATED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (queryResult.isReadOnly) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = "Query Failed",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = SemanticError
                )
            }

            // Export and View Toggles
            if (queryResult.error == null && queryResult.rows.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { onExportCsv(queryResult) },
                        modifier = Modifier.size(32.dp).testTag("export_csv_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Export CSV", tint = tokens.accentCyan, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { onExportJson(queryResult) },
                        modifier = Modifier.size(32.dp).testTag("export_json_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export JSON", tint = tokens.accentViolet, modifier = Modifier.size(18.dp))
                    }

                    PillChip(
                        text = "Table",
                        isSelected = viewMode == 0,
                        onClick = { onToggleViewMode(0) },
                        testTag = "results_table_toggle"
                    )
                    PillChip(
                        text = "Chart",
                        isSelected = viewMode == 1,
                        onClick = { onToggleViewMode(1) },
                        testTag = "results_chart_toggle"
                    )
                }
            }
        }

        // Display results
        when {
            queryResult.error != null -> {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    fillColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    borderStroke = BorderStroke(1.dp, SemanticError.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = SemanticError, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Query Execution Failed",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = SemanticError
                            )
                        }

                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            fillColor = tokens.codeBackground
                        ) {
                            Text(
                                text = queryResult.error,
                                style = CodeTextStyle.copy(fontSize = 12.sp, color = SemanticError),
                                modifier = Modifier.padding(4.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            GradientButton(
                                text = "Edit Query in Console",
                                onClick = onNavigateToConsole,
                                leadingIcon = Icons.Default.Edit,
                                testTag = "retry_query_in_console_button"
                            )
                        }
                    }
                }
            }
            queryResult.rows.isEmpty() -> {
                EmptyStateView(
                    icon = Icons.Default.TableChart,
                    title = "0 Rows Returned",
                    message = "Query completed in ${queryResult.executionTimeMs}ms with no matching records.",
                    modifier = Modifier.fillMaxSize()
                )
            }
            viewMode == 1 -> {
                // Chart View
                DatabaseResultChartView(
                    queryResult = queryResult,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                // Scrollable Table View with Sticky Header & Zebra Rows
                val shape = RoundedCornerShape(14.dp)
                val horizontalScrollState = rememberScrollState()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(tokens.glassFill)
                        .border(tokens.glassBorderStroke, shape)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        // Sticky Header Row
                        Row(
                            modifier = Modifier
                                .background(tokens.backgroundEnd.copy(alpha = 0.95f))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            queryResult.columns.forEach { col ->
                                Text(
                                    text = col,
                                    style = CodeTextStyle.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.accentCyan,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.width(140.dp)
                                )
                            }
                        }

                        // Data Rows
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            itemsIndexed(queryResult.rows) { index, row ->
                                val rowBg = if (index % 2 == 0) tokens.glassFill else Color.Transparent

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(rowBg)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    row.forEachIndexed { colIdx, cell ->
                                        val colName = queryResult.columns.getOrElse(colIdx) { "Column" }
                                        Text(
                                            text = if (cell.length > 25) cell.take(25) + "…" else cell,
                                            style = CodeTextStyle.copy(fontSize = 12.sp),
                                            color = tokens.textPrimary,
                                            modifier = Modifier
                                                .width(140.dp)
                                                .clickable { onSelectCell(colName, cell) }
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
}

// ============================================================================
// 4. Query History View
// ============================================================================

@Composable
private fun SupabaseHistoryView(
    history: List<QueryHistoryItem>,
    onClearHistory: () -> Unit,
    onReRun: (String) -> Unit,
    onLoadIntoEditor: (String) -> Unit
) {
    val tokens = GlassTheme.tokens
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }

    if (history.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.History,
            title = "No Query History",
            message = "Queries executed against your Supabase database will be logged here locally.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${history.size} QUERIES LOGGED",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = tokens.textSecondary
            )

            IconButton(
                onClick = onClearHistory,
                modifier = Modifier.testTag("clear_supabase_history_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear History",
                    tint = tokens.textSecondary
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(history, key = { it.id }) { item ->
                GlassCard(
                    onClick = { onLoadIntoEditor(item.query) },
                    testTag = "history_card_${item.id}"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (item.isSuccess) tokens.accentViolet.copy(alpha = 0.18f) else SemanticError.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = item.category,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isSuccess) tokens.accentViolet else SemanticError,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = dateFormat.format(Date(item.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.query,
                        style = CodeTextStyle.copy(fontSize = 12.sp),
                        color = tokens.textPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.resultSummary,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = if (item.isSuccess) SemanticSuccess else SemanticError
                            )
                        )

                        TextButton(
                            onClick = { onReRun(item.query) },
                            modifier = Modifier.testTag("rerun_history_query_${item.id}")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Re-Run", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// Helpers: Exporting CSV & JSON and Android Share
// ============================================================================

private fun exportToCsv(queryResult: QueryResult): String {
    val sb = StringBuilder()
    // Header
    sb.appendLine(queryResult.columns.joinToString(",") { escapeCsvCell(it) })
    // Rows
    for (row in queryResult.rows) {
        sb.appendLine(row.joinToString(",") { escapeCsvCell(it) })
    }
    return sb.toString()
}

private fun escapeCsvCell(value: String): String {
    val needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
    return if (needsQuotes) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }
}

private fun exportToJson(queryResult: QueryResult): String {
    val jsonArray = JSONArray()
    for (row in queryResult.rows) {
        val obj = JSONObject()
        for (i in queryResult.columns.indices) {
            val col = queryResult.columns[i]
            val valStr = row.getOrElse(i) { "" }
            obj.put(col, valStr)
        }
        jsonArray.put(obj)
    }
    return jsonArray.toString(2)
}

private fun shareOrCopyData(context: Context, data: String, mimeType: String, filename: String) {
    try {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, data)
            putExtra(Intent.EXTRA_TITLE, filename)
            type = mimeType
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export Query Results")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    } catch (_: Exception) {
        // Fallback: Copy to clipboard
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(filename, data)
        clipboard?.setPrimaryClip(clip)
    }
}
