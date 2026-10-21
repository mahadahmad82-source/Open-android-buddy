package com.mahad.openbuddy.data.remote.supabase

import com.mahad.openbuddy.domain.model.StatementClassification
import com.mahad.openbuddy.domain.model.StatementSafetyType
import java.util.Locale

object SqlSafetyClassifier {

    /**
     * Splits SQL statements while respecting quotes and comments.
     */
    fun splitStatements(sql: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var inLineComment = false
        var inBlockComment = false
        var i = 0

        while (i < sql.length) {
            val c = sql[i]
            val nextC = if (i + 1 < sql.length) sql[i + 1] else null

            when {
                inLineComment -> {
                    current.append(c)
                    if (c == '\n' || c == '\r') {
                        inLineComment = false
                    }
                }
                inBlockComment -> {
                    current.append(c)
                    if (c == '*' && nextC == '/') {
                        current.append(nextC)
                        i++
                        inBlockComment = false
                    }
                }
                inSingleQuote -> {
                    current.append(c)
                    if (c == '\'') {
                        // Check for escaped quote ''
                        if (nextC == '\'') {
                            current.append(nextC)
                            i++
                        } else {
                            inSingleQuote = false
                        }
                    }
                }
                inDoubleQuote -> {
                    current.append(c)
                    if (c == '"') {
                        if (nextC == '"') {
                            current.append(nextC)
                            i++
                        } else {
                            inDoubleQuote = false
                        }
                    }
                }
                c == '-' && nextC == '-' -> {
                    current.append(c).append(nextC)
                    i++
                    inLineComment = true
                }
                c == '/' && nextC == '*' -> {
                    current.append(c).append(nextC)
                    i++
                    inBlockComment = true
                }
                c == '\'' -> {
                    current.append(c)
                    inSingleQuote = true
                }
                c == '"' -> {
                    current.append(c)
                    inDoubleQuote = true
                }
                c == ';' -> {
                    val stmt = current.toString().trim()
                    if (stmt.isNotBlank()) {
                        statements.add(stmt)
                    }
                    current.clear()
                }
                else -> {
                    current.append(c)
                }
            }
            i++
        }

        val remaining = current.toString().trim()
        if (remaining.isNotBlank()) {
            statements.add(remaining)
        }

        return statements
    }

    /**
     * Classifies a SQL statement for safety validation and confirmation dialogs.
     */
    fun classify(sql: String): StatementClassification {
        val trimmed = sql.trim()
        val statements = splitStatements(trimmed)

        if (statements.size > 1) {
            return StatementClassification(
                type = StatementSafetyType.DESTRUCTIVE,
                targetTable = null,
                hasMultipleStatements = true,
                statementCount = statements.size,
                explanation = "Multiple statements (${statements.size}) detected. Only a single SQL statement can be executed at a time for database safety."
            )
        }

        if (statements.isEmpty()) {
            return StatementClassification(
                type = StatementSafetyType.READ_ONLY,
                targetTable = null,
                hasMultipleStatements = false,
                statementCount = 0,
                explanation = "Empty statement."
            )
        }

        val singleSql = statements[0]
        val normalized = singleSql.replace(Regex("""\s+"""), " ").trim()
        val upper = normalized.uppercase(Locale.US)

        val targetTable = extractTargetTable(singleSql)

        // 1. Check Destructive statements
        when {
            upper.startsWith("DROP ") -> {
                val objectType = when {
                    upper.contains("TABLE") -> "table"
                    upper.contains("SCHEMA") -> "schema"
                    upper.contains("DATABASE") -> "database"
                    upper.contains("VIEW") -> "view"
                    upper.contains("FUNCTION") -> "function"
                    upper.contains("INDEX") -> "index"
                    else -> "database object"
                }
                return StatementClassification(
                    type = StatementSafetyType.DESTRUCTIVE,
                    targetTable = targetTable,
                    hasMultipleStatements = false,
                    statementCount = 1,
                    explanation = "DROP operation permanently deletes $objectType ${targetTable ?: ""} and all its data."
                )
            }
            upper.startsWith("TRUNCATE ") || upper.startsWith("TRUNCATE TABLE ") -> {
                return StatementClassification(
                    type = StatementSafetyType.DESTRUCTIVE,
                    targetTable = targetTable,
                    hasMultipleStatements = false,
                    statementCount = 1,
                    explanation = "TRUNCATE operation permanently removes all rows from table ${targetTable ?: ""}."
                )
            }
            upper.startsWith("DELETE ") || upper.startsWith("DELETE FROM ") -> {
                val hasWhere = upper.contains(" WHERE ")
                return StatementClassification(
                    type = StatementSafetyType.DESTRUCTIVE,
                    targetTable = targetTable,
                    hasMultipleStatements = false,
                    statementCount = 1,
                    explanation = if (hasWhere) {
                        "DELETE statement will remove matching records from table ${targetTable ?: ""}."
                    } else {
                        "DANGER: DELETE statement without a WHERE clause will delete ALL rows in table ${targetTable ?: ""}!"
                    }
                )
            }
            upper.startsWith("ALTER ") -> {
                return StatementClassification(
                    type = StatementSafetyType.DESTRUCTIVE,
                    targetTable = targetTable,
                    hasMultipleStatements = false,
                    statementCount = 1,
                    explanation = "ALTER statement modifies the schema structure of table ${targetTable ?: ""}."
                )
            }
            upper.startsWith("UPDATE ") -> {
                val hasWhere = upper.contains(" WHERE ")
                return if (!hasWhere) {
                    StatementClassification(
                        type = StatementSafetyType.DESTRUCTIVE,
                        targetTable = targetTable,
                        hasMultipleStatements = false,
                        statementCount = 1,
                        explanation = "DANGER: UPDATE statement without a WHERE clause will modify ALL rows in table ${targetTable ?: ""}!"
                    )
                } else {
                    StatementClassification(
                        type = StatementSafetyType.MUTATING_WRITE,
                        targetTable = targetTable,
                        hasMultipleStatements = false,
                        statementCount = 1,
                        explanation = "UPDATE statement will modify rows matching WHERE criteria in table ${targetTable ?: ""}."
                    )
                }
            }
            upper.startsWith("INSERT ") || upper.startsWith("INSERT INTO ") -> {
                return StatementClassification(
                    type = StatementSafetyType.MUTATING_WRITE,
                    targetTable = targetTable,
                    hasMultipleStatements = false,
                    statementCount = 1,
                    explanation = "INSERT statement will write new row(s) into table ${targetTable ?: ""}."
                )
            }
            upper.startsWith("CREATE ") -> {
                return StatementClassification(
                    type = StatementSafetyType.MUTATING_WRITE,
                    targetTable = targetTable,
                    hasMultipleStatements = false,
                    statementCount = 1,
                    explanation = "CREATE statement creates a new database object."
                )
            }
            upper.startsWith("SELECT ") || upper.startsWith("WITH ") || upper.startsWith("EXPLAIN ") || upper.startsWith("SHOW ") -> {
                return StatementClassification(
                    type = StatementSafetyType.READ_ONLY,
                    targetTable = targetTable,
                    hasMultipleStatements = false,
                    statementCount = 1,
                    explanation = "Read-only query."
                )
            }
            else -> {
                return StatementClassification(
                    type = StatementSafetyType.MUTATING_WRITE,
                    targetTable = targetTable,
                    hasMultipleStatements = false,
                    statementCount = 1,
                    explanation = "Database operation."
                )
            }
        }
    }

    /**
     * Automatically applies LIMIT 100 to SELECT queries that have no LIMIT clause.
     */
    fun sanitizeAndApplyLimit(sql: String): Pair<String, Boolean> {
        val trimmed = sql.trim().removeSuffix(";")
        val upper = trimmed.uppercase(Locale.US)

        val isSelect = upper.startsWith("SELECT ") || (upper.startsWith("WITH ") && upper.contains("SELECT "))
        if (!isSelect) {
            return Pair(trimmed, false)
        }

        // Check if LIMIT clause already exists (e.g. LIMIT 50)
        val hasLimit = Regex("""\bLIMIT\s+\d+""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
        if (hasLimit) {
            return Pair(trimmed, false)
        }

        return Pair("$trimmed\nLIMIT 100", true)
    }

    private fun extractTargetTable(sql: String): String? {
        val patterns = listOf(
            Regex("""(?i)\bFROM\s+["']?([a-zA-Z0-9_.]+)["']?"""),
            Regex("""(?i)\bINTO\s+["']?([a-zA-Z0-9_.]+)["']?"""),
            Regex("""(?i)\bUPDATE\s+["']?([a-zA-Z0-9_.]+)["']?"""),
            Regex("""(?i)\bTABLE\s+(?:IF\s+EXISTS\s+)?["']?([a-zA-Z0-9_.]+)["']?"""),
            Regex("""(?i)\bTRUNCATE\s+(?:TABLE\s+)?["']?([a-zA-Z0-9_.]+)["']?"""),
            Regex("""(?i)\bALTER\s+TABLE\s+["']?([a-zA-Z0-9_.]+)["']?""")
        )

        for (p in patterns) {
            val match = p.find(sql)
            if (match != null) {
                val table = match.groupValues[1]
                return table.split(".").last()
            }
        }
        return null
    }
}
