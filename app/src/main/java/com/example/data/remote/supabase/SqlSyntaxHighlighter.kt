package com.example.data.remote.supabase

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

object SqlSyntaxHighlighter {

    val KEYWORD_COLOR = Color(0xFF38BDF8) // Cyan / Sky
    val CLAUSE_COLOR = Color(0xFFA78BFA) // Violet / Purple
    val STRING_COLOR = Color(0xFF4ADE80) // Green
    val NUMBER_COLOR = Color(0xFFFBBF24) // Amber / Orange
    val COMMENT_COLOR = Color(0xFF94A3B8) // Muted Slate
    val FUNCTION_COLOR = Color(0xFFF472B6) // Pink
    val OPERATOR_COLOR = Color(0xFFE2E8F0) // Light slate

    private val KEYWORDS = setOf(
        "SELECT", "FROM", "WHERE", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "CROSS",
        "FULL", "ON", "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "INSERT",
        "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "VIEW",
        "INDEX", "DROP", "ALTER", "ADD", "COLUMN", "TRUNCATE", "DISTINCT", "AS",
        "AND", "OR", "NOT", "IN", "IS", "NULL", "LIKE", "ILIKE", "BETWEEN",
        "EXISTS", "UNION", "ALL", "CASE", "WHEN", "THEN", "ELSE", "END",
        "ASC", "DESC", "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CASCADE",
        "DEFAULT", "CHECK", "UNIQUE", "RETURNING", "WITH", "RECURSIVE", "EXPLAIN",
        "ANALYZE", "SHOW", "GRANT", "REVOKE", "DATABASE", "SCHEMA", "BEGIN", "COMMIT", "ROLLBACK"
    )

    private val FUNCTIONS = setOf(
        "COUNT", "SUM", "AVG", "MIN", "MAX", "COALESCE", "NULLIF", "NOW", "CURRENT_TIMESTAMP",
        "CURRENT_DATE", "CURRENT_TIME", "DATE_TRUNC", "EXTRACT", "CONCAT", "LOWER", "UPPER",
        "LENGTH", "TRIM", "SUBSTRING", "ROUND", "CEIL", "FLOOR", "ABS", "GEN_RANDOM_UUID", "ROW_NUMBER"
    )

    fun highlight(sql: String): AnnotatedString {
        return buildAnnotatedString {
            append(sql)

            // 1. Highlight numbers
            val numberRegex = Regex("""\b\d+(?:\.\d+)?\b""")
            for (match in numberRegex.findAll(sql)) {
                addStyle(
                    SpanStyle(color = NUMBER_COLOR, fontWeight = FontWeight.Normal),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // 2. Highlight keywords and functions
            val wordRegex = Regex("""\b[A-Za-z_][A-Za-z0-9_]*\b""")
            for (match in wordRegex.findAll(sql)) {
                val wordUpper = match.value.uppercase()
                when {
                    KEYWORDS.contains(wordUpper) -> {
                        val color = if (wordUpper in listOf("SELECT", "FROM", "WHERE", "JOIN", "GROUP", "ORDER", "LIMIT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER", "TRUNCATE")) {
                            KEYWORD_COLOR
                        } else {
                            CLAUSE_COLOR
                        }
                        addStyle(
                            SpanStyle(color = color, fontWeight = FontWeight.Bold),
                            match.range.first,
                            match.range.last + 1
                        )
                    }
                    FUNCTIONS.contains(wordUpper) -> {
                        addStyle(
                            SpanStyle(color = FUNCTION_COLOR, fontWeight = FontWeight.SemiBold),
                            match.range.first,
                            match.range.last + 1
                        )
                    }
                }
            }

            // 3. Highlight string literals ('...')
            val stringRegex = Regex("""'[^']*'""")
            for (match in stringRegex.findAll(sql)) {
                addStyle(
                    SpanStyle(color = STRING_COLOR),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // 4. Highlight single-line comments (-- ...)
            val lineCommentRegex = Regex("""--.*$""", RegexOption.MULTILINE)
            for (match in lineCommentRegex.findAll(sql)) {
                addStyle(
                    SpanStyle(color = COMMENT_COLOR),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // 5. Highlight block comments (/* ... */)
            val blockCommentRegex = Regex("""/\*[\s\S]*?\*/""")
            for (match in blockCommentRegex.findAll(sql)) {
                addStyle(
                    SpanStyle(color = COMMENT_COLOR),
                    match.range.first,
                    match.range.last + 1
                )
            }
        }
    }

    val visualTransformation = VisualTransformation { text ->
        TransformedText(highlight(text.text), OffsetMapping.Identity)
    }
}
