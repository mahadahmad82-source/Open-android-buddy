package com.mahad.openbuddy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahad.openbuddy.ui.theme.CodeBackground
import com.mahad.openbuddy.ui.theme.GlassTheme
import com.mahad.openbuddy.ui.theme.SyntaxComment
import com.mahad.openbuddy.ui.theme.SyntaxFunction
import com.mahad.openbuddy.ui.theme.SyntaxKeyword
import com.mahad.openbuddy.ui.theme.SyntaxNumber
import com.mahad.openbuddy.ui.theme.SyntaxString
import com.mahad.openbuddy.ui.theme.SyntaxType
import kotlinx.coroutines.delay

@Composable
fun CodeBlockView(
    code: String,
    language: String,
    modifier: Modifier = Modifier,
    onSaveToFile: ((suggestedFilename: String, language: String, code: String) -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    val displayLang = language.ifBlank { "code" }
    val extension = when (displayLang.lowercase()) {
        "kotlin", "kt" -> "kt"
        "java" -> "java"
        "python", "py" -> "py"
        "javascript", "js" -> "js"
        "typescript", "ts" -> "ts"
        "json" -> "json"
        "xml" -> "xml"
        "html" -> "html"
        "css" -> "css"
        "sql" -> "sql"
        "sh", "bash" -> "sh"
        "rust", "rs" -> "rs"
        "go" -> "go"
        "cpp", "c++" -> "cpp"
        "c" -> "c"
        else -> "txt"
    }
    val defaultFilename = "Snippet_${System.currentTimeMillis() % 10000}.$extension"

    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CodeBackground)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
    ) {
        Column {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131826))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.05f),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Language Label pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E2638))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = displayLang.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlassTheme.tokens.accentCyan
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Copy Button
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(code))
                            isCopied = true
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("code_block_copy_button")
                    ) {
                        if (isCopied) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Copied",
                                tint = GlassTheme.tokens.success,
                                modifier = Modifier.size(17.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy code",
                                tint = GlassTheme.tokens.textSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    if (onSaveToFile != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                onSaveToFile(defaultFilename, displayLang, code)
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("code_block_save_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SaveAlt,
                                contentDescription = "Save code draft to file",
                                tint = GlassTheme.tokens.textSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            // Code Content with Horizontal Scroll
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                val highlighted = remember(code, language) {
                    highlightSyntax(code, language)
                }
                Text(
                    text = highlighted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFFF1F5F9)
                )
            }
        }
    }
}

/**
 * Lightweight, robust syntax highlighter for code snippets
 */
fun highlightSyntax(code: String, language: String): AnnotatedString {
    return buildAnnotatedString {
        append(code)

        val text = code
        val keywords = setOf(
            "val", "var", "fun", "class", "interface", "object", "package", "import",
            "return", "if", "else", "when", "for", "while", "do", "break", "continue",
            "throw", "try", "catch", "finally", "override", "private", "public", "protected",
            "internal", "open", "final", "abstract", "sealed", "data", "enum", "suspend",
            "def", "async", "await", "lambda", "from", "as", "const", "let", "function",
            "export", "default", "struct", "impl", "fn", "mut", "select", "from", "where",
            "insert", "update", "delete", "create", "table", "null", "true", "false"
        )

        val types = setOf(
            "String", "Int", "Boolean", "Float", "Double", "Long", "Unit", "List",
            "Map", "Set", "Flow", "StateFlow", "Result", "Modifier", "Composable",
            "Context", "CoroutineScope", "View", "Activity", "Array"
        )

        // Highlight strings: "..." or '...'
        val stringRegex = Regex("\"[^\"]*\"|'[^']*'")
        stringRegex.findAll(text).forEach { match ->
            addStyle(
                SpanStyle(color = SyntaxString),
                match.range.first,
                match.range.last + 1
            )
        }

        // Highlight comments: //... or #...
        val commentRegex = Regex("(//.*)|(#.*)")
        commentRegex.findAll(text).forEach { match ->
            addStyle(
                SpanStyle(color = SyntaxComment),
                match.range.first,
                match.range.last + 1
            )
        }

        // Highlight numbers: digits
        val numberRegex = Regex("\\b\\d+(\\.\\d+)?\\b")
        numberRegex.findAll(text).forEach { match ->
            addStyle(
                SpanStyle(color = SyntaxNumber),
                match.range.first,
                match.range.last + 1
            )
        }

        // Highlight keywords & types
        val wordRegex = Regex("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b")
        wordRegex.findAll(text).forEach { match ->
            val word = match.value
            if (keywords.contains(word)) {
                addStyle(
                    SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold),
                    match.range.first,
                    match.range.last + 1
                )
            } else if (types.contains(word)) {
                addStyle(
                    SpanStyle(color = SyntaxType),
                    match.range.first,
                    match.range.last + 1
                )
            }
        }
    }
}
