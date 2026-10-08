package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MarkdownBlock {
    data class Paragraph(val text: String, val isHeading: Boolean = false, val headingLevel: Int = 0) : MarkdownBlock()
    data class Code(val language: String, val code: String) : MarkdownBlock()
    data class ListItem(val text: String, val index: Int? = null) : MarkdownBlock()
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onSaveCodeToFile: (filename: String, language: String, code: String) -> Unit
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MarkdownBlock.Code -> {
                    CodeBlockView(
                        code = block.code,
                        language = block.language,
                        onSaveToFile = onSaveCodeToFile,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    if (block.isHeading) {
                        val (fontSize, fontWeight) = when (block.headingLevel) {
                            1 -> 20.sp to FontWeight.Bold
                            2 -> 18.sp to FontWeight.Bold
                            else -> 16.sp to FontWeight.SemiBold
                        }
                        Text(
                            text = formatInlineMarkdown(block.text, textColor),
                            fontSize = fontSize,
                            fontWeight = fontWeight,
                            lineHeight = (fontSize.value * 1.3).sp,
                            color = textColor,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                        )
                    } else {
                        Text(
                            text = formatInlineMarkdown(block.text, textColor),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = textColor,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
                is MarkdownBlock.ListItem -> {
                    Row(modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)) {
                        Text(
                            text = if (block.index != null) "${block.index}. " else "• ",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatInlineMarkdown(block.text, textColor),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Splits raw markdown into paragraphs, code blocks, lists, and headings
 */
fun parseMarkdown(raw: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = raw.lines()
    var inCodeBlock = false
    var currentLang = ""
    val codeBuilder = StringBuilder()

    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        if (line.trimStart().startsWith("```")) {
            if (inCodeBlock) {
                // Ending code block
                blocks.add(MarkdownBlock.Code(currentLang, codeBuilder.toString().trimEnd()))
                codeBuilder.clear()
                inCodeBlock = false
                currentLang = ""
            } else {
                // Starting code block
                inCodeBlock = true
                currentLang = line.trimStart().removePrefix("```").trim()
            }
            i++
            continue
        }

        if (inCodeBlock) {
            codeBuilder.append(line).append("\n")
            i++
            continue
        }

        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Heading: # H1, ## H2, ### H3
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            val headingText = trimmed.drop(level).trim()
            blocks.add(MarkdownBlock.Paragraph(headingText, isHeading = true, headingLevel = level))
            i++
            continue
        }

        // Ordered or Unordered list item
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            blocks.add(MarkdownBlock.ListItem(trimmed.substring(2).trim()))
            i++
            continue
        }
        val numberedMatch = Regex("^(\\d+)\\.\\s+(.*)").find(trimmed)
        if (numberedMatch != null) {
            val num = numberedMatch.groupValues[1].toIntOrNull()
            val text = numberedMatch.groupValues[2]
            blocks.add(MarkdownBlock.ListItem(text, index = num))
            i++
            continue
        }

        // Normal paragraph (may group continuous text lines)
        val paragraphText = StringBuilder(line)
        while (i + 1 < lines.size &&
            lines[i + 1].trim().isNotEmpty() &&
            !lines[i + 1].trimStart().startsWith("```") &&
            !lines[i + 1].trimStart().startsWith("#") &&
            !lines[i + 1].trimStart().startsWith("- ") &&
            !lines[i + 1].trimStart().startsWith("* ") &&
            !Regex("^(\\d+)\\.\\s+").containsMatchIn(lines[i + 1].trim())
        ) {
            i++
            paragraphText.append(" ").append(lines[i].trim())
        }
        blocks.add(MarkdownBlock.Paragraph(paragraphText.toString()))
        i++
    }

    if (inCodeBlock && codeBuilder.isNotEmpty()) {
        blocks.add(MarkdownBlock.Code(currentLang, codeBuilder.toString().trimEnd()))
    }

    return blocks
}

/**
 * Formats inline bold (`**text**`), italic (`*text*`), and inline code (`` `code` ``)
 */
fun formatInlineMarkdown(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("(`([^`]+)`)|(\\*\\*([^*]+)\\*\\*)|(\\*([^*]+)\\*)")

        val matches = regex.findAll(text)
        for (match in matches) {
            // Append plain text before the match
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }

            when {
                // Inline code `...`
                match.value.startsWith("`") -> {
                    val codeContent = match.groupValues[2]
                    val start = length
                    append(codeContent)
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            background = Color(0x336366F1),
                            color = Color(0xFF818CF8)
                        ),
                        start,
                        length
                    )
                }
                // Bold `**...**`
                match.value.startsWith("**") -> {
                    val boldContent = match.groupValues[4]
                    val start = length
                    append(boldContent)
                    addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor),
                        start,
                        length
                    )
                }
                // Italic `*...*`
                match.value.startsWith("*") -> {
                    val italicContent = match.groupValues[6]
                    val start = length
                    append(italicContent)
                    addStyle(
                        SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor),
                        start,
                        length
                    )
                }
            }
            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
