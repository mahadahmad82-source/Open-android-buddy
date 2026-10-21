package com.mahad.openbuddy.data.repository

import com.mahad.openbuddy.data.local.dao.QueryHistoryDao
import com.mahad.openbuddy.data.local.entity.QueryHistoryEntity
import com.mahad.openbuddy.data.provider.AiProviderFactory
import com.mahad.openbuddy.data.remote.supabase.SqlSafetyClassifier
import com.mahad.openbuddy.data.remote.supabase.SupabaseApiService
import com.mahad.openbuddy.data.security.SecretStore
import com.mahad.openbuddy.domain.model.AiError
import com.mahad.openbuddy.domain.model.AiProviderConfig
import com.mahad.openbuddy.domain.model.ChatMessage
import com.mahad.openbuddy.domain.model.DatabaseTableInfo
import com.mahad.openbuddy.domain.model.MessageRole
import com.mahad.openbuddy.domain.model.QueryHistoryItem
import com.mahad.openbuddy.domain.model.QueryResult
import com.mahad.openbuddy.domain.model.StatementClassification
import com.mahad.openbuddy.domain.model.StatementSafetyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class SupabaseRepository(
    private val secretStore: SecretStore,
    private val queryHistoryDao: QueryHistoryDao,
    private val providerFactory: AiProviderFactory,
    private val apiService: SupabaseApiService = SupabaseApiService()
) {

    private val _tables = MutableStateFlow<List<DatabaseTableInfo>>(emptyList())
    val tables: StateFlow<List<DatabaseTableInfo>> = _tables.asStateFlow()

    private val _isSchemaLoading = MutableStateFlow(false)
    val isSchemaLoading: StateFlow<Boolean> = _isSchemaLoading.asStateFlow()

    private val _schemaError = MutableStateFlow<String?>(null)
    val schemaError: StateFlow<String?> = _schemaError.asStateFlow()

    private val _isQueryRunning = MutableStateFlow(false)
    val isQueryRunning: StateFlow<Boolean> = _isQueryRunning.asStateFlow()

    private val _isAiGenerating = MutableStateFlow(false)
    val isAiGenerating: StateFlow<Boolean> = _isAiGenerating.asStateFlow()

    private val _lastQueryResult = MutableStateFlow<QueryResult?>(null)
    val lastQueryResult: StateFlow<QueryResult?> = _lastQueryResult.asStateFlow()

    private val _isWriteMode = MutableStateFlow(false)
    val isWriteMode: StateFlow<Boolean> = _isWriteMode.asStateFlow()

    val queryHistory: Flow<List<QueryHistoryItem>> = queryHistoryDao.getAllHistory().map { list ->
        list.map { entity ->
            QueryHistoryItem(
                id = entity.id,
                query = entity.query,
                category = entity.category,
                timestamp = entity.timestamp,
                resultSummary = entity.resultSummary,
                executionTimeMs = entity.executionTimeMs,
                isSuccess = entity.isSuccess,
                errorMessage = entity.errorMessage
            )
        }
    }.flowOn(Dispatchers.IO)

    fun setWriteMode(enabled: Boolean) {
        _isWriteMode.value = enabled
    }

    fun hasCredentials(): Boolean {
        return secretStore.hasSupabaseProjectRef() && secretStore.hasSupabaseToken()
    }

    fun getProjectRef(): String? {
        return secretStore.getSupabaseProjectRef()
    }

    fun classifyStatement(sql: String): StatementClassification {
        return SqlSafetyClassifier.classify(sql)
    }

    suspend fun refreshSchema(): Result<List<DatabaseTableInfo>> = withContext(Dispatchers.IO) {
        val projectRef = secretStore.getSupabaseProjectRef()
        val token = secretStore.getSupabaseToken()

        if (projectRef.isNullOrBlank() || token.isNullOrBlank()) {
            val err = "Supabase Project Ref or Personal Access Token is not configured. Please open Settings > Tokens Vault to configure."
            _schemaError.value = err
            return@withContext Result.failure(Exception(err))
        }

        _isSchemaLoading.value = true
        _schemaError.value = null

        val result = apiService.fetchSchema(projectRef, token)
        _isSchemaLoading.value = false

        if (result.isSuccess) {
            val list = result.getOrThrow()
            _tables.value = list
            _schemaError.value = null
            Result.success(list)
        } else {
            val err = result.exceptionOrNull()?.message ?: "Failed to inspect database schema"
            _schemaError.value = err
            Result.failure(Exception(err))
        }
    }

    suspend fun previewTable(tableName: String): Result<QueryResult> = withContext(Dispatchers.IO) {
        val previewSql = "SELECT * FROM \"$tableName\" LIMIT 50;"
        executeQueryInternal(
            sql = previewSql,
            forceReadOnly = true,
            category = "PREVIEW"
        )
    }

    suspend fun executeQuery(sql: String, bypassSafetyCheck: Boolean = false): Result<QueryResult> = withContext(Dispatchers.IO) {
        val trimmed = sql.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Query cannot be empty."))
        }

        val classification = SqlSafetyClassifier.classify(trimmed)

        if (classification.hasMultipleStatements) {
            val err = "Safety violation: Multiple SQL statements in one run are rejected. Please execute a single statement."
            val failedResult = QueryResult(
                columns = emptyList(),
                rows = emptyList(),
                rowCount = 0,
                executionTimeMs = 0,
                error = err,
                isReadOnly = true,
                executedSql = trimmed
            )
            _lastQueryResult.value = failedResult
            return@withContext Result.failure(Exception(err))
        }

        val writeModeActive = _isWriteMode.value
        if (!writeModeActive && classification.type != StatementSafetyType.READ_ONLY) {
            val err = "Write operation rejected: Database is currently in Read-Only mode. Toggle Write Mode to permit modifications."
            val failedResult = QueryResult(
                columns = emptyList(),
                rows = emptyList(),
                rowCount = 0,
                executionTimeMs = 0,
                error = err,
                isReadOnly = true,
                executedSql = trimmed
            )
            _lastQueryResult.value = failedResult
            return@withContext Result.failure(Exception(err))
        }

        // Apply auto LIMIT 100 on SELECT if read-only
        val (finalSql, wasLimitAdded) = if (classification.type == StatementSafetyType.READ_ONLY) {
            SqlSafetyClassifier.sanitizeAndApplyLimit(trimmed)
        } else {
            Pair(trimmed, false)
        }

        val category = when (classification.type) {
            StatementSafetyType.READ_ONLY -> "SELECT"
            StatementSafetyType.MUTATING_WRITE -> "WRITE"
            StatementSafetyType.DESTRUCTIVE -> "DESTRUCTIVE"
        }

        executeQueryInternal(
            sql = finalSql,
            forceReadOnly = !writeModeActive,
            category = category
        )
    }

    private suspend fun executeQueryInternal(
        sql: String,
        forceReadOnly: Boolean,
        category: String
    ): Result<QueryResult> = withContext(Dispatchers.IO) {
        val projectRef = secretStore.getSupabaseProjectRef()
        val token = secretStore.getSupabaseToken()

        if (projectRef.isNullOrBlank() || token.isNullOrBlank()) {
            val err = "Supabase connection not configured. Please enter your Project Ref and Personal Access Token in Settings."
            val failedResult = QueryResult(
                columns = emptyList(),
                rows = emptyList(),
                rowCount = 0,
                executionTimeMs = 0,
                error = err,
                isReadOnly = forceReadOnly,
                executedSql = sql
            )
            _lastQueryResult.value = failedResult
            return@withContext Result.failure(Exception(err))
        }

        _isQueryRunning.value = true

        val result = apiService.executeQuery(
            projectRef = projectRef,
            token = token,
            sql = sql,
            readOnly = forceReadOnly
        )

        _isQueryRunning.value = false

        if (result.isSuccess) {
            val queryResult = result.getOrThrow()
            _lastQueryResult.value = queryResult

            // Save to local Room history
            val summary = if (queryResult.error == null) {
                "${queryResult.rowCount} rows in ${queryResult.executionTimeMs}ms"
            } else {
                "Error: ${queryResult.error}"
            }

            val historyEntity = QueryHistoryEntity(
                id = UUID.randomUUID().toString(),
                query = sql,
                category = category,
                timestamp = System.currentTimeMillis(),
                resultSummary = summary,
                executionTimeMs = queryResult.executionTimeMs,
                isSuccess = queryResult.error == null,
                errorMessage = queryResult.error
            )
            queryHistoryDao.insertHistoryItem(historyEntity)

            Result.success(queryResult)
        } else {
            val errorMsg = result.exceptionOrNull()?.message ?: "Query execution failed"
            val failedResult = QueryResult(
                columns = emptyList(),
                rows = emptyList(),
                rowCount = 0,
                executionTimeMs = 0,
                error = errorMsg,
                isReadOnly = forceReadOnly,
                executedSql = sql
            )
            _lastQueryResult.value = failedResult

            // Save error to history
            val historyEntity = QueryHistoryEntity(
                id = UUID.randomUUID().toString(),
                query = sql,
                category = category,
                timestamp = System.currentTimeMillis(),
                resultSummary = "Failed: $errorMsg",
                executionTimeMs = 0,
                isSuccess = false,
                errorMessage = errorMsg
            )
            queryHistoryDao.insertHistoryItem(historyEntity)

            Result.failure(Exception(errorMsg))
        }
    }

    /**
     * AI SQL Generator: generates SQL based on user prompt and schema ONLY.
     * Never sends row data to AI provider.
     */
    suspend fun generateSqlFromPrompt(
        prompt: String,
        provider: AiProviderConfig,
        apiKey: String
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            _isAiGenerating.value = true

            val schemaText = buildSchemaContextString(_tables.value)
            val aiProvider = providerFactory.createProviderWithCustomKey(provider, apiKey)

            val systemInstructions = """
                You are Open Buddy, a PostgreSQL expert database assistant.
                You help users generate SQL queries for their Supabase PostgreSQL database based on the provided schema.

                CRITICAL RULES:
                1. Always output the generated PostgreSQL query inside a single ```sql ... ``` code block.
                2. After the code block, provide a short 1-2 sentence explanation in plain English.
                3. Only reference table names and column names that exist in the Schema context below. Do not guess or hallucinate unlisted tables.
                4. Default to standard SELECT statements. If the user explicitly asks to modify data, generate the appropriate statement with clear WHERE clauses.
                5. Do not include row data or personal details.
            """.trimIndent()

            val userMessage = """
                === DATABASE SCHEMA ===
                $schemaText
                =======================

                USER REQUEST:
                $prompt
            """.trimIndent()

            val chatMessages = listOf(
                ChatMessage(
                    role = MessageRole.USER,
                    content = userMessage
                )
            )

            val responseBuilder = StringBuilder()
            aiProvider.streamChat(chatMessages, systemInstructions).collect { chunk ->
                responseBuilder.append(chunk)
            }

            val fullResponse = responseBuilder.toString().trim()
            _isAiGenerating.value = false

            // Extract SQL from code block
            val sqlMatch = Regex("""```(?:sql)?\s*([\s\S]*?)```""", RegexOption.IGNORE_CASE).find(fullResponse)
            val sql = sqlMatch?.groupValues?.get(1)?.trim() ?: fullResponse.lines().firstOrNull { it.trim().startsWith("SELECT", ignoreCase = true) } ?: fullResponse

            val explanation = if (sqlMatch != null) {
                fullResponse.replace(sqlMatch.value, "").trim()
            } else {
                "Generated PostgreSQL query."
            }

            Result.success(Pair(sql, explanation))
        } catch (e: Exception) {
            _isAiGenerating.value = false
            Result.failure(e)
        }
    }

    /**
     * AI SQL Explainer: explains what a SQL query does in plain English.
     */
    suspend fun explainQuery(
        sql: String,
        provider: AiProviderConfig,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            _isAiGenerating.value = true
            val schemaText = buildSchemaContextString(_tables.value)
            val aiProvider = providerFactory.createProviderWithCustomKey(provider, apiKey)

            val systemInstructions = "You are a senior PostgreSQL Database Administrator. Explain PostgreSQL queries clearly and concisely in plain English."

            val userMessage = """
                Explain the following PostgreSQL query in plain English:
                ```sql
                $sql
                ```
                Database schema for reference:
                $schemaText

                Please explain:
                1. What data this query retrieves or alters.
                2. How tables are joined and filtered.
                3. Performance considerations or potential bottlenecks.
            """.trimIndent()

            val responseBuilder = StringBuilder()
            aiProvider.streamChat(listOf(ChatMessage(MessageRole.USER, userMessage)), systemInstructions).collect {
                responseBuilder.append(it)
            }

            _isAiGenerating.value = false
            Result.success(responseBuilder.toString().trim())
        } catch (e: Exception) {
            _isAiGenerating.value = false
            Result.failure(e)
        }
    }

    /**
     * AI SQL Optimizer: analyzes and suggests optimizations/indexes for a query.
     */
    suspend fun optimizeQuery(
        sql: String,
        provider: AiProviderConfig,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            _isAiGenerating.value = true
            val schemaText = buildSchemaContextString(_tables.value)
            val aiProvider = providerFactory.createProviderWithCustomKey(provider, apiKey)

            val systemInstructions = "You are a PostgreSQL optimization and indexing specialist. Suggest concrete, high-impact SQL optimizations."

            val userMessage = """
                Optimize the following PostgreSQL query:
                ```sql
                $sql
                ```
                Database schema:
                $schemaText

                Please provide:
                1. Optimized SQL query rewrite (inside ```sql ... ```).
                2. Recommended indexes (e.g., CREATE INDEX ...).
                3. Explanation of performance gains.
            """.trimIndent()

            val responseBuilder = StringBuilder()
            aiProvider.streamChat(listOf(ChatMessage(MessageRole.USER, userMessage)), systemInstructions).collect {
                responseBuilder.append(it)
            }

            _isAiGenerating.value = false
            Result.success(responseBuilder.toString().trim())
        } catch (e: Exception) {
            _isAiGenerating.value = false
            Result.failure(e)
        }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        queryHistoryDao.clearHistory()
    }

    suspend fun deleteHistoryItem(id: String) = withContext(Dispatchers.IO) {
        queryHistoryDao.deleteHistoryById(id)
    }

    private fun buildSchemaContextString(tables: List<DatabaseTableInfo>): String {
        if (tables.isEmpty()) {
            return "(Schema not loaded yet. Use standard PostgreSQL public schema tables.)"
        }
        val sb = StringBuilder()
        for (table in tables) {
            sb.appendLine("Table: ${table.tableName}")
            if (table.columns.isNotEmpty()) {
                sb.appendLine("Columns:")
                for (col in table.columns) {
                    val flags = mutableListOf<String>()
                    if (col.isPrimaryKey) flags.add("PRIMARY KEY")
                    if (!col.isNullable) flags.add("NOT NULL")
                    if (col.foreignKeyTable != null) flags.add("FK -> ${col.foreignKeyTable}.${col.foreignKeyColumn}")
                    if (col.defaultValue != null) flags.add("DEFAULT ${col.defaultValue}")

                    val flagStr = if (flags.isNotEmpty()) " [${flags.joinToString(", ")}]" else ""
                    sb.appendLine("  - ${col.name} (${col.dataType})$flagStr")
                }
            }
            sb.appendLine()
        }
        return sb.toString()
    }
}
