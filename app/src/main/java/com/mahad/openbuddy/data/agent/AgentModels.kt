package com.mahad.openbuddy.data.agent

import org.json.JSONObject

/**
 * Phase 2 — agent core contracts.
 *
 * The agent works with ANY configured provider (Gemini / Claude / OpenAI-compatible)
 * through a provider-neutral tool protocol: the model emits fenced blocks like
 *
 * ```tool
 * {"name": "github_read_file", "arguments": {"owner": "me", "repo": "app", "path": "README.md"}}
 * ```
 *
 * The engine parses those blocks, executes the matching [AgentTool], feeds the
 * result back as a user message, and loops until the model answers without
 * requesting a tool.
 */

data class ToolOutcome(
    val text: String,
    /** Absolute file path of media produced by the tool (image/video), if any. */
    val mediaPath: String? = null,
    /** "image" or "video" — matches the [[image:...]] / [[video:...]] chat markers. */
    val mediaKind: String? = null
)

interface AgentTool {
    /** Stable machine name the model uses, e.g. "github_read_file". */
    val name: String

    /** One or two lines: what it does + its arguments, shown to the model. */
    val description: String

    /**
     * When true, the engine pauses and asks the user to approve before running.
     * Used for anything that changes the outside world (calls, SMS, file writes,
     * destructive SQL, paid media generation).
     */
    val requiresApproval: Boolean get() = false

    /** Short human-readable summary for the approval card. */
    fun approvalSummary(args: JSONObject): String = "$name(${args})"

    suspend fun execute(args: JSONObject): ToolOutcome
}

/** A tool call waiting for the user's approve/reject decision in the chat UI. */
data class ToolApprovalRequest(
    val id: String,
    val toolName: String,
    val summary: String
)

/** One parsed tool call from a model response. */
data class ParsedToolCall(
    val name: String,
    val arguments: JSONObject
)

object ToolCallParser {

    private val fencedRegex = Regex(
        "```(?:tool|tool_call|json)?\\s*\\n(\\{.*?\\})\\s*```",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val tagRegex = Regex(
        "<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    /** Extracts every tool call in the text; blocks that are not tool JSON are ignored. */
    fun parse(text: String): List<ParsedToolCall> {
        val calls = mutableListOf<ParsedToolCall>()
        val seen = mutableSetOf<Int>()
        for (match in fencedRegex.findAll(text) + tagRegex.findAll(text)) {
            if (!seen.add(match.range.first)) continue
            val jsonText = match.groupValues[1]
            try {
                val obj = JSONObject(jsonText)
                val name = obj.optString("name", "")
                if (name.isBlank()) continue
                // Only treat the block as a tool call if it really looks like one,
                // so ordinary JSON code samples in answers are not executed.
                if (!obj.has("arguments") && !obj.has("args") && !obj.has("parameters")) continue
                val args = obj.optJSONObject("arguments")
                    ?: obj.optJSONObject("args")
                    ?: obj.optJSONObject("parameters")
                    ?: JSONObject()
                calls.add(ParsedToolCall(name, args))
            } catch (_: Exception) {
                // Not valid JSON — ignore the block.
            }
        }
        return calls
    }

    /** The user-visible part of a model response: text with tool blocks removed. */
    fun visibleText(text: String): String {
        var out = text
        for (match in (fencedRegex.findAll(text) + tagRegex.findAll(text)).toList().asReversed()) {
            out = out.removeRange(match.range)
        }
        return out.trim()
    }
}
