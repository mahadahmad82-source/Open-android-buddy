package com.example.data.remote.supabase

import com.example.domain.model.DatabaseColumnInfo
import com.example.domain.model.DatabaseTableInfo
import com.example.domain.model.QueryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SupabaseApiService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val BASE_URL = "https://api.supabase.com/v1/projects"
    }

    /**
     * Executes SQL query against Supabase Management API.
     * Endpoint: POST https://api.supabase.com/v1/projects/{ref}/database/query
     * Body: { "query": "<sql>", "read_only": <boolean> }
     */
    suspend fun executeQuery(
        projectRef: String,
        token: String,
        sql: String,
        readOnly: Boolean
    ): Result<QueryResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val cleanRef = cleanProjectRef(projectRef)
            val cleanToken = token.trim()

            if (cleanRef.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Supabase Project Ref is missing. Please configure it in Settings."))
            }
            if (cleanToken.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Supabase Personal Access Token is missing. Please configure it in Settings."))
            }

            val payload = JSONObject().apply {
                put("query", sql)
                put("read_only", readOnly)
            }

            val url = "$BASE_URL/$cleanRef/database/query"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $cleanToken")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "Open-Buddy-Android")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val elapsedMs = System.currentTimeMillis() - startTime
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val parsed = parseQuerySuccessResponse(responseBody, elapsedMs, readOnly, sql)
                    Result.success(parsed)
                } else {
                    val errorMsg = parseHttpError(response, responseBody)
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: IOException) {
            Result.failure(Exception("Network error connecting to Supabase: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Inspects PostgreSQL information_schema to extract tables, columns, types, primary keys, and foreign keys.
     */
    suspend fun fetchSchema(
        projectRef: String,
        token: String
    ): Result<List<DatabaseTableInfo>> = withContext(Dispatchers.IO) {
        val schemaSql = """
            SELECT 
                c.table_name,
                c.column_name,
                c.data_type,
                c.is_nullable,
                c.column_default,
                CASE WHEN pk.column_name IS NOT NULL THEN 'YES' ELSE 'NO' END AS is_primary_key,
                fk.foreign_table_name,
                fk.foreign_column_name
            FROM information_schema.columns c
            LEFT JOIN (
                SELECT ku.table_schema, ku.table_name, ku.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage ku
                    ON tc.constraint_name = ku.constraint_name
                    AND tc.table_schema = ku.table_schema
                WHERE tc.constraint_type = 'PRIMARY KEY'
            ) pk ON c.table_schema = pk.table_schema AND c.table_name = pk.table_name AND c.column_name = pk.column_name
            LEFT JOIN (
                SELECT 
                    kcu.table_schema,
                    kcu.table_name,
                    kcu.column_name,
                    ccu.table_name AS foreign_table_name,
                    ccu.column_name AS foreign_column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                    ON tc.constraint_name = kcu.constraint_name
                    AND tc.table_schema = kcu.table_schema
                JOIN information_schema.constraint_column_usage ccu
                    ON ccu.constraint_name = tc.constraint_name
                    AND ccu.table_schema = tc.table_schema
                WHERE tc.constraint_type = 'FOREIGN KEY'
            ) fk ON c.table_schema = fk.table_schema AND c.table_name = fk.table_name AND c.column_name = fk.column_name
            WHERE c.table_schema = 'public'
            ORDER BY c.table_name, c.ordinal_position;
        """.trimIndent()

        val queryRes = executeQuery(projectRef, token, schemaSql, readOnly = true)
        if (queryRes.isFailure) {
            return@withContext Result.failure(queryRes.exceptionOrNull() ?: Exception("Failed to fetch schema"))
        }

        val result = queryRes.getOrThrow()
        val tableMap = mutableMapOf<String, MutableList<DatabaseColumnInfo>>()

        // Map column indices
        val colNameIdx = result.columns.indexOf("column_name")
        val tblNameIdx = result.columns.indexOf("table_name")
        val dataTypeIdx = result.columns.indexOf("data_type")
        val isNullableIdx = result.columns.indexOf("is_nullable")
        val colDefaultIdx = result.columns.indexOf("column_default")
        val isPkIdx = result.columns.indexOf("is_primary_key")
        val fkTableIdx = result.columns.indexOf("foreign_table_name")
        val fkColIdx = result.columns.indexOf("foreign_column_name")

        for (row in result.rows) {
            if (tblNameIdx == -1 || colNameIdx == -1 || row.size <= tblNameIdx || row.size <= colNameIdx) continue
            val tableName = row[tblNameIdx]
            val columnName = row[colNameIdx]
            val dataType = if (dataTypeIdx != -1 && dataTypeIdx < row.size) row[dataTypeIdx] else "text"
            val isNullable = if (isNullableIdx != -1 && isNullableIdx < row.size) row[isNullableIdx].equals("YES", ignoreCase = true) else true
            val isPk = if (isPkIdx != -1 && isPkIdx < row.size) row[isPkIdx].equals("YES", ignoreCase = true) else false
            val defaultValue = if (colDefaultIdx != -1 && colDefaultIdx < row.size && row[colDefaultIdx] != "null") row[colDefaultIdx] else null
            val foreignTable = if (fkTableIdx != -1 && fkTableIdx < row.size && row[fkTableIdx] != "null" && row[fkTableIdx].isNotBlank()) row[fkTableIdx] else null
            val foreignCol = if (fkColIdx != -1 && fkColIdx < row.size && row[fkColIdx] != "null" && row[fkColIdx].isNotBlank()) row[fkColIdx] else null

            val colInfo = DatabaseColumnInfo(
                name = columnName,
                dataType = dataType,
                isNullable = isNullable,
                isPrimaryKey = isPk,
                defaultValue = defaultValue,
                foreignKeyTable = foreignTable,
                foreignKeyColumn = foreignCol
            )
            tableMap.getOrPut(tableName) { mutableListOf() }.add(colInfo)
        }

        val tables = tableMap.map { (tableName, cols) ->
            DatabaseTableInfo(
                tableName = tableName,
                rowCount = 0,
                columns = cols
            )
        }.sortedBy { it.tableName }

        Result.success(tables)
    }

    /**
     * Tests connectivity to Supabase project via Management API.
     */
    suspend fun testConnection(projectRef: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        val testSql = "SELECT current_database(), current_user, version();"
        val queryResult = executeQuery(projectRef, token, testSql, readOnly = true)
        if (queryResult.isSuccess) {
            val res = queryResult.getOrThrow()
            val dbName = res.rows.firstOrNull()?.firstOrNull() ?: "postgres"
            Result.success("Connected to database '$dbName' on project '$projectRef' successfully.")
        } else {
            Result.failure(queryResult.exceptionOrNull() ?: Exception("Connection failed"))
        }
    }

    private fun parseQuerySuccessResponse(
        responseBody: String,
        executionTimeMs: Long,
        readOnly: Boolean,
        sql: String
    ): QueryResult {
        val trimmed = responseBody.trim()

        if (trimmed.startsWith("[")) {
            val jsonArray = JSONArray(trimmed)
            if (jsonArray.length() == 0) {
                return QueryResult(
                    columns = emptyList(),
                    rows = emptyList(),
                    rowCount = 0,
                    executionTimeMs = executionTimeMs,
                    error = null,
                    isReadOnly = readOnly,
                    executedSql = sql
                )
            }

            // Extract unique column names preserving order
            val columns = linkedSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val keys = obj.keys()
                while (keys.hasNext()) {
                    columns.add(keys.next())
                }
            }

            val colList = columns.toList()
            val rows = mutableListOf<List<String>>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.opt(i)
                if (item is JSONObject) {
                    val row = colList.map { col ->
                        if (item.isNull(col)) "null" else item.opt(col)?.toString() ?: ""
                    }
                    rows.add(row)
                } else {
                    rows.add(listOf(item?.toString() ?: ""))
                }
            }

            return QueryResult(
                columns = colList,
                rows = rows,
                rowCount = rows.size,
                executionTimeMs = executionTimeMs,
                error = null,
                isReadOnly = readOnly,
                executedSql = sql
            )
        } else if (trimmed.startsWith("{")) {
            val jsonObject = JSONObject(trimmed)
            if (jsonObject.has("error") || jsonObject.has("message")) {
                val err = jsonObject.optString("error", jsonObject.optString("message", "Unknown error"))
                return QueryResult(
                    columns = emptyList(),
                    rows = emptyList(),
                    rowCount = 0,
                    executionTimeMs = executionTimeMs,
                    error = err,
                    isReadOnly = readOnly,
                    executedSql = sql
                )
            }

            val colList = jsonObject.keys().asSequence().toList()
            val rowList = colList.map { key ->
                if (jsonObject.isNull(key)) "null" else jsonObject.opt(key)?.toString() ?: ""
            }
            return QueryResult(
                columns = colList,
                rows = listOf(rowList),
                rowCount = 1,
                executionTimeMs = executionTimeMs,
                error = null,
                isReadOnly = readOnly,
                executedSql = sql
            )
        } else {
            return QueryResult(
                columns = listOf("result"),
                rows = if (trimmed.isNotBlank()) listOf(listOf(trimmed)) else emptyList(),
                rowCount = if (trimmed.isNotBlank()) 1 else 0,
                executionTimeMs = executionTimeMs,
                error = null,
                isReadOnly = readOnly,
                executedSql = sql
            )
        }
    }

    private fun parseHttpError(response: Response, responseBody: String): String {
        val code = response.code
        val message = try {
            val json = JSONObject(responseBody)
            json.optString("message", json.optString("error", json.optString("msg", "")))
        } catch (_: Exception) {
            ""
        }

        val detail = if (message.isNotBlank()) message else responseBody.take(200)

        return when (code) {
            400 -> if (detail.isNotBlank()) "Query Error (400): $detail" else "Bad Request (400). Please check your SQL syntax."
            401 -> "Unauthorized (401). Invalid Supabase Personal Access Token. Please verify in Settings."
            403 -> "Forbidden (403). Your Personal Access Token does not have permission for this project."
            404 -> "Project Not Found (404). Verify your Supabase Project Ref in Settings."
            429 -> "Rate Limited (429). Too many requests sent to Supabase Management API. Please wait a moment."
            500 -> if (detail.contains("read-only", ignoreCase = true) || detail.contains("read only", ignoreCase = true)) {
                "Read-Only Violation: Cannot execute write operation while in Read-Only mode. Toggle Write Mode to permit updates."
            } else if (detail.isNotBlank()) {
                "Database Server Error: $detail"
            } else {
                "Internal Server Error (500) from Supabase."
            }
            else -> if (detail.isNotBlank()) "Error ($code): $detail" else "Supabase API returned HTTP $code"
        }
    }

    private fun cleanProjectRef(input: String): String {
        var clean = input.trim()
        val match = Regex("""https?://([a-zA-Z0-9_-]+)\.supabase\.co""").find(clean)
        if (match != null) {
            return match.groupValues[1]
        }
        clean = clean.removePrefix("https://").removePrefix("http://")
        val slashIndex = clean.indexOf('/')
        if (slashIndex != -1) {
            clean = clean.substring(0, slashIndex)
        }
        return clean.removeSuffix(".supabase.co")
    }
}
