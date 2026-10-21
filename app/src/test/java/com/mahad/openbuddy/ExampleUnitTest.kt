package com.mahad.openbuddy

import com.mahad.openbuddy.domain.model.ProviderType
import com.mahad.openbuddy.ui.components.MarkdownBlock
import com.mahad.openbuddy.ui.components.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testMarkdownParsingWithCodeBlocks() {
        val raw = """
            Here is some text.
            ```kotlin
            fun hello() = "world"
            ```
            And another paragraph.
        """.trimIndent()

        val blocks = parseMarkdown(raw)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        assertTrue(blocks[1] is MarkdownBlock.Code)
        val codeBlock = blocks[1] as MarkdownBlock.Code
        assertEquals("kotlin", codeBlock.language)
        assertEquals("fun hello() = \"world\"", codeBlock.code)
    }

    @Test
    fun testProviderTypes() {
        assertEquals(ProviderType.GEMINI, ProviderType.fromString("GEMINI"))
        assertEquals(ProviderType.CLAUDE, ProviderType.fromString("claude"))
        assertEquals(ProviderType.KIMI, ProviderType.fromString("kimi"))
        assertEquals(ProviderType.CUSTOM_OPENAI, ProviderType.fromString("custom_openai"))
    }

    @Test
    fun testSensitiveDataRedactor() {
        val input = "Contact me at dev@example.com or 555-234-5678. Key: sk-123456789012345678901234 and gh_pat: ghp_123456789012345678901234567890123456"
        val result = com.mahad.openbuddy.data.security.SensitiveDataRedactor.redact(input)
        assertTrue(result.wasRedacted)
        assertEquals(4, result.redactionsCount)
        assertTrue(result.text.contains("[REDACTED_EMAIL]"))
        assertTrue(result.text.contains("[REDACTED_PHONE]"))
        assertTrue(result.text.contains("[REDACTED_API_KEY]"))
        assertTrue(result.text.contains("[REDACTED_GITHUB_TOKEN]"))
    }

    @Test
    fun testSqlSafetyClassification() {
        // Read-only
        val selectCls = com.mahad.openbuddy.data.remote.supabase.SqlSafetyClassifier.classify("SELECT * FROM users WHERE active = true;")
        assertEquals(com.mahad.openbuddy.domain.model.StatementSafetyType.READ_ONLY, selectCls.type)
        assertEquals("users", selectCls.targetTable)
        assertEquals(false, selectCls.hasMultipleStatements)

        // Auto limit
        val (sqlWithLimit, added) = com.mahad.openbuddy.data.remote.supabase.SqlSafetyClassifier.sanitizeAndApplyLimit("SELECT * FROM users")
        assertTrue(added)
        assertTrue(sqlWithLimit.contains("LIMIT 100"))

        val (sqlKeepLimit, added2) = com.mahad.openbuddy.data.remote.supabase.SqlSafetyClassifier.sanitizeAndApplyLimit("SELECT * FROM users LIMIT 25")
        assertEquals(false, added2)
        assertEquals("SELECT * FROM users LIMIT 25", sqlKeepLimit)

        // Multiple statements rejection
        val multiCls = com.mahad.openbuddy.data.remote.supabase.SqlSafetyClassifier.classify("SELECT 1; DROP TABLE users;")
        assertTrue(multiCls.hasMultipleStatements)
        assertEquals(2, multiCls.statementCount)

        // Mutating write
        val insertCls = com.mahad.openbuddy.data.remote.supabase.SqlSafetyClassifier.classify("INSERT INTO customers (name) VALUES ('Alice');")
        assertEquals(com.mahad.openbuddy.domain.model.StatementSafetyType.MUTATING_WRITE, insertCls.type)
        assertEquals("customers", insertCls.targetTable)

        val updateWhereCls = com.mahad.openbuddy.data.remote.supabase.SqlSafetyClassifier.classify("UPDATE products SET price = 10 WHERE id = 1;")
        assertEquals(com.mahad.openbuddy.domain.model.StatementSafetyType.MUTATING_WRITE, updateWhereCls.type)

        // Destructive statements
        val dropCls = com.mahad.openbuddy.data.remote.supabase.SqlSafetyClassifier.classify("DROP TABLE analytics;")
        assertEquals(com.mahad.openbuddy.domain.model.StatementSafetyType.DESTRUCTIVE, dropCls.type)
        assertEquals("analytics", dropCls.targetTable)

        val truncateCls = com.mahad.openbuddy.data.remote.supabase.SqlSafetyClassifier.classify("TRUNCATE TABLE logs;")
        assertEquals(com.mahad.openbuddy.domain.model.StatementSafetyType.DESTRUCTIVE, truncateCls.type)
        assertEquals("logs", truncateCls.targetTable)

        val updateNoWhereCls = com.mahad.openbuddy.data.remote.supabase.SqlSafetyClassifier.classify("UPDATE products SET price = 0;")
        assertEquals(com.mahad.openbuddy.domain.model.StatementSafetyType.DESTRUCTIVE, updateNoWhereCls.type)

        val deleteCls = com.mahad.openbuddy.data.remote.supabase.SqlSafetyClassifier.classify("DELETE FROM orders WHERE id = 5;")
        assertEquals(com.mahad.openbuddy.domain.model.StatementSafetyType.DESTRUCTIVE, deleteCls.type)
    }
}
