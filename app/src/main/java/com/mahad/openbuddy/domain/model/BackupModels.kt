package com.mahad.openbuddy.domain.model

import com.mahad.openbuddy.data.local.entity.AiProviderEntity
import com.mahad.openbuddy.data.local.entity.CodeDraftEntity
import com.mahad.openbuddy.data.local.entity.ConversationEntity
import com.mahad.openbuddy.data.local.entity.MessageEntity
import com.mahad.openbuddy.data.local.entity.QueryHistoryEntity

/**
 * Represents the structured payload of an Open Buddy local backup before encryption.
 * Explicitly excludes all API keys, tokens, and passwords.
 */
data class LocalBackupData(
    val version: Int = 1,
    val appName: String = "Open Buddy",
    val exportedAt: Long = System.currentTimeMillis(),
    val conversations: List<ConversationEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
    val aiProviders: List<AiProviderEntity> = emptyList(),
    val queryHistory: List<QueryHistoryEntity> = emptyList(),
    val codeDrafts: List<CodeDraftEntity> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    val integrationsMetadata: Map<String, String?> = emptyMap()
)

/**
 * Encrypted envelope format stored to the user's filesystem.
 */
data class BackupEnvelope(
    val magic: String = MAGIC_HEADER,
    val version: Int = CURRENT_VERSION,
    val kdf: String = "PBKDF2WithHmacSHA256",
    val iterations: Int = PBKDF2_ITERATIONS,
    val salt: String,       // Base64 encoded
    val iv: String,         // Base64 encoded
    val ciphertext: String, // Base64 encoded
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MAGIC_HEADER = "OPEN_BUDDY_BACKUP"
        const val LEGACY_MAGIC_HEADER = "CODEPILOT_BACKUP"
        const val CURRENT_VERSION = 1
        const val PBKDF2_ITERATIONS = 100_000
    }
}

/**
 * Tracked metadata for the most recent local backup saved on this device.
 */
data class LastBackupInfo(
    val timestamp: Long,
    val sizeBytes: Long,
    val fileName: String
) {
    val formattedSize: String
        get() {
            if (sizeBytes < 1024) return "$sizeBytes B"
            val kb = sizeBytes / 1024.0
            if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            return String.format(java.util.Locale.US, "%.2f MB", mb)
        }
}

/**
 * Strategy for restoring data from a backup.
 */
enum class RestoreMode {
    MERGE,
    REPLACE
}

/**
 * Preview summary displayed to the user after successful decryption and validation of a backup.
 */
data class RestorePreviewSummary(
    val backupCreatedAt: Long,
    val conversationCount: Int,
    val messageCount: Int,
    val queryHistoryCount: Int,
    val draftCount: Int,
    val providerCount: Int,
    val settingsCount: Int,
    val rawBackupData: LocalBackupData
)

/**
 * Domain exceptions for backup encryption and restoration.
 */
sealed class BackupRestoreException(message: String) : Exception(message) {
    class WrongPassphrase : BackupRestoreException("Incorrect passphrase. Decryption failed.")
    class CorruptedFile(details: String = "") : BackupRestoreException("The backup file is corrupted or not a valid Open Buddy backup. ${details.trim()}".trim())
    class IncompatibleVersion(val fileVersion: Int, val supportedVersion: Int) : BackupRestoreException("Incompatible backup version (v$fileVersion). This version of Open Buddy supports up to v$supportedVersion.")
    class InvalidStructure(details: String) : BackupRestoreException("Backup content structure is invalid: $details")
}
