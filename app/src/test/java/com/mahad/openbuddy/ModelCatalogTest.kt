package com.mahad.openbuddy

import com.mahad.openbuddy.data.provider.ModelCatalog
import com.mahad.openbuddy.domain.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun pickDefaultModel_geminiPrefersFlash25() {
        val models = listOf("gemini-2.5-pro", "gemini-2.5-flash", "text-embedding-004")
        assertEquals("gemini-2.5-flash", ModelCatalog.pickDefaultModel(ProviderType.GEMINI, models))
    }

    @Test
    fun pickDefaultModel_claudePrefersSonnet() {
        val models = listOf("claude-haiku-4-5", "claude-sonnet-4-5")
        assertEquals("claude-sonnet-4-5", ModelCatalog.pickDefaultModel(ProviderType.CLAUDE, models))
    }

    @Test
    fun pickDefaultModel_openAiCompatibleSkipsNonChatModels() {
        val models = listOf("whisper-1", "text-embedding-3-small", "gpt-4o-mini", "gpt-4o")
        assertEquals("gpt-4o", ModelCatalog.pickDefaultModel(ProviderType.CUSTOM_OPENAI, models))
    }

    @Test
    fun pickDefaultModel_emptyListReturnsNull() {
        assertNull(ModelCatalog.pickDefaultModel(ProviderType.GEMINI, emptyList()))
    }
}
