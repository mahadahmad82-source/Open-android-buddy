package com.example.data.security

import java.util.regex.Pattern

data class RedactionResult(
    val text: String,
    val wasRedacted: Boolean,
    val redactionsCount: Int
)

/**
 * Redaction utility for masking sensitive personally identifiable information (PII)
 * and authentication tokens before sending prompts over the wire to AI providers.
 */
object SensitiveDataRedactor {

    // Common Email pattern
    private val EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    )

    // Phone numbers: international and local formats
    private val PHONE_PATTERN = Pattern.compile(
        "(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b"
    )

    // OpenAI API keys (sk-...)
    private val OPENAI_KEY_PATTERN = Pattern.compile(
        "\\bsk-[A-Za-z0-9_\\-]{20,}\\b"
    )

    // Anthropic API keys (sk-ant-...)
    private val ANTHROPIC_KEY_PATTERN = Pattern.compile(
        "\\bsk-ant-[A-Za-z0-9_\\-]{20,}\\b"
    )

    // Google API Keys (AIza...)
    private val GOOGLE_KEY_PATTERN = Pattern.compile(
        "\\bAIza[0-9A-Za-z_\\-]{35}\\b"
    )

    // GitHub Personal Access Tokens (ghp_..., gho_..., github_pat_...)
    private val GITHUB_TOKEN_PATTERN = Pattern.compile(
        "\\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{36,}\\b|\\bgithub_pat_[A-Za-z0-9_]{30,}\\b"
    )

    // Supabase / JWT tokens (ey...)
    private val JWT_TOKEN_PATTERN = Pattern.compile(
        "\\bey[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}\\b"
    )

    // Generic Authorization Bearer tokens
    private val BEARER_TOKEN_PATTERN = Pattern.compile(
        "(?i)\\b(bearer\\s+)[A-Za-z0-9_\\-\\.~+/]{20,}\\b"
    )

    // Explicit secret key assignments (e.g. api_key = "...", secret = '...')
    private val EXPLICIT_SECRET_PATTERN = Pattern.compile(
        "(?i)\\b(api[_-]?key|secret[_-]?key|access[_-]?token|auth[_-]?token)(\\s*[:=]\\s*[\"']?)[A-Za-z0-9_\\-\\.~+/]{16,}([\"']?)"
    )

    fun redact(input: String): RedactionResult {
        if (input.isBlank()) {
            return RedactionResult(input, wasRedacted = false, redactionsCount = 0)
        }

        var result = input
        var count = 0

        fun applyRegex(pattern: Pattern, replacement: String) {
            val matcher = pattern.matcher(result)
            var matches = 0
            while (matcher.find()) {
                matches++
            }
            if (matches > 0) {
                count += matches
                result = pattern.matcher(result).replaceAll(replacement)
            }
        }

        // Apply redactions in priority order
        applyRegex(OPENAI_KEY_PATTERN, "[REDACTED_API_KEY]")
        applyRegex(ANTHROPIC_KEY_PATTERN, "[REDACTED_API_KEY]")
        applyRegex(GOOGLE_KEY_PATTERN, "[REDACTED_API_KEY]")
        applyRegex(GITHUB_TOKEN_PATTERN, "[REDACTED_GITHUB_TOKEN]")
        applyRegex(JWT_TOKEN_PATTERN, "[REDACTED_JWT_TOKEN]")
        applyRegex(EMAIL_PATTERN, "[REDACTED_EMAIL]")
        applyRegex(PHONE_PATTERN, "[REDACTED_PHONE]")

        // Regex with group references
        val bearerMatcher = BEARER_TOKEN_PATTERN.matcher(result)
        var bearerMatches = 0
        while (bearerMatcher.find()) bearerMatches++
        if (bearerMatches > 0) {
            count += bearerMatches
            result = BEARER_TOKEN_PATTERN.matcher(result).replaceAll("$1[REDACTED_TOKEN]")
        }

        val secretMatcher = EXPLICIT_SECRET_PATTERN.matcher(result)
        var secretMatches = 0
        while (secretMatcher.find()) secretMatches++
        if (secretMatches > 0) {
            count += secretMatches
            result = EXPLICIT_SECRET_PATTERN.matcher(result).replaceAll("$1$2[REDACTED_SECRET]$3")
        }

        return RedactionResult(
            text = result,
            wasRedacted = count > 0,
            redactionsCount = count
        )
    }
}
