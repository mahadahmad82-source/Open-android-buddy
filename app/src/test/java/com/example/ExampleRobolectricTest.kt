package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Open Buddy", appName)
  }

  @Test
  fun `test LocalBackupManager encrypt and decrypt success`() {
    val testData = com.example.domain.model.LocalBackupData(
      conversations = listOf(
        com.example.data.local.entity.ConversationEntity(
          id = "conv-1",
          title = "Test Chat",
          createdAt = 1000L,
          updatedAt = 2000L
        )
      ),
      messages = listOf(
        com.example.data.local.entity.MessageEntity(
          id = "msg-1",
          conversationId = "conv-1",
          role = "USER",
          content = "Hello World",
          timestamp = 1500L
        )
      ),
      codeDrafts = listOf(
        com.example.data.local.entity.CodeDraftEntity(
          id = "draft-1",
          filename = "sample.kt",
          language = "kotlin",
          code = "fun main() = println(\"Test\")",
          createdAt = 1000L
        )
      ),
      queryHistory = listOf(
        com.example.data.local.entity.QueryHistoryEntity(
          id = "query-1",
          query = "SELECT * FROM test",
          category = "SELECT",
          timestamp = 1200L,
          resultSummary = "10 rows",
          executionTimeMs = 15,
          isSuccess = true
        )
      ),
      aiProviders = listOf(
        com.example.data.local.entity.AiProviderEntity(
          id = "prov-gemini",
          name = "Gemini Pro",
          type = "GEMINI",
          modelName = "gemini-2.5-flash",
          baseUrl = null,
          isDefault = true
        )
      ),
      settings = mapOf(
        "pref_biometric_lock_enabled" to "true",
        "pref_auto_lock_timeout_minutes" to "5"
      ),
      integrationsMetadata = mapOf(
        "hasGitHubConnected" to "true",
        "supabaseUrl" to "https://xyz.supabase.co"
      )
    )

    val passphrase = "SecurePassword123!"
    val plainJson = com.example.data.backup.LocalBackupManager.serializeBackupData(testData)
    val envelopeJson = com.example.data.backup.LocalBackupManager.encryptBackup(plainJson, passphrase.toCharArray())
    org.junit.Assert.assertTrue(envelopeJson.contains("OPEN_BUDDY_BACKUP"))
    org.junit.Assert.assertFalse(envelopeJson.contains("Hello World"))
    org.junit.Assert.assertFalse(envelopeJson.contains(passphrase))

    val summary = com.example.data.backup.LocalBackupManager.decryptAndValidateBackup(envelopeJson, passphrase.toCharArray())
    assertEquals(1, summary.conversationCount)
    assertEquals(1, summary.messageCount)
    assertEquals(1, summary.draftCount)
    assertEquals(1, summary.queryHistoryCount)
    assertEquals(1, summary.providerCount)
    assertEquals(2, summary.settingsCount)

    val raw = summary.rawBackupData
    assertEquals(1, raw.conversations.size)
    assertEquals("Test Chat", raw.conversations[0].title)
    assertEquals(1, raw.messages.size)
    assertEquals("Hello World", raw.messages[0].content)
    assertEquals("https://xyz.supabase.co", raw.integrationsMetadata["supabaseUrl"])
  }

  @Test(expected = com.example.domain.model.BackupRestoreException.WrongPassphrase::class)
  fun `test LocalBackupManager wrong passphrase throws exception`() {
    val testData = com.example.domain.model.LocalBackupData(
      conversations = listOf(
        com.example.data.local.entity.ConversationEntity(
          id = "conv-1",
          title = "Test Chat",
          createdAt = 1000L,
          updatedAt = 2000L
        )
      )
    )
    val plainJson = com.example.data.backup.LocalBackupManager.serializeBackupData(testData)
    val envelopeJson = com.example.data.backup.LocalBackupManager.encryptBackup(plainJson, "CorrectPassword".toCharArray())
    com.example.data.backup.LocalBackupManager.decryptAndValidateBackup(envelopeJson, "WrongPassword".toCharArray())
  }

  @Test(expected = com.example.domain.model.BackupRestoreException.CorruptedFile::class)
  fun `test LocalBackupManager corrupted envelope throws exception`() {
    val badJson = """{"magic":"BAD_MAGIC","version":1}"""
    com.example.data.backup.LocalBackupManager.decryptAndValidateBackup(badJson, "AnyPassword".toCharArray())
  }
}
